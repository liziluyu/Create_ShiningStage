package com.shiningstage.create_shining_stage;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = CreateShiningStage.MOD_ID, dist = Dist.CLIENT)
public class CreateShiningStageClient {
    public CreateShiningStageClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerRenderers);
    }

    private void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntityTypes.SPOTLIGHT.get(), SpotlightRenderer::new);
    }
}
