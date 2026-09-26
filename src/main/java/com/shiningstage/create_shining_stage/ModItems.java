package com.shiningstage.create_shining_stage;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Plain items; block items stay next to their blocks in {@link ModBlocks}. */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, CreateShiningStage.MOD_ID);

    /** The cold spark machine's only accepted item, in and out of its single slot. */
    public static final Supplier<Item> COLD_SPARK_FUEL =
        ITEMS.register("cold_spark_fuel", () -> new Item(new Item.Properties()));
}
