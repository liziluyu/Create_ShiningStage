package com.shiningstage.create_shining_stage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared 1.20.1 NBT encoding for a speaker's microphone binding. */
final class SpeakerBindingNbt {
    private static final String ITEM_KEY = "CreateShiningStageBoundMic";
    private static final String DIMENSION_KEY = "Dimension";
    private static final String POSITION_KEY = "Position";

    private SpeakerBindingNbt() {
    }

    static GlobalPos get(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root == null ? null : read(root, ITEM_KEY);
    }

    static void set(ItemStack stack, GlobalPos pos) {
        write(stack.getOrCreateTag(), ITEM_KEY, pos);
    }

    static void remove(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root != null) {
            root.remove(ITEM_KEY);
            if (root.isEmpty()) {
                stack.setTag(null);
            }
        }
    }

    static void write(CompoundTag parent, String key, GlobalPos pos) {
        CompoundTag value = new CompoundTag();
        value.putString(DIMENSION_KEY, pos.dimension().location().toString());
        value.putLong(POSITION_KEY, pos.pos().asLong());
        parent.put(key, value);
    }

    static GlobalPos read(CompoundTag parent, String key) {
        if (!parent.contains(key, 10)) {
            return null;
        }
        CompoundTag value = parent.getCompound(key);
        ResourceLocation dimension = ResourceLocation.tryParse(value.getString(DIMENSION_KEY));
        if (dimension == null || !value.contains(POSITION_KEY, 4)) {
            return null;
        }
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimension);
        return GlobalPos.of(dimensionKey, BlockPos.of(value.getLong(POSITION_KEY)));
    }
}
