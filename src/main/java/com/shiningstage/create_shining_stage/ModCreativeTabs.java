package com.shiningstage.create_shining_stage;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateShiningStage.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = CREATIVE_TABS.register("main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.create_shining_stage"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> new ItemStack(ModBlocks.SPOTLIGHT.get()))
            .displayItems((params, output) -> {
                output.accept(new ItemStack(ModBlocks.SPOTLIGHT_ITEM.get()));
                output.accept(new ItemStack(ModBlocks.TRIPOD_ITEM.get()));
                output.accept(new ItemStack(ModBlocks.MICROPHONE_ITEM.get()));
                output.accept(new ItemStack(ModBlocks.SPEAKER_ITEM.get()));
                output.accept(new ItemStack(ModBlocks.TRUSS_ITEM.get()));
                // The three truss-encased forms stay out of the tab on purpose. They are not separate
                // content, they are what a shaft/cogwheel becomes when a truss is clipped over it, and
                // they are not obtainable any other way: loot drops the bare part and pick-block is the
                // only thing that ever asks for their item. Create lists its own encased variants here
                // (AllCreativeModeTabs), so this is a deliberate difference, not an oversight - ours
                // draw the truss's model and would read as three duplicate truss icons in the tab.
                output.accept(new ItemStack(ModBlocks.POSITION_ZERO_ITEM.get()));
                output.accept(new ItemStack(ModBlocks.COLD_SPARK_MACHINE_ITEM.get()));
                output.accept(new ItemStack(ModItems.COLD_SPARK_FUEL.get()));
            })
            .build());
}
