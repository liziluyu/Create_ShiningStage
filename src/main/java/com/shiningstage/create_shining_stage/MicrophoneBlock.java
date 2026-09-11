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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

public class MicrophoneBlock extends DirectionalBlock implements IBE<MicrophoneBlockEntity> {
    public static final MapCodec<MicrophoneBlock> CODEC = simpleCodec(MicrophoneBlock::new);

    public MicrophoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public Class<MicrophoneBlockEntity> getBlockEntityClass() {
        return MicrophoneBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MicrophoneBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.MICROPHONE.get();
    }

    // Cache the power state in the BE so the sound event handler reads a field instead of querying neighbors.
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MicrophoneBlockEntity be) {
            be.setRedstoneOn(level.getBestNeighborSignal(pos) > 0);
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
