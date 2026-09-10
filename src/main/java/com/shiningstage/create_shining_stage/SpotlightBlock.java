package com.shiningstage.create_shining_stage;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

public class SpotlightBlock extends DirectionalBlock implements IBE<SpotlightBlockEntity> {
    public static final MapCodec<SpotlightBlock> CODEC = simpleCodec(SpotlightBlock::new);

    public SpotlightBlock(Properties properties) {
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public Class<SpotlightBlockEntity> getBlockEntityClass() {
        return SpotlightBlockEntity.class;
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
}
