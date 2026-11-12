package com.shiningstage.create_shining_stage;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Single-cell stand for the spotlight and microphone.
 */
public class TripodBlock extends Block {
    public static final MapCodec<TripodBlock> CODEC = simpleCodec(TripodBlock::new);

    /** The stand's central column; the splayed legs are too thin to collide. */
    private static final VoxelShape SHAPE = Block.box(7, 0, 7, 9, 16, 9);

    public TripodBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return context.getClickedFace() == Direction.UP ? defaultBlockState() : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

}
