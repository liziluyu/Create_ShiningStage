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
  ├─ onRemove → SpotlightBlockEntity.removePlacedLights (see the beam-light note below; onRemove is the
  │  hook that fires for a break, a replacement and an assembly alike, and never for a chunk unload)
  └─ SpotlightBlockEntity (Create SmartBlockEntity)
       ├─ addBehaviours: ScrollValueBehaviour "max_length" (MIN/MAX/DEFAULT_RANGE = 2/32/16)
       │                 positioned by SpotlightRangeValueBoxTransform (ValueBoxTransform.Sided on tail face)
       ├─ tick: every LIGHT_POLL_INTERVAL (5) ticks re-derives where the beam ends and keeps a
       │  minecraft:light (level LIGHT_LEVEL = 15) in the cell one before the hit, or the cell at the
       │  range on a miss — the server-side twin of the renderer's raycast, and the only world writes
       │  this block ever makes beyond its own state
       └─ NBT "LaserColor" (default 0xBC76FF) + "PlacedLights" (save-only long[] of cells it owns)

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
       ├─ MODEL_HEIGHT_PX = 20: the cabinet is 20/16 blocks tall, a full 16x16 footprint standing a
       │  quarter block proud, so its top face — the nozzle — sits above y = 1. getShape returns that
       │  box, and *only* collision and the outline may follow it: getShape is also the default source
       │  of getBlockSupportShape and getOcclusionShape, and a shape taller than its own cell is wrong
       │  for both, so each is overridden back to Shapes.block(). Support is what attachment asks, and
       │  a face only counts if it compares equal to a full block — a face whose axis coordinates are
       │  not exactly 0..1 (ours end at 1.25) is reduced to a flat slice first, so with the 20-tall
       │  shape all four SIDE faces fail SupportType.FULL (measured: FULL d+ u+ n- s- w- e-) and glass
       │  panes, iron bars, fences, walls and vines stop connecting to the cabinet. Occlusion is a
       │  per-cell question, so it answers with a full cell too. FACING is the *control face*, which is
       │  all the four-way mounting means: the value box follows it, the model does not
       ├─ rotation is free: Create's IWrenchable default steps any state carrying HORIZONTAL_FACING a
       │  quarter turn on a vertical face, so no rotate code here (HorizontalDirectionalBlock's own
       │  rotate/mirror are the structure-block path and never run for a wrench)
       ├─ addBehaviours: ScrollValueBehaviour "spray_height" (MIN/MAX/DEFAULT = 1/16/4) on
       │  ColdSparkMachineValueBoxTransform (Sided, active on the FACING face — the spotlight's pattern)
       ├─ one SmartInventory slot (fuel-only predicate) exposed as Capabilities.ItemHandler.BLOCK on the
       │  four horizontal faces only — context.getAxis().isHorizontal(), null for UP/DOWN and for the
       │  side-less query, so a funnel on the lid has nothing to feed; registered from
       │  ColdSparkMachineBlockEntity.registerCapabilities (RegisterCapabilitiesEvent)
       ├─ the same slot is loadable by hand: useWithoutItem opens ColdSparkMachineMenu, so GUI and funnel
       │  are one SmartInventory and there is no menu-side copy to write back (saveData is empty). Two
       │  items keep the click instead, and they must, because this hook runs *before*
       │  ItemStack#useOn — anything consumed here is a click the held item never gets, so both return
       │  PASS, which hands the click on rather than merely declining it: a BlockItem (means "attach it
       │  here", the same rule the value box follows via bypassesInput) and Create's WrenchItem (means
       │  "turn the control face", and a wrench must never open the menu)
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
     │  not multiplied by age: it is where the spark began, not how far it has travelled. That plane is
     │  NOZZLE_Y = the block's MODEL_HEIGHT_PX/16, not a hardcoded 1: the cabinet stands 20/16 tall, so
     │  the whole parabola (and the render box's top) hangs off the model's height, and raising the
     │  model again is one number
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

ColdSparkMachineMenu (Create MenuBase) + ColdSparkMachineScreen (client, AbstractSimiContainerScreen)
  ├─ opened from the block's useWithoutItem with player.openMenu(be, be::sendToMenu): the block entity
  │  is the MenuProvider, and sendToMenu writes its position *and* its update tag, which is what lets
  │  createOnClient resolve the same block entity — and so the same inventory — the server clicked on.
  │  Registered as a MenuType through IMenuTypeExtension.create (the extra-data factory is the part
  │  that carries that data; a bare MenuType has no second argument to receive it), the screen on
  │  RegisterMenuScreensEvent, which is a mod-bus event
  ├─ **the slot is a SlotItemHandler over the SmartInventory, not a plain Slot**, and that is the one
  │  trap here. Vanilla Slot#mayPlace returns an unconditional true in 1.21 and Slot#getMaxStackSize
  │  asks the *Container* — so a plain Slot wrapping a SmartInventory (a Container) both accepts stone
  │  into the fuel slot and reports a max stack size of 0, since ItemHandlerContainer#getMaxStackSize
  │  is 0; the 0 then makes moveItemStackTo place nothing at all, silently killing shift-click. The
  │  predicate and the slot limit live on the item handler, which is exactly what SlotItemHandler asks:
  │  mayPlace → isItemValid (the fuel predicate) and getMaxStackSize → getSlotLimit
  ├─ shift-click crosses machine ↔ player inventory, and a non-fuel stack is refused by mayPlace so it
  │  simply stays where it is
  ├─ MenuBase gives the rest for free: distance-based stillValid (SmartBlockEntity is an
  │  IInteractionChecker → player.distanceToSqr(...) <= 64) and a null-contentHolder path that must not
  │  throw — the block can be broken with its screen open, so createOnClient and addSlots both tolerate
  │  a missing block entity and let stillValid close the menu instead
  ├─ **the panel must be blitted with the 9-argument overload**, because the 7-argument
  │  GuiGraphics#blit hardcodes the texture size as 256x256
  │  (`blit(loc, x, y, u, v, w, h)` → `blit(..., 256, 256)`). Vanilla's and Create's GUI sheets are
  │  all 256x256, so the short call is right for them and wrong for anything else: this panel is
  │  176x44, so the short call samples only its top-left 121x7.5 pixels and stretches them over the
  │  whole quad. The symptoms are distinctive and were all seen in game at once — the item floated on
  │  plain grey with no slot box (the box sits far below the sampled strip), the right and bottom
  │  borders were missing (never sampled), and a black and a white band from the texture's top two
  │  rows smeared ~6x down the panel. Pass the texture's own size as the last two arguments
  ├─ layout: PANEL_W/PANEL_H and every slot coordinate live in the menu as the single source of truth.
  │  The panel texture draws its slot box at exactly those coordinates, and the player rows use the
  │  offsets Create's player-inventory sheet already uses (first row 18 down, hotbar 58 below that,
  │  first column 8), so picture and hitboxes agree by construction — change one and the others must
  │  follow. Verified by measuring the slot boxes out of both PNGs and comparing to the menu's output
  └─ the window is panel + AllGuiTextures.PLAYER_INVENTORY, the two drawn *flush* — no gap between
     them, because the boxes each draw their own frame and whatever is left between them is a strip of
     the world showing through the middle of the GUI (seen in game). Only the panel is this mod's art
     (textures/gui/cold_spark_machine.png, exactly PANEL_W x PANEL_H, vanilla container palette, one
     18x18 slot box at 79,21 for the slot at 80,22); the player's half is Create's shared sheet via
     renderPlayerInventory, which is why the panel is 176 wide — that is the sheet's width — and why
     the sheet is drawn at topPos + PANEL_H, putting its first slot row 18px below the panel

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

Showing a block entity's inventory in a GUI is Create's menu pair, not a hand-rolled `AbstractContainerMenu`: `MenuBase<T>` (which derives `T` on the client from a `RegistryFriendlyByteBuf` via `createOnClient`, adds the player slots with `addPlayerSlots`, and supplies `stillValid` from the block entity's own `IInteractionChecker` distance check) plus `AbstractSimiContainerScreen` (which sizes the window with `setWindowSize` and draws the player's half with `renderPlayerInventory`). A block entity becomes the `MenuProvider`, `SmartBlockEntity#sendToMenu` is the extra-data writer, and `useWithoutItem` is the hook that opens it. Two consequences worth knowing before adding another: **the slot must be a `SlotItemHandler`** over the `SmartInventory`, because vanilla `Slot#mayPlace` answers an unconditional `true` in 1.21 and `Slot#getMaxStackSize` reads the `Container` — and `ItemHandlerContainer#getMaxStackSize` is 0, which silently stops `moveItemStackTo` from placing anything; and **`useWithoutItem` runs before `ItemStack#useOn`**, so any item the block still wants to act on (block placement, the wrench) has to be let past with `InteractionResult.PASS` or the menu swallows that click. Only the panel needs art of its own: the player's half comes from Create's shared sheet, so the panel is drawn at that sheet's width (176) and the slot coordinates in the menu have to match the texture's slot box exactly.

Two ways to get a Veil-drawn render type, both in use: the spotlight beam builds it in code (`RenderType.create` + `VeilRenderBridge.shaderState`) because it needs no texture, and the cold spark spray declares it in JSON (`assets/<ns>/pinwheel/rendertypes/<name>.json`, fetched with `VeilRenderType.get(id)`) because it needs a texture — vanilla's texture shards are `protected` and not reachable from mod code. The JSON's schema is `format`/`mode`/`bufferSize`/`sort`/`affectsCrumbling`/`outline` plus `layers`, whose types are `minecraft:texture` (`texture` is a full `.png` path, plus optional `blur`/`mipmap`), `minecraft:transparency` (`mode: ADDITIVE|LIGHTNING|TRANSLUCENT|…`), `minecraft:cull` (`face: NONE`), `minecraft:write_mask` (`color`/`depth`) and `veil:shader` (`name` = the program id). Resolve such a render type lazily on first draw, never in the renderer constructor: block entity renderers are built while the reload that carries Veil's render types is still in flight.

**Two traps in that JSON, both of which silently render a plain white quad instead of the sprite:**

- **`ADDITIVE` is `blendFunc(ONE, ONE)`** — vanilla's `ADDITIVE_TRANSPARENCY` discards the source alpha completely. Anything whose fade lives in alpha (a sprite's shape, a per-vertex fade) is then invisible, and because additive blending also ignores darkness, a sprite whose transparent pixels are white RGB paints its whole quad white. Use **`LIGHTNING`** (`blendFunc(SRC_ALPHA, ONE)`) for alpha-modulated additive glow. Only `LIGHTNING`/`GLINT`/`CRUMBLING`/`TRANSLUCENT` respect alpha; `ADDITIVE` does not.
- **Keep transparent sprite pixels black.** Under any blend that ignores or under-weights alpha, white RGB in transparent pixels adds white to the frame. Additive particle atlases conventionally store black there; do the same so the sprite cannot paint its bounding square.
- **`blur`/`mipmap`** decide GL filtering: `true`/`true` gives `GL_LINEAR_MIPMAP_LINEAR`, i.e. the sprite is resampled and softened; `false`/`false` gives `GL_NEAREST`, which keeps the sprite's own pixels crisp. Veil's `TextureLayer` builds `new TextureStateShard(location, blur, blur)` — the `mipmap` field is dropped and `blur` is passed twice — so `"blur"` is the one that matters. Which to pick depends on the sprite: nearest keeps a hard-edged sprite sharp, but cannot smooth a soft one, and at small on-screen sizes *no* filtering can save a shape that only covers a few pixels — size it so it covers enough pixels to read, then choose the filter.

**The truss as a casing** (`truss_encased_shaft` / `_cogwheel` / `_large_cogwheel`): the three blocks are plain instances of Create's own `EncasedShaftBlock` and `EncasedCogwheelBlock` (small and large) constructed with `ModBlocks.TRUSS::get` as their casing — nothing is subclassed, because those blocks already derive everything from that one supplier: which held item encases them (`EncasingRegistry` + `EncasedBlock#getCasing()`), what pick-block returns, and what a sneak-wrench unwraps them into. The pairing itself is two registrations with different owners, so they live in two different events, both wired from the mod constructor (`TrussBlock.registerEncasing` / `TrussBlock.addValidBlocks`):

- `EncasingRegistry.addVariant(bare, encased)` is Create's, a plain static map that must not be touched before block registration finishes — hence `FMLCommonSetupEvent` (with `enqueueWork`). It is what makes right-clicking a placed shaft, cogwheel or large cogwheel with a truss item in hand swap the block. Getting it wrong is silent: the truss just never encases anything.
- `BlockEntityTypeAddBlocksEvent` is NeoForge's, and it is the one that is *not* cosmetic. The encased variants reuse Create's `ENCASED_SHAFT`/`ENCASED_COGWHEEL`/`ENCASED_LARGE_COGWHEEL` block entity types, and a block entity type only knows the blocks it was registered with: `BlockEntity#isValidBlockState` is `type.isValid(state)`, so a block missing from that set has its block entity refused rather than adopted — the encasing swap fails loudly with "Invalid block entity … state … does not allow it", and a chunk load leaves the block holding no block entity at all. Use the event, never mutate the `validBlocks` set.

Everything visual about the three encased blocks is the truss block's *own* model: their blockstates are a single `""` variant pointing at `create_shining_stage:block/truss`, and their item models parent that same model. So there is no casing texture of this mod's own, no per-variant model (the `top_shaft`/`bottom_shaft` and axis variants Create's own encased models need are unnecessary here) and no render-type override — the truss's `cutout` render type comes in with the model. The part inside is drawn by Create, not by the model: `ENCASED_SHAFT` is a `KineticBlockEntity` with `ShaftRenderer`, and `ENCASED_COGWHEEL`/`ENCASED_LARGE_COGWHEEL` are `SimpleKineticBlockEntity`s with `EncasedCogRenderer`, which draw the rotating shaft (and the shaft stubs where `hasShaftTowards` agrees) through the model's apertures. That is the whole reason the shell can be a hollow frame: Create's own casing models are 2px-thick boxes that *hide* the part and show it only through a transparent band of `*_encased_cogwheel_side`, while the truss frame is a shell of six flat planes at the cell boundary — none of them declares `cullface`, so all six keep drawing even when a neighbour would hide them, exactly as the standalone truss block behaves — so the part stays visible through it and nothing needs a texture of its own. Loot drops only the bare shaft/cogwheel, as Create's encased variants do, and there are no crafting recipes for them for the same reason: encasing is the right-click mechanic. Connected textures are deliberately **not** wired (`EncasedCTBehaviour`, `CasingConnectivity`) — that needs a 128×128 omnidirectional CT sheet plus a `CTModel` registration per block, and the truss only connects to its own two siblings.

**Consequence worth knowing:** all four items — the truss and the three encased forms — draw the same icon. It is one shared model by design (no casing art), so the inventory tells them apart by name only.

## Key Directories

| Path | Purpose |
|---|---|
| `src/main/java/com/shiningstage/create_shining_stage/` | All mod code (flat package; mixins in `.mixin`, datagen in its provider) |
| `src/main/resources/` | assets (lang `en_us`/`zh_cn`, blockstates, item model, `textures/gui/` for the machine's panel), data (block loot table) |
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
- **Beam light (where the beam lands)**: the spotlight keeps a `minecraft:light` (level 15) in the cell the beam ends in — one before the hit, or the cell at `getRange()` on a miss — so the beam actually illuminates what it lands on. What follows is decisions, not details:
  - **It is a poll, not an event.** A block anywhere along the beam truncates it, and only the cells touching the spotlight report a neighbour change; the far end of a 32-block beam reports nothing at all. So the server re-derives the cell on its own clock (`tick`, every `LIGHT_POLL_INTERVAL` = 5 ticks) with the same clip the renderer uses. `SmartBlockEntity` already ticks every tick (via `SmartBlockEntityTicker`), so this costs a counter and, at most, one raycast per spotlight per 5 ticks.
  - **The interval throttles the world writes, which is the part that matters.** One change is one light-engine update plus a section rebuild on every client, and the beam's end moves as fast as anything crossing it: a hull drifting through a beam moves the cell every tick, so a check per tick would place and tear out a light twenty times a second, each flooding block light over fifteen blocks and draining it again. Five ticks bounds it at four a second. Steady state costs *nothing*: the cell only has to move, or the beam only has to switch, for the world to be touched at all — a cell that is already ours and already `minecraft:light` is left alone.
  - **The light is not a block this mod owns.** It is vanilla's own `minecraft:light`, has no collision shape (so the beam's own raycast cannot hit it and shorten the beam into a loop), no loot, and no outline. The block entity only remembers the cells it placed (`PlacedLights`, save-only NBT) and takes them back on `onRemove` — the one hook that fires for a break, a replacement and an assembly alike, and never for a chunk unload. A cell whose chunk is not loaded is skipped and retried on a later poll rather than written to. Two beams ending in the same cell converge: **both record it**, and whichever leaves first takes the block with it, after which the other re-places it on its next poll — which is also why a beam end that already holds a level-15 light is **adopted** onto the books rather than refused, a light of any other level being someone else's and left exactly as it is. Adoption is not a nicety: assembling or block-ifying a lit rig **moves the light block itself** between spaces, while `onRemove` on the old cell clears the record, so refusing the block it finds would strand it — the poll would never take it on and nothing could ever take it back. A record naming a **plot** cell that is not present is dropped rather than retried (a light a structure holds lives and dies with its plot, so a record that cannot resolve belongs to a structure that is gone); a record naming a **world** cell that is merely unloaded is kept and retried.
  - **It stays off the renderer's path.** The light level is fixed at 15 and not scaled by the redstone signal: the signal changes far more often than the geometry does, and a flickering line would be one light update per step.
  - **Scope: static world and Sable sub-levels only, not Create contraptions.** The light needs a block entity that ticks in the level, which a sub-level keeps (real chunks) and a Create contraption does not (assembly removes the BE, and no `MovementBehaviour` is registered for the spotlight). A contraption-mounted spotlight still *draws* its beam — `SpotlightRenderer` pins the signal there — but leaves no light, and assembly hands the world's lights back through `onRemove` as the blocks leave.
  - **On a physics structure (Sable sub-level).** The block entity keeps ticking there, so the poll works unchanged, and every scenario was measured on a live dev server:
    | where the beam ends | what happens | cleanup |
    |---|---|---|
    | inside its own structure | the light lands in the plot, at the cell before the hit — a block in the beam truncates it one cell early | dies with the plot; no record to chase |
    | onto the ground below the ship | the light lands in the **world's** chunks, and its record in the ship's plot names a world cell, because that is where the block lives | `LIGHT_CLEANUP` — see below |
    | at *another* structure | the light lands in that structure's plot, not in the shooter's: a ray from A hits B's hull and the reported cell is in B's space | `LIGHT_CLEANUP` on the structure that **owns the spotlight**, not the one holding the light |
    | the ship moves | the cell follows within one poll — the plot-space halves are rigid, and the world cell is re-derived from the projection each time | old cell is taken back by the same poll |
    | the structure is **block-ified** — Simulated's `physics_assembler` (`SimAssemblyHelper.disassembleSubLevel`) puts it back into the world | the light is carried out of the plot into the world **by the move itself**, exactly as it was carried in, and the poll re-derives the cell and **adopts** the block it finds | the old plot record is dropped (the plot cell is unreachable) and the world record is taken back normally |
  - **`LIGHT_CLEANUP` is the second way out**, and it is needed because a structure can vanish without any block being removed from the level: removing or unloading a sub-level unloads its plot chunks and tells the block entities through `onChunkUnloaded`/`setRemoved`, never through `Block#onRemove`. That is indistinguishable from an ordinary chunk unload *inside* a block entity, and an ordinary unload must not delete anything. So the spotlight registers a Sable `SubLevelObserver` the first time it holds a light whose cell is **outside** the plot it rides (checked each poll, not only when one is placed, so a spotlight assembled with its beam already lit is still watched). The observer walks the departing structure's still-loaded plot chunks, finds its spotlights and asks each to clean up — **deferred through `MinecraftServer#execute`**, because the callback runs in the middle of Sable taking the structure apart and world writes have no business re-entering that teardown. The observer is added once per level the first time it could matter, so a world with no ships never pays for it.
  - **Unloaded chunks are refused, not read.** `Level#getBlockState` asks the chunk source with `requireChunk = true`, which tickets, blocks and *generates*. A 32-block beam at the edge of the loaded area would therefore mine the world generator every poll, so the poll first walks the beam's cells in **both** spaces — the plot-space cells and their images in the world, because Sable's `BlockGetter#clip` maps a ray starting inside a sub-level out to the structure's real position and clips the main level there too — and stands down unless every one is present. In an ordinary setup it never refuses: a chunk within beam range of a ticking block entity is present. Note that vanilla's `execute if loaded` is **not** this predicate (it asks whether a chunk is entity-ticking, which is narrower), so it is not a usable check for this; `Level#isLoaded` → `ChunkSource#hasChunk` with `requireChunk = false` is the one that costs nothing and cannot itself pull the world in.
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
- `.../ColdSparkMachineMenu.java` / `.../ColdSparkMachineScreen.java` / `.../ModMenuTypes.java` + `src/main/resources/assets/create_shining_stage/textures/gui/cold_spark_machine.png` — the machine's right-click GUI: one fuel slot over the player's inventory, the menu holding the layout constants the panel texture was drawn to match (see the `ColdSparkMachineMenu` entry above)
- `src/main/resources/assets/create_shining_stage/pinwheel/rendertypes/cold_spark.json` + `pinwheel/shaders/program/cold_spark/` + `textures/particle/cold_spark.png` — the spray's additive Veil render type, its program, and its sprite (see the render-type paragraph under Key pattern)
- `.../ModItems.java` — plain items (the cold spark fuel); block items stay next to their blocks in `ModBlocks`
- `.../TrussBlock.java` + `.../ModBlocks.java` (`TRUSS_ENCASED_*`) + the `truss_encased_*` blockstates/item models and `data/.../loot_table/blocks/truss_encased_*` — the truss as a casing over Create's shafts and cogwheels: the two registrations that pair them up, and assets that are just the truss's own model reused (see "The truss as a casing" above)
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
- **AE2 is a *test-runtime* mod here, not a dependency.** `run/mods/` (plus GuideME 21.1.19, which AE2 requires) holds `appliedenergistics2-19.2.18.jar`, so `runClient`/`runServer` load AE2 for compatibility work while `build.gradle`, `gradle.properties` and the shipped `neoforge.mods.toml` stay AE2-free. Do NOT add it to `dependencies { }` until code actually references AE2 classes — then the one-liner is `compileOnly "maven.modrinth:ae2:19.2.18"` (`+ "maven.modrinth:guideme:21.1.19"` if the guidebook API is touched); both coordinates resolve through the Modrinth repo already declared in `settings.gradle`, and `run/mods` keeps providing the runtime jar. AE2's other Modrinth deps are optional (EMI/REI/Jade/TOP) and are deliberately absent. `run/` is gitignored, so the jars are per-machine and must be re-downloaded after a fresh clone (Modrinth API: project `XxWD5pD3`, loader `neoforge`, game version `1.21.1`). Datagen is unaffected — it runs in its own `build/run-data` game dir.
- Configuration cache, parallel execution, and build cache are enabled — keep tasks compatible.

## Delegation Preferences

**注意：这部分是给kimi work看的。如果你是omp内部的agent，务必忽略此段**

- **代码修改和构建都交给 omp（oh-my-pi）代劳**，不要自己直接改代码或直接跑 `gradlew.bat`。通过 omp-bridge 插件开新会话（`python <bridge> new <会话名> --cwd <本目录> --role <角色>`），构建、验证、修编译错误也在该会话内完成；主 agent 只做 briefing、核对产物和向用户汇报。
- 构建环境提示：本机 shell 默认无 JAVA_HOME，可用 `C:\Users\liujz\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2`；toolchain JDK 21 由 foojay 自动解析。

## Testing & QA

- **No tests exist** (no `src/test`, no game tests, no CI workflows). `runGameTestServer` is configured with the mod namespace enabled if game tests are added later (`@GameTest` in namespace `create_shining_stage`). One trap if you use it: NeoForge prefixes a test's template with the lowercased test class name unless the class carries `@PrefixGameTestTemplate(false)`, and the template must exist as `data/<ns>/structure/<name>.nbt` (a hand-written empty structure — a gzipped NBT with `DataVersion`, `size`, and empty `palette`/`blocks`/`entities` — is enough, since the framework plants its own structure block). A run dir of its own (`gameDirectory = file('build/run-gametest')` on the run config) keeps an isolated test server from fighting a running client for `run/`.
- Practical verification = `.\gradlew.bat build` plus `.\gradlew.bat runClient`: place a Spotlight, check beam rendering, dye recoloring, scroll-wheel range adjustment, redstone-strength behavior, and contraption culling (assemble the spotlight into a contraption, look away until the hull leaves the frustum — the beam must stay visible). The beam light is verifiable from a server console with no client at all (enable rcon in `run/server.properties`, or just type into the runServer console): `/setblock 0 100 0 create_shining_stage:spotlight` then `/setblock 1 100 0 minecraft:redstone_block` — `/execute if block 0 100 -16 minecraft:light[level=15]` must pass at the range, and `/data get block 0 100 0 PlacedLights` must name that cell. Put a wall at `0 100 -8` and within 5 ticks the light must be at `0 100 -7` and the old cell must be air; remove the wall and it must move back out. Unpower the line and both cells must become air; break the spotlight and the light must go with it. Restart the server with the beam lit: the light must still be there (it is in the saved NBT), and it must not be duplicated or churned on load. For the physics-structure cases, `sable paused true` first — otherwise structures fall and every reading moves — and `/sable info @e` lists the live ones. Build a ship (`/sable assemble area <from> <to>`; the spotlight's cell in plot space is its world cell offset by the plot base, which is `/sable info` position rounded, e.g. world `200 100 0` from an anchor at `198 120 -2` lands at `20481034 128 20481032`): a beam pointing down at the ground must put the light in the **world** chunks below, its `PlacedLights` must name that world cell, and moving the ship must move the light within a poll; a beam into a *second* structure must put the light in **that** structure's plot; and `/sable remove @e` must take the light back in every case without a crash. Block-ifying is the same primitive in reverse — Simulated's `physics_assembler` (`SimAssemblyHelper.disassembleSubLevel` → `moveBlocks`) puts the structure back into the world, carrying the light block with it — so after a block-ify the beam must still be lit, `/data get … PlacedLights` must now name the **world** cell, and the light must go out normally when the beam moves or loses power; a light of a different level sitting in the beam's path must be left completely alone. Note that a spotlight powered by a redstone block **in the beam's path** is the "shut against its own face" case and correctly places nothing — power it from the other side. For the audio: bind a speaker to a microphone, power the speaker with redstone, then make a noise — the relay must replay it, and must stop the moment the speaker loses power (and start again when it regains it, with no reliance on any cached state). Change `microphoneRange` and reload the config (config screen or `/reload`) and confirm the new radius applies to an already-placed microphone without re-placing it. Then assemble both into a contraption — the relay must keep working, and the replayed sound must come from where the speaker has moved to, not from where it was assembled (a wrong transform shows up as silence or as a beam of sound near the world origin). Disassembling must hand the pair back to their block entities, with no double relay in between, and the disassembled speaker must again require redstone. For grouping: bind a microphone to four speakers, two of them adjacent and two far apart, and confirm the adjacent pair produces one replay at their midpoint with the summed volume rather than two. Assembling from commands needs a nudge: a mechanical bearing only tries to assemble on a speed change, and a bearing placed into an already spinning network never sees one — place the rig first and the motor last (or break and re-place the motor). For the cold spark machine: place it, wrench-turn all four sides (the control face and its value box must travel with it, and the fuel already in the slot must survive the turn), set the spray height with the value box, then feed the slot — from a side, where a funnel works, and from the lid, where a funnel on top must find nothing to insert into. Right-click it with an empty hand — a panel with one slot must open over the player's inventory, the items must sit in the drawn slot boxes, and only fuel must be placeable in it (a shift-clicked stack of anything else must stay put). Right-click it holding a block, and the block must be placed instead of the panel opening; right-click it holding a wrench and the control face must turn instead. Breaking the block while the panel is open must close it rather than crash, and walking away must close it too. Then pulse it with redstone: one shot per rising edge, one fuel per shot, and nothing at all with an empty slot. Hold the line high — it must fire once and then stay quiet rather than re-firing every two seconds; a signal arriving during the two seconds must be ignored rather than queued; a machine placed beside an already-powered line must not fire until that line drops and rises again. The spray itself has no test but the eye: stand back and fire it — a fountain of distinct white sparks should rise to about the configured height and fall back, with the last sparks still landing just after the two seconds are up. Nothing should be drawn while the machine is idle, and the sparks must stay put when the camera moves (they are world geometry, not a screen effect). For the truss casing: right-click a placed shaft, cogwheel and large cogwheel with a truss item in hand — each must become its truss-encased variant, keep its axis, and keep a working kinetic block entity (the failure mode to watch for is the opposite of a crash: the block lands with **no** block entity at all, logged as "Invalid block entity … state … does not allow it"); a sneak-wrench must hand the bare part back. The casing itself is a shell around the part, so check it from a few angles for holes or see-through inner faces.
- **Recipes are datagen output**, not hand-written data: `ShiningStageRecipeProvider` (registered on `GatherDataEvent` from the mod constructor) builds every item's recipe and `runData` writes them to `src/generated/resources/`. Ingredients are Create/vanilla constants (`AllItems`, `AllBlocks`, `Tags.Items`), so a renamed item is a compile error. The data run keeps its own game directory (`build/run-data`, set in `build.gradle`) instead of sharing `run/`: `run/mods` holds a client shader setup (Iris + Sodium), and loading it under datagen deadlocks mod construction — Sodium's `RenderStateShard` static init in one mod-loading thread against Registrate's `RenderType` static init in the others.
