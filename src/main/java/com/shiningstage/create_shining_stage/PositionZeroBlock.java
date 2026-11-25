package com.shiningstage.create_shining_stage;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Floor marking strip with a single displayed weapon. FACING turns it in 90-degree steps. */
public class PositionZeroBlock extends HorizontalDirectionalBlock implements IBE<PositionZeroBlockEntity> {
    /**
     * Outline shapes of the two plate elements of block/position_zero/position_zero (0.2 and 0.1 model units tall),
     * so the selection box hugs the marking instead of the full cube. Collision stays empty
     * (Properties#noCollission): the model is a floor decal, and a full cube here would be an invisible
     * one-block wall on top of the very floor the strip marks. The rotated copies are those boxes
     * turned clockwise about the block center, which is exactly what a blockstate "y" of 90/180/270
     * does to the model, so shape and model stay aligned on every facing.
     */
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
        Block.box(4.5, 0, 8, 11.5, 0.2, 11), Block.box(6.5, 0, 5, 9.5, 0.1, 8));
    private static final VoxelShape SHAPE_EAST = rotateClockwise(SHAPE_NORTH);
    private static final VoxelShape SHAPE_SOUTH = rotateClockwise(SHAPE_EAST);
    private static final VoxelShape SHAPE_WEST = rotateClockwise(SHAPE_SOUTH);

    public PositionZeroBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntityType<? extends PositionZeroBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.POSITION_ZERO.get();
    }

    @Override
    public Class<PositionZeroBlockEntity> getBlockEntityClass() {
        return PositionZeroBlockEntity.class;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Matches SpeakerBlock and the vanilla furnace/chest convention: the marking's front faces the placer.
        // HorizontalDirectionalBlock supplies rotate/mirror, which is what lets a wrench step it around.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // FACING is HORIZONTAL_FACING, so the default only ever covers NORTH (and unreachable UP/DOWN,
        // which the property type still admits - same idiom as vanilla TrapDoorBlock).
        return switch (state.getValue(FACING)) {
            case EAST -> SHAPE_EAST;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PositionZeroBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            if (!be.isEmpty()) {
                if (!level.isClientSide) be.retrieve(player);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }
        boolean allowed = level.isClientSide
            ? PositionZeroBlockEntity.canInsertClient(held)
            : PositionZeroBlockEntity.canInsert(held);
        if (be.isEmpty() && allowed) {
            if (!level.isClientSide) be.insertOne(held, player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Creative destruction bypasses playerDestroy/loot generation, but stored display contents still
        // need the same recovery behavior they had before this block used its normal loot path.
        if (!level.isClientSide && player.isCreative()
            && level.getBlockEntity(pos) instanceof PositionZeroBlockEntity be)
            be.dropStoredItem();
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, builder));
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof PositionZeroBlockEntity be) {
            ItemStack stored = be.getStored().copy();
            if (!stored.isEmpty()) drops.add(stored);
        }
        return drops;
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
        return Shapes.or(Shapes.empty(), boxes.stream().map(Shapes::create).toArray(VoxelShape[]::new));
    }
}
