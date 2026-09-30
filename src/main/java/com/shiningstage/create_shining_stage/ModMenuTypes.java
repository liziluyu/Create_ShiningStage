package com.shiningstage.create_shining_stage;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, CreateShiningStage.MOD_ID);

    /**
     * Registered through {@code IMenuTypeExtension}, not with a bare {@code MenuType}: the machine's
     * menu needs its block entity on the client as well as on the server, and the extra-data factory is
     * what carries it there (see {@code ColdSparkMachineBlockEntity#createMenu}).
     */
    public static final DeferredHolder<MenuType<?>, MenuType<ColdSparkMachineMenu>> COLD_SPARK_MACHINE =
        MENU_TYPES.register("cold_spark_machine",
            () -> IMenuTypeExtension.create(ColdSparkMachineMenu::new));
}
