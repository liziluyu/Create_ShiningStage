package com.shiningstage.create_shining_stage;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/** One server-owned display stack for the position-zero marking. */
public class PositionZeroBlockEntity extends SmartBlockEntity {
    private ItemStack stored = ItemStack.EMPTY;

    public PositionZeroBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.POSITION_ZERO.get(), pos, state);
    }

    public static boolean canInsert(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (Config.POSITION_ZERO_ADDITIONAL_ITEMS.get().contains(id.toString())) return true;
        return "minecraft".equals(id.getNamespace())
            && (stack.is(Tags.Items.MELEE_WEAPON_TOOLS) || stack.is(Tags.Items.RANGED_WEAPON_TOOLS));
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public boolean isEmpty() {
        return stored.isEmpty();
    }

    public ItemStack getStored() {
        return stored;
    }

    /** Store one item on the server, leaving creative players' held stacks unchanged. */
    public void insertOne(ItemStack held, Player player) {
        if (level == null || level.isClientSide || !isEmpty() || !canInsert(held)) return;
        stored = held.copyWithCount(1);
        if (!player.isCreative()) {
            held.shrink(1);
        }
        setChanged();
        sendData();
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 1f);
    }

    public void retrieve(Player player) {
        if (level == null || level.isClientSide || isEmpty()) return;
        ItemStack result = stored;
        stored = ItemStack.EMPTY;
        if (!player.getInventory().add(result)) player.drop(result, false);
        setChanged();
        sendData();
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.75f, 1f);
    }

    public void dropStoredItem() {
        if (level == null || level.isClientSide || isEmpty()) return;
        ItemEntity item = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.25,
            worldPosition.getZ() + 0.5, stored);
        level.addFreshEntity(item);
        stored = ItemStack.EMPTY;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!stored.isEmpty()) {
            tag.put("Stored", stored.save(registries));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        stored = tag.contains("Stored") ? ItemStack.parse(registries, tag.getCompound("Stored")).orElse(ItemStack.EMPTY)
            : ItemStack.EMPTY;
    }
}
