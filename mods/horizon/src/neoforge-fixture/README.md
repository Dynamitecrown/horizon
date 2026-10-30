# NeoForge bridge counter fixture

Original dev-only test mod for Minecraft **26.3**, NeoForge **26.3.0.99**, Java **25**.
Mod id: `horizon_bridge_fixture`; version: `0.1.0`. It is not a Java-to-Horizon bridge.

The `counter` block runs Java code: each empty-hand right-click adds **3**; every **20 running
server ticks** adds **2**. Value, elapsed ticks, interaction count and paused state are saved in
the block entity. Pausing stops both the value and elapsed-tick counter. Breaking/replacing the
block discards its state; the dropped item places a fresh counter. Only loaded blocks tick.

## Build

Uses the existing JDK compiler and the player's already-installed, patched Prism client, like
Horizon's other NeoForge oracle tools. No Gradle, game download or redistribution of game classes.
Launch this exact NeoForge version once in Prism before building. From the repository root:

```powershell
python tools/build_bridge_fixture.py --prism-root "$env:APPDATA\PrismLauncher" --jdk "C:\path\to\jdk-25"
```

The tool compiles with Java 25 and warnings treated as errors, runs assert-based logic tests,
then packages only our own classes and JSON/TOML resources. Minecraft's installed textures are
referenced by id, never copied. Output: `.local/bridge-fixture/horizon-bridge-fixture-0.1.0.jar`;
its SHA-256 is printed. The build uses fixed ZIP timestamps for repeatable output on the same JDK.

## Use in a disposable test world

Put that JAR into the test instance's `minecraft/mods` folder and restart the instance.
It needs no Balm, Waystones or other mod dependencies. Use a creative world with cheats enabled:

```mcfunction
/give @s horizon_bridge_fixture:counter
/bridgefixture state <x> <y> <z>
/bridgefixture pause <x> <y> <z>
/bridgefixture resume <x> <y> <z>
/bridgefixture reset <x> <y> <z>
/bridgefixture interact <x> <y> <z>
/bridgefixture verify <x> <y> <z>
/bridgefixture trace <x> <y> <z> 100
```

Commands require gamemaster permissions and loaded coordinates in the current dimension.
`interact` calls the same Java counter operation as right-click, for automation. `verify` tests
the real block-entity save/load codecs without changing the placed block. It does not replace
a full quit/reopen test. State is one JSON object with `schema`, `value`, `ticks`, `interactions`
and `paused`. `trace` logs at most 200 subsequent server-tick samples with `BRIDGE_FIXTURE` in
the game log; tracing is transient and never saved. No files or network channels are opened.

For an exact comparison: `/tick freeze`, reset, interact three times, then `/tick step 100`.
After the step finishes, state must be value **19**, ticks **100**, interactions **3**. Pause
the counter, save and quit, reopen, and compare the state before resuming. Run the same JAR
hash and scenario on both sides of a future bridge. A second independent fixture and actual
Horizon execution are separate work; this one proves neither loader compatibility nor bridging.

## Reference evidence (2026-10-28)

Tested on Windows x64, Adoptium JDK 25.0.5+8, Minecraft 26.3, NeoForge 26.3.0.99,
Prism 9.2, using an isolated profile and disposable creative world. JAR SHA-256:
`106bd4130c253805768c293d86a9afc31e17984797b5389a6193818f676837b0`.
The repository baseline is `cba52f4e5d7f99c592a26823fe8727beafe6ebff`; no Horizon runtime was used.

- Block renders using the referenced vanilla model texture; three real empty-hand right-clicks add 9.
- 100 stepped server ticks: value 19, ticks 100, interactions 3. All 100 trace samples match.
- Pausing and stepping 20 more ticks preserves those fields; the save/load codec round-trip passes.
- Save/quit/reopen preserves value 19, ticks 100, interactions 3 and paused=true.
- Resume and 20 more ticks: value 21, ticks 120, interactions 3.
- Compiler warnings-as-errors and the standalone logic checks pass.

Horizon gameplay, a second independent fixture, multiplayer, other mods, Forge and performance
comparisons remain untested. Track the remaining bridge proof in [issue #2](https://github.com/Dynamitecrown/horizon/issues/2).

Public API references: [registration](https://docs.neoforged.net/docs/concepts/registries/),
[block entities](https://docs.neoforged.net/docs/blockentities/),
[commands](https://docs.neoforged.net/docs/misc/commands/). Signatures are checked against the
installed 26.3 game, since the published guide currently describes 26.1.
