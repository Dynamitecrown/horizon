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

The standalone logic test checks these numbers. **The in-game NeoForge reference run has not been
done yet**: it needs a person running the scenario in the Prism instance.
