package com.shiningstage.create_shining_stage;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge client-only lifecycle and renderer registration. */
@Mod.EventBusSubscriber(modid = CreateShiningStage.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CreateShiningStageClient {
    private CreateShiningStageClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntityTypes.SPOTLIGHT.get(), SpotlightRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.COLD_SPARK_MACHINE.get(), ColdSparkMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.POSITION_ZERO.get(), PositionZeroRenderer::new);
    }

    @SubscribeEvent
    public static void registerMenuScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.COLD_SPARK_MACHINE.get(), ColdSparkMachineScreen::new));
    }

    @Mod.EventBusSubscriber(modid = CreateShiningStage.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private ForgeEvents() {}
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                SpeakerBindingOutliner.tick();
                ClientStageLighting.tick();
                SpotlightRenderer.tickOcclusionCache();
            }
        }
        @SubscribeEvent public static void chunkLoad(ChunkEvent.Load event) {
            if (event.getLevel() instanceof ClientLevel level) ClientStageLighting.chunkChanged(level, event.getChunk().getPos());
        }
        @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload event) {
            if (event.getLevel() instanceof ClientLevel level) {
                ClientStageLighting.chunkChanged(level, event.getChunk().getPos());
                SpotlightRenderer.chunkUnloaded(level, event.getChunk().getPos());
            }
        }
        @SubscribeEvent public static void levelUnload(LevelEvent.Unload event) {
            if (event.getLevel() instanceof ClientLevel level) {
                ClientStageLighting.unload(level);
                SpotlightRenderer.clearOcclusionCache();
            }
        }
        @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            StageLightingSettings.resetClient();
            PositionZeroItemPolicy.resetClient();
            SpotlightRenderer.clearOcclusionCache();
        }
    }
}
