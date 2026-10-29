# Bridge compatibility evidence

Updated 2026-10-28. Both fixtures run in Horizon's own game behind the `bridge` cargo feature (M1
steps 1-4), and a third fixture checks how the bridge fails.

| Target | Original Java logic | Horizon gameplay | Save/reload | Multiplayer | Status |
| --- | --- | --- | --- | --- | --- |
| Self-authored NeoForge counter fixture | Passed on NeoForge reference; whole fixture runs unchanged through the bridge's stand-ins, Rust driving ticks, trace identical | Runs in Horizon's `Game` (M1 step 3, `bridge` feature): trace identical, real right-clicks | Passed on NeoForge reference and in Horizon's `Game` across a real quit and reopen | Not tested | [evidence](../../mods/horizon/src/neoforge-fixture/README.md#reference-evidence-2026-10-28), [M0 spike](m0-spike.md), [M1 results](m1-design.md#results-of-steps-1-and-2-2026-10-28), [step 3](m1-design.md#results-of-step-3-2026-10-28), [issue #2](https://github.com/Dynamitecrown/horizon/issues/2) |
| Self-authored second behavior fixture (pulse) | Passed on NeoForge reference; runs unchanged through the stand-ins, trace identical | Runs in Horizon's `Game` (M1 step 3, `bridge` feature): trace identical, real right-clicks | Passed on NeoForge reference and in Horizon's `Game` across a real quit and reopen | Not tested | [evidence](../../mods/horizon/src/neoforge-fixture-pulse/README.md#reference-evidence-2026-10-28), [M1 results](m1-design.md#results-of-steps-1-and-2-2026-10-28), [step 3](m1-design.md#results-of-step-3-2026-10-28) |
| Self-authored fault fixture (needs a member the bridge lacks) | Runs on NeoForge reference (reads the game time on tick 5, no error) | Stops on tick 5 with one error naming `mc.Level.getGameTime()`; the game and other bridged blocks go on | Its save keeps the state from the failure; it runs again after a reopen | Not tested | [README](../../mods/horizon/src/neoforge-fixture-fault/README.md), [step 4](m1-design.md#results-of-step-4-2026-10-28) |
| Third-party NeoForge mod | Not selected | Not tested | Not tested | Not tested | Selection follows M1 |
| Forge mod | Not tested | Not tested | Not tested | Not tested | Deferred until NeoForge proof |

Horizon already converts some Java mods' data and assets. Those existing results are not
evidence of this experiment executing their Java gameplay code.

For every future row, retain the exact mod id/version/SHA-256, source Minecraft and loader
versions, Horizon commit, backend/version, OS, tested features, omitted features, reference
comparison, performance results, reproduction commands and a link to its issue or pull request.
Separate **execution only**, **content converted**, **native port**, **partially bridged** and
**bridged for the listed tests**. A loader's verdict alone never makes a row fully compatible.
