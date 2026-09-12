package com.shiningstage.create_shining_stage;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** Decorative stage frame: no block entity, no behaviours. Its tooltip is the only description. */
public class TrussBlock extends Block {
    public static final MapCodec<TrussBlock> CODEC = simpleCodec(TrussBlock::new);

    private static final String TOOLTIP_1 = "block.create_shining_stage.truss.tooltip.1";
    private static final String TOOLTIP_2 = "block.create_shining_stage.truss.tooltip.2";

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
    }
}
