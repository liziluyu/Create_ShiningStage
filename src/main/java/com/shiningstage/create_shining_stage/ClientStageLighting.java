package com.shiningstage.create_shining_stage;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A second, client-only vanilla block-light engine. Server light packets only ever reach the normal
 * engine; render reads merge the two layers. No fake blocks, world writes, shader-specific lighting,
 * or server gameplay light are involved. All mutation happens on the client thread.
 */
public final class ClientStageLighting {
    private static volatile @Nullable ClientStageLighting active;

    private final ClientLevel level;
    private final Set<SpotlightBlockEntity> fixtures = Collections.newSetFromMap(new IdentityHashMap<>());
    private final LightChunks chunks = new LightChunks();
    private final Set<Long> sources = new HashSet<>();
    private final Set<Long> sourceSections = new HashSet<>();
    private final Set<Long> affectedSections = new HashSet<>();
    // Sodium also reads whole DataLayers from worker threads. Cache by both immutable layer identities,
    // never modify the server's layer, and publish a complete merged layer in one step.
    private final StageLightLayerMerger mergedLayers = new StageLightLayerMerger();
    private volatile BlockLightEngine engine;
    private boolean rebuild;
    private int poll;

    private ClientStageLighting(ClientLevel level) {
        this.level = level;
        engine = new BlockLightEngine(chunks);
    }

    private static @Nullable ClientStageLighting current() {
        ClientLevel level = Minecraft.getInstance().level;
        ClientStageLighting lighting = active;
        if (level == null) {
            active = null;
            return null;
        }
        if (lighting == null || lighting.level != level) {
            lighting = new ClientStageLighting(level);
            active = lighting;
        }
        return lighting;
    }

    /** Called by a client-only mixin, so the common block entity never links client classes. */
    public static void register(SpotlightBlockEntity fixture) {
        // Client mixins also exist on an integrated server; never touch Minecraft from its thread.
        if (fixture.getLevel() == null || !fixture.getLevel().isClientSide) return;
        ClientStageLighting lighting = current();
        if (lighting != null && fixture.getLevel() == lighting.level) {
            lighting.fixtures.add(fixture);
        }
    }

    public static void tick() {
        ClientStageLighting lighting = current();
        if (lighting == null) return;
        if (--lighting.poll <= 0 || lighting.rebuild) {
            lighting.poll = SpotlightBlockEntity.LIGHT_POLL_INTERVAL;
            lighting.updateSources();
        }
        if (lighting.engine.hasLightWork()) lighting.engine.runLightUpdates();
    }

    public static void unload(net.minecraft.world.level.Level level) {
        ClientStageLighting lighting = active;
        if (lighting != null && lighting.level == level) active = null;
    }

    /** Coalesced topology rebuild: loading geometry can open a path which was previously bedrock. */
    public static void chunkChanged(net.minecraft.world.level.Level level, ChunkPos chunk) {
        ClientStageLighting lighting = active;
        if (lighting == null || lighting.level != level) return;
        for (long section : lighting.sourceSections) {
            if (Math.abs(SectionPos.x(section) - chunk.x) <= 1
                && Math.abs(SectionPos.z(section) - chunk.z) <= 1) {
                lighting.rebuild = true;
                break;
            }
        }
    }

    private boolean loaded(BlockPos pos) {
        return !level.isOutsideBuildHeight(pos)
            && loadedChunk(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private @Nullable LevelChunk loadedChunk(int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunk(x, z, false);
        // Sable returns its shared EmptyLevelChunk for absent plot holders, even with load=false.
        return chunk != null && chunk.getPos().x == x && chunk.getPos().z == z ? chunk : null;
    }

    private boolean pathLoaded(Vec3 start, Vec3 end) {
        return BlockGetter.traverseBlocks(start, end, this,
            (lighting, pos) -> lighting.loaded(pos) ? null : Boolean.FALSE,
            lighting -> Boolean.TRUE);
    }

    private void updateSources() {
        Set<Long> next = new HashSet<>();
        fixtures.removeIf(be -> be.isRemoved() || !loaded(be.getBlockPos())
            || level.getBlockEntity(be.getBlockPos()) != be);
        for (SpotlightBlockEntity fixture : fixtures) {
            BlockPos pos = fixture.getBlockPos();
            BlockState state = fixture.getBlockState();
            boolean fill = fixture instanceof FillLightBlockEntity;
            boolean powered = fill ? state.getValue(FillLightBlock.POWERED) : level.hasNeighborSignal(pos);
            if (!powered) continue;
            // The fill light's own housing used to emit block light as well as its beam endpoint.
            if (fill) next.add(pos.asLong());
            Vec3 normal = fixture.beamDirection();
            Vec3 center = Vec3.atCenterOf(pos);
            addEndpoint(next, pos, center, normal, fixture.getRange(), true);
        }
        // Contraption block entities live in VirtualRenderWorld and do not tick in the main level.
        // Use the same captured spotlight list as culling, independent of whether a renderer ran.
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof AbstractContraptionEntity contraptionEntity) || entity.isRemoved()) continue;
            Contraption contraption = contraptionEntity.getContraption();
            if (contraption == null) continue;
            SpotlightBeams beams = (SpotlightBeams) contraption;
            for (SpotlightBeams.Beam beam : beams.createShiningStage$spotlightBeams()) {
                var be = contraption.getBlockEntityClientSide(beam.localPos());
                int range = be instanceof SpotlightBlockEntity spotlight ? spotlight.getRange() : beam.storedRange();
                addContraptionEndpoint(next, contraptionEntity, beam, range, false);
            }
            for (SpotlightBeams.Beam beam : beams.createShiningStage$fillLightBeams()) {
                var info = contraption.getBlocks().get(beam.localPos());
                if (info != null && info.state().getValue(FillLightBlock.POWERED)) {
                    addContraptionEndpoint(next, contraptionEntity, beam, StageLightingSettings.clientFillLightRange(), true);
                }
            }
        }

        if (!rebuild && next.equals(sources)) return;
        Set<Long> nextSections = new HashSet<>();
        Set<Long> nextColumns = new HashSet<>();
        for (long source : next) {
            nextSections.add(SectionPos.blockToSection(source));
            nextColumns.add(ChunkPos.asLong(BlockPos.getX(source) >> 4, BlockPos.getZ(source) >> 4));
        }
        if (rebuild) {
            // Discarding the overlay cannot leave stale light behind: dirty the old footprint first.
            for (long section : affectedSections) dirty(SectionPos.of(section));
            affectedSections.clear();
            mergedLayers.clear();
            sources.clear();
            sourceSections.clear();
            engine = new BlockLightEngine(chunks);
            rebuild = false;
        }

        // Allocate source sections even in completely empty air. Vanilla allocates their one-section
        // halo, enough for level-15 propagation, without allocating the rest of the loaded world.
        for (long section : nextSections) {
            if (!sourceSections.contains(section)) {
                engine.updateSectionStatus(SectionPos.of(section), false);
                engine.setLightEnabled(new ChunkPos(SectionPos.x(section), SectionPos.z(section)), true);
            }
        }
        Set<Long> changed = new HashSet<>(sources);
        changed.removeAll(next);
        for (long source : next) if (!sources.contains(source)) changed.add(source);
        sources.clear();
        sources.addAll(next);
        for (long source : changed) engine.checkBlock(BlockPos.of(source));
        // Drain old sources before releasing their storage, including propagation into adjacent cells.
        if (engine.hasLightWork()) engine.runLightUpdates();
        for (long section : sourceSections) {
            if (!nextSections.contains(section)) {
                engine.updateSectionStatus(SectionPos.of(section), true);
                if (!nextColumns.contains(ChunkPos.asLong(SectionPos.x(section), SectionPos.z(section)))) {
                    engine.setLightEnabled(new ChunkPos(SectionPos.x(section), SectionPos.z(section)), false);
                }
            }
        }
        sourceSections.clear();
        sourceSections.addAll(nextSections);
        if (engine.hasLightWork()) engine.runLightUpdates();
        // Do not retain a moving ship's entire travelled path in render caches/bookkeeping.
        Set<Long> footprint = new HashSet<>();
        for (long section : sourceSections) {
            for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                footprint.add(SectionPos.offset(section, x, y, z));
            }
        }
        affectedSections.retainAll(footprint);
        mergedLayers.retain(footprint);
        if (sources.isEmpty()) {
            // Also release enabled-column bookkeeping after the last light leaves.
            engine = new BlockLightEngine(chunks);
            affectedSections.clear();
            mergedLayers.clear();
        }
    }

    private void addContraptionEndpoint(Set<Long> next, AbstractContraptionEntity entity,
                                       SpotlightBeams.Beam beam, int range, boolean fill) {
        Vec3 localCenter = Vec3.atCenterOf(beam.localPos());
        Vec3 center = entity.toGlobalVector(localCenter, 1f, false);
        Vec3 normal = entity.toGlobalVector(localCenter.add(beam.direction()), 1f, false).subtract(center).normalize();
        if (fill && loaded(BlockPos.containing(center))) next.add(BlockPos.containing(center).asLong());
        addEndpoint(next, BlockPos.containing(center), center, normal, range, false);
    }

    private void addEndpoint(Set<Long> next, BlockPos owner, Vec3 center, Vec3 normal, int range,
                             boolean projectFromSubLevel) {
        Vec3 start = center.add(normal.scale(.5));
        Vec3 end = start.add(normal.scale(range));
        Vec3 worldStart = projectFromSubLevel ? SableCompat.toWorldPosition(level, owner, start) : start;
        Vec3 worldEnd = projectFromSubLevel ? SableCompat.toWorldPosition(level, owner, end) : end;
        if (!pathLoaded(worldStart, worldEnd)) return;
        BlockHitResult hit = level.clip(SpotlightBlock.beamClipContext(owner, start, end));
        BlockPos cell;
        if (hit.getType() == HitResult.Type.MISS) {
            Vec3 miss = center.add(normal.scale(range));
            if (projectFromSubLevel) miss = SableCompat.toWorldPosition(level, owner, miss);
            cell = BlockPos.containing(miss);
        } else {
            // Sable returns the hit in the target structure's coordinate system.
            cell = hit.getBlockPos().relative(hit.getDirection());
        }
        if (!cell.equals(owner) && loaded(cell) && level.getBlockState(cell).isAir()) next.add(cell.asLong());
    }

    private boolean matches(LightChunkGetter source) {
        return source != chunks && source.getLevel() == level;
    }

    /** Only our engine replaces real block emission; the world's engine stays authoritative. */
    public static int emission(BlockLightEngine engine, long pos, int original) {
        ClientStageLighting lighting = active;
        return lighting != null && lighting.engine == engine
            ? (lighting.sources.contains(pos) ? SpotlightBlockEntity.LIGHT_LEVEL : 0) : original;
    }

    public static void checkBlock(LightChunkGetter source, BlockPos pos) {
        ClientStageLighting lighting = active;
        if (lighting != null && lighting.matches(source) && !lighting.sources.isEmpty()) lighting.engine.checkBlock(pos);
    }

    public static void lightDisabled(LightChunkGetter source, ChunkPos pos) {
        ClientStageLighting lighting = active;
        if (lighting != null && lighting.matches(source)) chunkChanged(lighting.level, pos);
    }

    public static int brightness(LightChunkGetter source, BlockPos pos, int original) {
        ClientStageLighting lighting = active;
        return lighting != null && lighting.matches(source)
            ? Math.max(original, lighting.engine.getLightValue(pos)) : original;
    }

    public static @Nullable DataLayer layer(LightChunkGetter source, SectionPos section, @Nullable DataLayer original) {
        ClientStageLighting lighting = active;
        if (lighting == null || !lighting.matches(source)) return original;
        return lighting.mergedLayers.merge(section.asLong(), original, lighting.engine.getDataLayerData(section));
    }

    private void dirty(SectionPos section) {
        level.getChunkSource().onLightUpdate(LightLayer.BLOCK, section);
    }

    private final class LightChunks implements LightChunkGetter {
        @Override public @Nullable LightChunk getChunkForLighting(int x, int z) {
            return loadedChunk(x, z);
        }

        @Override public BlockGetter getLevel() { return level; }

        @Override public void onLightUpdate(LightLayer layer, SectionPos section) {
            affectedSections.add(section.asLong());
            mergedLayers.invalidate(section.asLong());
            dirty(section);
        }
    }
}
