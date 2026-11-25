package com.shiningstage.create_shining_stage;

import java.util.function.Supplier;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Block item for the truss, with the optional AE2 cable-cover interaction kept isolated. */
public class TrussBlockItem extends BlockItem {
    private static final boolean AE2_PRESENT = ModList.get().isLoaded("ae2");

    @Nullable
    private final Supplier<? extends Item> facadeItem;

    public TrussBlockItem(Block block, Item.Properties properties) {
        this(block, properties, null);
    }

    /**
     * @param facadeItem supplier for the AE2-only, hidden IFacadeItem registered by the mod item
     *                   registry; keep null when AE2 is absent
     */
    public TrussBlockItem(Block block, Item.Properties properties,
                          @Nullable Supplier<? extends Item> facadeItem) {
        super(block, properties);
        this.facadeItem = facadeItem;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (AE2_PRESENT && facadeItem != null) {
            InteractionResult covered = Ae2TrussFacade.tryPlace(stack, context, facadeItem.get());
            if (covered != InteractionResult.PASS) {
                return covered;
            }
        }
        return super.onItemUseFirst(stack, context);
    }

}
