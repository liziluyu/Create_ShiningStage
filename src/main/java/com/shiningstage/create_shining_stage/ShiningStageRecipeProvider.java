package com.shiningstage.create_shining_stage;

import java.util.concurrent.CompletableFuture;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.data.recipe.MechanicalCraftingRecipeBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;

/**
 * Recipes for the whole stage set. The hardware items are plated in Create's sturdy sheet: the set
 * is stage equipment, and the sheet keeps the one recipe-shaped tie to Create's progression
 * (obsidian dust, lava, two pressings) instead of competing with its own material ladder. Everything
 * else is the part that particular item is: glass for the lens, a note block for the speaker,
 * amethyst for the microphone capsule, andesite alloy for the framing. The floor marking is the one
 * exception — it is paper tape, not hardware.
 *
 * <p>Runs under {@code runData}; the output lands in {@code src/generated/resources}. Datagen rather
 * than hand-written JSON because every ingredient is then a compile-checked Create/vanilla constant,
 * and because {@link MechanicalCraftingRecipeBuilder} is the only sane way to author the spotlight's
 * mechanical crafting pattern.
 */
public class ShiningStageRecipeProvider extends RecipeProvider {
    /**
     * Create's own brass sheet tag ({@code c:plates/brass}), the convention its electron tube recipe
     * uses for iron. NeoForge ships no constant for the plate tags Create declares itself.
     */
    private static final TagKey<Item> BRASS_PLATES =
        ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "plates/brass"));

    public ShiningStageRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        spotlight(output);
        tripod(output);
        microphone(output);
        speaker(output);
        truss(output);
        positionZero(output);
    }

    /**
     * A sheet-metal housing around the lens, a brass casing for the body and two electron tubes for the
     * redstone-side control. Mechanical crafting (3x3 crafters) rather than a crafting table: the set's
     * flagship is the one item worth the Create-specific gate, and it keeps the pattern readable.
     */
    private void spotlight(RecipeOutput output) {
        MechanicalCraftingRecipeBuilder.shapedRecipe(ModBlocks.SPOTLIGHT_ITEM.get())
            .key('S', AllItems.STURDY_SHEET)
            .key('G', Items.GLASS)
            .key('T', AllItems.ELECTRON_TUBE)
            .key('C', AllBlocks.BRASS_CASING)
            .patternLine(" S ")
            .patternLine("SGS")
            .patternLine("TCT")
            .build(output, id("mechanical_crafting/spotlight"));
    }

    /** Three sheets for the legs and plate, andesite alloy for the joint. Two per craft: a spotlight stands on a stack. */
    private void tripod(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.TRIPOD_ITEM.get(), 2)
            .define('S', AllItems.STURDY_SHEET)
            .define('A', AllItems.ANDESITE_ALLOY)
            .pattern(" S ")
            .pattern("SAS")
            .unlockedBy("has_sturdy_sheet", has(AllItems.STURDY_SHEET))
            .save(output, id("crafting/tripod"));
    }

    /**
     * Amethyst capsule on a brass boom, tube preamp in a sheet-metal body. No stand of its own: the
     * microphone only places on a tripod (MicrophoneBlock.getStateForPlacement), so the tripod is the
     * stand's recipe.
     */
    private void microphone(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.MICROPHONE_ITEM.get())
            .define('A', Tags.Items.GEMS_AMETHYST)
            .define('S', AllItems.STURDY_SHEET)
            .define('B', BRASS_PLATES)
            .define('E', AllItems.ELECTRON_TUBE)
            .pattern(" A ")
            .pattern("SBS")
            .pattern(" E ")
            .unlockedBy("has_sturdy_sheet", has(AllItems.STURDY_SHEET))
            .unlockedBy("has_amethyst", has(Tags.Items.GEMS_AMETHYST))
            .save(output, id("crafting/microphone"));
    }

    /** Note block driver in a sheet-metal cabinet, tube amp on top. */
    private void speaker(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.SPEAKER_ITEM.get())
            .define('S', AllItems.STURDY_SHEET)
            .define('E', AllItems.ELECTRON_TUBE)
            .define('N', Items.NOTE_BLOCK)
            .pattern("SES")
            .pattern(" N ")
            .unlockedBy("has_sturdy_sheet", has(AllItems.STURDY_SHEET))
            .unlockedBy("has_note_block", has(Items.NOTE_BLOCK))
            .save(output, id("crafting/speaker"));
    }

    /** Framing gets built in walls, so it comes four at a time; andesite alloy is the lattice, sheets the plates. */
    private void truss(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.TRUSS_ITEM.get(), 4)
            .define('S', AllItems.STURDY_SHEET)
            .define('A', AllItems.ANDESITE_ALLOY)
            .pattern("SAS")
            .unlockedBy("has_sturdy_sheet", has(AllItems.STURDY_SHEET))
            .save(output, id("crafting/truss"));
    }

    /** Paper tape with a painted stripe: the marking is a decal, not a structure, so it costs no sheeting. */
    private void positionZero(RecipeOutput output) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.POSITION_ZERO_ITEM.get(), 2)
            .requires(Items.PAPER)
            .requires(Items.PINK_DYE)
            .unlockedBy("has_paper", has(Items.PAPER))
            .save(output, id("crafting/position_zero"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateShiningStage.MOD_ID, path);
    }
}
