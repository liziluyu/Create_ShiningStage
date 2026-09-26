# Repository Guidelines

## Project Overview

**Create Shining Stage** (`create_shining_stage`) is a NeoForge 1.21.1 addon for the Create mod. It adds a single content piece: a **Spotlight** block that emits a colored, redstone-controlled light beam (frustum) — dyeable, with scroll-adjustable length (2–32 blocks), designed to work correctly on Create: Aeronautics moving ships via Sable's sub-level-aware raycasting.

- Mod ID: `create_shining_stage`, group: `com.shiningstage`, version `1.0.0`
- Required runtime deps (declared in `neoforge.mods.toml`): `create` [6.0.10,6.1.0), `aeronautics_bundled` [1.3.0,1.4.0), `sable` [2.0.5,)
- License: All Rights Reserved

## Architecture & Data Flow

Small flat package — all classes live directly in `com.shiningstage.create_shining_stage`, except the mixins, which must sit in `com.shiningstage.create_shining_stage.mixin` (a mixin config claims its whole package):

```
CreateShiningStage (mod ctor) ── registers ──► ModBlocks / ModBlockEntityTypes / ModCreativeTabs (DeferredRegister)
                                       └──► Config.SPEC (COMMON: speaker grouping radius)
CreateShiningStageClient (@Mod dist=CLIENT) ── EntityRenderersEvent.RegisterRenderers ──► SpotlightRenderer
                                       └── ClientTickEvent.Post ──► SpeakerBindingOutliner

SpotlightBlock (DirectionalBlock + IBE<SpotlightBlockEntity>)
  ├─ FACING state; dye/amethyst right-click → SpotlightBlockEntity.setLaserColor → notifyUpdate
  └─ SpotlightBlockEntity (Create SmartBlockEntity)
       ├─ addBehaviours: ScrollValueBehaviour "max_length" (MIN/MAX/DEFAULT_RANGE = 2/32/16)
       │                 positioned by SpotlightRangeValueBoxTransform (ValueBoxTransform.Sided on tail face)
       └─ NBT read/write for LaserColor (default 0xBC76FF)

SpotlightRenderer (BlockEntityRenderer)
  └─ per frame: signal = level.getBestNeighborSignal(pos), pinned to CONTRAPTION_SIGNAL (4) inside a
     Create contraption (redstone is unreadable there) → beam spread + alpha
     → vanilla clip() raycast → Sable.HELPER.distanceSquaredWithSubLevels (ship-aware true distance)
     → draws translucent QUAD frustum via custom RenderType (no cull, no depth write, color-write only)

EntityCullingMixin (@Mixin Entity, client-only, in .mixin subpackage) ──► SpotlightRenderer.contraptionCullingBox
  └─ widens a contraption's culling box to its spotlight beams: Create renders contraption block
     entities only while the contraption entity itself passes culling, so a beam used to vanish
     along with the hull

SpotlightBeams (duck interface) + ContraptionMixin (client-only) ──► cache for the above
  └─ Contraption#readNBT TAIL scans the structure once and stores its spotlights; the culling query
     then costs one interface call + list iteration (empty list for contraptions without spotlights)

ColdSparkMachineBlock (HorizontalDirectionalBlock + IBE + Create's IWrenchable)
  └─► ColdSparkMachineBlockEntity (Create SmartBlockEntity)
       ├─ plain full cube (cube_all) — no shape override, no noOcclusion; FACING is the *control face*,
       │  which is all the four-way mounting now means: the value box follows it, the model does not
       ├─ rotation is free: Create's IWrenchable default steps any state carrying HORIZONTAL_FACING a
       │  quarter turn on a vertical face, so no rotate code here (HorizontalDirectionalBlock's own
       │  rotate/mirror are the structure-block path and never run for a wrench)
       ├─ addBehaviours: ScrollValueBehaviour "spray_height" (MIN/MAX/DEFAULT = 1/16/4) on
       │  ColdSparkMachineValueBoxTransform (Sided, active on the FACING face — the spotlight's pattern)
       ├─ one SmartInventory slot (fuel-only predicate) exposed as Capabilities.ItemHandler.BLOCK on the
       │  four horizontal faces only — context.getAxis().isHorizontal(), null for UP/DOWN and for the
       │  side-less query, so a funnel on the lid has nothing to feed; registered from
       │  ColdSparkMachineBlockEntity.registerCapabilities (RegisterCapabilitiesEvent)
       ├─ shot: a rising redstone edge while idle spends one fuel and sets SPRAY_DURATION (2s of ticks).
       │  The line is sampled every tick, spraying or not, and the sample *is* the edge: a signal during
       │  the two seconds is swallowed rather than queued, so a held line gives exactly one shot and
       │  the next needs a low-then-high again, and the spray can never be extended or restarted. The
       │  first sample after placement/load is a baseline, not an edge — a machine coming up beside a
       │  live line stays idle
       ├─ playerWillDestroy drops the slot (not onRemove: assembly also clears the block out of the level)
       └─ NBT "Inventory" + "SprayTicks" (save-only, the server's own countdown) + "SprayStartTick"/
          "SpraySerial" (synced). A shot spends one fuel and calls sendData().

ColdSparkMachineRenderer (client BlockEntityRenderer)
  └─ the spray: SPARK_COUNT camera-facing quads thrown along a parabola. NOT a particle system — no
     per-spark object, no client tick, no simulation, and nothing in ParticleEngine. Design invariants:
     ├─ a spark's whole life is a closed form of "how long ago did the shot start", so the client runs
     │  its own clock off the synced SprayStartTick and needs no per-tick updates; a client joining
     │  mid-shot draws the sparks still in the air
     ├─ a spark burns at BRIGHTNESS for its whole rise: there is no fade-in, and the only thing that
     │  dims it is its own descent, so opacity never decreases before the apex. A fade-in cannot be
     │  localised to one spark either — sparks are born continuously through the shot, so some would
     │  always be mid-ramp and the column would look permanently soft
     ├─ a spark does not leave from the block's centre point: it starts somewhere inside a disc of
     │  BIRTH_RADIUS (2/16 block) on the nozzle plane, so the spray comes out of an aperture the size
     │  of a nozzle instead of a mathematical point (at close range the base otherwise reads as one
     │  bright dot). Radius is taken as sqrt(u) so the sparks are uniform over the *disc* — taking it
     │  linearly bunches them at the centre and leaves the rim bare. The offset is added to the drift,
     │  not multiplied by age: it is where the spark began, not how far it has travelled
     ├─ the launch offset and the drift share ONE azimuth (byte 2 of h1), so a spark born on the left is
     │  thrown further left: the two reinforce instead of being unrelated random directions that mostly
     │  cancel out. That is also what a nozzle does — its flow diverges away from the centre. It lets
     │  the radius ride byte 2 of h2, which is why no third hash exists; all four of h2's bytes are
     │  spoken for (0 lateral, 1 warmth, 2 birth radius, 3 size), so a new per-spark random value needs
     │  a new byte from somewhere, not a silent reuse of one of these
     ├─ a spark only *rises*: it dies FALL_DISTANCE (0.75) blocks past its apex instead of riding the
     │  parabola back to the nozzle. The drop at the death point is exactly FALL_DISTANCE by
     │  construction, since ½·G·(2·FALL_DISTANCE/G) = FALL_DISTANCE, and the fade-out is spent inside
     │  that descent — so the spray reads as a fountain that stops in the air rather than as an arc of
     │  dots raining back down through itself. Note the base of the column still looks empty for a
     │  different reason: sparks are *born* there, at the nozzle plane, so birth is spread over the
     │  block below the nozzle rather than all of them starting at the mouth
     ├─ each sprite is rolled to point the way its spark was *fired*, not the way it is currently
     │  moving: the launch direction is fixed at birth, so a spark keeps the orientation it left the
     │  nozzle at instead of slowly tumbling as gravity turns its velocity. The pose carries that
     │  direction into the world (which is what makes it right on a contraption), and there it is
     │  projected onto the camera's screen axes; the projection is normalised rather than passed
     │  through an arctangent, so the roll costs a handful of multiplies and no trigonometry. Being
     │  camera-facing, a quad has exactly *one* rotation axis — the view axis — so rolling is just
     │  spinning the local right/up basis about it, which keeps the quad in the screen plane (their
     │  cross product is unchanged) and mirrors nothing, leaving corner winding and UVs alone.
     │  Degenerate case: fired straight at or away from the camera there is no on-screen direction, so
     │  the sprite stays upright rather than rolling on noise. Magnitude: the tilt is the launch
     │  direction's angle from vertical, i.e. atan(LATERAL_SPREAD × [0.5, 1.5]) — only ±1.7°..5.1°,
     │  because the cone is deliberately narrow. It is therefore invisible on a round sprite and needs
     │  an elongated one to read at all; widen LATERAL_SPREAD (which also widens the cone) or add a
     │  decorrelated per-spark roll if a stronger lean is ever wanted
     ├─ randomness is hash(machine pos, shot serial, spark index) — a RandomSource can only be walked
     │  forward, so it cannot answer "spark 137" and would give each client a different fountain
     ├─ one QUADS buffer, one draw call, zero per-frame allocation (scratch matrix/vectors are fields)
     ├─ billboards: the camera's right/up are mapped into the pose's local space through the *inverse
     │  pose*, so one inversion per machine covers every spark and contraption rotation comes out right.
     │  Sprite UVs follow vanilla's own camera-facing quad (SingleQuadParticle.renderRotatedQuad): the
     │  camera-up corners take V=0, since V runs 0 at the sprite's top row. Inverting that pair draws
     │  every spark upside down, which a vertically symmetric sprite hides completely — as the first
     │  placeholder did, so the bug only surfaced once real art went in
     ├─ additive blending is order-independent, so nothing is sorted; no depth write means overlapping
     │  sparks cannot depth-cull each other
     ├─ the sprite is this mod's own, at
     │  src/main/resources/assets/create_shining_stage/textures/particle/cold_spark.png; the render
     │  type JSON names it as a full path, so reshaping or recolouring the spray is an art edit there
     │  and nothing else. It is a 16x48 strip of SPRITE_FRAMES (3) square cells, each a vertical streak
     │  that is near-white at its top row and warm orange at its bottom, with binary alpha; the three
     │  differ only in LENGTH (10, 7, 5 texels), so a spark visibly shrinks as it flies. Drawn with
     │  GL_NEAREST, which suits hard-edged art. The orientation is deliberate: V=0 is the strip's top
     │  row and also the end the roll points along the launch direction, so the white *leading* tip
     │  leads and the orange tail trails — and because cells stack in V, that means the bright end of
     │  each cell must stay at the TOP of its own cell. Keep at least one axis elongated: the roll is
     │  only a few degrees, so a round or square sprite throws that information away
     ├─ the cell is picked by the spark's own AGE (spriteCell), not by a clock: cells 0..FRAMES-2 split
     │  the rise and the last cell covers the apex plus the whole descent. Split that way *because* the
     │  fall is a fixed FALL_TICKS however fast the spark was thrown — equal slices of the life would
     │  run the shortest cell out well before the apex on a tall spray, and the spark would look burnt
     │  out halfway up. Keying the last cell to riseTicks (== speed/GRAVITY, the tick the spark
     │  actually reaches its apex) puts it exactly at the apex for every spark regardless of speed
     ├─ the strip must NOT be a .mcmeta animated sprite. Vanilla animates by ticking a frame counter
     │  on the *shared* SpriteContents and uploading that frame over the atlas cell in place, so the
     │  frame is driven by a global clock: every spark would flip cell on the same tick and the spray
     │  would flicker between shapes instead of each spark aging on its own. Indexing V by age keeps
     │  every cell resident at once, which is what lets a jet of sparks show all of them simultaneously.
     │  It also costs nothing extra: same 4 vertices, same 1 draw call, same single texture
     ├─ each cell's art must stay clear of its own V edges. Cell boundaries land on texel boundaries
     │  under GL_NEAREST, so a streak touching its edge could sample the neighbouring cell's lit
     │  texels; the cells' transparent padding is what prevents that. Re-check after any art edit
     ├─ own Veil program (pinwheel/shaders/program/cold_spark) + own render type
     │  (pinwheel/rendertypes/cold_spark.json), for the same reason the beam has one: a shader pack
     │  replaces vanilla's shader getters, so a vanilla-drawn spark would be shaded as a lit surface
     ├─ the render type is resolved lazily on first draw, because BE renderers are constructed while
     │  the reload carrying Veil's render types is still in flight; a failure is logged once, not thrown
     │  (a renderer that throws takes the game down with it)
     └─ culling: shouldRenderOffScreen (a 16-block spray can outlive its section), getViewDistance, a
        render bounding box covering the column, and shouldRender false unless sparks are in the air.
        The box is grown *symmetrically* so its centre stays on the block — see the physics-structure
        note below; a box centred on the middle of the column would cull the whole spray on a ship

MicrophoneBlock / SpeakerBlock (HorizontalDirectionalBlock + IBE) ──► SoundRelayHandler (server only)
  ├─ binding: right-click a microphone with a speaker item (BOUND_MIC data component), placing the
  │  speaker registers both directions; breaks unregister the other end
  ├─ relay: PlayLevelSoundEvent.AtPosition/.AtEntity → every live microphone in range (the range is
  │  Config's microphoneRange, one value for all microphones — no per-block value box) → linear
  │  falloff → each bound speaker replays with level.playSound (guard flag keeps a replay
  │  uncatchable), with speakers within Config's speakerClusterRadius merged into one replay at
  │  their common centre; a *placed* speaker only replays while redstone-powered
  ├─ placed: MicrophoneBlockEntity.onLoad/invalidate keep the registry in step with chunk load
  ├─ held: SpeakerBindingOutliner (client) outlines the microphone a held, bound speaker item
  │  names, through Create's Outliner on the client tick; SpeakerBlock.setPlacedBy spends BOUND_MIC,
  │  which is what ends the outline the moment the speaker lands
  └─ mounted: MicrophoneMovementBehaviour/SpeakerMovementBehaviour (Create actors, registered in
     commonSetup) re-register the device every tick from the contraption's captured block data,
     keyed by the position the block had when the structure was assembled — assembly clears the
     block entities out of the level, so only the contraption entity's transform knows where the
     device is now, and the binding's stored world position keeps resolving to it
```

Key pattern: **game logic in the block/BE, all visuals in the client renderer**. There is no custom networking — `ScrollValueBehaviour` handles range sync, and `notifyUpdate()` syncs color via the standard BE client packet.

Storage blocks expose NeoForge's item handler capability: `event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, context) -> …)` from a static `registerCapabilities(RegisterCapabilitiesEvent)` wired in the mod constructor, so a funnel, chute, arm or pipe sees the same handler without the block owning any I/O code. What a slot accepts is the `SmartInventory` stack predicate (`isItemValid`) — Create's `SmartInventory` is what makes "fuel only" one predicate instead of a per-route check. Which *faces* have an inventory is the provider's `context`, and returning `null` leaves that face with no capability at all: the cold spark machine allows `context.getAxis().isHorizontal()` only, so its lid refuses a funnel instead of feeding it. A side-gated provider must null-check the context — a side-less query (`getCapability(cap, pos, null)`) passes `null`, and Create's own side-gated inventories answer that with "no handler".

Two ways to get a Veil-drawn render type, both in use: the spotlight beam builds it in code (`RenderType.create` + `VeilRenderBridge.shaderState`) because it needs no texture, and the cold spark spray declares it in JSON (`assets/<ns>/pinwheel/rendertypes/<name>.json`, fetched with `VeilRenderType.get(id)`) because it needs a texture — vanilla's texture shards are `protected` and not reachable from mod code. The JSON's schema is `format`/`mode`/`bufferSize`/`sort`/`affectsCrumbling`/`outline` plus `layers`, whose types are `minecraft:texture` (`texture` is a full `.png` path, plus optional `blur`/`mipmap`), `minecraft:transparency` (`mode: ADDITIVE|LIGHTNING|TRANSLUCENT|…`), `minecraft:cull` (`face: NONE`), `minecraft:write_mask` (`color`/`depth`) and `veil:shader` (`name` = the program id). Resolve such a render type lazily on first draw, never in the renderer constructor: block entity renderers are built while the reload that carries Veil's render types is still in flight.

**Two traps in that JSON, both of which silently render a plain white quad instead of the sprite:**

- **`ADDITIVE` is `blendFunc(ONE, ONE)`** — vanilla's `ADDITIVE_TRANSPARENCY` discards the source alpha completely. Anything whose fade lives in alpha (a sprite's shape, a per-vertex fade) is then invisible, and because additive blending also ignores darkness, a sprite whose transparent pixels are white RGB paints its whole quad white. Use **`LIGHTNING`** (`blendFunc(SRC_ALPHA, ONE)`) for alpha-modulated additive glow. Only `LIGHTNING`/`GLINT`/`CRUMBLING`/`TRANSLUCENT` respect alpha; `ADDITIVE` does not.
- **Keep transparent sprite pixels black.** Under any blend that ignores or under-weights alpha, white RGB in transparent pixels adds white to the frame. Additive particle atlases conventionally store black there; do the same so the sprite cannot paint its bounding square.
- **`blur`/`mipmap`** decide GL filtering: `true`/`true` gives `GL_LINEAR_MIPMAP_LINEAR`, i.e. the sprite is resampled and softened; `false`/`false` gives `GL_NEAREST`, which keeps the sprite's own pixels crisp. Veil's `TextureLayer` builds `new TextureStateShard(location, blur, blur)` — the `mipmap` field is dropped and `blur` is passed twice — so `"blur"` is the one that matters. Which to pick depends on the sprite: nearest keeps a hard-edged sprite sharp, but cannot smooth a soft one, and at small on-screen sizes *no* filtering can save a shape that only covers a few pixels — size it so it covers enough pixels to read, then choose the filter.

## Key Directories

| Path | Purpose |
|---|---|
| `src/main/java/com/shiningstage/create_shining_stage/` | All mod code (flat package; mixins in `.mixin`, datagen in its provider) |
| `src/main/resources/` | assets (lang `en_us`/`zh_cn`, blockstates, item model), data (block loot table) |
| `src/main/templates/META-INF/neoforge.mods.toml` | Mod metadata template, `${...}` expanded at build time |
| `src/generated/resources/` | Datagen output — recipes and their recipe-book advancements, from `ShiningStageRecipeProvider`; wired as an extra resource sourceSet |
| `libs/` | Local jar deps, both `compileOnly`: `sable-companion-common-1.21.1-1.6.0.jar` (`SableCompanion` interface) and `veil-neoforge-1.21.1-4.3.2.jar` (draws the beam; Veil ships inside sable's jarjar, so this vendored copy tracks `sable_version`) |
| `docs/superpowers/` | Design spec + implementation plan (Chinese) from initial scaffolding |
| `run/` | Dev runtime dir (logs, saves) — generated, not source |

## Development Commands

Use the Gradle wrapper (Windows: `gradlew.bat`, or `.\gradlew.bat`):

```powershell
.\gradlew.bat build            # compile + jar → build/libs/create_shining_stage-1.0.0.jar
.\gradlew.bat runClient        # dev client (Create + Aeronautics + Sable on classpath)
.\gradlew.bat runServer        # headless server (--nogui)
.\gradlew.bat runData          # datagen → writes src/generated/resources/ (own game dir: build/run-data)
.\gradlew.bat runGameTestServer  # game-test runner; namespace create_shining_stage pre-enabled
```

No lint/format tasks are configured (no Spotless/Checkstyle).

## Code Conventions & Common Patterns

- **Registration**: NeoForge `DeferredRegister` in `Mod*` holder classes, registered from the mod constructor. Registrate is on the classpath (Create dependency) but is **not** used — keep using DeferredRegister for consistency.
- **Block+BE pairing**: block implements Create's `IBE<T>` (`getBlockEntityClass`/`getBlockEntityType`); BE extends `SmartBlockEntity` and adds `BlockEntityBehaviour`s in `addBehaviours`.
- **Blockstate props**: extend vanilla `DirectionalBlock` with `FACING`; call `registerDefaultState` and implement `codec()`/`createBlockStateDefinition`.
- **Client-only code**: separate `@Mod(value = MOD_ID, dist = Dist.CLIENT)` class; renderers registered via `EntityRenderersEvent.RegisterRenderers` on the mod event bus. Per-frame world overlays go through Create's `Outliner` (Ponder ticks and renders it every frame), refreshed from a `ClientTickEvent.Post` listener on `NeoForge.EVENT_BUS` — the outline then inherits Create's own block-outline look. An item that names a target block (the bound speaker) MUST consume that data when the placement happens: `BlockItem.place` hands `setPlacedBy` the player's own hand stack, so a component left behind outlives the binding and keeps drawing its outline.
- **Rendering**: custom `RenderType.create(...)` composite state; emit raw quads with `VertexConsumer` + `PoseStack` matrix. Invariants in `SpotlightRenderer`: `NO_CULL`, `COLOR_WRITE` (no depth write — crossing translucent beams must not depth-cull each other), translucency. The beam is drawn by **this mod's own Veil (Pinwheel) program** (`assets/create_shining_stage/pinwheel/shaders/program/spotlight_beam/`, bound with `VeilRenderBridge.shaderState`) rather than a vanilla one: a shader pack adapts by replacing *vanilla's* shader getters, so a vanilla-drawn beam gets shaded as a lit surface and lit by the pack's own path, while a Veil program is never reached by that substitution and is emitted light under every pack and with no pack installed alike. It also keeps the beam's two scalars — alpha and taper — in float UVs; a vertex colour's alpha is 8 bits, and a beam dimmed by a low redstone signal rounds to nothing inside it. The cross-section is drawn with **constant** alpha on all four sides, never a per-side falloff: a side's edges are the frustum's corners, which project into the middle of the beam off-axis, so any per-side falloff drags a transparent slit down the beam's centre. `COLOR_WRITE` also means the beam lands in no shadow map, so it casts no shadow in either pass.
- **Constants**: tunables are `static final` fields with javadoc at the top of the owning class (e.g. `MIN_RANGE`, `HALF_ANGLE_TAN`) — adjust there, not inline.
- **Ship safety**: any world-space distance involving raycast hits MUST use `Sable.HELPER.distanceSquaredWithSubLevels(...)`, never `Vec3.distanceTo` — hits in Aeronautics sub-levels sit at far-away coordinates and vanilla distance explodes. See comment in `SpotlightRenderer.render`.
- **Contraptions**: redstone is unreadable inside a Create contraption (its block entities are rebuilt in the contraption's own world), so `SpotlightRenderer` pins the signal to `CONTRAPTION_SIGNAL` (4/15) there. Detection is `be.getLevel() instanceof VirtualRenderWorld` — Create's `ClientContraption` is the only thing that instantiates that world. Sable sub-levels are deliberately NOT special-cased: their block entities keep reading real redstone. **The cold spark machine takes no Create-contraption support at all** (decided): it is an Aeronautics-machine, and the three things a Create contraption would have cost it are all free on a Sable sub-level.
- **Physics structures (Sable sub-levels)**: a sub-level is real chunks in a plot far from the origin, so a machine assembled onto one needs none of the contraption machinery above — its block entity keeps ticking, keeps reading real redstone, and keeps its state, because Sable captures the BE NBT on assembly (`SubLevelAssemblyHelper` does `saveWithFullMetadata` before the move and `loadWithComponents` after it, so the fuel slot and the spray timer survive; the untagged default path is fine here precisely because the machine drops its contents from `playerWillDestroy`, not `onRemove`). Rendering comes out right for the same reason the billboard does: Sable poses a sub-level's block entities with the plot→projected transform and hands `shouldRender` a *plot-space* camera position (`sable$setCameraPosition`), and this renderer works in whatever space the pose names. **The one thing that does not come free is the render bounding box**: Sable redirects `ClientHooks.isBlockEntityRendererVisible` in `LevelRenderer.renderLevel` and resolves which structure a block entity belongs to from `getRenderBoundingBox(be).getCenter()` (`Sable.HELPER.getContainingClient(center)`), then frustum-tests the box transformed by that sub-level's logical pose. A box whose centre has floated off the block therefore resolves to *no* sub-level, falls through to an untransformed plot-space frustum test, and is culled forever — so any renderer here whose drawing extends away from its block MUST grow its box symmetrically, never only towards the drawing.
- **Contraption mounts**: a block whose gameplay lives in its block entity stops working the moment its structure is assembled, because assembly moves the block entities into the contraption's block data and out of the level. Register a Create `MovementBehaviour` for it (in `commonSetup`, via `MovementBehaviour.REGISTRY.register`) and republish whatever the relay needs from `MovementContext`: `blockEntityData` (the captured NBT — behaviour values like `ScrollValue` are in there) plus a live position taken from `AbstractContraptionEntity#toGlobalVector`, never from the block's captured (contraption-local) position. Register on `tick`, not `startMoving`: assembly is the only path that calls `startMoving`, so a structure re-read from NBT would otherwise stay unregistered. `canBeDisabledVia` returns `null` for the audio blocks on purpose — Create's contraption controls are a redstone-driven actor gate, and an empty filter disables every actor that reports one.
- **Mic range & speaker power**: the microphone's listening range is a single COMMON config value (`Config.MICROPHONE_RANGE`, default 16) read live at relay time — deliberately *not* a `ScrollValueBehaviour`. A value box is only reachable through a block entity, and assembly moves block entities into the contraption's block data, so a per-block range would silently freeze at assembly time. A *placed* speaker only replays while `level.getBestNeighborSignal(pos) > 0`; that check lives in `SoundRelayHandler.speakerPosition`, which is already asked per event, so no tick and no cached field. A *mounted* speaker is exempt on purpose: its world position is surrounded by the contraption entity's own air, so no signal is readable there, and the contraption controls that could express a gate (`canBeDisabledVia`) are left unwired for the same reason the microphone's are — a stage keeps sounding for as long as its structure is assembled.
- **Speaker grouping**: an event is replayed once per *group* of speakers, not once per speaker. Overlapping copies are separate `SoundInstance`s client-side — each rolls its own sample variant, takes its own channel, and subtracts nothing from its neighbours, so a row of speakers a block apart is heard as the same noise several times rather than as one louder sound, and no client-side fix can merge them back. `SoundRelayHandler` collects the whole event first (`accumulate`) and only then plays (`flush`): one `Target` per bound speaker (keyed by binding position, so one speaker bound to several microphones is heard once, at the loudest), then one replay per group of speakers within `Config.SPEAKER_CLUSTER_RADIUS` (default 2, 0 = every speaker on its own), positioned at the group's volume-weighted centre with the volumes summed and clamped to 1. Group distances use the Sable helper like every other world-space distance.
- **Mixins**: two client-only mixins in `mixin/` — `EntityCullingMixin` (vanilla `Entity`, widens a contraption's culling box) and `ContraptionMixin` (Create's `Contraption`, captures spotlights at `readNBT` TAIL). They are declared in `src/main/resources/create_shining_stage.mixins.json` (which declares `package com.shiningstage.create_shining_stage.mixin`) and registered via a `[[mixins]]` block in `neoforge.mods.toml`. They MUST stay in that subpackage: a mixin config claims every class under its declared package, so a mixin sitting in the mod's main package makes the other mod classes unloadable. No refmap is generated or needed (NeoForge runs on official mappings); `build.gradle` passes `-proc:none` so sponge-mixin's refmap processor does not fail the build, so **`@Inject` targets are not checked at compile time** — verify target methods against the jar when renaming them. `@Inject` into Create internals should use the full descriptor (e.g. `readNBT(...)V`) so a signature change fails loudly instead of silently matching nothing.
- **Contraption culling**: the spotlight list is captured once, at contraption read time, and stored on the `Contraption` instance (`SpotlightBeams` duck interface, volatile — read on the network thread, queried on the render thread). Positions and facings are safe to cache (a contraption's blocks never change at runtime; only block *states* do). Beam *lengths* are NOT cached: they come from the live client block entity via `getBlockEntityClientSide`, so the scroll value still applies immediately. The injection point must stay at TAIL of `readNBT` — `readBlocksCompound` clears and re-fills `blocks`, so scanning mid-read would see a half-filled structure.
- **Comments**: explain *why* (invariants, upstream-mod behavior) in English, not *what*. Design docs are in Chinese; code is English.
- **Translations**: add every user-facing string to both `en_us.json` and `zh_cn.json`; blocks/items use `block.create_shining_stage.<name>` keys, value-box labels use `block.create_shining_stage.<name>.<label>`.

## Important Files

- `src/main/java/.../CreateShiningStage.java` — mod entry point (`@Mod`), all DeferredRegister wiring
- `.../SpotlightBlock.java` / `SpotlightBlockEntity.java` / `SpotlightRenderer.java` — the full content implementation
- `.../MicrophoneBlock.java` / `MicrophoneBlockEntity.java` / `SpeakerBlock.java` / `SpeakerBlockEntity.java` / `SoundRelayHandler.java` — the sound relay (server side)
- `.../ColdSparkMachineBlock.java` / `ColdSparkMachineBlockEntity.java` / `ColdSparkMachineValueBoxTransform.java` / `ColdSparkMachineRenderer.java` — the cold spark machine: four-way control face, spray-height panel, one side-fed fuel slot, the redstone-triggered two-second shot, and the spray it draws
- `src/main/resources/assets/create_shining_stage/pinwheel/rendertypes/cold_spark.json` + `pinwheel/shaders/program/cold_spark/` + `textures/particle/cold_spark.png` — the spray's additive Veil render type, its program, and its sprite (see the render-type paragraph under Key pattern)
- `.../ModItems.java` — plain items (the cold spark fuel); block items stay next to their blocks in `ModBlocks`
- `.../MicrophoneMovementBehaviour.java` / `SpeakerMovementBehaviour.java` — Create contraption actors that keep a mounted microphone listening and a mounted speaker playable
- `.../SpeakerBindingOutliner.java` — client-only; outlines the microphone a held, bound speaker item points at (see the `held:` line above)
- `.../Config.java` — COMMON spec; holds `MICROPHONE_RANGE` (listening radius, shared by every microphone) and `SPEAKER_CLUSTER_RADIUS` (see Speaker grouping)
- `.../ShiningStageRecipeProvider.java` — datagen recipes for every item (sturdy-sheet based, except the paper-and-dye floor marking); output committed under `src/generated/resources/data/create_shining_stage/recipe/`
- `.../mixin/EntityCullingMixin.java` / `.../mixin/ContraptionMixin.java` + `.../SpotlightBeams.java` + `src/main/resources/create_shining_stage.mixins.json` — contraption culling fix and its read-time spotlight capture
- `build.gradle` — ModDev Gradle config, Create/Ponder/Flywheel/Registrate/Aeronautics/Sable deps, run configs
- `gradle.properties` — **all versions live here** (`minecraft_version`, `neo_version`, `create_version`, `parchment_*`, `mod_version`, …); bump versions here, never hardcode
- `settings.gradle` — repository list (see Runtime/Tooling for the mirror setup)
- `docs/superpowers/specs/2026-09-10-create-shining-stage-design.md` — workspace design rationale and version table

## Runtime/Tooling Preferences

- **JDK 21** (toolchain, auto-resolved via foojay), **Gradle 9.2.1** wrapper, **ModDev Gradle** (`net.neoforged.moddev` 2.0.146), Parchment mappings `2024.11.17` on official Mojang mappings.
- **China network environment**: wrapper distribution and Maven repos use mirrors (Tencent, Aliyun, BMCLAPI) — do not remove them. `gradle.properties` has commented-out proxy settings (`systemProp.http.proxyHost/Port`) for when `maven.neoforged.net` is unreachable.
- Sable's `SableCompanion` interface must compile against `libs/sable-companion-common-1.21.1-1.6.0.jar` (`compileOnly files(...)`); the outer Modrinth jar alone is insufficient.
- Configuration cache, parallel execution, and build cache are enabled — keep tasks compatible.

## Delegation Preferences

**注意：这部分是给kimi work看的。如果你是omp内部的agent，务必忽略此段**

- **代码修改和构建都交给 omp（oh-my-pi）代劳**，不要自己直接改代码或直接跑 `gradlew.bat`。通过 omp-bridge 插件开新会话（`python <bridge> new <会话名> --cwd <本目录> --role <角色>`），构建、验证、修编译错误也在该会话内完成；主 agent 只做 briefing、核对产物和向用户汇报。
- 构建环境提示：本机 shell 默认无 JAVA_HOME，可用 `C:\Users\liujz\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2`；toolchain JDK 21 由 foojay 自动解析。

## Testing & QA

- **No tests exist** (no `src/test`, no game tests, no CI workflows). `runGameTestServer` is configured with the mod namespace enabled if game tests are added later (`@GameTest` in namespace `create_shining_stage`).
- Practical verification = `.\gradlew.bat build` plus `.\gradlew.bat runClient`: place a Spotlight, check beam rendering, dye recoloring, scroll-wheel range adjustment, redstone-strength behavior, and contraption culling (assemble the spotlight into a contraption, look away until the hull leaves the frustum — the beam must stay visible). For the audio: bind a speaker to a microphone, power the speaker with redstone, then make a noise — the relay must replay it, and must stop the moment the speaker loses power (and start again when it regains it, with no reliance on any cached state). Change `microphoneRange` and reload the config (config screen or `/reload`) and confirm the new radius applies to an already-placed microphone without re-placing it. Then assemble both into a contraption — the relay must keep working, and the replayed sound must come from where the speaker has moved to, not from where it was assembled (a wrong transform shows up as silence or as a beam of sound near the world origin). Disassembling must hand the pair back to their block entities, with no double relay in between, and the disassembled speaker must again require redstone. For grouping: bind a microphone to four speakers, two of them adjacent and two far apart, and confirm the adjacent pair produces one replay at their midpoint with the summed volume rather than two. Assembling from commands needs a nudge: a mechanical bearing only tries to assemble on a speed change, and a bearing placed into an already spinning network never sees one — place the rig first and the motor last (or break and re-place the motor). For the cold spark machine: place it, wrench-turn all four sides (the control face and its value box must travel with it, and the fuel already in the slot must survive the turn), set the spray height with the value box, then feed the slot — from a side, where a funnel works, and from the lid, where a funnel on top must find nothing to insert into. Pulse it with redstone: one shot per rising edge, one fuel per shot, and nothing at all with an empty slot. Hold the line high — it must fire once and then stay quiet rather than re-firing every two seconds; a signal arriving during the two seconds must be ignored rather than queued; a machine placed beside an already-powered line must not fire until that line drops and rises again. The spray itself has no test but the eye: stand back and fire it — a fountain of distinct white sparks should rise to about the configured height and fall back, with the last sparks still landing just after the two seconds are up. Nothing should be drawn while the machine is idle, and the sparks must stay put when the camera moves (they are world geometry, not a screen effect).
- **Recipes are datagen output**, not hand-written data: `ShiningStageRecipeProvider` (registered on `GatherDataEvent` from the mod constructor) builds every item's recipe and `runData` writes them to `src/generated/resources/`. Ingredients are Create/vanilla constants (`AllItems`, `AllBlocks`, `Tags.Items`), so a renamed item is a compile error. The data run keeps its own game directory (`build/run-data`, set in `build.gradle`) instead of sharing `run/`: `run/mods` holds a client shader setup (Iris + Sodium), and loading it under datagen deadlocks mod construction — Sodium's `RenderStateShard` static init in one mod-loading thread against Registrate's `RenderType` static init in the others.
