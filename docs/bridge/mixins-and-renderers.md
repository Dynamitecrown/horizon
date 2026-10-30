# Mods that change the game: Mixins and Flywheel

Research note (2026-10-29), not a plan anyone has agreed to. The question: many NeoForge mods don't
only *add* to Minecraft, they *change* it, with Mixins (and access transformers or coremods), and some
render through Flywheel, Create's rendering engine. What would it take for the bridge, or Horizon itself,
to take those changes on?

Facts about Mixin, MixinExtras, Flywheel and Create were checked on 2026-10-29 against their public
repositories, licences, build properties and API package names ([sources](#sources)), never against any
mod's own code (D5). Where a point is still my reading rather than a source, it says so.

## Why they can't simply run

A Mixin is a class in the mod's jar whose annotations say "patch this vanilla method": run my code at
its start (`@Inject` at `HEAD`), before it returns (`RETURN`), around a call inside it (`INVOKE`,
`@Redirect`), change an argument or constant (`@ModifyArg`, `@ModifyConstant`), replace it
(`@Overwrite`), or add fields and accessors (`@Shadow`, `@Accessor`, `@Unique`). At load time the Mixin
library (SpongePowered Mixin, MIT) rewrites the vanilla class's bytecode. Many mods also use
**MixinExtras** (MIT, bundled with NeoForge since 20.2.84), which adds injectors such as
`@WrapOperation` and `@ModifyReturnValue`; the bridge would need both.

Horizon has no vanilla Java classes to rewrite: the game is Rust. In the bridge, the vanilla names a
mod uses point at our stand-ins, which are thin and forward to Rust. So:

- A Mixin into a vanilla method **body** (an `INVOKE` point, a local variable, an ordinal) has nothing to
  patch: the body is Rust, with its own shape.
- A Mixin that only needs the method's **start or end**, or adds an accessor, can be honoured if Horizon
  has a matching point.
- A Mixin into **another mod's** class (mod-to-mod compatibility) can be applied for real, because that
  class is Java in the bridge's JVM.

So Mixins can't be "run"; their intent has to be translated, one target at a time.

## What Horizon already has

| Piece | What it gives | Where |
| --- | --- | --- |
| Native mods | Mixins for Horizon's **Rust** source (HEAD, BEFORE_CALL, RETURN, MODIFY_ARG, OVERWRITE...), built per Nightly; not sandboxed | [native-mods.md](../native-mods.md), horizon-mixins |
| Sandboxed `.hmod` hooks | A fixed list of points the game calls; "mods add, they cannot patch" | [mods.md](../mods.md) |
| Porting policy | "New hooks into vanilla behaviour (what Mixins do): each becomes a named hook, added for everyone" | [modpacks.md](../modpacks.md) |
| Port census | Counts mixins per mod in a pack, sorts mods into tiers | `horizon-port-pack` |
| Planned rendering | Data models (Bedrock geometry + animations) and a budgeted draw-list interface (API 0.5) | modpacks.md |

## Four ways to take Mixins on

**1. Real Mixin inside the bridge's JVM** (small to medium). Load the Mixin library into the bridge
(SpongePowered Mixin and MixinExtras are both MIT) and apply mods' Mixins to Java that exists there:
other mods' classes, and the stand-ins for accessors and `@Shadow` fields. This alone makes mod-to-mod
compatibility Mixins and many accessor Mixins work. It changes nothing in Horizon.

**2. Translate Mixins into Horizon hooks** (the main road; grows with use). Horizon gets **named hooks**
at the Rust points that match the vanilla methods mods target most (`LivingEntity.hurt`,
`ServerLevel.tick`, block placement, item use, ...), one hook system shared by `.hmod`s, bridged mods
and native mods. The bridge reads each Mixin's target, method and injection point from its annotations.
Where Horizon has the matching hook, the bridge registers the Mixin's handler there and calls it through
JNI, with a stand-in `CallbackInfo` so it can cancel or set the return value. Where it doesn't, the mod
loads with a named "not supported yet" error, as step 4's fault fixture does.
- Maps well: `HEAD`, `RETURN`, cancellable injections, `@ModifyReturnValue`, accessors.
- Maps sometimes: `@ModifyArg` and `@Redirect` on calls that Horizon also makes as a distinct step.
- Rarely maps: local-variable capture, ordinals, `@Overwrite` of large methods, constant changes inside formulas.
- Cost per call on hot paths: about 90 ns per cached JNI call (M0); fine per event, to watch per entity per tick.
- Which hooks to add first: a census of Mixin targets across the mods players want, by target
  method. That needs the class files' annotations, so it is the same D5 question as Shogi's surface.

**3. Native ports** (per mod, by hand). What a Mixin does deep inside the engine (collision, physics,
networking, chunk loading) becomes a native mod with horizon-mixins, or a feature of Horizon. Native code
has the player's rights and pins a Nightly, as today.

**4. Upstream** (ongoing). A hook or feature many mods want goes into Horizon for everyone, as the
porting policy says. Over time this shrinks 2 and 3.

## Flywheel and custom rendering

Flywheel (MIT, NeoForge and Fabric) is "a modern engine for modded Minecraft": an alternate path for
entity and block entity rendering that uses GPU instancing. Its 1.x API (package
`dev.engine_room.flywheel.api`) has **visuals** per thing drawn (`BlockEntityVisual`, `EntityVisual`,
`EffectVisual`), updated per frame (`DynamicVisual`), per tick (`TickableVisual`) or on light changes
(`LightUpdatedVisual`); each holds **instances** of a model with a material, of a type from its library
(`TransformedInstance`, `OrientedInstance`, `PosedInstance`, `ShadowInstance`, coloured and lit variants).
Mods may also define **their own instance layouts with their own shaders**. Its companion **Vanillin**
draws vanilla's block entities and entities through Flywheel. (The project wiki still shows the 0.x API,
from 2022; the package names above are 1.x.)

Flywheel's backend runs inside Minecraft's Java renderer, so it can't run in Horizon. Its **API** is
close to data, though, which makes it the most promising rendering target:

- **Flywheel API stand-ins backed by Horizon's renderer** (medium to large). The bridge supplies
  Flywheel's API classes; behind them, Horizon keeps the instances in GPU buffers and draws them with
  wgpu instancing next to its own block entities. Mods' visual code runs unchanged. Tick- and light-updated visuals call in rarely;
  `DynamicVisual`s call every frame, which is the cost to measure. A mod's own instance types come with
  GLSL shaders: Horizon already turns shader packs' GLSL into naga modules, so those could go the same
  way (to check: how close Flywheel's shader conventions are to what that path expects). Models come
  from the mod's own assets, as bridged blocks' models do already.
- **Plain `BlockEntityRenderer` / `EntityRenderer`** (immediate mode through `PoseStack` and
  `VertexConsumer`, every frame): stand-ins could record the vertices each frame through JNI into a
  draw list. That works but costs per frame per renderer: API 0.5's budgeted draw list is where this belongs.
- **Direct OpenGL, shaders, render-pipeline Mixins** (Sodium, Iris, ...): no. Horizon has its own
  shader-pack format.

A prerequisite for all of these: today the bridge runs **only where the world is simulated**. Renderers,
screens, key bindings and a mod's client-bound packets are client code, so the bridge needs a JVM in the
client too, plus a way for a mod's two halves to talk (its packets over Horizon's connection).

## Create, as the hard example

| Part | What it needs | Way |
| --- | --- | --- |
| Registration (Registrate), blocks, items, recipes | Stand-in surface, recipe types | Bridge, as M1 |
| Kinetic network (rotation, stress) | Block entities, neighbour updates, ticking | Bridge (Java logic runs as is) |
| Kinetic visuals | Flywheel 1.x (and Vanillin) | Flywheel API stand-ins, plus its own instance shaders (my reading: rotating parts are a custom instance type) |
| Contraptions (moving structures as entities, collision with players) | Entities, movement and collision Mixins | Hooks for some; most likely native work in Horizon |
| Trains (track graph, signals, track rendering) | All of the above plus entities over long distances | Last |
| Ponder (in-game tutorial scenes) | Client GUI and scene rendering | Client JVM plus rendering |
| Its Mixins (counted, not read) | Census first | 1-4 above |

What its sources say (2026-10-29):

- **Licence:** code MIT; everything under `src/main/resources/assets/` (textures, models, sounds, ...)
  **All Rights Reserved**. The bridge could run its code, but `tools/bridge_mod.py` copies a jar's
  assets into the mod folder: fine on a player's own machine, never in anything published or shipped.
- **Version:** its development branch is Minecraft **1.21.1**, NeoForge 21.1.219, with Flywheel 1.0.6,
  Ponder 1.0.85, Registrate and Vanillin, plus optional integrations (JEI, Curios, Sodium, CC: Tweaked,
  Farmer's Delight). No 26.x build was found; Flywheel has a `26.1.2/dev` branch. D1 targets 26.3 only,
  so Create needs a 26.x release first, or D1 revisited.

Realistically it is a goal for after M2 and M3, once one simpler real mod runs.

## Suggested order

1. Answer the D5 question (may the bridge read class-file annotations and constant-pool names of
   permissively licensed mods?). Everything below needs it.
2. Census of Mixin targets and Flywheel use across candidate mods → the first named hooks.
3. Real Mixin in the bridge's JVM (way 1), with a fixture of our own that uses an accessor and a
   mod-to-mod Mixin.
4. One named hook end to end (way 2), shared with `.hmod`s: a fixture whose `HEAD` injection cancels,
   checked against NeoForge like M1's fixtures.
5. The bridge in the client (a JVM there, a mod's packets between its halves).
6. Flywheel API stand-ins with one fixture of ours that uses Flywheel, then a permitted real mod.

What stays out of reach on this road: render-engine and shader mods, and mods that are mostly
`@Overwrite`s of the game's internals. Those would be native ports or Horizon features of their own.

## Sources

Checked 2026-10-29:
[SpongePowered Mixin](https://github.com/SpongePowered/Mixin) (MIT),
[MixinExtras](https://github.com/LlamaLad7/MixinExtras) (MIT; bundled with NeoForge 20.2.84+),
[Flywheel](https://github.com/Engine-Room/Flywheel) (MIT; API packages
[visual](https://github.com/Engine-Room/Flywheel/tree/1.21.1/dev/common/src/api/java/dev/engine_room/flywheel/api/visual),
[instance types](https://github.com/Engine-Room/Flywheel/tree/1.21.1/dev/common/src/lib/java/dev/engine_room/flywheel/lib/instance)),
[Create](https://github.com/Creators-of-Create/Create) (its
[LICENSE.md](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/LICENSE.md) and
[gradle.properties](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/gradle.properties)),
[NeoForge for 26.1](https://neoforged.net/news/26.1release/). NeoForge's own Mixin documentation page
was not found (404), so how NeoForge loads Mixins is not checked here.
