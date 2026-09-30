package com.shiningstage.create_shining_stage;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

/** Decorative stage frame: no block entity, no behaviours. Its tooltip is the only description. */
public class TrussBlock extends Block {
    public static final MapCodec<TrussBlock> CODEC = simpleCodec(TrussBlock::new);

    private static final String TOOLTIP_1 = "block.create_shining_stage.truss.tooltip.1";
    private static final String TOOLTIP_2 = "block.create_shining_stage.truss.tooltip.2";
    private static final String TOOLTIP_3 = "block.create_shining_stage.truss.tooltip.3";

    public TrussBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    // Called by BlockItem.appendHoverText, so the lines show on the item form only.
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable(TOOLTIP_1));
        tooltip.add(Component.translatable(TOOLTIP_2));
        tooltip.add(Component.translatable(TOOLTIP_3));
    }

    // --- The truss as a casing for Create's shafts and cogwheels ---------------------------------
    //
    // Two registries have to learn about the pairing, and they belong to different owners, so they
    // are wired in two different events. The blocks themselves are plain instances of Create's
    // EncasedShaftBlock / EncasedCogwheelBlock (see ModBlocks): they read their casing from the
    // EncasedBlock#getCasing() they were constructed with, so the only thing that can be missing is
    // Create's knowledge of which held item encases which block, and NeoForge's knowledge of which
    // blocks live in which block entity type.

    /**
     * Create resolves "right-click a shaft with item X" through {@link EncasingRegistry}, a plain
     * static map that must not be touched before block registration has finished - and this is the
     * first hook after it. Missing an entry here is silent: the truss simply never encases anything.
     */
    public static void registerEncasing(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            EncasingRegistry.addVariant(AllBlocks.SHAFT.get(), ModBlocks.TRUSS_ENCASED_SHAFT.get());
            EncasingRegistry.addVariant(AllBlocks.COGWHEEL.get(), ModBlocks.TRUSS_ENCASED_COGWHEEL.get());
            EncasingRegistry.addVariant(AllBlocks.LARGE_COGWHEEL.get(),
                ModBlocks.TRUSS_ENCASED_LARGE_COGWHEEL.get());
        });
    }

    /**
     * The encased variants reuse Create's block entity types, which is what makes their kinetic
     * behaviour and rendering Create's own with no code here at all. A block entity type only knows
     * the blocks it was registered with, though, and that set is load-bearing rather than cosmetic:
     * {@code BlockEntity#isValidBlockState} is asked on every chunk load, and a block missing from it
     * has its block entity thrown away and rebuilt from scratch. NeoForge's event is the supported
     * way to extend the set, and it checks that the block shares a superclass with the existing ones.
     */
    public static void addValidBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(AllBlockEntityTypes.ENCASED_SHAFT.get(), ModBlocks.TRUSS_ENCASED_SHAFT.get());
        event.modify(AllBlockEntityTypes.ENCASED_COGWHEEL.get(), ModBlocks.TRUSS_ENCASED_COGWHEEL.get());
        event.modify(AllBlockEntityTypes.ENCASED_LARGE_COGWHEEL.get(), ModBlocks.TRUSS_ENCASED_LARGE_COGWHEEL.get());
    }
}
