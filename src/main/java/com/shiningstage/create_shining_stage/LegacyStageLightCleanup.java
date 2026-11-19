package com.shiningstage.create_shining_stage;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** One-way migration for recorded physical lights and their saved propagation footprint. */
public final class LegacyStageLightCleanup extends SavedData {
    private static final String DATA_NAME = CreateShiningStage.MOD_ID + "_legacy_lights";
    private static final Factory<LegacyStageLightCleanup> FACTORY =
        new Factory<>(LegacyStageLightCleanup::new, LegacyStageLightCleanup::load);
    private static final BlockState LEGACY_LIGHT =
        Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
    private final LegacyLightCleanupQueue queue = new LegacyLightCleanupQueue();

    private LegacyStageLightCleanup() {}

    private static LegacyStageLightCleanup load(CompoundTag tag, HolderLookup.Provider registries) {
        LegacyStageLightCleanup data = new LegacyStageLightCleanup();
        for (long packed : tag.getLongArray("Positions")) {
            data.queue.restoreRemoval(cell(BlockPos.of(packed)));
        }
        ListTag sections = tag.getList("RelightSections", Tag.TAG_COMPOUND);
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag entry = sections.getCompound(i);
            SectionPos section = SectionPos.of(entry.getLong("Section"));
            for (long origin : entry.getLongArray("Origins")) {
                data.queue.restoreRelight(new LegacyLightCleanupQueue.Section(section.x(), section.y(), section.z()),
                    cell(BlockPos.of(origin)));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("Positions", queue.removalPositions().stream().mapToLong(cell -> pos(cell).asLong()).toArray());
        ListTag sections = new ListTag();
        queue.relightJobs().forEach((section, origins) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Section", SectionPos.asLong(section.x(), section.y(), section.z()));
            entry.putLongArray("Origins", origins.stream().mapToLong(cell -> pos(cell).asLong()).toArray());
            sections.add(entry);
        });
        tag.put("RelightSections", sections);
        return tag;
    }

    static void accept(ServerLevel level, Set<BlockPos> positions) {
        if (positions.isEmpty()) return;
        LegacyStageLightCleanup data = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        LegacyLightCleanupQueue.Access access = access(level);
        for (BlockPos pos : positions) if (data.queue.add(cell(pos), access)) data.setDirty();
    }

    static void recheckLegacyEmission(ServerLevel level, BlockPos pos) {
        LegacyStageLightCleanup data = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        if (data.queue.addRelight(cell(pos), access(level))) data.setDirty();
    }

    /** A false result retains the record. Never infer ownership from the light's level alone. */
    private static boolean removeLoadedLight(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        BlockState state = chunk.getBlockState(pos);
        return !state.equals(LEGACY_LIGHT) || level.removeBlock(pos, false);
    }

    private static LegacyLightCleanupQueue.Access access(ServerLevel level) {
        return new LegacyLightCleanupQueue.Access() {
            @Override public boolean loaded(long chunk) {
                return level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)) != null;
            }
            @Override public boolean removeRecorded(LegacyLightCleanupQueue.Cell cell) {
                return removeLoadedLight(level, pos(cell));
            }
            @Override public void recheck(LegacyLightCleanupQueue.Cell cell) {
                BlockPos pos = pos(cell);
                if (!level.isOutsideBuildHeight(pos)) SableCompat.checkBlockLight(level, pos);
            }
        };
    }

    private static LegacyLightCleanupQueue.Cell cell(BlockPos pos) {
        return new LegacyLightCleanupQueue.Cell(pos.getX(), pos.getY(), pos.getZ());
    }
    private static BlockPos pos(LegacyLightCleanupQueue.Cell cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LegacyStageLightCleanup data = level.getDataStorage().get(FACTORY, DATA_NAME);
            if (data != null) data.queue.chunkLoaded(event.getChunk().getPos().toLong());
        }
    }
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LegacyStageLightCleanup data = level.getDataStorage().get(FACTORY, DATA_NAME);
            if (data != null) data.queue.chunkUnloaded(event.getChunk().getPos().toLong());
        }
    }
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        LegacyStageLightCleanup data = level.getDataStorage().get(FACTORY, DATA_NAME);
        if (data != null && !data.queue.isEmpty() && data.queue.process(access(level), 4)) data.setDirty();
    }
}
