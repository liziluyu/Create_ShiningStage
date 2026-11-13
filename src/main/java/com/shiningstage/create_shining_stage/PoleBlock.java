package com.shiningstage.create_shining_stage;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A narrow stage pole that grows an arm toward every adjacent non-air block. */
public class PoleBlock extends Block {
    public static final MapCodec<PoleBlock> CODEC = simpleCodec(PoleBlock::new);

    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty EAST = BooleanProperty.create("east");

    private static final VoxelShape MID = Block.box(6.5, 6.5, 6.5, 9.5, 9.5, 9.5);
    private static final VoxelShape DOWN_ARM = Block.box(7, 0, 7, 9, 8, 9);
    private static final VoxelShape UP_ARM = Block.box(7, 8, 7, 9, 16, 9);
    private static final VoxelShape NORTH_ARM = Block.box(7, 7, 0, 9, 9, 8);
    private static final VoxelShape SOUTH_ARM = Block.box(7, 7, 8, 9, 9, 16);
    private static final VoxelShape WEST_ARM = Block.box(0, 7, 7, 8, 9, 9);
    private static final VoxelShape EAST_ARM = Block.box(8, 7, 7, 16, 9, 9);

    public PoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(DOWN, false).setValue(UP, false)
            .setValue(NORTH, false).setValue(SOUTH, false)
            .setValue(WEST, false).setValue(EAST, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DOWN, UP, NORTH, SOUTH, WEST, EAST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(property(direction), connectsTo(context.getLevel(), context.getClickedPos().relative(direction)));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(property(direction), !neighborState.isAir());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean down = state.getValue(DOWN);
        boolean up = state.getValue(UP);
        boolean north = state.getValue(NORTH);
        boolean south = state.getValue(SOUTH);
        boolean west = state.getValue(WEST);
        boolean east = state.getValue(EAST);

        if (down && up && !north && !south && !west && !east) {
            return Block.box(7, 0, 7, 9, 16, 9);
        }
        if (north && south && !down && !up && !west && !east) {
            return Block.box(7, 7, 0, 9, 9, 16);
        }
        if (west && east && !down && !up && !north && !south) {
            return Block.box(0, 7, 7, 16, 9, 9);
        }

        VoxelShape shape = MID;
        if (down) shape = Shapes.or(shape, DOWN_ARM);
        if (up) shape = Shapes.or(shape, UP_ARM);
        if (north) shape = Shapes.or(shape, NORTH_ARM);
        if (south) shape = Shapes.or(shape, SOUTH_ARM);
        if (west) shape = Shapes.or(shape, WEST_ARM);
        if (east) shape = Shapes.or(shape, EAST_ARM);
        return shape;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return StageRedstoneNetwork.getSignal(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean moving) {
        // Neighbor updates are queued; ignore our own network broadcasts to avoid re-broadcast loops.
        if (block instanceof TripodBlock || block instanceof PoleBlock) return;
        StageRedstoneNetwork.notifyNetwork(level, pos, this);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        if (!oldState.is(state.getBlock())) StageRedstoneNetwork.notifyNetwork(level, pos, this);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            for (Direction direction : Direction.values()) {
                StageRedstoneNetwork.notifyNetwork(level, pos.relative(direction), this);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return transform(state, rotation::rotate);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return transform(state, mirror::mirror);
    }

    private static BlockState transform(BlockState state, java.util.function.UnaryOperator<Direction> transform) {
        BlockState result = state;
        for (Direction from : Direction.values()) {
            result = result.setValue(property(transform.apply(from)), state.getValue(property(from)));
        }
        return result;
    }

    private static boolean connectsTo(LevelReader level, BlockPos pos) {
        return !level.getBlockState(pos).isAir();
    }

    private static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
    }
}
