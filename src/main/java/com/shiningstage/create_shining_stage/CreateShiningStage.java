package com.shiningstage.create_shining_stage;

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CreateShiningStage.MOD_ID)
public final class CreateShiningStage {
    public static final String MOD_ID = "create_shining_stage";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateShiningStage(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModBlocks.ITEMS.register(bus);
        ModItems.ITEMS.register(bus);
        ModBlockEntityTypes.BLOCK_ENTITIES.register(bus);
        ModMenuTypes.MENU_TYPES.register(bus);
        ModCreativeTabs.CREATIVE_TABS.register(bus);
        StageNetwork.register();
        bus.addListener(this::commonSetup);
        bus.addListener(this::gatherData);
        bus.addListener(PositionZeroItemPolicy::onConfigLoading);
        bus.addListener(PositionZeroItemPolicy::onConfigReloading);
        bus.addListener(StageLightingSettings::onConfigLoading);
        bus.addListener(StageLightingSettings::onConfigReloading);
        MinecraftForge.EVENT_BUS.addListener(SoundRelayHandler::onAtPosition);
        MinecraftForge.EVENT_BUS.addListener(SoundRelayHandler::onAtEntity);
        MinecraftForge.EVENT_BUS.addListener(StageRedstoneNetwork::onNeighborNotify);
        MinecraftForge.EVENT_BUS.addListener(StageRedstoneNetwork::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(StageRedstoneNetwork::onChunkUnload);
        MinecraftForge.EVENT_BUS.addListener(StageRedstoneNetwork::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(PositionZeroItemPolicy::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(StageLightingSettings::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.level.LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                SoundRelayHandler.clearDimension(level.dimension());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent event) ->
            SoundRelayHandler.clearAll());
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            TrussBlock.registerEncasing();
            TrussBlock.addValidBlocks();
            MovementBehaviour.REGISTRY.register(ModBlocks.MICROPHONE.get(), new MicrophoneMovementBehaviour());
            MovementBehaviour.REGISTRY.register(ModBlocks.SPEAKER.get(), new SpeakerMovementBehaviour());
        });
        LOGGER.info("Create Shining Stage loaded on Forge 1.20.1");
    }

    private void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
            new ShiningStageRecipeProvider(event.getGenerator().getPackOutput()));
        event.getGenerator().addProvider(event.includeClient(),
            new PoleModelProvider(event.getGenerator().getPackOutput(), event.getExistingFileHelper()));
    }
}
