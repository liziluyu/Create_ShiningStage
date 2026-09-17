package com.shiningstage.create_shining_stage;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Floor marking strip: plain decorative block, no block entity. FACING turns it in 90-degree steps. */
public class PositionZeroBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<PositionZeroBlock> CODEC = simpleCodec(PositionZeroBlock::new);

    /**
     * Outline shapes of the two plate elements of block/position_zero (0.2 and 0.1 model units tall),
     * so the selection box hugs the marking instead of the full cube. Collision stays empty
     * (Properties#noCollission): the model is a floor decal, and a full cube here would be an invisible
     * one-block wall on top of the very floor the strip marks. The rotated copies are those boxes
     * turned clockwise about the block center, which is exactly what a blockstate "y" of 90/180/270
     * does to the model, so shape and model stay aligned on every facing.
     */
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
        Block.box(4.5, 0, 8, 11.5, 0.2, 11),
        Block.box(6.5, 0, 5, 9.5, 0.1, 8));
    private static final VoxelShape SHAPE_EAST = rotateClockwise(SHAPE_NORTH);
    private static final VoxelShape SHAPE_SOUTH = rotateClockwise(SHAPE_EAST);
    private static final VoxelShape SHAPE_WEST = rotateClockwise(SHAPE_SOUTH);

    public PositionZeroBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // Matches SpeakerBlock and the vanilla furnace/chest convention: the marking's front faces the placer.
    // HorizontalDirectionalBlock supplies rotate/mirror, which is what lets a wrench step it around.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // FACING is HORIZONTAL_FACING, so the default only ever covers NORTH (and unreachable UP/DOWN,
        // which the property type still admits - same idiom as vanilla TrapDoorBlock).
        return switch (state.getValue(FACING)) {
            case EAST -> SHAPE_EAST;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    /**
     * Quarter turn clockwise seen from above - the (x, z) -> (1 - z, x) of blockstate "y": 90.
     * Works in block units, because that is what forAllBoxes reports; the model units Block.box takes
     * would silently shrink the result by 16x.
     */
    private static VoxelShape rotateClockwise(VoxelShape shape) {
        List<AABB> boxes = new ArrayList<>();
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
            boxes.add(new AABB(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
        return Shapes.or(Shapes.empty(),
            boxes.stream().map(Shapes::create).toArray(VoxelShape[]::new));
    }
}
