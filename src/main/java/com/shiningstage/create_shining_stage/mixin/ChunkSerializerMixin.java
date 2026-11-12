package com.shiningstage.create_shining_stage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.shiningstage.create_shining_stage.LegacyBlockMigration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/** Migrates legacy block palette entries before ChunkSerializer decodes them to registered states. */
@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {
    private static final String MIGRATED_TAG = "create_shining_stage$legacyPoleMigrated";

    @Inject(
        method = "read(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/ai/village/poi/PoiManager;Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/chunk/ProtoChunk;",
        at = @At("HEAD"))
    private static void createShiningStage$migrateLegacyPoleStates(
        ServerLevel level, PoiManager poiManager, RegionStorageInfo storageInfo, ChunkPos chunkPos,
        CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
        if (LegacyBlockMigration.migrate(tag)) tag.putBoolean(MIGRATED_TAG, true);
    }

    @Inject(
        method = "read(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/ai/village/poi/PoiManager;Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/chunk/ProtoChunk;",
        at = @At("RETURN"))
    private static void createShiningStage$markMigratedChunkDirty(
        ServerLevel level, PoiManager poiManager, RegionStorageInfo storageInfo, ChunkPos chunkPos,
        CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
        if (tag.getBoolean(MIGRATED_TAG)) {
            tag.remove(MIGRATED_TAG);
            cir.getReturnValue().setUnsaved(true);
        }
    }
}
