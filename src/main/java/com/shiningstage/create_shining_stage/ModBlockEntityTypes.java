package com.shiningstage.create_shining_stage;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateShiningStage.MOD_ID);

    public static final Supplier<BlockEntityType<SpotlightBlockEntity>> SPOTLIGHT =
        BLOCK_ENTITIES.register("spotlight",
            () -> BlockEntityType.Builder.of(SpotlightBlockEntity::new, ModBlocks.SPOTLIGHT.get()).build(null));
}
