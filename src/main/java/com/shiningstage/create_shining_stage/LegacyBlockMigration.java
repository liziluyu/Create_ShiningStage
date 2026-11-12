package com.shiningstage.create_shining_stage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Rewrites legacy tripod pole palette entries before the chunk section codec resolves block states. */
public final class LegacyBlockMigration {
    private static final String LEGACY_TRIPOD = CreateShiningStage.MOD_ID + ":tripod";
    private static final String LEGACY_POLE = CreateShiningStage.MOD_ID + ":tripod_pole";
    private static final String POLE = CreateShiningStage.MOD_ID + ":pole";

    private LegacyBlockMigration() {}

    public static boolean migrate(CompoundTag chunkData) {
        if (!chunkData.contains("sections", Tag.TAG_LIST)) return false;

        ListTag sections = chunkData.getList("sections", Tag.TAG_COMPOUND);
        boolean changed = false;
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            CompoundTag section = sections.getCompound(sectionIndex);
            if (!section.contains("block_states", Tag.TAG_COMPOUND)) continue;

            CompoundTag blockStates = section.getCompound("block_states");
            if (!blockStates.contains("palette", Tag.TAG_LIST)) continue;
            ListTag palette = blockStates.getList("palette", Tag.TAG_COMPOUND);
            for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
                CompoundTag state = palette.getCompound(paletteIndex);
                changed |= migrateState(state);
            }
        }
        return changed;
    }

    public static boolean migrateState(CompoundTag state) {
        String name = state.getString("Name");
        boolean legacyRegisteredPole = LEGACY_POLE.equals(name);
        boolean oldPolePart = LEGACY_TRIPOD.equals(name)
            && state.contains("Properties", Tag.TAG_COMPOUND)
            && "pole".equals(state.getCompound("Properties").getString("part"));
        if (!legacyRegisteredPole && !oldPolePart) return false;

        state.putString("Name", POLE);
        CompoundTag properties = new CompoundTag();
        properties.putString("north", "false");
        properties.putString("south", "false");
        properties.putString("east", "false");
        properties.putString("west", "false");
        properties.putString("up", "true");
        properties.putString("down", "true");
        state.put("Properties", properties);
        return true;
    }
}
