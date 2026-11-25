package com.shiningstage.create_shining_stage.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Accessors for Forge's extension of Create's kinetic BE valid-block sets. */
@Mixin(BlockEntityType.class)
public interface BlockEntityTypeAccessor {
    @Accessor("validBlocks")
    Set<Block> create_shining_stage$getValidBlocks();

    @Mutable
    @Accessor("validBlocks")
    void create_shining_stage$setValidBlocks(Set<Block> blocks);
}
