package com.shiningstage.create_shining_stage;

import java.util.List;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Shares spotlight settings and legacy-light migration; illumination is client-only. */
public class FillLightBlockEntity extends SpotlightBlockEntity {
    private boolean legacyEmissionMigrated;

    public FillLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FILL_LIGHT.get(), pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Range comes from the shared common config rather than a value box.
    }

    @Override public int getRange() {
        return level != null && level.isClientSide
            ? StageLightingSettings.clientFillLightRange() : Config.FILL_LIGHT_RANGE.get();
    }

    @Override public void tick() {
        super.tick();
        migrateLegacyEmission();
    }

    @Override public void onLoad() {
        super.onLoad();
        migrateLegacyEmission();
    }

    private void migrateLegacyEmission() {
        if (!(level instanceof ServerLevel serverLevel) || legacyEmissionMigrated) return;
        // Recheck the whole saved-light footprint, even when the source cell has already become dark.
        LegacyStageLightCleanup.recheckLegacyEmission(serverLevel, worldPosition);
        legacyEmissionMigrated = true;
        setChanged();
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) tag.putBoolean("LegacyEmissionMigrated", legacyEmissionMigrated);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket) legacyEmissionMigrated = tag.getBoolean("LegacyEmissionMigrated");
    }

    @Override protected Vec3 beamDirection() {
        return FillLightBlock.direction(getBlockState());
    }
}
