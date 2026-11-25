package com.shiningstage.create_shining_stage.client.beam;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "create_shining_stage", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BeamShaders {
    private static ShaderInstance beam;
    private static ShaderInstance coldSpark;
    private BeamShaders() {}
    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new BeamShaderInstance(event.getResourceProvider(),
                new ResourceLocation("create_shining_stage", "spotlight_beam"),
                DefaultVertexFormat.POSITION_TEX_COLOR), shader -> beam = shader);
        event.registerShader(new BeamShaderInstance(event.getResourceProvider(),
                new ResourceLocation("create_shining_stage", "cold_spark"),
                DefaultVertexFormat.POSITION_TEX_COLOR), shader -> coldSpark = shader);
    }
    public static ShaderInstance beam() { return beam; }
    public static ShaderInstance coldSpark() { return coldSpark; }
}
