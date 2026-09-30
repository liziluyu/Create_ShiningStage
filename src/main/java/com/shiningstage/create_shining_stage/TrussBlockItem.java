package com.shiningstage.create_shining_stage;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;

/**
 * The truss item: a plain block item that is also a tool, because the same truss covers a Create
 * shaft (see {@link TrussBlock}) and an AE2 cable (see {@link Ae2TrussFacade}).
 *
 * <p>This has to be an item subclass rather than a hook on the block, for one reason: the cover is
 * claimed through {@link Item#onItemUseFirst}, which runs <em>before</em> the clicked block's own use.
 * That ordering is not a preference - a cable bus uses the click itself to place parts and to open its
 * own UI, so a hook that ran after the block would never see the click at all. It is the same hook
 * AE2's own facade item uses.
 */
public class TrussBlockItem extends BlockItem {
    private static final String TOOLTIP_AE2 = "block.create_shining_stage.truss.tooltip.ae2";

    /**
     * Resolved once, when the item is registered, and deliberately kept as a primitive rather than a
     * method call at each use: {@link Ae2TrussFacade} names AE2 types in its constant pool, so merely
     * loading that class with AE2 absent would throw. The guard therefore has to be answerable without
     * touching it, and AE2 stays optional with nothing in {@code neoforge.mods.toml} declaring it.
     */
    private static final boolean AE2_PRESENT = ModList.get().isLoaded("ae2");

    public TrussBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (AE2_PRESENT) {
            InteractionResult covered = Ae2TrussFacade.tryPlace(stack, context);
            if (covered != InteractionResult.PASS) {
                return covered;
            }
        }
        return super.onItemUseFirst(stack, context);
    }

    /**
     * The block's own lines arrive through {@code super} (BlockItem hands hover text to the block),
     * so this only appends the cable line - and only where AE2 is actually installed, since it
     * describes a cable that the player could otherwise not have.
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (AE2_PRESENT) {
            tooltip.add(Component.translatable(TOOLTIP_AE2));
        }
    }
}
