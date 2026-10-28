# M0: execution feasibility and first fixture

Question: can unchanged Java mod logic invoke a small, well-defined compatibility surface that
mutates Horizon's authoritative world, at an acceptable cost? This document is an experiment plan,
not evidence that the bridge works.

## 1. Pin the test environment

Start from the Horizon commit in [the brief](README.md). Record the exact NeoForge build,
Minecraft version, JDK distribution/version, Rust version, operating system and execution backend.
Confirm an available loader/game pairing from official metadata before generating the fixture.
Record version differences explicitly. Keep downloaded binaries and outputs under `.local/`.

Build and run unmodified Horizon with scratch homes first, then the Java reference. Capture
baseline startup, memory and tick timings. Do not use a player's existing worlds or silently
download game files. A missing JDK or Minecraft installation is a prerequisite, not a test pass.

## 2. Compare execution approaches

Evaluate existing implementations through their public documentation and interfaces:

| Candidate | What to establish |
| --- | --- |
| Existing JVM, as a research baseline | Class loading, callbacks into Rust, thread ownership, object identity, exception propagation and measured overhead |
| Existing Rust Java runtime | Required class-file/JDK support, libraries, callbacks, limits and platform support; do not assume bytecode execution implies NeoForge support |
| Existing Java ahead-of-time compiler | Whether the fixture can retain its original logic, required runtime support, host imports, reflection/class loading limitations and reproducibility |

Choose the smallest approach that executes the fixture and can expose the required host calls.
Do not implement a new JVM, compiler or full loader before this comparison. Record rejected
candidates with concrete reasons and links. Treat the runtime's license and any runtime escape
paths as part of the choice. No runtime is selected or installed by the setup change.

## 3. Establish the Java reference

Write our own NeoForge fixture in the permitted `dev.horizonlab` namespace. It registers one
block with persistent integer state. Interaction adds a configurable amount; every N server
ticks updates the state by another amount. Expose enough state for an automated reference trace.
Use two fixture JARs with different update rules to detect accidental hard-coding.

Each fixture's logic JAR has one SHA-256 used by both runs. Generated adapters may supply host
classes or rewrite call boundaries, but must not replace the fixture's gameplay algorithm with
Rust. Record any transformations and hash their outputs separately.

## 4. Connect the minimum host surface

First prove an original method executes outside the game; label this **execution only**.
Then map only the fixture's registrations, interactions, server ticks and persistent state to
Horizon. Host operations run on the server thread and use Horizon's existing world mutation,
neighbor-update and saving paths. Avoid a second simulated world inside the execution backend.

Use the existing content preparation path for models and textures. Inspect native hooks before
changing engine interfaces. Unsupported calls must name the missing class/method and stop the
fixture cleanly; never answer with invented defaults that resemble compatibility.

## 5. Acceptance scenario

For each fixture, run exactly the same sequence on NeoForge and Horizon:

1. Place the block at a fixed position and verify its initial state and registration.
2. Interact three times; compare state after each interaction.
3. Advance 100 server ticks; compare the per-tick trace, not only the final value.
4. Save, quit, reopen and compare the restored state; continue interactions and ticking.
5. Exercise an unsupported host call and an exception; verify a clear failure and intact save.
6. Capture the visible block/state in Horizon, and compare the two fixture behaviors.

Retain versions, JAR hashes, commands, timings, peak memory, traces and the comparison result.
Generated files stay ignored. Publish reproducible commands and a redacted summary, never game
assets, third-party JARs or private absolute paths.

## Decision gate

M0 completes only with a reproducible execution spike and a documented backend decision.
M1 completes only after both unchanged fixtures pass the in-game scenario. Mark a partial
result as partial, with its first missing requirement. If execution works but loader behavior
or host mapping is impractical, explain the blocker and propose a narrower supported subset.
Data carry-over and manually ported Rust behavior do not satisfy either fixture's bridge gate.

After M1, select one real mod with suitable permissions and few dependencies. Verify its
requirements before promising support. Multiplayer and Windows/macOS/Linux portability require
their own evidence; a single-player result on one platform does not establish them.
