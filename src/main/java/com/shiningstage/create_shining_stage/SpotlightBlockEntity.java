package com.shiningstage.create_shining_stage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SpotlightBlockEntity extends SmartBlockEntity {
    /** Default warm white (#f8f7d2); dye right-click overrides, amethyst resets to this. */
    public static final int DEFAULT_COLOR = 0xF8F7D2;
    /** Adjustable beam length bounds, in blocks. */
    public static final int MIN_RANGE = 2;
    public static final int MAX_RANGE = 32;
    /** Initial beam length on placement. */
    public static final int DEFAULT_RANGE = 16;

    /** Client light endpoints are refreshed at most every five ticks. */
    public static final int LIGHT_POLL_INTERVAL = 5;
    /** Brightness of the client-only light sources. */
    public static final int LIGHT_LEVEL = 15;

    private int laserColor = DEFAULT_COLOR;
    private ScrollValueBehaviour range;
    /** Save-only migration records; this version never places light blocks. */
    private final Set<BlockPos> placedLights = new HashSet<>();

    public SpotlightBlockEntity(BlockPos pos, BlockState blockState) {
        this(ModBlockEntityTypes.SPOTLIGHT.get(), pos, blockState);
    }

    protected SpotlightBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                  BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        range = new ScrollValueBehaviour(
            Component.translatable("block.create_shining_stage.spotlight.max_length"),
            this, new SpotlightRangeValueBoxTransform()) {
            // Holding a placeable block means "attach it to the tail", not "adjust the range":
            // let the click fall through to block placement instead of opening the value box.
            @Override
            public boolean bypassesInput(ItemStack mainhandItem) {
                return mainhandItem.getItem() instanceof BlockItem;
            }
        }.between(MIN_RANGE, MAX_RANGE);
        range.value = DEFAULT_RANGE; // start at a mid value, not max
        behaviours.add(range);
    }

    /** Beam length in blocks (MIN_RANGE..MAX_RANGE), adjusted via the tail value box. */
    public int getRange() {
        return range == null ? DEFAULT_RANGE : range.getValue();
    }

    protected Vec3 beamDirection() {
        return SpotlightBlock.beamDirection(getBlockState());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Loading does not imply ticking. Detach ownership now so unloading or moving the fixture
        // cannot strand records in a block entity that the server will never tick again.
        handOffLegacyLights();
    }

    @Override
    public void invalidate() {
        // Only transfer saved work here: no block writes while a chunk/ship is being torn down.
        handOffLegacyLights();
        super.invalidate();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || placedLights.isEmpty()) {
            return;
        }
        handOffLegacyLights(); // fallback for custom assembly paths that do not invoke onLoad
    }

    /** Schedule recorded legacy sources and their cached-light footprint for safe cleanup. */
    public void removePlacedLights() {
        handOffLegacyLights();
    }

    /** Preserve unresolved migration work after this fixture is destroyed or assembled. */
    void handOffLegacyLights() {
        if (level instanceof ServerLevel serverLevel && !placedLights.isEmpty()) {
            LegacyStageLightCleanup.accept(serverLevel, placedLights);
            placedLights.clear();
            setChanged();
        }
    }

    /**
     * Transfers any remaining records after Sable teardown. Actual world edits are performed by the
     * level migration queue, never inside the structure removal callback.
     */
    public void removePlacedLightsLater() {
        if (level == null || placedLights.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        server.execute(this::handOffLegacyLights);
    }

    public int getLaserColor() {
        return laserColor;
    }

    public void setLaserColor(int laserColor) {
        this.laserColor = laserColor;
        notifyUpdate();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("LaserColor", laserColor);
        // Legacy cleanup ownership is server-only; virtual client lights are never persisted here.
        if (!clientPacket) {
            long[] cells = new long[placedLights.size()];
            int i = 0;
            for (BlockPos pos : placedLights) {
                cells[i++] = pos.asLong();
            }
            tag.putLongArray("PlacedLights", cells);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("LaserColor")) {
            laserColor = tag.getInt("LaserColor");
        }
        if (!clientPacket) {
            placedLights.clear();
            for (long cell : tag.getLongArray("PlacedLights")) {
                placedLights.add(BlockPos.of(cell));
            }
        }
    }
}
