# M0 spike: running the fixture's Java logic from Rust

Part of [issue #1](https://github.com/Dynamitecrown/horizon/issues/1). The general plan is
[feasibility.md](feasibility.md); this page is the first concrete experiment and its results.

## Question

Can a Rust process load the **unchanged** fixture JAR, run its original Java gameplay logic, keep
the state Horizon would save, and talk both ways (Rust → Java and Java → Rust), at a cost a
20-ticks-per-second server can afford?

This is **execution only** in the vocabulary of [compatibility.md](compatibility.md): no Horizon
world, block, renderer or save is involved yet.

## Design

`crates/horizon-bridge-spike` is a standalone binary, in its own Cargo workspace (like
`horizon-telemetry-server`), so the game's workspace and CI never build it.

| Part | What it does |
| --- | --- |
| Backend | HotSpot from the installed JDK 25, embedded through the JNI invocation API ([`jni`](https://docs.rs/jni/0.21) crate, `invocation` feature, which loads `jvm.dll`/`libjvm` at run time) |
| Class path | The fixture JAR exactly as built and tested (its SHA-256 is checked first), then one probe class of ours |
| Probe | `mods/horizon/src/bridge-spike/java/dev/horizonlab/bridge/spike/HostProbe.java`: calls the fixture's `CounterState` and reports each state through `native` methods implemented in Rust. It stands in for a future host adapter. |

### Checks

1. **Trace.** Through JNI from Rust: 3 `interact()`, then 100 `tick()`. Each tick's `json()` must equal the
   Java reference trace (`fixture-reference-trace.jsonl`, recorded in NeoForge) byte for byte.
2. **Persistence.** Pause, 20 ticks (no change), read the four fields into Rust (what Horizon would save),
   build a new object from them, resume, 20 ticks: value 21, ticks 120, interactions 3.
3. **Java → Rust.** `HostProbe.run(3, 100)` reports every tick to a Rust `native` method; the reports
   must match the same reference trace.
4. **Another thread.** The trace scenario on a second Rust thread attached to the same JVM, as a server
   thread would be.
5. **Clean failures.** A negative state and an `int` overflow must arrive in Rust as Java exceptions
   with their messages, leaving the JVM usable.
6. **Missing game classes.** Loading the fixture's `CounterEntity` must fail with the name of the first
   missing Minecraft class. That is the exact gap the host adapter has to fill.
7. **Origin.** `CounterState` must come from the fixture JAR, not a copy.

### Measurements

JVM start time, process memory before and after, and the mean cost of a Rust → Java call
(by name and with a cached method id), a Java → Rust call, and a full tick round trip.

## Running it

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot"
python tools/build_bridge_fixture.py --prism-root "$env:APPDATA\PrismLauncher" --jdk $env:JAVA_HOME
cargo run --release --manifest-path crates/horizon-bridge-spike/Cargo.toml -- `
  --fixture .local/bridge-fixture/horizon-bridge-fixture-0.1.0.jar `
  --reference <path to fixture-reference-trace.jsonl>
```

The build script compiles `HostProbe.java` with the JDK's `javac` (`JAVA_HOME`, else `PATH`). The
program exits non-zero if any check fails.

`TraceMain` in the same folder replays the reference scenario as a plain `main`, so any other Java
runtime can be checked with the same trace:
`java -cp <fixture.jar>;<spike classes> dev.horizonlab.bridge.spike.TraceMain`.

## Results (2026-10-28)

Windows 11 x64, Rust 1.98.1, Adoptium JDK 25.0.5+8 (HotSpot), `jni` 0.21.1, fixture SHA-256
`106bd4130c253805768c293d86a9afc31e17984797b5389a6193818f676837b0`. Three runs; every check passed every time.

| Check | Result |
| --- | --- |
| Fixture hash | matches the jar tested in NeoForge |
| Origin | `CounterState` loaded from the fixture jar |
| Trace, Rust → Java | 100 of 100 samples identical to the NeoForge trace |
| Save, restore, resume | paused at value 19, ticks 100; state rebuilt from Rust-held fields; resumed to 21, 120 |
| Trace, Java → Rust reports | 100 of 100 identical |
| Trace on a second thread | 100 of 100 identical |
| Invalid state | `java.lang.IllegalArgumentException: Negative counter state`, caught in Rust |
| Overflow | `java.lang.ArithmeticException: integer overflow`, caught in Rust |
| JVM after exceptions | still runs the scenario identically |
| Missing game class | `java.lang.NoClassDefFoundError: net/minecraft/world/level/block/entity/BlockEntity` |
| `TraceMain` on HotSpot's `java` | output identical to the NeoForge trace, and the same paused/resumed states |

| Measurement | Value (3 runs) |
| --- | --- |
| JVM start, embedded | 39–40 ms |
| Process memory | about 4 MiB before the JVM, 44 MiB after |
| Rust → Java call, method looked up by name | 690–710 ns |
| Rust → Java call, cached method id | 89–92 ns |
| Java → Rust call (`native` method) | 10–16 ns |
| One tick plus its state copied into a Rust `String` | 2.4–3.2 µs |

A server tick has a 50 ms budget. At about 90 ns per cached call, call overhead is not the limit
for small mods. Strings and object churn across the boundary cost far more than the calls, so a host
adapter should pass primitives and cache method ids.

**Verdict for this row: execution only, passed.** Nothing in Horizon's world, saves or renderer was used.

## Backends compared

| Backend | Status here | For | Against |
| --- | --- | --- | --- |
| HotSpot embedded through JNI | **Tested: passes** | Runs unchanged bytecode, full JDK, fast, mature; both call directions work | Needs a JDK 25 on the player's machine (Horizon's prepare step already does); not a sandbox: a bridged mod has the game process's full rights |
| [Ristretto](https://github.com/theseus-rs/ristretto) (JVM written in Rust, Apache-2.0/MIT, embeddable via `ristretto_vm`) | Not run: installing it was declined by this session's permission check | Fits upstream's "players run Rust" direction; no JDK install | Maturity for NeoForge-sized class graphs unknown; host-call API untested. Next step: run `TraceMain` with it. |
| [TeaVM](https://teavm.org/docs/wasm-gc-backend.html) Wasm GC backend (bytecode → WebAssembly) | Not run | Could produce code for the same WebAssembly sandbox as Horizon's mods; preparation-time compilation fits M5 | Compiles a closed program, so each mod jar plus every class it touches must be compiled ahead; host imports and running outside a browser unverified |
| GraalVM Native Image (ahead-of-time to a shared library) | Not run | Native speed, no JVM at run time | Closed world: classes must be known at build time; would need Minecraft's classes in the build, which cannot be shipped |

**Decision: continue with embedded HotSpot for M1.** It is the only backend proven here, and it is
fast enough. Keep the host adapter backend-neutral (primitives in, primitives out), so Ristretto or a
WebAssembly backend can replace it later. Try Ristretto with `TraceMain` before M2.

## What M1 needs next

The fixture's game-facing classes fail first on `net.minecraft.world.level.block.entity.BlockEntity`.
`BridgeFixture.java` uses about 25 Minecraft and NeoForge types (`Block`, `BlockEntity`, `BlockPos`,
`BlockState`, `ValueInput`/`ValueOutput`, `ServerLevel`, `DeferredRegister`, commands...). The bridge has to supply them:

1. **Our own stand-in classes**, implementing only what the fixtures call, each forwarding to Rust
   through `native` methods. They cannot be committed as `.java` under `net/minecraft/`
   (`tools/check_public_tree.py`), so they would be generated locally at preparation time, or kept
   under `dev/horizonlab` and renamed into place when the fixture is loaded.
2. **The player's own Minecraft and NeoForge jars**, loaded into the embedded JVM as the real
   classes, with world access redirected to Horizon. This runs much of Java Minecraft beside Horizon,
   so it defeats the purpose and is not recommended.

Option 1 is the M1 path. Its first slice: `Block` registration, `BlockEntity` with ticking and
`ValueInput`/`ValueOutput`, and `useWithoutItem`, backed by Horizon's server world.
