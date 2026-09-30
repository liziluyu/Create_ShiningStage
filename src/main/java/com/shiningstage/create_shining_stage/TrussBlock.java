package com.shiningstage.create_shining_stage;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

/** Decorative stage frame: no block entity, no behaviours. Its tooltip is the only description. */
public class TrussBlock extends Block {
    public static final MapCodec<TrussBlock> CODEC = simpleCodec(TrussBlock::new);

    /**
     * Which face this truss is covering, or {@link FacadeSide#NONE} for a truss standing in the world.
     *
     * <p>It exists for one reason and has no gameplay meaning: AE2 draws a facade by <em>clamping the
     * source block's model into a 1/16 slab</em> on the covered face, and the truss model is a shell
     * whose six planes sit on the cell boundaries. The plane on the boundary <em>opposite</em> the
     * covered face therefore lands on the slab's inner surface as a second, full-size face - seen
     * through the transparent middle of the truss texture, which reads as a doubled frame. The only
     * way to drop it is to hand AE2 a model without it, and selecting that model needs a state that
     * says which face was covered. The six variants live in the blockstate JSON, each one the truss
     * model minus the plane on the far side; see {@link #FACADE_SIDE_MODELS}.
     *
     * <p>{@link FacadeSide#NONE} must stay first: it is the value of the default state, which is what
     * placing a truss in the world produces and what the encasing registry expects.
     */
    public static final EnumProperty<FacadeSide> FACADE_SIDE =
        EnumProperty.create("facade_side", FacadeSide.class);

    private static final String TOOLTIP_1 = "block.create_shining_stage.truss.tooltip.1";
    private static final String TOOLTIP_2 = "block.create_shining_stage.truss.tooltip.2";
    private static final String TOOLTIP_3 = "block.create_shining_stage.truss.tooltip.3";

    public TrussBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACADE_SIDE);
    }

    /**
     * The truss state AE2 should use as its facade source for a cover on {@code side}: the truss
     * itself, wearing the state whose model lacks the plane that would otherwise be clamped onto the
     * back of the cover.
     */
    public static BlockState facadeSource(Direction side) {
        return ModBlocks.TRUSS.get().defaultBlockState()
            .setValue(FACADE_SIDE, FacadeSide.of(side));
    }

    /** {@link #FACADE_SIDE}'s values; the serialized names are the lowercase enum names. */
    public enum FacadeSide implements StringRepresentable {
        NONE(null),
        UP(Direction.UP),
        DOWN(Direction.DOWN),
        NORTH(Direction.NORTH),
        SOUTH(Direction.SOUTH),
        WEST(Direction.WEST),
        EAST(Direction.EAST);

        private final Direction side;

        FacadeSide(Direction side) {
            this.side = side;
        }

        /** The covered face, i.e. where the facade's slab sits - null for {@link #NONE}. */
        public Direction side() {
            return side;
        }

        /** The value covering {@code side}. Facades are only ever made on a real face of a block. */
        public static FacadeSide of(Direction side) {
            for (FacadeSide value : values()) {
                if (value.side == side) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Not a block face: " + side);
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // Called by BlockItem.appendHoverText, so the lines show on the item form only.
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable(TOOLTIP_1));
        tooltip.add(Component.translatable(TOOLTIP_2));
        tooltip.add(Component.translatable(TOOLTIP_3));
    }

    // --- The truss as a casing for Create's shafts and cogwheels ---------------------------------
    //
    // Two registries have to learn about the pairing, and they belong to different owners, so they
    // are wired in two different events. The blocks themselves are plain instances of Create's
    // EncasedShaftBlock / EncasedCogwheelBlock (see ModBlocks): they read their casing from the
    // EncasedBlock#getCasing() they were constructed with, so the only thing that can be missing is
    // Create's knowledge of which held item encases which block, and NeoForge's knowledge of which
    // blocks live in which block entity type.

    /**
     * Create resolves "right-click a shaft with item X" through {@link EncasingRegistry}, a plain
     * static map that must not be touched before block registration has finished - and this is the
     * first hook after it. Missing an entry here is silent: the truss simply never encases anything.
     */
    public static void registerEncasing(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            EncasingRegistry.addVariant(AllBlocks.SHAFT.get(), ModBlocks.TRUSS_ENCASED_SHAFT.get());
            EncasingRegistry.addVariant(AllBlocks.COGWHEEL.get(), ModBlocks.TRUSS_ENCASED_COGWHEEL.get());
            EncasingRegistry.addVariant(AllBlocks.LARGE_COGWHEEL.get(),
                ModBlocks.TRUSS_ENCASED_LARGE_COGWHEEL.get());
        });
    }

    /**
     * The encased variants reuse Create's block entity types, which is what makes their kinetic
     * behaviour and rendering Create's own with no code here at all. A block entity type only knows
     * the blocks it was registered with, though, and that set is load-bearing rather than cosmetic:
     * {@code BlockEntity#isValidBlockState} is asked on every chunk load, and a block missing from it
     * has its block entity thrown away and rebuilt from scratch. NeoForge's event is the supported
     * way to extend the set, and it checks that the block shares a superclass with the existing ones.
     */
    public static void addValidBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(AllBlockEntityTypes.ENCASED_SHAFT.get(), ModBlocks.TRUSS_ENCASED_SHAFT.get());
        event.modify(AllBlockEntityTypes.ENCASED_COGWHEEL.get(), ModBlocks.TRUSS_ENCASED_COGWHEEL.get());
        event.modify(AllBlockEntityTypes.ENCASED_LARGE_COGWHEEL.get(), ModBlocks.TRUSS_ENCASED_LARGE_COGWHEEL.get());
    }
}
