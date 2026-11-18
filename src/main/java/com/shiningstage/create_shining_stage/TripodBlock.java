package com.shiningstage.create_shining_stage;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Single-cell stand for the spotlight and microphone.
 */
public class TripodBlock extends Block {
    public static final MapCodec<TripodBlock> CODEC = simpleCodec(TripodBlock::new);
    public static final IntegerProperty POWER = StageRedstoneNetwork.POWER;

    /** The stand's central column; the splayed legs are too thin to collide. */
    private static final VoxelShape SHAPE = Block.box(7, 0, 7, 9, 16, 9);

    public TripodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return context.getClickedFace() == Direction.UP ? defaultBlockState() : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return StageRedstoneNetwork.getSignal(state);
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

}
