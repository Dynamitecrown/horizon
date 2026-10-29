# NeoForge bridge fault fixture (fixture 3)

Third original dev-only test mod for Minecraft **26.3**, NeoForge **26.3.0.99**, Java **25**.
Mod id: `horizon_bridge_fault`; version: `0.1.0`. It is not a Java-to-Horizon bridge.

It tests how the bridge fails ([M1 step 4](../../../../docs/bridge/m1-design.md#results-of-step-4-2026-10-28)):
one ticking block entity counts ticks and, on its **fifth** tick, reads `Level.getGameTime()`, a
member the bridge's stand-ins do not supply. Ticks and the game time it read (`-1` before) are saved.
An empty-hand right-click replies with its state.

## Build

```powershell
python tools/build_bridge_fixture.py --fixture fault --prism-root "$env:APPDATA\PrismLauncher" --jdk "C:\path\to\jdk-25"
```

Built on 2026-10-28 with Adoptium JDK 25.0.5+8: SHA-256
`fa868eaaa754d231c4ee149cf03e2fdf1faf1817825af6c0d9b7d2b52e61a938`.

## Expected

- **NeoForge:** it keeps ticking; after tick 5, `game_time` holds the level's time. **Not run in
  NeoForge yet**: only compiled against it.
- **Bridge:** on tick 5 it stops with one logged error naming `mc.Level.getGameTime()`; the game and
  other bridged blocks go on; its save keeps `ticks` 5 and `game_time` -1, and it ticks again after a reopen.
