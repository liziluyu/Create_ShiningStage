package com.shiningstage.create_shining_stage;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CreateShiningStage.MOD_ID, dist = Dist.CLIENT)
public class CreateShiningStageClient {
    public CreateShiningStageClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerRenderers);
        modEventBus.addListener(this::registerMenuScreens);
        // Create's own binding outlines are refreshed on the client tick (Create's ClientEvents), and
        // the game bus has no mod-event-bus equivalent to register them on.
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
            event -> SpeakerBindingOutliner.tick());
    }

    private void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.COLD_SPARK_MACHINE.get(), ColdSparkMachineScreen::new);
    }

    private void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntityTypes.SPOTLIGHT.get(), SpotlightRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.COLD_SPARK_MACHINE.get(), ColdSparkMachineRenderer::new);
    }
}
