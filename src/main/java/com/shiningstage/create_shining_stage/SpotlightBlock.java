package com.shiningstage.create_shining_stage;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SpotlightBlock extends DirectionalBlock implements IBE<SpotlightBlockEntity> {
    public static final MapCodec<SpotlightBlock> CODEC = simpleCodec(SpotlightBlock::new);

    /** How the spotlight is supported; only affects the model when FACING is horizontal. */
    public static final EnumProperty<MountType> MOUNT = EnumProperty.create("mount", MountType.class);

    /**
     * Outline/collision shapes, taken from the largest element of each model family
     * (the spotlight head, ~3..13 wide). Horizontal shapes are the north one rotated to match
     * the blockstate y-rotations; the dish flare pokes out of the block and is not collidable.
     */
    private static final VoxelShape SHAPE_UP = Block.box(2.95, 0, 2.95, 13.05, 14, 13.05);
    private static final VoxelShape SHAPE_DOWN = Block.box(2.95, 2, 2.95, 13.05, 16, 13.05);
    private static final VoxelShape SHAPE_NORTH = Block.box(2.95, 2.95, 2, 13.05, 13.05, 16);
    private static final VoxelShape SHAPE_EAST = Block.box(0, 2.95, 2.95, 14, 13.05, 13.05);
    private static final VoxelShape SHAPE_SOUTH = Block.box(2.95, 2.95, 0, 13.05, 13.05, 14);
    private static final VoxelShape SHAPE_WEST = Block.box(2, 2.95, 2.95, 16, 13.05, 13.05);
    /**
     * Support shapes for attachment checks only (SupportType, used by levers/buttons/torches):
     * a full slab covering the tail half, so the face opposite the beam reads as sturdy.
     * Never used for rendering or occlusion, unlike getShape/getOcclusionShape.
     */
    private static final VoxelShape SUPPORT_TAIL_NORTH = Block.box(0, 0, 0, 16, 16, 8);
    private static final VoxelShape SUPPORT_TAIL_SOUTH = Block.box(0, 0, 8, 16, 16, 16);
    private static final VoxelShape SUPPORT_TAIL_WEST = Block.box(0, 0, 0, 8, 16, 16);
    private static final VoxelShape SUPPORT_TAIL_EAST = Block.box(8, 0, 0, 16, 16, 16);
    private static final VoxelShape SUPPORT_TAIL_DOWN = Block.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape SUPPORT_TAIL_UP = Block.box(0, 8, 0, 16, 16, 16);

    public SpotlightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(MOUNT, MountType.GROUND));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        MountType mount = mountFor(context);
        if (mount == null) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite())
            .setValue(MOUNT, mount);
    }

    /**
     * Mount is chosen from the support at placement time and never re-evaluated afterwards:
     * top-face clicks sit on the block below (tripod if it is one, ground otherwise), bottom-face
     * clicks hang from the block above. Side clicks fall back to whatever support exists around the
     * target position; with no support at all placement is denied (the spotlight cannot float).
     */
    private static MountType mountFor(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (context.getClickedFace() == Direction.UP) {
            return level.getBlockState(pos.below()).getBlock() instanceof TripodBlock
                ? MountType.TRIPOD : MountType.GROUND;
        }
        if (context.getClickedFace() == Direction.DOWN) {
            return MountType.HANG;
        }
        if (level.getBlockState(pos.below()).getBlock() instanceof TripodBlock) {
            return MountType.TRIPOD;
        }
        if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            return MountType.GROUND;
        }
        if (level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN)) {
            return MountType.HANG;
        }
        return null;
    }

    @Override
    public Class<SpotlightBlockEntity> getBlockEntityClass() {
        return SpotlightBlockEntity.class;
    }
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case EAST -> SHAPE_EAST;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
        };
    }
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        // Only the tail face is sturdy; everything else stays non-attachable.
        return switch (state.getValue(FACING)) {
            case NORTH -> SUPPORT_TAIL_SOUTH;
            case SOUTH -> SUPPORT_TAIL_NORTH;
            case EAST -> SUPPORT_TAIL_WEST;
            case WEST -> SUPPORT_TAIL_EAST;
            case UP -> SUPPORT_TAIL_DOWN;
            case DOWN -> SUPPORT_TAIL_UP;
        };
    }

    @Override
    public BlockEntityType<? extends SpotlightBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.SPOTLIGHT.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof SpotlightBlockEntity be) {
            int color = -1;
            if (stack.getItem() instanceof DyeItem dye) {
                color = dye.getDyeColor().getTextColor();
            } else if (stack.is(Items.AMETHYST_SHARD)) {
                color = SpotlightBlockEntity.DEFAULT_COLOR;
            }

            if (color != -1 && color != be.getLaserColor()) {
                be.setLaserColor(color);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 0.3f, 1f);
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public enum MountType implements StringRepresentable {
        TRIPOD("tripod"),
        GROUND("ground"),
        HANG("hang");

        private final String name;

        MountType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
