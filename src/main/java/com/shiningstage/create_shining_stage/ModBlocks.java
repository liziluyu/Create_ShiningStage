package com.shiningstage.create_shining_stage;

import java.util.function.Supplier;

import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;

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
        ITEMS.register("truss", () -> new TrussBlockItem(TRUSS.get(), new Item.Properties()));

    // The truss as a casing: Create's own encased-shaft / encased-cogwheel blocks, told that the
    // casing they wear is this mod's truss. Nothing is subclassed - those blocks already do the
    // whole job through EncasedBlock#getCasing() (the casing item recognized by right-click, the
    // pick-block stack, the sneak-wrench unwrap back into the bare part), so all this mod supplies
    // is the block and its texture. See TrussBlock for the two registries that make the pairing
    // real: Create's EncasingRegistry (so right-clicking a shaft/cogwheel with a truss encases it)
    // and NeoForge's valid-block event for the block entity types these blocks share with Create's.
    private static BlockBehaviour.Properties trussCasingProperties() {
        return BlockBehaviour.Properties.of()
            .strength(3.5f)
            .sound(SoundType.METAL)
            .noOcclusion();
    }

    public static final Supplier<EncasedShaftBlock> TRUSS_ENCASED_SHAFT =
        BLOCKS.register("truss_encased_shaft", () -> new EncasedShaftBlock(trussCasingProperties(), TRUSS::get));

    public static final Supplier<BlockItem> TRUSS_ENCASED_SHAFT_ITEM =
        ITEMS.register("truss_encased_shaft", () -> new BlockItem(TRUSS_ENCASED_SHAFT.get(), new Item.Properties()));

    public static final Supplier<EncasedCogwheelBlock> TRUSS_ENCASED_COGWHEEL =
        BLOCKS.register("truss_encased_cogwheel",
            () -> new EncasedCogwheelBlock(trussCasingProperties(), false, TRUSS::get));

    public static final Supplier<BlockItem> TRUSS_ENCASED_COGWHEEL_ITEM =
        ITEMS.register("truss_encased_cogwheel", () -> new BlockItem(TRUSS_ENCASED_COGWHEEL.get(), new Item.Properties()));

    public static final Supplier<EncasedCogwheelBlock> TRUSS_ENCASED_LARGE_COGWHEEL =
        BLOCKS.register("truss_encased_large_cogwheel",
            () -> new EncasedCogwheelBlock(trussCasingProperties(), true, TRUSS::get));

    public static final Supplier<BlockItem> TRUSS_ENCASED_LARGE_COGWHEEL_ITEM =
        ITEMS.register("truss_encased_large_cogwheel",
            () -> new BlockItem(TRUSS_ENCASED_LARGE_COGWHEEL.get(), new Item.Properties()));

    public static final Supplier<ColdSparkMachineBlock> COLD_SPARK_MACHINE =
        BLOCKS.register("cold_spark_machine", () -> new ColdSparkMachineBlock(BlockBehaviour.Properties.of()
            .strength(3.5f)
            .sound(SoundType.METAL)));

    public static final Supplier<BlockItem> COLD_SPARK_MACHINE_ITEM =
        ITEMS.register("cold_spark_machine", () -> new BlockItem(COLD_SPARK_MACHINE.get(), new Item.Properties()));

    public static final Supplier<PositionZeroBlock> POSITION_ZERO =
        BLOCKS.register("position_zero", () -> new PositionZeroBlock(BlockBehaviour.Properties.of()
            .strength(3.5f)
            .sound(SoundType.METAL)
            // vanilla's spelling. Also clears canOcclude, so noOcclusion() on top is redundant.
            .noCollission()));

    public static final Supplier<BlockItem> POSITION_ZERO_ITEM =
        ITEMS.register("position_zero", () -> new BlockItem(POSITION_ZERO.get(), new Item.Properties()));
}
