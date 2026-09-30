package com.shiningstage.create_shining_stage;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cold spark machine: a stage cabinet that shoots a fountain of cold sparks out of its top face.
 *
 * <p>The cabinet is 20/16 blocks tall rather than a full block — a full 16x16 footprint, standing a
 * quarter block proud — so its top face sits above y = 1 and the block needs a shape to match. What
 * stays meaningful about the four-way mounting is the control face: {@code FACING} is where the
 * spray-height value box sits, so a wrench turn brings the controls to whichever side of the stage is
 * being worked from.
 */
public class ColdSparkMachineBlock extends HorizontalDirectionalBlock
    implements IBE<ColdSparkMachineBlockEntity>, IWrenchable {

    public static final MapCodec<ColdSparkMachineBlock> CODEC = simpleCodec(ColdSparkMachineBlock::new);

    /**
     * Height of the cabinet's model, in pixels. Its top face — the nozzle the spray leaves from — is
     * therefore at block-local y = {@code MODEL_HEIGHT_PX / 16}, not at the 1 of a full block. The
     * shape below and {@link ColdSparkMachineRenderer}'s launch plane both read this, so raising the
     * model again means changing one number.
     */
    public static final int MODEL_HEIGHT_PX = 20;

    /**
     * The cabinet as a box: full footprint, {@link #MODEL_HEIGHT_PX} tall — collision and the outline,
     * which is what {@code getCollisionShape} and {@code getVisualShape} take from here. The default is a
     * full 16-cube, which the model overruns by a quarter block, so without this the player would sink
     * into the top of the cabinet and the nozzle would sit above the collision.
     *
     * <p>Collision and the outline are all this may answer for. {@link #getBlockSupportShape} and
     * {@link #getOcclusionShape} default to it as well, and both want a full cell instead.
     */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, MODEL_HEIGHT_PX, 16);

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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    /**
     * What attachment asks: whether a face of this block can hold a torch, lever, button, rail or sign.
     * Placement tests it through {@code SupportType}, which wants a face that compares equal to a full
     * block — and the 20-pixel shape deliberately does not, since its faces are a quarter block taller
     * than the cell (a face whose coordinates are not exactly 0..1 is reduced to a flat slice before
     * being compared, so every face fails, the lid included). Overriding {@link #getShape} alone
     * therefore makes the cabinet un-buildable-on: nothing can be attached to its sides. A full cell is
     * what a solid cabinet should report here, and is what this block reported before it had a shape.
     */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /**
     * What neighbours cull their faces against and what the light engine treats as solid. Same story as
     * {@link #getBlockSupportShape}: it defaults to {@link #getShape}, and a shape taller than its cell
     * is the wrong answer here — occlusion is a per-cell question, so the cabinet occludes its own cell
     * like the solid block it is instead of claiming a quarter of the one above.
     */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
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
     * Right-click opens the fuel GUI, so the machine can be loaded by hand and not only by funnel.
     *
     * <p>Two items keep the click for themselves, because the block is used as scenery as much as as a
     * machine. A block in hand means "attach this here", which is the same reasoning the spray-height
     * value box uses to step aside for one (see {@code ColdSparkMachineBlockEntity#addBehaviours}), and a
     * wrench means "turn the control face". Both matter because this hook runs *before*
     * {@code ItemStack#useOn}: whatever consumes the click here is a click the held item never gets, so
     * returning {@code PASS} is what hands it on rather than merely declining it. Everything else — an
     * empty hand, fuel, a random tool — opens the menu.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Item item = player.getMainHandItem().getItem();
        if (item instanceof BlockItem || item instanceof WrenchItem) {
            return InteractionResult.PASS;
        }
        withBlockEntityDo(level, pos, be -> player.openMenu(be, be::sendToMenu));
        return InteractionResult.SUCCESS;
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
