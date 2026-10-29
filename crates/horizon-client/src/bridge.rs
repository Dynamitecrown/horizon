//! NeoForge mods through the bridge (docs/bridge/m1-design.md, step 3): a content mod folder holding
//! `bridge.jar` (the mod's jar, renamed to the bridge's stand-ins by `tools/bridge_remap.py`) gets
//! its blocks from the content mod and its block entities, tickers, right-clicks and commands from
//! the jar, run unchanged in an embedded JVM. The JVM side (`HostWorld`) keys block entities by
//! position; Horizon's chunks hold `Bridged` entries and their saved form.
//!
//! Without the `bridge` feature nothing here starts a JVM and no block is bridged.
use crate::chunk::block_entity::{Bridged, BridgedSync};
use horizon_world::nbt::Tag;

/// The keys every saved block entity has, which the JVM side does not keep.
const BASE_KEYS: [&str; 6] = ["id", "x", "y", "z", "keepPacked", "components"];

/// A fresh `Bridged` entity for the block `name`, if a bridged mod registered it.
pub fn for_block(name: &str) -> Option<Bridged> {
    let (block, kind) = jvm::block(name)?;
    Some(Bridged {
        block,
        kind,
        sync: BridgedSync::Fresh,
    })
}

/// A `Bridged` entity loaded from a save, if `tag`'s id is a bridged block entity type.
pub fn decode(tag: &Tag) -> Option<Bridged> {
    let id = match tag.get("id") {
        Some(Tag::String(id)) => id.to_string_lossy(),
        _ => return None,
    };
    let (block, kind) = jvm::kind(&id)?;
    Some(Bridged {
        block,
        kind,
        sync: BridgedSync::Saved(to_saved(tag)?),
    })
}

/// The fields `encode_block_entity` adds for a bridged entity: the JVM's own saved form while it
/// is live there, else the form it was loaded with or last saved before it failed.
pub fn save(e: &Bridged, pos: (i32, i32, i32)) -> Vec<(String, Tag)> {
    let saved = match &e.sync {
        BridgedSync::Fresh | BridgedSync::Failed(None) => return Vec::new(),
        BridgedSync::Saved(saved) | BridgedSync::Failed(Some(saved)) => saved.clone(),
        BridgedSync::Live => match jvm::save(pos) {
            Ok(saved) => saved,
            Err(err) => {
                // shortcut: a mod whose own save throws loses this entity's fields from the save (the
                // chunk and every other entity are kept); upgrade by keeping the last good form.
                log::error!(
                    "Horizon bridge: saving {} at {pos:?}: {}",
                    e.kind,
                    describe(&err)
                );
                return Vec::new();
            }
        },
    };
    from_saved(&saved)
}

/// Puts the entity at `pos` into the JVM (fresh, or from its saved form) if it isn't there yet:
/// true when it is live there. Only the world that simulates calls this; a client's copy stays as
/// decoded.
pub fn sync(e: &mut Bridged, pos: (i32, i32, i32)) -> bool {
    let done = match &e.sync {
        BridgedSync::Live => return true,
        BridgedSync::Failed(_) => return false,
        BridgedSync::Fresh => jvm::place(e.block, pos),
        BridgedSync::Saved(saved) => jvm::load(pos, saved),
    };
    match done {
        Ok(()) => e.sync = BridgedSync::Live,
        Err(err) => {
            let saved = match std::mem::replace(&mut e.sync, BridgedSync::Fresh) {
                BridgedSync::Saved(saved) => Some(saved),
                _ => None,
            };
            fail(e, pos, "placing", &err, saved);
        }
    }
    matches!(e.sync, BridgedSync::Live)
}

/// One server tick of the live entity at `pos`.
pub fn tick(e: &mut Bridged, pos: (i32, i32, i32)) {
    if let Err(err) = jvm::tick(pos) {
        let saved = jvm::save(pos).ok();
        fail(e, pos, "ticking", &err, saved);
    }
    jvm::forward_log();
}

/// An empty-hand right-click on the live entity at `pos`: the messages the player gets.
pub fn use_block(e: &mut Bridged, pos: (i32, i32, i32)) -> Vec<String> {
    let out = jvm::use_block(pos).unwrap_or_else(|err| {
        let saved = jvm::save(pos).ok();
        fail(e, pos, "using", &err, saved);
        String::new()
    });
    jvm::forward_log();
    out.lines().map(str::to_owned).collect()
}

/// Stops the entity after the mod failed on it: it no longer ticks or reacts, and saves keep
/// `saved`, its last form. The error is logged once, naming what the bridge lacks.
fn fail(e: &mut Bridged, pos: (i32, i32, i32), doing: &str, err: &str, saved: Option<String>) {
    log::error!(
        "Horizon bridge: {} at {pos:?} stopped while {doing}: {}",
        e.kind,
        describe(err)
    );
    e.sync = BridgedSync::Failed(saved);
}

/// A Java error as players and mod authors should read it: a member the mod needs but the bridge
/// does not supply is named with the game's name for its class (`mc/Level.getGameTime()`).
fn describe(err: &str) -> String {
    const MISSING: [&str; 4] = [
        "NoSuchMethodError",
        "NoSuchFieldError",
        "NoClassDefFoundError",
        "AbstractMethodError",
    ];
    let named = err
        .replace("dev/horizonlab/bridge/host/", "")
        .replace("dev.horizonlab.bridge.host.", "");
    if MISSING.iter().any(|m| err.contains(m)) {
        format!("not supported by the bridge yet: {named}")
    } else {
        named
    }
}

/// Runs `command` (no slash) if a bridged mod registered its root: Ok with the feedback lines,
/// or Err with the failure message. None when no bridged mod handles it.
pub fn command(command: &str) -> Option<Result<Vec<String>, String>> {
    let out = jvm::command(command)?;
    jvm::forward_log();
    Some(out.map(|text| text.lines().map(str::to_owned).collect()))
}

/// Starts the JVM with these mods' jars (`(jar, mod id)`) and loads each. Call once, before the
/// world loads; the mods' blocks must also be in the content their folders declare.
pub fn start(mods: &[(std::path::PathBuf, String)]) {
    if !mods.is_empty() {
        jvm::start(mods);
    }
}

/// A saved block entity in the JVM side's form (`CompoundTag.toJson`): its id, then its own
/// fields, ints, longs (`L`) and booleans (bytes). None for a field of another type.
fn to_saved(tag: &Tag) -> Option<String> {
    let Tag::Compound(map) = tag else { return None };
    let Some(Tag::String(id)) = tag.get("id") else {
        return None;
    };
    let mut out = format!("{{\"id\":\"{}\"", id.to_string_lossy());
    for (key, value) in map.iter() {
        let key = key.to_string_lossy();
        if BASE_KEYS.contains(&key.as_ref()) {
            continue;
        }
        let value = match value {
            Tag::Int(v) => v.to_string(),
            Tag::Long(v) => format!("{v}L"),
            // shortcut: the stand-in `ValueOutput` writes no bytes but booleans; upgrade when it does.
            Tag::Byte(v) => (*v != 0).to_string(),
            _ => return None,
        };
        out.push_str(&format!(",\"{key}\":{value}"));
    }
    out.push('}');
    Some(out)
}

/// The fields of a JVM-side saved form, without the id (`encode_block_entity` writes it).
fn from_saved(saved: &str) -> Vec<(String, Tag)> {
    match horizon_world::nbt::parse_snbt(saved) {
        Ok((Tag::Compound(map), _)) => map
            .into_iter()
            .map(|(k, v)| (k.to_string_lossy(), v))
            .filter(|(k, _)| k != "id")
            .collect(),
        other => {
            log::error!("Horizon bridge: unreadable saved form {saved}: {other:?}");
            Vec::new()
        }
    }
}

#[cfg(not(feature = "bridge"))]
mod jvm {
    type Pos = (i32, i32, i32);
    pub fn block(_: &str) -> Option<(&'static str, &'static str)> {
        None
    }
    pub fn kind(_: &str) -> Option<(&'static str, &'static str)> {
        None
    }
    pub fn start(mods: &[(std::path::PathBuf, String)]) {
        log::warn!(
            "Horizon: {} bridged mod(s) not started: this build has no bridge (build with --features bridge)",
            mods.len()
        );
    }
    pub fn place(_: &str, _: Pos) -> Result<(), String> {
        Err("no bridge in this build".into())
    }
    pub fn load(_: Pos, _: &str) -> Result<(), String> {
        Err("no bridge in this build".into())
    }
    pub fn save(_: Pos) -> Result<String, String> {
        Err("no bridge in this build".into())
    }
    pub fn tick(_: Pos) -> Result<(), String> {
        Ok(())
    }
    pub fn use_block(_: Pos) -> Result<String, String> {
        Ok(String::new())
    }
    pub fn command(_: &str) -> Option<Result<String, String>> {
        None
    }
    pub fn forward_log() {}
}

#[cfg(feature = "bridge")]
mod jvm {
    use jni::objects::{JObject, JString, JValue, JValueOwned};
    use jni::{InitArgsBuilder, JNIEnv, JNIVersion, JavaVM};
    use std::sync::OnceLock;

    type Pos = (i32, i32, i32);
    const WORLD: &str = "dev/horizonlab/bridge/host/HostWorld";
    const S: &str = "Ljava/lang/String;";

    struct Bridge {
        vm: JavaVM,
        /// Bridged blocks and their block entity types, interned for `Bridged`.
        blocks: Vec<(&'static str, &'static str)>,
    }
    static BRIDGE: OnceLock<Bridge> = OnceLock::new();

    pub fn block(name: &str) -> Option<(&'static str, &'static str)> {
        BRIDGE
            .get()?
            .blocks
            .iter()
            .copied()
            .find(|(b, _)| *b == name)
    }
    pub fn kind(id: &str) -> Option<(&'static str, &'static str)> {
        BRIDGE.get()?.blocks.iter().copied().find(|(_, k)| *k == id)
    }

    pub fn start(mods: &[(std::path::PathBuf, String)]) {
        if let Err(e) = try_start(mods) {
            log::error!("Horizon: bridged mods not started: {e}");
        }
    }
    fn try_start(mods: &[(std::path::PathBuf, String)]) -> Result<(), String> {
        let host = std::path::PathBuf::from(env!("HORIZON_BRIDGE_HOST_CLASSES"));
        let class_path = std::env::join_paths(
            std::iter::once(host).chain(mods.iter().map(|(jar, _)| jar.clone())),
        )
        .map_err(|e| e.to_string())?;
        let args = InitArgsBuilder::new()
            .version(JNIVersion::V8)
            .option(format!(
                "-Djava.class.path={}",
                class_path.to_string_lossy()
            ))
            // The game handles its own signals.
            .option("-Xrs")
            .build()
            .map_err(|e| e.to_string())?;
        let vm = JavaVM::new(args).map_err(|e| format!("starting the JVM: {e}"))?;
        let mut blocks = Vec::new();
        {
            let mut env = vm
                .attach_current_thread_permanently()
                .map_err(|e| e.to_string())?;
            for (jar, id) in mods {
                let jar = jar.to_string_lossy();
                let v = call(&mut env, "load", &format!("({S}{S}){S}"), &[&*jar, id])?;
                let registered = string(&mut env, v)?;
                log::info!("Horizon bridge: mod {id} registered {registered}");
                for entry in registered.split("; ") {
                    if let Some(block) = entry.strip_prefix("block ") {
                        let v = call(&mut env, "typeOf", &format!("({S}){S}"), &[block])?;
                        let kind = string(&mut env, v)?;
                        if !kind.is_empty() {
                            let leak =
                                |s: String| -> &'static str { Box::leak(s.into_boxed_str()) };
                            blocks.push((leak(block.to_owned()), leak(kind)));
                        }
                    }
                }
            }
            call(&mut env, "start", "()V", &[])?;
        }
        BRIDGE
            .set(Bridge { vm, blocks })
            .map_err(|_| "the bridge was started twice".to_string())
    }

    fn with<T>(f: impl FnOnce(&mut JNIEnv) -> Result<T, String>) -> Result<T, String> {
        let bridge = BRIDGE.get().ok_or("the bridge is not running")?;
        let mut env = bridge
            .vm
            .attach_current_thread_permanently()
            .map_err(|e| e.to_string())?;
        f(&mut env)
    }

    /// A static `HostWorld` method taking strings; a Java exception becomes its `toString()`.
    fn call<'a>(
        env: &mut JNIEnv<'a>,
        name: &str,
        sig: &str,
        args: &[&str],
    ) -> Result<JValueOwned<'a>, String> {
        let strings = args
            .iter()
            .map(|a| env.new_string(a))
            .collect::<jni::errors::Result<Vec<_>>>()
            .map_err(|e| e.to_string())?;
        let values: Vec<JValue> = strings.iter().map(|s| JValue::Object(s)).collect();
        env.call_static_method(WORLD, name, sig, &values)
            .map_err(|e| exception(env).unwrap_or_else(|| e.to_string()))
    }

    /// A static `HostWorld` method at a position, with an optional string after it.
    fn at<'a>(
        env: &mut JNIEnv<'a>,
        name: &str,
        sig: &str,
        p: Pos,
        s: Option<&str>,
    ) -> Result<JValueOwned<'a>, String> {
        let s = s
            .map(|s| env.new_string(s))
            .transpose()
            .map_err(|e| e.to_string())?;
        let mut values = vec![JValue::Int(p.0), JValue::Int(p.1), JValue::Int(p.2)];
        if let Some(s) = &s {
            values.push(JValue::Object(s));
        }
        env.call_static_method(WORLD, name, sig, &values)
            .map_err(|e| exception(env).unwrap_or_else(|| e.to_string()))
    }

    fn exception(env: &mut JNIEnv) -> Option<String> {
        if !env.exception_check().ok()? {
            return None;
        }
        let throwable = env.exception_occurred().ok()?;
        env.exception_clear().ok()?;
        let s = env
            .call_method(&throwable, "toString", "()Ljava/lang/String;", &[])
            .ok()?
            .l()
            .ok()?;
        env.get_string(&JString::from(s)).ok().map(Into::into)
    }

    pub fn place(block: &str, p: Pos) -> Result<(), String> {
        with(|env| {
            let block = env.new_string(block).map_err(|e| e.to_string())?;
            let args = [
                JValue::Object(&block),
                JValue::Int(p.0),
                JValue::Int(p.1),
                JValue::Int(p.2),
            ];
            env.call_static_method(WORLD, "place", format!("({S}III)V"), &args)
                .map(drop)
                .map_err(|e| exception(env).unwrap_or_else(|| e.to_string()))
        })
    }
    pub fn load(p: Pos, saved: &str) -> Result<(), String> {
        with(|env| at(env, "load", &format!("(III{S})V"), p, Some(saved)).map(drop))
    }
    pub fn save(p: Pos) -> Result<String, String> {
        with(|env| {
            let v = at(env, "save", &format!("(III){S}"), p, None)?;
            string(env, v)
        })
    }
    pub fn tick(p: Pos) -> Result<(), String> {
        with(|env| at(env, "tickAt", "(III)V", p, None).map(drop))
    }
    pub fn use_block(p: Pos) -> Result<String, String> {
        with(|env| {
            let v = at(env, "use", &format!("(III){S}"), p, None)?;
            string(env, v)
        })
    }
    pub fn command(line: &str) -> Option<Result<String, String>> {
        BRIDGE.get()?;
        let handles = with(|env| {
            call(env, "handles", &format!("({S})Z"), &[line])?
                .z()
                .map_err(|e| e.to_string())
        });
        match handles {
            Ok(false) => None,
            Ok(true) => Some(with(|env| {
                let v = call(env, "command", &format!("({S}){S}"), &[line])?;
                string(env, v)
            })),
            Err(e) => Some(Err(e)),
        }
    }
    /// The mods' log lines (the fixtures' traces) into Horizon's log.
    pub fn forward_log() {
        if BRIDGE.get().is_none() {
            return;
        }
        let lines = with(|env| {
            let v = call(env, "drainLog", &format!("(){S}"), &[])?;
            string(env, v)
        });
        match lines {
            Ok(lines) => lines.lines().for_each(|l| log::info!("{l}")),
            Err(e) => log::error!("Horizon bridge: reading the mods' log: {e}"),
        }
    }
    fn string(env: &mut JNIEnv, value: JValueOwned) -> Result<String, String> {
        let object: JObject = value.l().map_err(|e| e.to_string())?;
        env.get_string(&JString::from(object))
            .map(Into::into)
            .map_err(|e| e.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// A saved bridged entity goes to the JVM's form and back unchanged.
    #[test]
    fn saved_form_round_trips() {
        let java = r#"{"id":"horizon_bridge_pulse:pulse","pulse_schema":1,"level":210,"ticks":100L,"interactions":3,"charged":true,"paused":true}"#;
        let mut entries = vec![
            (
                "id".to_owned(),
                Tag::String("horizon_bridge_pulse:pulse".into()),
            ),
            ("x".to_owned(), Tag::Int(2)),
            ("components".to_owned(), Tag::Compound(Default::default())),
        ];
        let fields = from_saved(java);
        assert!(fields.contains(&("pulse_schema".to_owned(), Tag::Int(1))));
        assert!(fields.contains(&("ticks".to_owned(), Tag::Long(100))));
        assert!(fields.contains(&("charged".to_owned(), Tag::Byte(1))));
        entries.extend(fields.iter().cloned());
        let tag = Tag::compound(entries.iter().map(|(k, v)| (k.as_str(), v.clone())));
        // Horizon's compounds order keys their own way; the JVM side reads any order after the id.
        let back = to_saved(&tag).expect("only ints, longs and booleans");
        assert!(back.starts_with(r#"{"id":"horizon_bridge_pulse:pulse","#));
        let mut again = from_saved(&back);
        let mut expected = fields;
        again.sort_by(|a, b| a.0.cmp(&b.0));
        expected.sort_by(|a, b| a.0.cmp(&b.0));
        assert_eq!(again, expected);
    }

    /// A member the bridge lacks is named as such; other errors pass through.
    #[test]
    fn describes_missing_members() {
        assert_eq!(
            describe(
                "java.lang.NoSuchMethodError: 'long dev.horizonlab.bridge.host.mc.Level.getGameTime()'"
            ),
            "not supported by the bridge yet: java.lang.NoSuchMethodError: 'long mc.Level.getGameTime()'"
        );
        assert_eq!(
            describe("java.lang.IllegalStateException: x"),
            "java.lang.IllegalStateException: x"
        );
    }

    /// A failed entity no longer reaches the JVM, and saves keep the form it had.
    #[test]
    fn failed_entity_keeps_its_saved_form() {
        let saved = r#"{"id":"m:b","ticks":5L}"#.to_owned();
        let mut e = Bridged {
            block: "m:b",
            kind: "m:b",
            sync: BridgedSync::Failed(Some(saved)),
        };
        assert!(!sync(&mut e, (0, 0, 0)));
        assert_eq!(
            save(&e, (0, 0, 0)),
            vec![("ticks".to_owned(), Tag::Long(5))]
        );
        e.sync = BridgedSync::Failed(None);
        assert!(save(&e, (0, 0, 0)).is_empty());
    }
}
