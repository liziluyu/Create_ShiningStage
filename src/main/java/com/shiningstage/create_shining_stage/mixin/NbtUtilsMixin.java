package com.shiningstage.create_shining_stage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.shiningstage.create_shining_stage.LegacyBlockMigration;

import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;

/** Migrates legacy pole block state tags in structures and saved contraptions before decoding. */
@Mixin(NbtUtils.class)
public abstract class NbtUtilsMixin {
    @Inject(
        method = "readBlockState(Lnet/minecraft/core/HolderGetter;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("HEAD"))
    private static void createShiningStage$migrateLegacyPoleState(
        HolderGetter<?> registries, CompoundTag tag, CallbackInfoReturnable<BlockState> cir) {
        LegacyBlockMigration.migrateState(tag);
    }
}
