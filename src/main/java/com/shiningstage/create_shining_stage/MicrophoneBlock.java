package com.shiningstage.create_shining_stage;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MicrophoneBlock extends HorizontalDirectionalBlock implements IBE<MicrophoneBlockEntity> {
    public static final MapCodec<MicrophoneBlock> CODEC = simpleCodec(MicrophoneBlock::new);

    /**
     * Outline/collision shapes, taken from the two clusters of the tripod-mount model: the boom
     * arm sweeping in from the tail side and the head capsule at the mic's facing. These are the
     * authored-north model rotated to match the blockstate y-rotations; the head tips slightly
     * out of the block toward the facing and is clipped, like the spotlight's dish flare.
     */
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
        Block.box(6, 0, 1.5, 10, 7, 15.5),
        Block.box(6.5, 7, 0, 9.5, 14, 4.5));
    private static final VoxelShape SHAPE_EAST = Shapes.or(
        Block.box(0.5, 0, 6, 14.5, 7, 10),
        Block.box(11.5, 7, 6.5, 16, 14, 9.5));
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
        Block.box(6, 0, 0.5, 10, 7, 14.5),
        Block.box(6.5, 7, 11.5, 9.5, 14, 16));
    private static final VoxelShape SHAPE_WEST = Shapes.or(
        Block.box(1.5, 0, 6, 15.5, 7, 10),
        Block.box(0, 7, 6.5, 4.5, 14, 9.5));

    public MicrophoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // HorizontalDirectionalBlock does not register FACING itself; without this the constructor's
        // setValue(FACING, ...) throws and block registration aborts the whole mod.
        builder.add(FACING);
    }

    /**
     * Tripod-exclusive: the cell below the target position must be a tripod part (bottom or pole,
     * same rule the spotlight mounts with). FACING stays horizontal so the head can face the stage
     * from any side; it points back at the player on placement, like vanilla horizontal blocks.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (!(context.getLevel().getBlockState(pos.below()).getBlock() instanceof TripodBlock)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof TripodBlock;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case EAST -> SHAPE_EAST;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            // FACING is horizontal-only, so UP/DOWN cannot occur.
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public Class<MicrophoneBlockEntity> getBlockEntityClass() {
        return MicrophoneBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MicrophoneBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.MICROPHONE.get();
    }

    // Losing the tripod below pops the microphone off, chaining like TripodBlock's poles do.
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && !state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    // Binding: right-click with a speaker item stores this microphone on the stack; placing the speaker
    // consumes it (see SpeakerBlock.setPlacedBy). Sneak-click bypasses this so speakers can be placed against
    // a microphone face.
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(ModBlocks.SPEAKER_ITEM.get())) {
            if (!level.isClientSide) {
                stack.set(ModDataComponents.BOUND_MIC.get(), GlobalPos.of(level.dimension(), pos));
                player.displayClientMessage(
                    Component.translatable("block.create_shining_stage.speaker.bound"), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // On break, mute every bound speaker (binding does not revive if a new microphone is placed here later).
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
            && level.getBlockEntity(pos) instanceof MicrophoneBlockEntity be) {
            for (BlockPos speakerPos : List.copyOf(be.getBoundSpeakers())) {
                if (level.getBlockEntity(speakerPos) instanceof SpeakerBlockEntity speaker) {
                    speaker.clearBinding();
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}