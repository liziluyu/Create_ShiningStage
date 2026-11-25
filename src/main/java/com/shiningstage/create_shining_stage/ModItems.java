package com.shiningstage.create_shining_stage;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.fml.ModList;

/** Plain items; block items stay next to their blocks in {@link ModBlocks}. */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, CreateShiningStage.MOD_ID);

    /** The cold spark machine's only accepted item, in and out of its single slot. */
    public static final Supplier<Item> COLD_SPARK_FUEL =
        ITEMS.register("cold_spark_fuel", () -> new Item(new Item.Properties()));

    /** Saved AE2 facade stacks carry their orientation; intentionally absent from the creative tab. */
    public static final Supplier<Item> TRUSS_FACADE = ITEMS.register("truss_facade", () ->
        ModList.get().isLoaded("ae2")
            ? Ae2TrussFacade.createFacadeItem(new Item.Properties())
            : new Item(new Item.Properties()));
}
