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
                                       └──► Config.SPEC (empty COMMON ModConfigSpec, placeholder)
CreateShiningStageClient (@Mod dist=CLIENT) ── EntityRenderersEvent.RegisterRenderers ──► SpotlightRenderer

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
```

Key pattern: **game logic in the block/BE, all visuals in the client renderer**. There is no custom networking — `ScrollValueBehaviour` handles range sync, and `notifyUpdate()` syncs color via the standard BE client packet.

## Key Directories

| Path | Purpose |
|---|---|
| `src/main/java/com/shiningstage/create_shining_stage/` | All mod code (10 classes, flat package) |
| `src/main/resources/` | assets (lang `en_us`/`zh_cn`, blockstates, item model), data (block loot table) |
| `src/main/templates/META-INF/neoforge.mods.toml` | Mod metadata template, `${...}` expanded at build time |
| `src/generated/resources/` | Datagen output; wired as an extra resource sourceSet (currently empty — no datagen providers exist yet) |
| `libs/` | Local jar deps, both `compileOnly`: `sable-companion-common-1.21.1-1.6.0.jar` (`SableCompanion` interface) and `veil-neoforge-1.21.1-4.3.2.jar` (draws the beam; Veil ships inside sable's jarjar, so this vendored copy tracks `sable_version`) |
| `docs/superpowers/` | Design spec + implementation plan (Chinese) from initial scaffolding |
| `run/` | Dev runtime dir (logs, saves) — generated, not source |

## Development Commands

Use the Gradle wrapper (Windows: `gradlew.bat`, or `.\gradlew.bat`):

```powershell
.\gradlew.bat build            # compile + jar → build/libs/create_shining_stage-1.0.0.jar
.\gradlew.bat runClient        # dev client (Create + Aeronautics + Sable on classpath)
.\gradlew.bat runServer        # headless server (--nogui)
.\gradlew.bat runData          # datagen → writes src/generated/resources/
.\gradlew.bat runGameTestServer  # game-test runner; namespace create_shining_stage pre-enabled
```

No lint/format tasks are configured (no Spotless/Checkstyle).

## Code Conventions & Common Patterns

- **Registration**: NeoForge `DeferredRegister` in `Mod*` holder classes, registered from the mod constructor. Registrate is on the classpath (Create dependency) but is **not** used — keep using DeferredRegister for consistency.
- **Block+BE pairing**: block implements Create's `IBE<T>` (`getBlockEntityClass`/`getBlockEntityType`); BE extends `SmartBlockEntity` and adds `BlockEntityBehaviour`s in `addBehaviours`.
- **Blockstate props**: extend vanilla `DirectionalBlock` with `FACING`; call `registerDefaultState` and implement `codec()`/`createBlockStateDefinition`.
- **Client-only code**: separate `@Mod(value = MOD_ID, dist = Dist.CLIENT)` class; renderers registered via `EntityRenderersEvent.RegisterRenderers` on the mod event bus.
- **Rendering**: custom `RenderType.create(...)` composite state; emit raw quads with `VertexConsumer` + `PoseStack` matrix. Invariants in `SpotlightRenderer`: `NO_CULL`, `COLOR_WRITE` (no depth write — crossing translucent beams must not depth-cull each other), translucency. The beam is drawn by **this mod's own Veil (Pinwheel) program** (`assets/create_shining_stage/pinwheel/shaders/program/spotlight_beam/`, bound with `VeilRenderBridge.shaderState`) rather than a vanilla one: a shader pack adapts by replacing *vanilla's* shader getters, so a vanilla-drawn beam gets shaded as a lit surface and lit by the pack's own path, while a Veil program is never reached by that substitution and is emitted light under every pack and with no pack installed alike. It also keeps the beam's two scalars — alpha and taper — in float UVs; a vertex colour's alpha is 8 bits, and a beam dimmed by a low redstone signal rounds to nothing inside it. The cross-section is drawn with **constant** alpha on all four sides, never a per-side falloff: a side's edges are the frustum's corners, which project into the middle of the beam off-axis, so any per-side falloff drags a transparent slit down the beam's centre. `COLOR_WRITE` also means the beam lands in no shadow map, so it casts no shadow in either pass.
- **Constants**: tunables are `static final` fields with javadoc at the top of the owning class (e.g. `MIN_RANGE`, `HALF_ANGLE_TAN`) — adjust there, not inline.
- **Ship safety**: any world-space distance involving raycast hits MUST use `Sable.HELPER.distanceSquaredWithSubLevels(...)`, never `Vec3.distanceTo` — hits in Aeronautics sub-levels sit at far-away coordinates and vanilla distance explodes. See comment in `SpotlightRenderer.render`.
- **Contraptions**: redstone is unreadable inside a Create contraption (its block entities are rebuilt in the contraption's own world), so `SpotlightRenderer` pins the signal to `CONTRAPTION_SIGNAL` (4/15) there. Detection is `be.getLevel() instanceof VirtualRenderWorld` — Create's `ClientContraption` is the only thing that instantiates that world. Sable sub-levels are deliberately NOT special-cased: their block entities keep reading real redstone.
- **Mixins**: two client-only mixins in `mixin/` — `EntityCullingMixin` (vanilla `Entity`, widens a contraption's culling box) and `ContraptionMixin` (Create's `Contraption`, captures spotlights at `readNBT` TAIL). They are declared in `src/main/resources/create_shining_stage.mixins.json` (which declares `package com.shiningstage.create_shining_stage.mixin`) and registered via a `[[mixins]]` block in `neoforge.mods.toml`. They MUST stay in that subpackage: a mixin config claims every class under its declared package, so a mixin sitting in the mod's main package makes the other mod classes unloadable. No refmap is generated or needed (NeoForge runs on official mappings); `build.gradle` passes `-proc:none` so sponge-mixin's refmap processor does not fail the build, so **`@Inject` targets are not checked at compile time** — verify target methods against the jar when renaming them. `@Inject` into Create internals should use the full descriptor (e.g. `readNBT(...)V`) so a signature change fails loudly instead of silently matching nothing.
- **Contraption culling**: the spotlight list is captured once, at contraption read time, and stored on the `Contraption` instance (`SpotlightBeams` duck interface, volatile — read on the network thread, queried on the render thread). Positions and facings are safe to cache (a contraption's blocks never change at runtime; only block *states* do). Beam *lengths* are NOT cached: they come from the live client block entity via `getBlockEntityClientSide`, so the scroll value still applies immediately. The injection point must stay at TAIL of `readNBT` — `readBlocksCompound` clears and re-fills `blocks`, so scanning mid-read would see a half-filled structure.
- **Comments**: explain *why* (invariants, upstream-mod behavior) in English, not *what*. Design docs are in Chinese; code is English.
- **Translations**: add every user-facing string to both `en_us.json` and `zh_cn.json`; blocks/items use `block.create_shining_stage.<name>` keys, value-box labels use `block.create_shining_stage.<name>.<label>`.

## Important Files

- `src/main/java/.../CreateShiningStage.java` — mod entry point (`@Mod`), all DeferredRegister wiring
- `.../SpotlightBlock.java` / `SpotlightBlockEntity.java` / `SpotlightRenderer.java` — the full content implementation
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
- Practical verification = `.\gradlew.bat build` plus `.\gradlew.bat runClient`: place a Spotlight, check beam rendering, dye recoloring, scroll-wheel range adjustment, redstone-strength behavior, and contraption culling (assemble the spotlight into a contraption, look away until the hull leaves the frustum — the beam must stay visible).
- Datagen (`runData`) is wired but has no providers yet; if you add providers, output lands in `src/generated/resources/` (already on the resource path).
