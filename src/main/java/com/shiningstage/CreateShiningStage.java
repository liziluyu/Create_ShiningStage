package com.shiningstage;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CreateShiningStage.MOD_ID)
public final class CreateShiningStage {
    public static final String MOD_ID = "create_shining_stage";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateShiningStage(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Create Shining Stage initialized on Minecraft 1.20.1 Forge");
    }
}
