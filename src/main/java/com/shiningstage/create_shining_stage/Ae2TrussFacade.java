package com.shiningstage.create_shining_stage;

import appeng.api.implementations.items.IFacadeItem;
import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import appeng.facade.FacadePart;
import appeng.items.parts.FacadeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/** Optional AE2 bridge. Keep every AE2 reference in this class and only load it when AE2 is present. */
final class Ae2TrussFacade {
    private static final String SIDE_TAG = "TrussFacadeSide";

    private Ae2TrussFacade() {
    }

    /** Factory called only from the AE2-present item-registration branch. */
    static Item createFacadeItem(Item.Properties properties) {
        return new TrussFacadeItem(properties);
    }

    static InteractionResult tryPlace(ItemStack trussStack, UseOnContext context, Item facadeItem) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        IPartHost host = PartHelper.getPartHost(level, pos);
        if (host == null) {
            return InteractionResult.PASS;
        }

        ItemStack facadeStack = new ItemStack(facadeItem);
        CompoundTag tag = facadeStack.getOrCreateTag();
        tag.putString(SIDE_TAG, context.getClickedFace().getName());
        IFacadePart facade = new FacadePart(facadeStack, context.getClickedFace());
        if (!FacadeItem.canPlaceFacade(host, facade)
            || !host.getFacadeContainer().addFacade(facade)) {
            return InteractionResult.PASS;
        }

        BlockState truss = ((IFacadeItem) facadeItem).getTextureBlockState(facadeStack);
        SoundType sound = truss.getSoundType(level, pos, context.getPlayer());
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
            (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        host.markForSave();
        host.markForUpdate();

        Player player = context.getPlayer();
        if (!level.isClientSide && player != null && !player.isCreative()) {
            trussStack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * AE2 persists the stack returned by FacadePart, then reconstructs a FacadePart from that stack.
     * Keeping the covered face on this hidden item's NBT makes the panel-state variant survive saves,
     * sync packets, and facade drops without adding AE2 types to the common block item.
     */
    private static final class TrussFacadeItem extends Item implements IFacadeItem {
        private TrussFacadeItem(Properties properties) {
            super(properties);
        }

        @Override
        public IFacadePart createPartFromItemStack(ItemStack stack, Direction side) {
            ItemStack copy = stack.copy();
            copy.getOrCreateTag().putString(SIDE_TAG, side.getName());
            return new FacadePart(copy, side);
        }

        @Override
        public String getDescriptionId() {
            return ModBlocks.TRUSS.get().getDescriptionId();
        }

        @Override
        public ItemStack getTextureItem(ItemStack stack) {
            return new ItemStack(ModBlocks.TRUSS.get());
        }

        @Override
        public BlockState getTextureBlockState(ItemStack stack) {
            Direction side = Direction.byName(stack.getOrCreateTag().getString(SIDE_TAG));
            if (side == null) {
                return ModBlocks.TRUSS.get().defaultBlockState();
            }
            return TrussBlock.facadeSource(side);
        }
    }
}
