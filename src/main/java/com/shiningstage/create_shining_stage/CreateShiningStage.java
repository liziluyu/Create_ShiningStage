package com.shiningstage.create_shining_stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@Mod(CreateShiningStage.MOD_ID)
public class CreateShiningStage {
    public static final String MOD_ID = "create_shining_stage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public CreateShiningStage(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModBlockEntityTypes.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::gatherData);
        NeoForge.EVENT_BUS.addListener(SoundRelayHandler::onAtPosition);
        NeoForge.EVENT_BUS.addListener(SoundRelayHandler::onAtEntity);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Contraption actors: a mounted microphone or speaker keeps relaying while its structure
            // moves, with no block entity and no redstone involved (see SoundRelayHandler).
            MovementBehaviour.REGISTRY.register(ModBlocks.MICROPHONE.get(), new MicrophoneMovementBehaviour());
            MovementBehaviour.REGISTRY.register(ModBlocks.SPEAKER.get(), new SpeakerMovementBehaviour());
        });
        LOGGER.info("Create Shining Stage loaded");
    }

    // runData only: recipes are generated into src/generated/resources, which build.gradle puts on the
    // resource path, so the generated files are what ships.
    private void gatherData(final GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
            new ShiningStageRecipeProvider(event.getGenerator().getPackOutput(), event.getLookupProvider()));
    }
}
