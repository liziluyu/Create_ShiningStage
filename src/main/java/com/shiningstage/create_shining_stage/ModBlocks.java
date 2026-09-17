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
            .sound(SoundType.METAL)
            .noOcclusion()));

    public static final Supplier<BlockItem> SPOTLIGHT_ITEM =
        ITEMS.register("spotlight", () -> new BlockItem(SPOTLIGHT.get(), new Item.Properties()));

    public static final Supplier<TripodBlock> TRIPOD =
        BLOCKS.register("tripod", () -> new TripodBlock(BlockBehaviour.Properties.of()
            .strength(3.5f)
            .sound(SoundType.METAL)
            .noOcclusion()));

    public static final Supplier<BlockItem> TRIPOD_ITEM =
        ITEMS.register("tripod", () -> new BlockItem(TRIPOD.get(), new Item.Properties()));

    public static final Supplier<MicrophoneBlock> MICROPHONE =
        BLOCKS.register("microphone", () -> new MicrophoneBlock(BlockBehaviour.Properties.of()
            .strength(3.5f)
            .sound(SoundType.METAL)
            // The tripod-mount model is not a full cube; occluding would cut see-through gaps in neighbors.
            .noOcclusion()));

    public static final Supplier<BlockItem> MICROPHONE_ITEM =
        ITEMS.register("microphone", () -> new BlockItem(MICROPHONE.get(), new Item.Properties()));

    public static final Supplier<SpeakerBlock> SPEAKER =
        BLOCKS.register("speaker", () -> new SpeakerBlock(BlockBehaviour.Properties.of()
            .strength(3.5f)
            .sound(SoundType.WOOD)
            .noOcclusion()));

    public static final Supplier<BlockItem> SPEAKER_ITEM =
        ITEMS.register("speaker", () -> new BlockItem(SPEAKER.get(), new Item.Properties()));

    public static final Supplier<TrussBlock> TRUSS =
        BLOCKS.register("truss", () -> new TrussBlock(BlockBehaviour.Properties.of()
            .strength(3.5f)
            .sound(SoundType.METAL)
            .noOcclusion()));

    public static final Supplier<BlockItem> TRUSS_ITEM =
        ITEMS.register("truss", () -> new BlockItem(TRUSS.get(), new Item.Properties()));
}
