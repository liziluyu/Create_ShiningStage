package com.shiningstage.create_shining_stage;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Cold spark machine: a stage cabinet that shoots a fountain of cold sparks out of its top face.
 *
 * <p>A plain full cube, so its outline and collision need no shape override and it occludes like any
 * solid block. What stays meaningful about the four-way mounting is the control face: {@code FACING} is
 * where the spray-height value box sits, so a wrench turn brings the controls to whichever side of the
 * stage is being worked from.
 */
public class ColdSparkMachineBlock extends HorizontalDirectionalBlock
    implements IBE<ColdSparkMachineBlockEntity>, IWrenchable {

    public static final MapCodec<ColdSparkMachineBlock> CODEC = simpleCodec(ColdSparkMachineBlock::new);

    public ColdSparkMachineBlock(Properties properties) {
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

    /**
     * Control face towards the placer, the vanilla horizontal-block convention (furnace/chest/speaker).
     *
     * <p>Rotating it is {@link IWrenchable}'s job: its default rotation steps any block whose state
     * carries {@code HORIZONTAL_FACING} a quarter turn when the wrench hits a vertical face, which is
     * exactly the four-way mounting wanted here. {@code HorizontalDirectionalBlock}'s own rotate/mirror
     * are the structure-block path and never run for a wrench.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public Class<ColdSparkMachineBlockEntity> getBlockEntityClass() {
        return ColdSparkMachineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ColdSparkMachineBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.COLD_SPARK_MACHINE.get();
    }

    /**
     * A player break hands the stored fuel back. Deliberately not in onRemove: contraption assembly also
     * clears the block out of the level, and a drop there would hand the machine's contents to both the
     * contraption and the ground.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()
            && level.getBlockEntity(pos) instanceof ColdSparkMachineBlockEntity be) {
            Containers.dropContents(level, pos, be.getInventory());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
