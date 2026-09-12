package com.shiningstage.create_shining_stage;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import org.jetbrains.annotations.Nullable;

public class SpeakerBlock extends HorizontalDirectionalBlock implements IBE<SpeakerBlockEntity> {
    public static final MapCodec<SpeakerBlock> CODEC = simpleCodec(SpeakerBlock::new);

    public SpeakerBlock(Properties properties) {
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

    // Grill faces the placer, the vanilla horizontal-block convention (ChestBlock/FurnaceBlock).
    // HorizontalDirectionalBlock supplies rotate/mirror, which is what lets structure blocks and the
    // Create wrench turn the speaker in 90-degree steps.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public Class<SpeakerBlockEntity> getBlockEntityClass() {
        return SpeakerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpeakerBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.SPEAKER.get();
    }

    // Binding flow (display-link style): the stack carries BOUND_MIC from right-clicking a microphone;
    // placing registers both directions. A stack without the component places an inert decoration.
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)) {
            return;
        }
        GlobalPos mic = stack.get(ModDataComponents.BOUND_MIC.get());
        if (mic == null) {
            return;
        }
        Player player = placer instanceof Player p ? p : null;
        if (!mic.dimension().equals(level.dimension())) {
            warn(player, "block.create_shining_stage.speaker.bind_failed_dimension");
            return;
        }
        if (!(level.getBlockEntity(mic.pos()) instanceof MicrophoneBlockEntity micBe)) {
            warn(player, "block.create_shining_stage.speaker.bind_failed_unloaded");
            return;
        }
        speaker.bindTo(mic);
        micBe.addSpeaker(pos);
    }

    private static void warn(@Nullable Player player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    // On break, unregister from the microphone so its bound-speaker set never leaks stale positions.
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
            && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker && speaker.getBoundMic() != null) {
            GlobalPos mic = speaker.getBoundMic();
            if (mic.dimension().equals(level.dimension())
                && level.getBlockEntity(mic.pos()) instanceof MicrophoneBlockEntity micBe) {
                micBe.removeSpeaker(pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
