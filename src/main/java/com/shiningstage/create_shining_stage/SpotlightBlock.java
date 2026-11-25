package com.shiningstage.create_shining_stage;

import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

public class SpotlightBlock extends DirectionalBlock implements IBE<SpotlightBlockEntity>, IWrenchable {

    /** Ground, hanging, tripod, or one of the two side-connection models. */
    public static final EnumProperty<MountType> MOUNT = EnumProperty.create("mount", MountType.class);
    public static final DirectionProperty MODEL_FACING = DirectionProperty.create("model_facing", Direction.Plane.HORIZONTAL);
    /** Four 22.5-degree steps within each of the existing quarter turns; old states default to 0. */
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);
    public static final float ROTATION_STEP = 22.5f;

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
            .setValue(MOUNT, MountType.GROUND).setValue(MODEL_FACING, Direction.NORTH).setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT, MODEL_FACING, ROTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        MountType mount = mountFor(context);
        Direction beam = context.getNearestLookingDirection().getOpposite();
        Direction modelFacing = context.getHorizontalDirection().getOpposite();
        if (context.getClickedFace().getAxis().isHorizontal()) {
            Direction side = context.getClickedFace();
            if (modelFacing.getAxis() == side.getAxis()) {
                modelFacing = side.getClockWise();
            }
            Direction localEastWorld = modelFacing.getClockWise();
            mount = localEastWorld == side.getOpposite() ? MountType.SIDE_LEFT : MountType.SIDE_RIGHT;
            beam = modelFacing;
        }
        int rotation = 0;
        if (mount != MountType.SIDE_LEFT && mount != MountType.SIDE_RIGHT) {
            int step = Math.floorMod(Math.round(context.getRotation() / ROTATION_STEP), 16);
            modelFacing = Direction.fromYRot((step / 4) * 90 + 180);
            if (beam.getAxis().isHorizontal()) {
                beam = modelFacing;
            }
            rotation = step % 4;
        }
        return defaultBlockState().setValue(FACING, beam).setValue(MOUNT, mount)
            .setValue(MODEL_FACING, modelFacing).setValue(ROTATION, rotation);
    }

    /**
     * Mount is chosen from the clicked face and never re-evaluated: top-face clicks use the tripod
     * appearance only when placed on a pole, bottom-face clicks hang, and side clicks use a side model.
     */
    private static MountType mountFor(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (context.getClickedFace() == Direction.UP) {
            return level.getBlockState(pos.below()).getBlock() instanceof PoleBlock
                ? MountType.TRIPOD : MountType.GROUND;
        }
        if (context.getClickedFace() == Direction.DOWN) {
            return MountType.HANG;
        }
        return MountType.SIDE_LEFT;
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        int rotation = state.getValue(ROTATION) + 1;
        if (rotation < 4) {
            return state.setValue(ROTATION, rotation);
        }
        Direction beam = state.getValue(FACING);
        Direction modelFacing = state.getValue(MODEL_FACING);
        MountType mount = state.getValue(MOUNT);
        if (mount == MountType.SIDE_LEFT || mount == MountType.SIDE_RIGHT) {
            if (beam == modelFacing) beam = Direction.UP;
            else if (beam == Direction.UP) beam = modelFacing.getOpposite();
            else if (beam == modelFacing.getOpposite()) beam = Direction.DOWN;
            else beam = modelFacing;
        } else if (beam.getAxis().isHorizontal()) {
            beam = beam.getClockWise();
            modelFacing = modelFacing.getClockWise();
        } else {
            modelFacing = modelFacing.getClockWise();
        }
        return state.setValue(FACING, beam).setValue(MODEL_FACING, modelFacing).setValue(ROTATION, 0);
    }

    private static boolean isSideMounted(BlockState state) {
        MountType mount = state.getValue(MOUNT);
        return mount == MountType.SIDE_LEFT || mount == MountType.SIDE_RIGHT;
    }

    private static Vec3 rotationAxis(BlockState state) {
        return Vec3.atLowerCornerOf((isSideMounted(state)
            ? state.getValue(MODEL_FACING).getClockWise() : Direction.UP).getNormal());
    }

    private static float rotationRadians(BlockState state) {
        return (float) Math.toRadians(state.getValue(ROTATION) * ROTATION_STEP)
            * (isSideMounted(state) ? 1 : -1);
    }

    /** Shared by the housing, beam, client light sources and range value box. */
    public static Vec3 rotateVector(BlockState state, Vec3 vector) {
        Vec3 axis = rotationAxis(state);
        double angle = rotationRadians(state);
        double cos = Math.cos(angle);
        return vector.scale(cos).add(axis.cross(vector).scale(Math.sin(angle)))
            .add(axis.scale(axis.dot(vector) * (1 - cos)));
    }

    public static Quaternionf modelRotation(BlockState state) {
        Vec3 axis = rotationAxis(state);
        return new Quaternionf().rotationAxis(rotationRadians(state), (float) axis.x, (float) axis.y, (float) axis.z);
    }

    public static Vec3 beamDirection(BlockState state) {
        return rotateVector(state, Vec3.atLowerCornerOf(state.getValue(FACING).getNormal()));
    }

    public static ClipContext beamClipContext(BlockPos source, Vec3 start, Vec3 end) {
        return new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
            (net.minecraft.world.entity.Entity) null) {
            @Override
            public VoxelShape getBlockShape(BlockState state, BlockGetter level, BlockPos pos) {
                // Exclude the housing (whose rotated outline can extend in front of its lens) and
                // every light block, whose outline can depend on the held-item context. Everything
                // else uses its outline so selectable plants and other non-colliding blocks stop the ray.
                return pos.equals(source) || state.is(net.minecraft.world.level.block.Blocks.LIGHT)
                    ? Shapes.empty() : super.getBlockShape(state, level, pos);
            }
        };
    }

    @Override
    public Class<SpotlightBlockEntity> getBlockEntityClass() {
        return SpotlightBlockEntity.class;
    }
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = switch (state.getValue(FACING)) {
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case EAST -> SHAPE_EAST;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
        };
        if (state.getValue(ROTATION) == 0) {
            return shape;
        }
        // A voxel shape is axis aligned: enclose each rotated box for collision and selection.
        VoxelShape[] rotated = { Shapes.empty() };
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
            double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            for (int corner = 0; corner < 8; corner++) {
                Vec3 p = rotateVector(state, new Vec3((corner & 1) == 0 ? x0 - .5 : x1 - .5,
                    (corner & 2) == 0 ? y0 - .5 : y1 - .5, (corner & 4) == 0 ? z0 - .5 : z1 - .5));
                minX = Math.min(minX, p.x + .5); maxX = Math.max(maxX, p.x + .5);
                minY = Math.min(minY, p.y + .5); maxY = Math.max(maxY, p.y + .5);
                minZ = Math.min(minZ, p.z + .5); maxZ = Math.max(maxZ, p.z + .5);
            }
            rotated[0] = Shapes.or(rotated[0], Shapes.box(minX, minY, minZ, maxX, maxY, maxZ));
        });
        return rotated[0];
    }
    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(ROTATION) != 0 && (isSideMounted(state) || state.getValue(FACING).getAxis().isHorizontal())) {
            return Shapes.empty(); // an angled tail no longer lies on a whole block face
        }
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
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.getBlockEntity(pos) instanceof SpotlightBlockEntity be) {
            int color = -1;
            if (stack.getItem() instanceof DyeItem dye) {
                float[] rgb = dye.getDyeColor().getTextureDiffuseColors();
                color = ((int) (rgb[0] * 255) << 16) | ((int) (rgb[1] * 255) << 8)
                    | (int) (rgb[2] * 255);
            } else if (stack.is(Items.AMETHYST_SHARD)) {
                color = SpotlightBlockEntity.DEFAULT_COLOR;
            }

            if (color != -1) {
                if (!level.isClientSide && color != be.getLaserColor()) {
                    be.setLaserColor(color);
                    level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 0.3f, 1f);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public enum MountType implements StringRepresentable {
        TRIPOD("tripod"),
        GROUND("ground"),
        HANG("hang"),
        SIDE_LEFT("side_left"),
        SIDE_RIGHT("side_right");

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
