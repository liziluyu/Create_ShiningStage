package com.shiningstage.create_shining_stage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.shiningstage.create_shining_stage.ClientStageLighting;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;

@Mixin(BlockLightEngine.class)
public abstract class ClientBlockLightEngineMixin {
    @ModifyReturnValue(method = "getEmission(JLnet/minecraft/world/level/block/state/BlockState;)I", at = @At("RETURN"))
    private int createShiningStage$virtualEmission(int original, long pos, BlockState state) {
        return ClientStageLighting.emission((BlockLightEngine) (Object) this, pos, original);
    }
}