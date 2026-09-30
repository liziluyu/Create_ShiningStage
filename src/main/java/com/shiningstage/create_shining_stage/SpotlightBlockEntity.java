package com.shiningstage.create_shining_stage;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class SpotlightBlockEntity extends SmartBlockEntity {
    /** Default warm white (#f8f7d2); dye right-click overrides, amethyst resets to this. */
    public static final int DEFAULT_COLOR = 0xF8F7D2;
    /** Adjustable beam length bounds, in blocks. */
    public static final int MIN_RANGE = 2;
    public static final int MAX_RANGE = 32;
    /** Initial beam length on placement. */
    public static final int DEFAULT_RANGE = 16;

    /**
     * How often, in ticks, the beam's end is re-derived. This is a poll rather than an event because a
     * block anywhere along the beam truncates it, and only the blocks touching the spotlight report a
     * neighbour change — the far end of a 32-block beam reports nothing at all.
     *
     * <p>The interval throttles the world edits the check can produce, not the check itself. One change
     * costs one light-engine update plus a section rebuild on every client, and the beam's end moves as
     * fast as anything crossing it: a hull drifting through the beam moves the cell every tick, so a
     * check per tick would place and tear out a light twenty times a second, each flooding block light
     * over a fifteen-block radius and then draining it again. Five ticks bounds that at four a second
     * while keeping the light within a quarter second of the beam. Steady state costs nothing: the cell
     * only has to move for the world to be touched at all.
     */
    public static final int LIGHT_POLL_INTERVAL = 5;

    /**
     * Light level of the block left where the beam ends. Full brightness, and deliberately not scaled by
     * the redstone signal: the signal changes far more often than the beam's geometry does — a
     * flickering line would be one light update per step — and the block is there to mark where the
     * beam lands, not to reproduce the beam's own dimming.
     */
    public static final int LIGHT_LEVEL = 15;

    /** What is left where the beam ends. Only ever placed into air, so never waterlogged. */
    private static final BlockState BEAM_LIGHT =
        Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL);

    /**
     * Puts back the lights of a structure that is going away.
     *
     * <p>A spotlight riding a physics structure keeps its block entity ticking, so its polls keep the
     * light in step exactly as they do in the world. The structure itself, though, can vanish without
     * a single block being removed from the level: removing or unloading a sub-level unloads its plot
     * chunks, and the block entities in them are told through {@code onChunkUnloaded}/{@code setRemoved}
     * and never through {@code Block#onRemove}. That is the same shape as an ordinary chunk unload,
     * which must not delete anything, so the two cannot be told apart from inside a block entity —
     * which is why a light left in the world by a structure needs this second way out.
     *
     * <p>Sable calls its observers at the top of exactly that teardown, while the plot chunks are still
     * loaded, so the departing structure's spotlights can still be found and asked to clean up after
     * themselves.
     */
    private static final SubLevelObserver LIGHT_CLEANUP = new SubLevelObserver() {
        @Override
        public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
            // Both reasons: a removed structure is gone, and one that is merely unloaded has left the
            // loaded world too. Either way its beam no longer exists here, and a light left behind
            // would outlive it forever. The lights of a structure that comes back are re-placed by the
            // first poll after it loads.
            for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof SpotlightBlockEntity spotlight) {
                        // Not the cleanup itself, but a request for it a moment later: this callback
                        // runs in the middle of Sable taking the structure apart, and the world writes
                        // it needs are none of that teardown's business. Reading the plot's block
                        // entities is safe here; touching the level is not.
                        spotlight.removePlacedLightsLater();
                    }
                }
            }
        }
    };

    /** Levels whose sub-level container already carries {@link #LIGHT_CLEANUP}. */
    private static final Set<Level> OBSERVED_LEVELS = Collections.newSetFromMap(new WeakHashMap<>());

    private int laserColor = DEFAULT_COLOR;
    private ScrollValueBehaviour range;
    /** Cells this spotlight has put {@link #BEAM_LIGHT} into and not yet taken back. */
    private final Set<BlockPos> placedLights = new HashSet<>();
    /**
     * Ticks until the beam's end is looked at again. Starts at 0, so a freshly placed or loaded
     * spotlight settles its light on its first tick rather than a quarter second later.
     */
    private int lightPoll;

    public SpotlightBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.SPOTLIGHT.get(), pos, blockState);
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

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        if (--lightPoll <= 0) {
            lightPoll = LIGHT_POLL_INTERVAL;
            updateBeamLight();
        }
    }

    /**
     * Re-derives where the beam ends and keeps a light block there: placed when the cell moves, taken
     * back when the beam moves off it or goes dark, and left strictly alone otherwise — a spotlight
     * whose beam is not moving costs no world writes at all, and so no light updates.
     *
     * <p>Two beams ending in the same cell converge rather than fight: both record it, and whichever
     * leaves first takes the block with it, after which the other re-places it on its next poll.
     */
    private void updateBeamLight() {
        BlockPos end;
        if (level.getBestNeighborSignal(worldPosition) <= 0) {
            end = null; // the beam is off, so nothing anywhere is lit by it
        } else if (!beamPathLoaded()) {
            return; // nothing can be decided from here; the light stays where the last poll put it
        } else {
            end = beamEndCell();
        }

        // Anything on the books that is no longer where the beam ends is taken back. A cell whose chunk
        // is not loaded keeps its record instead, so it is retried on a later poll rather than left
        // behind as an invisible light source.
        placedLights.removeIf(pos -> !pos.equals(end) && removeBeamLight(pos));
        // The one light nothing else can take back is the one a structure leaves *outside* itself: a
        // ship's beam ends on the ground, in the world's own chunks, and when the ship goes away its
        // block entity goes with the plot rather than through Block#onRemove, leaving that light in the
        // world forever. A light the structure holds dies with the structure, so only this case needs
        // watching. Asked of the recorded cells each poll rather than of the moment one is placed: a
        // spotlight assembled with its beam already lit has its record from the world, and would
        // otherwise never be watched.
        if (Sable.HELPER.isInPlotGrid(level, worldPosition)) {
            for (BlockPos light : placedLights) {
                if (!Sable.HELPER.isInPlotGrid(level, light)) {
                    observeSubLevelRemovals();
                    break;
                }
            }
        }
        if (end == null || !cellPresent(end)) {
            return;
        }
        BlockState there = level.getBlockState(end);
        if (there.is(Blocks.LIGHT)) {
            // Take it on the books. This is normally the spotlight's own light, and taking it rather
            // than refusing it is what makes the block survive a move: assembling a lit rig copies
            // every block of the region into the structure's plot — the light included — while the
            // removal of the old cell clears this record. Refusing an unrecorded light would then
            // strand it: the poll would never adopt it, and nothing could ever take it back, so it
            // would glow inside the hull forever even after the beam moved on or went dark.
            //
            // It also settles two beams ending in the same cell: both record it, and if one leaves and
            // takes the block with it, the other re-places it on its next poll. A light of a different
            // level is someone else's and is left exactly as it is.
            if (there.equals(BEAM_LIGHT)) {
                placedLights.add(end);
            }
            return;
        }
        if (!there.isAir()) {
            return; // not ours to take — water, or a block a player has put there since
        }
        if (level.setBlock(end, BEAM_LIGHT, Block.UPDATE_ALL)) {
            placedLights.add(end);
        }
    }

    /**
     * Whether every cell the beam crosses is safe to read.
     *
     * <p>{@code Level#getBlockState} does not only read: it asks the chunk source with
     * {@code requireChunk = true}, which adds a ticket, blocks the calling thread until the chunk is
     * there, and generates it if it is not. A raycast that reached past the world's loaded area would
     * therefore mint terrain every {@link #LIGHT_POLL_INTERVAL} ticks. So the poll stands down while
     * the beam points at chunks that are not present, and picks up again once they are.
     *
     * <p>In practice this almost never refuses — a chunk within beam range of a ticking block entity is
     * present in every ordinary setup, which is why nothing here is load-bearing for correctness. It
     * exists for the boundary case alone: a spotlight at the edge of the loaded area with a 32-block
     * beam pointing outward. Note that vanilla's own {@code execute if loaded} is *not* the predicate
     * for this — it asks whether a chunk is entity-ticking, which is narrower, so it reports chunks as
     * absent that are present and perfectly readable.
     *
     * <p>A spotlight riding a structure is checked in both spaces, because Sable's
     * {@code BlockGetter#clip} maps a ray starting inside a sub-level out to the structure's real
     * position and clips the main level there as well: the cells that can be touched are the plot-space
     * ones *and* their images in the world.
     */
    private boolean beamPathLoaded() {
        Direction facing = getBlockState().getValue(SpotlightBlock.FACING);
        SubLevelAccess subLevel = Sable.HELPER.getContaining(level, worldPosition);
        Pose3dc pose = subLevel == null ? null : subLevel.logicalPose();
        Vector3d projected = pose == null ? null : new Vector3d();
        for (int step = 1; step <= getRange(); step++) {
            BlockPos cell = worldPosition.relative(facing, step);
            if (!cellPresent(cell)) {
                return false;
            }
            if (projected != null) {
                // Where that cell sits in the world. A rigid transform preserves spacing, so the cells
                // of the plot-space walk are the cells of the world-space ray.
                pose.transformPosition(projected.set(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5));
                if (!cellPresent(BlockPos.containing(projected.x, projected.y, projected.z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether that cell's chunk is already there to be read.
     *
     * <p>{@code Level#isLoaded} resolves to {@code ChunkSource#hasChunk}, which asks for the chunk at
     * {@code ChunkStatus.FULL} with {@code requireChunk = false}: no ticket is added and no generation
     * is started, so the question costs nothing and cannot itself pull the world in. That is exactly
     * the predicate the clip needs — present, or not — and it is what vanilla asks before reading a
     * chunk it may not hold.
     */
    private boolean cellPresent(BlockPos cell) {
        return level.isLoaded(cell);
    }

    /**
     * Makes the level's sub-level container call {@link #LIGHT_CLEANUP}, once per level and only when
     * a spotlight actually has a light outside the structure it rides — a world with no ships in it
     * never pays for any of this.
     */
    private void observeSubLevelRemovals() {
        if (!OBSERVED_LEVELS.add(level)) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            // No container yet: forget the mark, so a later poll can try again rather than leaving the
            // level permanently unwatched.
            OBSERVED_LEVELS.remove(level);
            return;
        }
        container.addObserver(LIGHT_CLEANUP);
    }

    /**
     * The cell the beam ends in, or null when there is nothing to light: the beam is shut against its
     * own face and so is shorter than a cell.
     *
     * <p>The raycast mirrors {@link SpotlightRenderer}'s — same clip, same bounds — so the block lands in
     * the last cell the drawn frustum covers: the cell before the hit, or the cell at {@link #getRange()}
     * when nothing is in the way. Only the cell is taken from it, never the ship-aware distance the
     * renderer needs for the beam's length: a hit is reported in whatever space the block it hit lives
     * in, so a beam crossing onto a sub-level still ends in that sub-level's own coordinates.
     */
    @Nullable
    private BlockPos beamEndCell() {
        Direction facing = getBlockState().getValue(SpotlightBlock.FACING);
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 start = Vec3.atCenterOf(worldPosition).add(normal.scale(0.5));
        Vec3 end = start.add(normal.scale(getRange()));
        BlockHitResult hit = level.clip(new ClipContext(start, end,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        // A light block has no collision shape, so the one placed here is invisible to this clip: the
        // beam never truncates itself, and the cell stays put on the next poll.
        BlockPos cell = hit.getType() == HitResult.Type.MISS
            ? worldPosition.relative(facing, getRange())
            : hit.getBlockPos().relative(facing.getOpposite());
        return cell.equals(worldPosition) ? null : cell;
    }

    /**
     * Takes back the light at that cell while it is still ours, reporting whether the record can be
     * dropped.
     *
     * <p>A cell whose chunk is not present cannot be read, and the answer then depends on which space
     * it is in. A world cell is a real light in a real chunk that has merely been unloaded, so the
     * record is kept and retried on a later poll. A <em>plot</em> cell is not: a light a structure holds
     * lives and dies with the structure's plot, so a plot cell we cannot reach belongs to a structure
     * that is no longer here — most often because the block was moved back into the world, which copies
     * the light out of the plot and leaves this record naming where it used to be. Keeping it would mean
     * a name that can never be resolved and never be cleared.
     */
    private boolean removeBeamLight(BlockPos pos) {
        if (!cellPresent(pos)) {
            return Sable.HELPER.isInPlotGrid(level, pos);
        }
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.removeBlock(pos, false);
        }
        return true;
    }

    /**
     * Takes back every light this spotlight placed. Called from {@link SpotlightBlock#onRemove} — the
     * hook that runs for a break, a replacement and a structure assembly alike, and never for a chunk
     * unload, where writing to the level would be a chunk save in flight.
     */
    public void removePlacedLights() {
        if (level == null) {
            return;
        }
        // The "chunk not loaded" answer is discarded here: this block entity is going away, so a record
        // kept for a retry would never be read again.
        placedLights.forEach(this::removeBeamLight);
        placedLights.clear();
    }

    /**
     * Asks for {@link #removePlacedLights} to run once the server finishes what it is doing, rather than
     * inside it. {@link #LIGHT_CLEANUP} is called from the middle of a structure's removal, and the task
     * queue puts these writes after that teardown has unwound instead of re-entering it.
     */
    public void removePlacedLightsLater() {
        if (level == null || placedLights.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        server.execute(this::removePlacedLights);
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
        // Save-only: the client never places a light block and has no say in where the server put one,
        // and a synced copy could only disagree with the world it is looking at.
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
