//! M0 spike (docs/bridge/m0-spike.md): load the unchanged fixture JAR into an embedded JVM, run its
//! original `CounterState` logic from Rust, compare every tick with the NeoForge reference trace and
//! measure what the calls cost. Exits non-zero when a check fails.
use jni::objects::{JClass, JObject, JString, JValue};
use jni::signature::{Primitive, ReturnType};
use jni::sys::jlong;
use jni::{InitArgsBuilder, JNIEnv, JNIVersion, JavaVM, NativeMethod};
use sha2::{Digest, Sha256};
use std::sync::Mutex;
use std::time::Instant;

const COUNTER: &str = "dev/horizonlab/bridge/CounterState";
const PROBE: &str = "dev/horizonlab/bridge/spike/HostProbe";
/// The jar tested in the NeoForge reference run (mods/horizon/src/neoforge-fixture/README.md).
const FIXTURE_SHA256: &str = "106bd4130c253805768c293d86a9afc31e17984797b5389a6193818f676837b0";
const CALLS: i32 = 1_000_000;

static REPORTS: Mutex<Vec<String>> = Mutex::new(Vec::new());

type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;

/// The fields Horizon would keep in its own save for one counter.
#[derive(Debug, PartialEq)]
struct Saved {
    value: i32,
    ticks: i64,
    interactions: i32,
    paused: bool,
}

fn main() {
    let mut args = std::env::args().skip(1);
    let (mut fixture, mut reference) = (None, None);
    while let Some(arg) = args.next() {
        match arg.as_str() {
            "--fixture" => fixture = args.next(),
            "--reference" => reference = args.next(),
            _ => usage(),
        }
    }
    let (Some(fixture), Some(reference)) = (fixture, reference) else {
        usage()
    };
    match run(&fixture, &reference) {
        Ok(0) => println!("RESULT pass"),
        Ok(failed) => {
            println!("RESULT fail ({failed} checks)");
            std::process::exit(1);
        }
        Err(e) => {
            println!("RESULT error: {e}");
            std::process::exit(2);
        }
    }
}

fn usage() -> ! {
    eprintln!("usage: horizon-bridge-spike --fixture FIXTURE.jar --reference TRACE.jsonl");
    std::process::exit(2);
}

fn run(fixture: &str, reference: &str) -> Result<usize> {
    let mut failed = 0;
    let mut check = |name: &str, ok: bool, detail: String| {
        println!("{} {name}: {detail}", if ok { "PASS" } else { "FAIL" });
        failed += usize::from(!ok);
    };

    let jar = std::fs::read(fixture)?;
    let sha = format!("{:x}", Sha256::digest(&jar));
    check("fixture hash", sha == FIXTURE_SHA256, sha);
    let expected: Vec<String> = std::fs::read_to_string(reference)?
        .lines()
        .map(str::to_owned)
        .collect();
    if expected.len() != 100 {
        return Err(format!("reference trace has {} lines, expected 100", expected.len()).into());
    }

    let before = memory();
    let start = Instant::now();
    let class_path = std::env::join_paths([
        std::path::Path::new(fixture),
        env!("SPIKE_CLASSES").as_ref(),
    ])?;
    let vm = JavaVM::new(
        InitArgsBuilder::new()
            .version(JNIVersion::V8)
            .option(format!(
                "-Djava.class.path={}",
                class_path.to_string_lossy()
            ))
            // The host owns signals (Ctrl+C, crashes), as a game would.
            .option("-Xrs")
            .option("-Xmx256m")
            .build()?,
    )?;
    let startup = start.elapsed();
    let mut env = vm.attach_current_thread()?;
    println!("INFO jvm start: {:.1} ms", startup.as_secs_f64() * 1e3);
    println!(
        "INFO java: {}",
        system_property(&mut env, "java.vm.name")?
            + " "
            + &system_property(&mut env, "java.version")?
    );

    let probe = env.find_class(PROBE)?;
    env.register_native_methods(
        &probe,
        &[
            NativeMethod {
                name: "report".into(),
                sig: "(Ljava/lang/String;)V".into(),
                fn_ptr: report as *mut _,
            },
            NativeMethod {
                name: "echo".into(),
                sig: "(J)J".into(),
                fn_ptr: echo as *mut _,
            },
        ],
    )?;

    // 7. Origin: the logic runs from the fixture jar, not from a copy.
    let name = env.new_string("dev.horizonlab.bridge.CounterState")?;
    let origin = env_call_static(
        &mut env,
        &probe,
        "origin",
        "(Ljava/lang/String;)Ljava/lang/String;",
        &[JValue::Object(&name)],
    )?;
    let origin = string(&mut env, origin)?;
    check(
        "origin",
        origin.ends_with("horizon-bridge-fixture-0.1.0.jar"),
        origin,
    );

    // 1. Trace from Rust through JNI.
    let trace = scenario(&mut env)?;
    check(
        "trace (Rust -> Java)",
        trace == expected,
        first_difference(&trace, &expected),
    );

    // 2. Persistence through state Rust owns.
    let counter = new_counter(
        &mut env,
        &Saved {
            value: 0,
            ticks: 0,
            interactions: 0,
            paused: false,
        },
    )?;
    for _ in 0..3 {
        env.call_method(&counter, "interact", "()V", &[])?;
    }
    for _ in 0..100 {
        env.call_method(&counter, "tick", "()V", &[])?;
    }
    env.set_field(&counter, "paused", "Z", JValue::Bool(1))?;
    for _ in 0..20 {
        env.call_method(&counter, "tick", "()V", &[])?;
    }
    let saved = save(&mut env, &counter)?;
    let paused_ok = saved
        == Saved {
            value: 19,
            ticks: 100,
            interactions: 3,
            paused: true,
        };
    let restored = new_counter(
        &mut env,
        &Saved {
            paused: false,
            ..saved
        },
    )?;
    for _ in 0..20 {
        env.call_method(&restored, "tick", "()V", &[])?;
    }
    let resumed = save(&mut env, &restored)?;
    check(
        "save, restore, resume",
        paused_ok
            && resumed
                == Saved {
                    value: 21,
                    ticks: 120,
                    interactions: 3,
                    paused: false,
                },
        format!("paused {saved:?}, resumed {resumed:?}"),
    );

    // 3. Java calling the Rust host on every tick.
    REPORTS.lock().unwrap().clear();
    env_call_static(
        &mut env,
        &probe,
        "run",
        "(II)Ljava/lang/String;",
        &[JValue::Int(3), JValue::Int(100)],
    )?;
    let reports = std::mem::take(&mut *REPORTS.lock().unwrap());
    check(
        "trace (Java -> Rust reports)",
        reports == expected,
        first_difference(&reports, &expected),
    );

    // 4. Another thread attached to the same JVM, as a server thread would be.
    let threaded = std::thread::scope(|s| {
        s.spawn(|| -> std::result::Result<Vec<String>, String> {
            let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
            scenario(&mut env).map_err(|e| e.to_string())
        })
        .join()
        .unwrap()
    })?;
    check(
        "trace on a second thread",
        threaded == expected,
        first_difference(&threaded, &expected),
    );

    // 5. Java exceptions arrive in Rust as errors with their message; the JVM stays usable.
    let negative = new_counter(
        &mut env,
        &Saved {
            value: -1,
            ticks: 0,
            interactions: 0,
            paused: false,
        },
    );
    let negative = take_exception(&mut env, negative.map(|_| ()))?;
    check(
        "exception: invalid state",
        negative.contains("IllegalArgumentException"),
        negative,
    );
    let near_max = new_counter(
        &mut env,
        &Saved {
            value: i32::MAX - 1,
            ticks: 0,
            interactions: 0,
            paused: false,
        },
    )?;
    let overflow = env
        .call_method(&near_max, "interact", "()V", &[])
        .map(|_| ());
    let overflow = take_exception(&mut env, overflow)?;
    check(
        "exception: overflow",
        overflow.contains("ArithmeticException"),
        overflow,
    );
    let again = scenario(&mut env)?;
    check(
        "JVM usable after exceptions",
        again == expected,
        first_difference(&again, &expected),
    );

    // 6. The game-facing classes need Minecraft's; the first missing one is the host adapter's first job.
    let entity = env
        .find_class("dev/horizonlab/bridge/BridgeFixture$CounterEntity")
        .map(|_| ());
    let missing = take_exception(&mut env, entity)?;
    check(
        "missing game class is named",
        missing.contains("net/minecraft") || missing.contains("net.minecraft"),
        missing,
    );

    // Costs.
    let counter = new_counter(
        &mut env,
        &Saved {
            value: 0,
            ticks: 0,
            interactions: 0,
            paused: false,
        },
    )?;
    let start = Instant::now();
    for _ in 0..CALLS {
        env.call_method(&counter, "tick", "()V", &[])?;
    }
    report_cost("Rust -> Java call by name (tick)", start);
    let tick = env.get_method_id(COUNTER, "tick", "()V")?;
    let start = Instant::now();
    for _ in 0..CALLS {
        // SAFETY: `tick` is `()V` on `counter`'s class.
        unsafe {
            env.call_method_unchecked(&counter, tick, ReturnType::Primitive(Primitive::Void), &[])?
        };
    }
    report_cost("Rust -> Java call, cached id (tick)", start);
    let start = Instant::now();
    let sum = env_call_static(&mut env, &probe, "echoLoop", "(I)J", &[JValue::Int(CALLS)]);
    report_cost("Java -> Rust call (echo)", start);
    drop(sum);
    let start = Instant::now();
    for _ in 0..10_000 {
        env.with_local_frame(4, |env| -> jni::errors::Result<()> {
            env.call_method(&counter, "tick", "()V", &[])?;
            let json = env
                .call_method(&counter, "json", "()Ljava/lang/String;", &[])?
                .l()?;
            let _: String = env.get_string(&JString::from(json))?.into();
            Ok(())
        })?;
    }
    let per = start.elapsed().as_secs_f64() * 1e9 / 10_000.0;
    println!("INFO tick + state to Rust string: {per:.0} ns per tick");

    let after = memory();
    println!(
        "INFO process memory: {:.1} MiB before JVM, {:.1} MiB after",
        before as f64 / 1048576.0,
        after as f64 / 1048576.0
    );
    Ok(failed)
}

/// 3 interactions then 100 ticks on a fresh counter, each tick's JSON.
fn scenario(env: &mut JNIEnv) -> Result<Vec<String>> {
    let counter = new_counter(
        env,
        &Saved {
            value: 0,
            ticks: 0,
            interactions: 0,
            paused: false,
        },
    )?;
    for _ in 0..3 {
        env.call_method(&counter, "interact", "()V", &[])?;
    }
    let mut trace = Vec::with_capacity(100);
    for _ in 0..100 {
        env.call_method(&counter, "tick", "()V", &[])?;
        let json = env
            .call_method(&counter, "json", "()Ljava/lang/String;", &[])?
            .l()?;
        trace.push(string(env, json)?);
    }
    Ok(trace)
}

fn new_counter<'a>(env: &mut JNIEnv<'a>, s: &Saved) -> jni::errors::Result<JObject<'a>> {
    env.new_object(
        COUNTER,
        "(IJIZ)V",
        &[
            JValue::Int(s.value),
            JValue::Long(s.ticks),
            JValue::Int(s.interactions),
            JValue::Bool(u8::from(s.paused)),
        ],
    )
}

fn save(env: &mut JNIEnv, counter: &JObject) -> Result<Saved> {
    Ok(Saved {
        value: env.get_field(counter, "value", "I")?.i()?,
        ticks: env.get_field(counter, "ticks", "J")?.j()?,
        interactions: env.get_field(counter, "interactions", "I")?.i()?,
        paused: env.get_field(counter, "paused", "Z")?.z()?,
    })
}

fn env_call_static<'a>(
    env: &mut JNIEnv<'a>,
    class: &JClass,
    name: &str,
    sig: &str,
    args: &[JValue],
) -> jni::errors::Result<JObject<'a>> {
    let value = env.call_static_method(class, name, sig, args)?;
    Ok(value.l().unwrap_or_default())
}

fn string(env: &mut JNIEnv, object: JObject) -> jni::errors::Result<String> {
    Ok(env.get_string(&JString::from(object))?.into())
}

fn system_property(env: &mut JNIEnv, key: &str) -> Result<String> {
    let key = env.new_string(key)?;
    let value = env
        .call_static_method(
            "java/lang/System",
            "getProperty",
            "(Ljava/lang/String;)Ljava/lang/String;",
            &[JValue::Object(&key)],
        )?
        .l()?;
    Ok(string(env, value)?)
}

/// The pending Java exception from a failed call, as `toString()`, cleared so the JVM goes on.
fn take_exception(env: &mut JNIEnv, call: jni::errors::Result<()>) -> Result<String> {
    if call.is_ok() {
        return Ok("no exception".into());
    }
    if !env.exception_check()? {
        return Ok(format!(
            "error without a Java exception: {}",
            call.unwrap_err()
        ));
    }
    let throwable = env.exception_occurred()?;
    env.exception_clear()?;
    let text = env
        .call_method(&throwable, "toString", "()Ljava/lang/String;", &[])?
        .l()?;
    Ok(string(env, text)?)
}

fn first_difference(got: &[String], want: &[String]) -> String {
    match got.iter().zip(want).position(|(a, b)| a != b) {
        Some(i) => format!("tick {} differs: got {} want {}", i + 1, got[i], want[i]),
        None if got.len() != want.len() => format!("{} samples, want {}", got.len(), want.len()),
        None => format!(
            "{} samples identical, last {}",
            got.len(),
            got.last().map_or("-", String::as_str)
        ),
    }
}

fn report_cost(what: &str, start: Instant) {
    println!(
        "INFO {what}: {:.0} ns per call",
        start.elapsed().as_secs_f64() * 1e9 / f64::from(CALLS)
    );
}

fn memory() -> usize {
    memory_stats::memory_stats().map_or(0, |m| m.physical_mem)
}

extern "system" fn report(mut env: JNIEnv, _class: JClass, json: JString) {
    if let Ok(json) = env.get_string(&json) {
        if let Ok(mut reports) = REPORTS.lock() {
            reports.push(json.into());
        }
    }
}

extern "system" fn echo(_env: JNIEnv, _class: JClass, value: jlong) -> jlong {
    value + 1
}
