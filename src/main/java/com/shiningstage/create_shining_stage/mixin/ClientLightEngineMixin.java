package com.shiningstage.create_shining_stage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.shiningstage.create_shining_stage.ClientStageLighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LightEngine;

/** Merge only block light. Sky light and the server-supplied light storage are untouched. */
@Mixin(LightEngine.class)
public abstract class ClientLightEngineMixin {
    @Accessor("chunkSource")
    protected abstract LightChunkGetter createShiningStage$getChunkSource();

    @ModifyReturnValue(method = "getLightValue(Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"))
    private int createShiningStage$brightness(int original, BlockPos pos) {
        return (Object) this instanceof BlockLightEngine
            ? ClientStageLighting.brightness(createShiningStage$getChunkSource(), pos, original) : original;
    }

    @ModifyReturnValue(method = "getDataLayerData(Lnet/minecraft/core/SectionPos;)Lnet/minecraft/world/level/chunk/DataLayer;", at = @At("RETURN"))
    private DataLayer createShiningStage$layer(DataLayer original, SectionPos pos) {
        return (Object) this instanceof BlockLightEngine
            ? ClientStageLighting.layer(createShiningStage$getChunkSource(), pos, original) : original;
    }

    @Inject(method = "checkBlock(Lnet/minecraft/core/BlockPos;)V", at = @At("TAIL"))
    private void createShiningStage$geometryChanged(BlockPos pos, CallbackInfo ci) {
        if ((Object) this instanceof BlockLightEngine) ClientStageLighting.checkBlock(createShiningStage$getChunkSource(), pos);
    }

    @Inject(method = "setLightEnabled(Lnet/minecraft/world/level/ChunkPos;Z)V", at = @At("TAIL"))
    private void createShiningStage$chunkUnloaded(ChunkPos pos, boolean enabled, CallbackInfo ci) {
        if (!enabled && (Object) this instanceof BlockLightEngine) ClientStageLighting.lightDisabled(createShiningStage$getChunkSource(), pos);
    }
}
