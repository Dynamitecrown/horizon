# Bridge compatibility evidence

Updated 2026-10-28. No bridge runtime or gameplay integration exists in this setup change.

| Target | Original Java logic | Horizon gameplay | Save/reload | Multiplayer | Status |
| --- | --- | --- | --- | --- | --- |
| Self-authored NeoForge counter fixture | Not tested | Not tested | Not tested | Not tested | Planned for M0/M1 |
| Self-authored second behavior fixture | Not tested | Not tested | Not tested | Not tested | Planned for M1 |
| Third-party NeoForge mod | Not selected | Not tested | Not tested | Not tested | Selection follows M1 |
| Forge mod | Not tested | Not tested | Not tested | Not tested | Deferred until NeoForge proof |

Horizon already converts some Java mods' data and assets. Those existing results are not
evidence of this experiment executing their Java gameplay code.

For every future row, retain the exact mod id/version/SHA-256, source Minecraft and loader
versions, Horizon commit, backend/version, OS, tested features, omitted features, reference
comparison, performance results, reproduction commands and a link to its issue or pull request.
Separate **execution only**, **content converted**, **native port**, **partially bridged** and
**bridged for the listed tests**. A loader's verdict alone never makes a row fully compatible.
