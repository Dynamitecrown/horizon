# NeoForge bridge pulse fixture (fixture 2)

Second original dev-only test mod for Minecraft **26.3**, NeoForge **26.3.0.99**, Java **25**.
Mod id: `horizon_bridge_pulse`; version: `0.1.0`. It is not a Java-to-Horizon bridge.

It exists so a bridge cannot pass [issue #2](https://github.com/Dynamitecrown/horizon/issues/2) by
hard-coding the counter fixture's rule. Same shape as [the counter](../neoforge-fixture/README.md)
(one ticking block entity, saved state, the same commands), different logic:

- Each empty-hand right-click toggles **charged** and counts an interaction.
- Every **7** running server ticks the level rises by **5 × interactions** while charged, or by **1** otherwise.
- Pausing stops the level and the elapsed-tick counter. Level, ticks, interactions, charged and
  paused are saved in the block entity.

## Build

From the repository root:

```powershell
python tools/build_bridge_fixture.py --fixture pulse --prism-root "$env:APPDATA\PrismLauncher" --jdk "C:\path\to\jdk-25"
```

Output: `.local/bridge-fixture/horizon-bridge-pulse-0.1.0.jar`, with its SHA-256 printed. Built on
2026-10-28 with Adoptium JDK 25.0.5+8: `1708431c1afd23d74b78c446338e8152df34e058e36aac667cd294181903f879`.

## Commands

Same as the counter's, under `/bridgepulse`: `state`, `reset`, `pause`, `resume`, `interact`,
`verify`, and `trace <x> <y> <z> <ticks>`. Trace lines are tagged `BRIDGE_PULSE` in the game log.
State is one JSON object: `schema`, `level`, `ticks`, `interactions`, `charged`, `paused`.

## Reference scenario

`/tick freeze`, `reset`, `interact` three times, `trace ... 100`, `/tick step 100`. Expected:

| After | level | ticks | interactions | charged |
| --- | --- | --- | --- | --- |
| 3 interactions | 0 | 0 | 3 | true |
| tick 7 | 15 | 7 | 3 | true |
| 100 ticks | 210 | 100 | 3 | true |
| pause, 20 ticks, save/quit/reopen | 210 | 100 | 3 | true |
| resume, 20 ticks | 255 | 120 | 3 | true |

The standalone logic test checks these numbers.

## Reference evidence (2026-10-28)

Tested on Windows x64, Adoptium JDK 25.0.5+8, Minecraft 26.3, NeoForge 26.3.0.99, Prism, in the
QA instance's disposable creative world (only the two fixtures installed). JAR SHA-256:
`1708431c1afd23d74b78c446338e8152df34e058e36aac667cd294181903f879`. No Horizon runtime was used.
The scenario was driven through chat commands (`interact`, not real right-clicks).

- Every row of the table above matches, including the save/quit/reopen.
- 100 `BRIDGE_PULSE` trace samples; the mod's own `verify` passes before and after the reopen.
- The bridge host world ([M1 results](../../../../docs/bridge/m1-design.md#results-of-steps-1-and-2-2026-10-28))
  reproduces the trace: 100 of 100 samples identical.
