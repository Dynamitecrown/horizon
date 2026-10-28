# Forge/NeoForge bridge research

This fork investigates running original Forge/NeoForge gameplay code against Horizon's Rust
implementation. A supported JAR may require automatic preparation or compilation, but its logic
must not be rewritten by hand for a result to count as bridged. Asset extraction and native Rust
ports are useful, separately reported outcomes.

**Status: M0 execution spike passed.** The fixture's original Java logic runs from Rust in an
embedded JVM and matches the NeoForge trace ([m0-spike.md](m0-spike.md)). No loader compatibility
layer or gameplay bridge exists yet, and no third-party mod is supported.

## Baseline

| Setting | Value |
| --- | --- |
| Upstream | <https://github.com/horizon-rust/horizon> |
| Research fork | <https://github.com/Dynamitecrown/horizon> |
| Working branch | `codex/neoforge-bridge` |
| Initial Horizon commit | `cba52f4e5d7f99c592a26823fe8727beafe6ebff` |
| Horizon Minecraft version | `26.3`, from `research-lock.json` at that commit |
| First loader | NeoForge 26.3.0.99 on Minecraft 26.3; JDK 25 (spike: Adoptium 25.0.5+8) |
| Execution backend | Embedded HotSpot through JNI for M1, kept behind a backend-neutral adapter ([m0-spike.md](m0-spike.md#backends-compared)) |

Keep `main` aligned with upstream. Changes belong on the research branch or small topic branches.
Update the baseline deliberately and rerun the relevant comparisons. No automatic game releases,
upstream submissions or compatibility claims follow from this setup.

## Milestones

| Milestone | Exit evidence |
| --- | --- |
| M0: execution feasibility | Reproducible execution spike, exact version tuple, runtime comparison and an explicit continue/pivot decision |
| M1: unchanged NeoForge fixtures | Two self-authored JARs execute different interactions and ticks against Horizon, save/reload correctly, and match the Java reference |
| M2: first real mod | A small permitted mod's selected feature set passes reference comparisons, with missing features listed |
| M3: persistence and recovery | Versioned state, failure recovery, missing-mod handling and reopen tests protect player worlds |
| M4: multiplayer | The supported registry, packets, state and interactions work between explicitly tested client/server combinations |
| M5: drag-and-drop preparation | Exact JAR identification, dependency checks, preparation cache and actionable unsupported-feature reports |

The [tracking issue](https://github.com/Dynamitecrown/horizon/issues/7) links the milestone tasks:
[M0](https://github.com/Dynamitecrown/horizon/issues/1),
[M1](https://github.com/Dynamitecrown/horizon/issues/2),
[M2](https://github.com/Dynamitecrown/horizon/issues/3),
[M3](https://github.com/Dynamitecrown/horizon/issues/4),
[M4](https://github.com/Dynamitecrown/horizon/issues/5), and
[M5](https://github.com/Dynamitecrown/horizon/issues/6).
Tasks live in the [fork's issues](https://github.com/Dynamitecrown/horizon/issues) and
[milestones](https://github.com/Dynamitecrown/horizon/milestones). None of these milestones is
complete at setup. Later milestones depend on evidence from M0 and M1.

## Scope and constraints

- Start with one NeoForge/Minecraft version pair, preferably the same Minecraft version as Horizon.
  If that pairing is unavailable, record cross-version adaptation as separate work rather than
  silently treating it as loader compatibility. Forge comes after the NeoForge mechanism works.
- Reuse `horizon-port-pack` for supported metadata, content and asset preparation. Keep original-code
  execution separate from its existing content-only verdicts.
- Use Horizon's mod API where sufficient and narrowly scoped native hooks where necessary.
  A normal sandboxed `.hmod` cannot supply game APIs the host does not expose.
- No complete Forge API clone, arbitrary Mixins, custom renderers, major modpacks or unmodified
  Java-server interoperability in the first prototype.
- Native Rust code, an interpreter and a JVM have different trust properties. Do not describe any
  backend as sandboxed without enforcement tests; initial execution uses only our own fixtures.
- No real saves in tests. Follow the scratch-home isolation in [AGENTS.md](../../AGENTS.md),
  keep game/mod binaries and prepared data in ignored `.local/`, and retain the public-tree checks.

## Upstream relationship

Follow [CONTRIBUTING.md](../../CONTRIBUTING.md): original implementations from public documentation,
file formats and observed behavior; no copying or translating another mod/loader's source.
Our fixture sources are our own dev-only Java under `mods/horizon/src/` and `dev/horizonlab/`.
Third-party mod artifacts and derived game resources never enter Git.

The upstream direction is player-facing Rust. An ordinary embedded JVM is a research option to
evaluate, not an adopted product architecture or a claim of upstream acceptance. Review dependency
licenses and that direction before shipping a backend. Source-level study of third-party runtime
implementations is outside the initial spike; evaluate their documented interfaces.

## Next action

M1: the stand-in game classes the fixture needs, backed by Horizon's server world
([m0-spike.md](m0-spike.md#what-m1-needs-next)), and a second fixture. Update [the evidence table](compatibility.md).
Report failures and unsupported APIs explicitly. A successful load or a rendered block alone
does not establish gameplay compatibility.
