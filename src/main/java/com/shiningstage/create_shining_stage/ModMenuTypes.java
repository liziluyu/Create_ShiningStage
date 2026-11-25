package com.shiningstage.create_shining_stage;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, CreateShiningStage.MOD_ID);

    /**
     * Registered through {@code IForgeMenuType}, not with a bare {@code MenuType}: the machine's
     * menu needs its block entity on the client as well as on the server, and the extra-data factory is
     * what carries it there (see {@code ColdSparkMachineBlockEntity#createMenu}).
     */
    public static final RegistryObject<MenuType<ColdSparkMachineMenu>> COLD_SPARK_MACHINE =
        MENU_TYPES.register("cold_spark_machine",
            () -> IForgeMenuType.create(ColdSparkMachineMenu::new));
}
