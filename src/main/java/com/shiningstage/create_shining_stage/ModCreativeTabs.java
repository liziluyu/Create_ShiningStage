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
            .displayItems((params, output) -> output.accept(new ItemStack(ModBlocks.SPOTLIGHT_ITEM.get())))
            .build());
}
