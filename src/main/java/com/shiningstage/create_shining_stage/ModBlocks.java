package com.shiningstage.create_shining_stage;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, CreateShiningStage.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, CreateShiningStage.MOD_ID);

    public static final Supplier<SpotlightBlock> SPOTLIGHT =
        BLOCKS.register("spotlight", () -> new SpotlightBlock(BlockBehaviour.Properties.of()
            .strength(3.5f)
            .sound(SoundType.METAL)));

    public static final Supplier<BlockItem> SPOTLIGHT_ITEM =
        ITEMS.register("spotlight", () -> new BlockItem(SPOTLIGHT.get(), new Item.Properties()));

    public static final Supplier<MicrophoneBlock> MICROPHONE =
        BLOCKS.register("microphone", () -> new MicrophoneBlock(BlockBehaviour.Properties.of()
            .strength(3.5f)
            .sound(SoundType.METAL)));

    public static final Supplier<BlockItem> MICROPHONE_ITEM =
        ITEMS.register("microphone", () -> new BlockItem(MICROPHONE.get(), new Item.Properties()));
}
