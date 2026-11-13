package com.shiningstage.create_shining_stage;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;

public class FillLightBlock extends Block implements IBE<FillLightBlockEntity>, IWrenchable {
    public static final MapCodec<FillLightBlock> CODEC = simpleCodec(FillLightBlock::new);
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 15);
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    private static final VoxelShape SHAPE = Block.box(1, 0, 0, 15, 14, 16);

    public FillLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ROTATION, 0).setValue(POWERED, false));
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, POWERED);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        int rotation = Math.floorMod(Math.round(context.getRotation() / 22.5f), 16);
        return defaultBlockState().setValue(ROTATION, rotation)
            .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        int rotation = (state.getValue(ROTATION) + 1) % 16;
        return state.setValue(ROTATION, rotation);
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, Math.floorMod(state.getValue(ROTATION) + rotation.ordinal() * 4, 16));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        int r = state.getValue(ROTATION);
        return state.setValue(ROTATION, mirror == Mirror.LEFT_RIGHT ? Math.floorMod(8 - r, 16)
            : mirror == Mirror.FRONT_BACK ? Math.floorMod(-r, 16) : r);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                               BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            boolean powered = level.hasNeighborSignal(pos);
            if (state.getValue(POWERED) != powered) {
                level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
            }
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                             BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public static Vec3 direction(BlockState state) {
        double angle = Math.toRadians(state.getValue(ROTATION) * 22.5);
        return new Vec3(Math.sin(angle), 0, -Math.cos(angle));
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!level.isClientSide && !state.is(next.getBlock())
            && level.getBlockEntity(pos) instanceof FillLightBlockEntity be) be.removePlacedLights();
        super.onRemove(state, level, pos, next, moved);
    }

    @Override public Class<FillLightBlockEntity> getBlockEntityClass() { return FillLightBlockEntity.class; }
    @Override public BlockEntityType<? extends FillLightBlockEntity> getBlockEntityType() { return ModBlockEntityTypes.FILL_LIGHT.get(); }
}
