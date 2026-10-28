# Bridge compatibility evidence

Updated 2026-10-28. The bridge runs both fixtures in its own host world; nothing is integrated into
Horizon's game yet.

| Target | Original Java logic | Horizon gameplay | Save/reload | Multiplayer | Status |
| --- | --- | --- | --- | --- | --- |
| Self-authored NeoForge counter fixture | Passed on NeoForge reference; whole fixture runs unchanged through the bridge's stand-ins, Rust driving ticks, trace identical | Host world only (M1 step 2); not in Horizon's `Game` | Passed on NeoForge reference; save/restore through the host passed (one process) | Not tested | [evidence](../../mods/horizon/src/neoforge-fixture/README.md#reference-evidence-2026-10-28), [M0 spike](m0-spike.md), [M1 results](m1-design.md#results-of-steps-1-and-2-2026-10-28), [issue #2](https://github.com/Dynamitecrown/horizon/issues/2) |
| Self-authored second behavior fixture (pulse) | Runs unchanged through the stand-ins with expected values; NeoForge reference run not done | Host world only (M1 step 2) | Save/restore through the host passed (one process) | Not tested | [README](../../mods/horizon/src/neoforge-fixture-pulse/README.md), [M1 results](m1-design.md#results-of-steps-1-and-2-2026-10-28) |
| Third-party NeoForge mod | Not selected | Not tested | Not tested | Not tested | Selection follows M1 |
| Forge mod | Not tested | Not tested | Not tested | Not tested | Deferred until NeoForge proof |

Horizon already converts some Java mods' data and assets. Those existing results are not
evidence of this experiment executing their Java gameplay code.

For every future row, retain the exact mod id/version/SHA-256, source Minecraft and loader
versions, Horizon commit, backend/version, OS, tested features, omitted features, reference
comparison, performance results, reproduction commands and a link to its issue or pull request.
Separate **execution only**, **content converted**, **native port**, **partially bridged** and
**bridged for the listed tests**. A loader's verdict alone never makes a row fully compatible.
