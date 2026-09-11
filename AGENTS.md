# Repository Guidelines

## Project Overview

**Create Shining Stage** (`create_shining_stage`) is a NeoForge 1.21.1 addon for the Create mod. It adds a single content piece: a **Spotlight** block that emits a colored, redstone-controlled light beam (frustum) — dyeable, with scroll-adjustable length (2–32 blocks), designed to work correctly on Create: Aeronautics moving ships via Sable's sub-level-aware raycasting.

- Mod ID: `create_shining_stage`, group: `com.shiningstage`, version `1.0.0`
- Required runtime deps (declared in `neoforge.mods.toml`): `create` [6.0.10,6.1.0), `aeronautics_bundled` [1.3.0,1.4.0), `sable` [2.0.5,)
- License: All Rights Reserved

## Architecture & Data Flow

Small flat package — all classes live directly in `com.shiningstage.create_shining_stage` (no subpackages):

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
  └─ per frame: redstone level.getBestNeighborSignal(pos)/15 → beam spread + alpha (0 signal = no render)
     → vanilla clip() raycast → Sable.HELPER.distanceSquaredWithSubLevels (ship-aware true distance)
     → draws translucent QUAD frustum via custom RenderType (no cull, no depth write, color-write only)
```

Key pattern: **game logic in the block/BE, all visuals in the client renderer**. There is no custom networking — `ScrollValueBehaviour` handles range sync, and `notifyUpdate()` syncs color via the standard BE client packet.

## Key Directories

| Path | Purpose |
|---|---|
| `src/main/java/com/shiningstage/create_shining_stage/` | All mod code (10 classes, flat package) |
| `src/main/resources/` | assets (lang `en_us`/`zh_cn`, blockstates, item model), data (block loot table) |
| `src/main/templates/META-INF/neoforge.mods.toml` | Mod metadata template, `${...}` expanded at build time |
| `src/generated/resources/` | Datagen output; wired as an extra resource sourceSet (currently empty — no datagen providers exist yet) |
| `libs/` | Local jar deps: `sable-companion-common-1.21.1-1.6.0.jar` (compileOnly; `SableCompanion` interface lives in Sable's jarjar) |
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
- **Rendering**: custom `RenderType.create(...)` composite state; emit raw quads with `VertexConsumer` + `PoseStack` matrix. Invariants in `SpotlightRenderer`: `NO_CULL`, `COLOR_WRITE` (no depth write — crossing translucent beams must not depth-cull each other), translucency.
- **Constants**: tunables are `static final` fields with javadoc at the top of the owning class (e.g. `MIN_RANGE`, `HALF_ANGLE_TAN`) — adjust there, not inline.
- **Ship safety**: any world-space distance involving raycast hits MUST use `Sable.HELPER.distanceSquaredWithSubLevels(...)`, never `Vec3.distanceTo` — hits in Aeronautics sub-levels sit at far-away coordinates and vanilla distance explodes. See comment in `SpotlightRenderer.render`.
- **Comments**: explain *why* (invariants, upstream-mod behavior) in English, not *what*. Design docs are in Chinese; code is English.
- **Translations**: add every user-facing string to both `en_us.json` and `zh_cn.json`; blocks/items use `block.create_shining_stage.<name>` keys, value-box labels use `block.create_shining_stage.<name>.<label>`.

## Important Files

- `src/main/java/.../CreateShiningStage.java` — mod entry point (`@Mod`), all DeferredRegister wiring
- `.../SpotlightBlock.java` / `SpotlightBlockEntity.java` / `SpotlightRenderer.java` — the full content implementation
- `build.gradle` — ModDev Gradle config, Create/Ponder/Flywheel/Registrate/Aeronautics/Sable deps, run configs
- `gradle.properties` — **all versions live here** (`minecraft_version`, `neo_version`, `create_version`, `parchment_*`, `mod_version`, …); bump versions here, never hardcode
- `settings.gradle` — repository list (see Runtime/Tooling for the mirror setup)
- `docs/superpowers/specs/2026-09-10-create-shining-stage-design.md` — workspace design rationale and version table

## Runtime/Tooling Preferences

- **JDK 21** (toolchain, auto-resolved via foojay), **Gradle 9.2.1** wrapper, **ModDev Gradle** (`net.neoforged.moddev` 2.0.146), Parchment mappings `2024.11.17` on official Mojang mappings.
- **China network environment**: wrapper distribution and Maven repos use mirrors (Tencent, Aliyun, BMCLAPI) — do not remove them. `gradle.properties` has commented-out proxy settings (`systemProp.http.proxyHost/Port`) for when `maven.neoforged.net` is unreachable.
- Sable's `SableCompanion` interface must compile against `libs/sable-companion-common-1.21.1-1.6.0.jar` (`compileOnly files(...)`); the outer Modrinth jar alone is insufficient.
- Configuration cache, parallel execution, and build cache are enabled — keep tasks compatible.

## Testing & QA

- **No tests exist** (no `src/test`, no game tests, no CI workflows). `runGameTestServer` is configured with the mod namespace enabled if game tests are added later (`@GameTest` in namespace `create_shining_stage`).
- Practical verification = `.\gradlew.bat build` plus `.\gradlew.bat runClient`: place a Spotlight, check beam rendering, dye recoloring, scroll-wheel range adjustment, and redstone-strength behavior.
- Datagen (`runData`) is wired but has no providers yet; if you add providers, output lands in `src/generated/resources/` (already on the resource path).
