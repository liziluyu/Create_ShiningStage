package com.shiningstage.create_shining_stage;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Vertical stand for the spotlight: the lowest cell is a bottom, every cell above it is a pole,
 * and poles can only extend straight up from another tripod part. A spotlight placed on top of any
 * part gets the tripod mount variant (see SpotlightBlock.mountFor).
 */
public class TripodBlock extends Block {
    public static final MapCodec<TripodBlock> CODEC = simpleCodec(TripodBlock::new);

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** Central 2x2 column shared by both models; the bottom's splayed legs are too thin to collide. */
    private static final VoxelShape SHAPE = Block.box(7, 0, 7, 9, 16, 9);

    public TripodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.BOTTOM));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Top-face clicks only: bottom stands on the clicked block, pole stacks straight up on a tripod.
        if (context.getClickedFace() != Direction.UP) {
            return null;
        }
        Part part = context.getLevel().getBlockState(context.getClickedPos().below()).getBlock() instanceof TripodBlock
            ? Part.POLE : Part.BOTTOM;
        return defaultBlockState().setValue(PART, part);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(PART) == Part.BOTTOM
            || level.getBlockState(pos.below()).getBlock() instanceof TripodBlock;
    }

    // A pole with no tripod below pops off; its own removal notifies the pole above, chaining upward.
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public enum Part implements StringRepresentable {
        BOTTOM("bottom"),
        POLE("pole");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
