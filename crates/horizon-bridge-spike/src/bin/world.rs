//! M1 step 2 (docs/bridge/m1-design.md): a fixture's own block, block entity, commands and ticker
//! run unchanged, through the bridge's stand-ins, in a world Rust drives tick by tick. The scenario
//! is the NeoForge reference run's: reset, 3 interactions, a 100-tick trace, pause, save and restore,
//! resume. Exits non-zero when a check fails.
//!
//!     horizon-bridge-world --jar REMAPPED.jar --mod ID --block ID --command ROOT
//!         [--reference TRACE.jsonl] --expect-100 JSON --expect-paused JSON --expect-resumed JSON
use jni::objects::{JObject, JString, JValue};
use jni::{InitArgsBuilder, JNIEnv, JNIVersion, JavaVM};
use std::collections::HashMap;
use std::time::Instant;

const WORLD: &str = "dev/horizonlab/bridge/host/HostWorld";
const S: &str = "Ljava/lang/String;";

type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;

fn main() {
    let mut options = HashMap::new();
    let mut args = std::env::args().skip(1);
    while let Some(key) = args.next() {
        let Some(value) = args.next() else { usage() };
        options.insert(key.trim_start_matches("--").to_owned(), value);
    }
    for required in [
        "jar",
        "mod",
        "block",
        "command",
        "expect-100",
        "expect-paused",
        "expect-resumed",
    ] {
        if !options.contains_key(required) {
            usage();
        }
    }
    match run(&options) {
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
    eprintln!(
        "usage: horizon-bridge-world --jar REMAPPED.jar --mod ID --block ID --command ROOT \
         [--reference TRACE.jsonl] --expect-100 JSON --expect-paused JSON --expect-resumed JSON"
    );
    std::process::exit(2);
}

fn run(o: &HashMap<String, String>) -> Result<usize> {
    let mut failed = 0;
    let mut check = |name: &str, ok: bool, detail: &str| {
        println!("{} {name}: {detail}", if ok { "PASS" } else { "FAIL" });
        failed += usize::from(!ok);
    };
    let class_path = std::env::join_paths([
        env!("SPIKE_HOST_CLASSES").as_ref(),
        std::path::Path::new(&o["jar"]),
    ])?;
    let vm = JavaVM::new(
        InitArgsBuilder::new()
            .version(JNIVersion::V8)
            .option(format!(
                "-Djava.class.path={}",
                class_path.to_string_lossy()
            ))
            .option("-Xrs")
            .option("-Xverify:all")
            .build()?,
    )?;
    let mut env = vm.attach_current_thread()?;
    let w = World;
    let (x, y, z) = (0, 64, 0);
    let at = format!("{x} {y} {z}");
    let cmd = |line: &str| format!("{} {line}", o["command"]);

    let start = Instant::now();
    let registered = w.call(&mut env, "load", &[&o["jar"], &o["mod"]])?;
    w.call(&mut env, "start", &[])?;
    println!(
        "INFO load and start: {:.1} ms",
        start.elapsed().as_secs_f64() * 1e3
    );
    let block = &o["block"];
    check(
        "registrations",
        registered == format!("block {block}; item {block}; block_entity_type {block}"),
        &registered,
    );

    w.place(&mut env, block, x, y, z)?;
    w.call(&mut env, "command", &[&cmd(&format!("reset {at}"))])?;
    for _ in 0..3 {
        w.call(&mut env, "command", &[&cmd(&format!("interact {at}"))])?;
    }
    w.call(&mut env, "command", &[&cmd(&format!("trace {at} 100"))])?;
    let start = Instant::now();
    for _ in 0..100 {
        w.call(&mut env, "tick", &[])?;
    }
    let tick_time = start.elapsed();
    let log = w.call(&mut env, "drainLog", &[])?;
    let trace: Vec<String> = log
        .lines()
        .filter_map(|line| line.find('{').map(|i| line[i..].to_owned()))
        .collect();
    println!(
        "INFO 100 ticks: {:.0} µs per tick",
        tick_time.as_secs_f64() * 1e4
    );
    check(
        "100 trace lines",
        trace.len() == 100,
        &format!("{} lines", trace.len()),
    );
    check(
        "state after 100 ticks",
        trace.last() == Some(&o["expect-100"]),
        trace.last().map_or("-", String::as_str),
    );
    if let Some(reference) = o.get("reference") {
        let expected: Vec<String> = std::fs::read_to_string(reference)?
            .lines()
            .map(str::to_owned)
            .collect();
        let first = trace.iter().zip(&expected).position(|(a, b)| a != b);
        let same = first.is_none() && trace.len() == expected.len();
        let detail = match first {
            Some(i) => format!("tick {} differs: {} vs {}", i + 1, trace[i], expected[i]),
            None => format!("{} of {} samples identical", trace.len(), expected.len()),
        };
        check("trace matches the NeoForge reference", same, &detail);
    }

    w.call(&mut env, "command", &[&cmd(&format!("pause {at}"))])?;
    for _ in 0..20 {
        w.call(&mut env, "tick", &[])?;
    }
    let paused = w.call(&mut env, "command", &[&cmd(&format!("state {at}"))])?;
    check("paused 20 ticks", paused == o["expect-paused"], &paused);
    let saved = w.at(&mut env, "save", x, y, z, None)?;
    println!("INFO saved: {saved}");
    w.at(&mut env, "restore", x, y, z, Some(&saved))?;
    let restored = w.call(&mut env, "command", &[&cmd(&format!("state {at}"))])?;
    check(
        "restored from the saved form",
        restored == o["expect-paused"],
        &restored,
    );
    let verified = w.call(&mut env, "command", &[&cmd(&format!("verify {at}"))]);
    check(
        "mod's own save/load round trip (verify)",
        verified.is_ok(),
        &format!("{verified:?}"),
    );
    w.call(&mut env, "command", &[&cmd(&format!("resume {at}"))])?;
    for _ in 0..20 {
        w.call(&mut env, "tick", &[])?;
    }
    let resumed = w.call(&mut env, "command", &[&cmd(&format!("state {at}"))])?;
    check("resumed 20 ticks", resumed == o["expect-resumed"], &resumed);

    w.place(&mut env, block, x, y, z + 1)?;
    let reply = w.at(&mut env, "use", x, y, z + 1, None)?;
    check(
        "right-click reaches useWithoutItem",
        reply.contains("\"interactions\":1"),
        &reply,
    );

    let bad = w.call(&mut env, "command", &[&cmd("nonsense")]);
    let bad = match bad {
        Ok(text) => format!("no error: {text}"),
        Err(_) => exception(&mut env)?,
    };
    check(
        "bad command fails with a message",
        bad.contains("Unknown or incomplete command"),
        &bad,
    );
    Ok(failed)
}

/// Calls into `HostWorld`'s static methods with strings and ints only.
struct World;

impl World {
    fn call(&self, env: &mut JNIEnv, name: &str, args: &[&str]) -> Result<String> {
        let strings = args
            .iter()
            .map(|a| env.new_string(a))
            .collect::<jni::errors::Result<Vec<_>>>()?;
        let values: Vec<JValue> = strings.iter().map(|s| JValue::Object(s)).collect();
        let returns = !matches!(name, "start" | "tick");
        let sig = format!(
            "({}){}",
            S.repeat(args.len()),
            if returns { S } else { "V" }
        );
        let value = env.call_static_method(WORLD, name, sig, &values)?;
        if !returns {
            return Ok(String::new());
        }
        string(env, value.l()?)
    }

    fn place(&self, env: &mut JNIEnv, block: &str, x: i32, y: i32, z: i32) -> Result<()> {
        let block = env.new_string(block)?;
        env.call_static_method(
            WORLD,
            "place",
            "(Ljava/lang/String;III)V",
            &[
                JValue::Object(&block),
                JValue::Int(x),
                JValue::Int(y),
                JValue::Int(z),
            ],
        )?;
        Ok(())
    }

    /// `save`/`use` (returning a string) or `restore` (taking one) at a position.
    fn at(
        &self,
        env: &mut JNIEnv,
        name: &str,
        x: i32,
        y: i32,
        z: i32,
        text: Option<&str>,
    ) -> Result<String> {
        let mut values = vec![JValue::Int(x), JValue::Int(y), JValue::Int(z)];
        let text = text.map(|t| env.new_string(t)).transpose()?;
        if let Some(text) = &text {
            values.push(JValue::Object(text));
            env.call_static_method(WORLD, name, "(IIILjava/lang/String;)V", &values)?;
            return Ok(String::new());
        }
        let value = env.call_static_method(WORLD, name, "(III)Ljava/lang/String;", &values)?;
        string(env, value.l()?)
    }
}

fn string(env: &mut JNIEnv, object: JObject) -> Result<String> {
    Ok(env.get_string(&JString::from(object))?.into())
}

/// The pending Java exception as `toString()`, cleared.
fn exception(env: &mut JNIEnv) -> Result<String> {
    if !env.exception_check()? {
        return Ok("error without a Java exception".into());
    }
    let throwable = env.exception_occurred()?;
    env.exception_clear()?;
    let text = env
        .call_method(&throwable, "toString", "()Ljava/lang/String;", &[])?
        .l()?;
    string(env, text)
}
