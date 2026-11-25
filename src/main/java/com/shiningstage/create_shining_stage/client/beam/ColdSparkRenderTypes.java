package com.shiningstage.create_shining_stage.client.beam;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class ColdSparkRenderTypes extends RenderType {
    public static final RenderType SPRAY = create("create_shining_stage:cold_spark",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(BeamShaders::coldSpark))
                    .setTextureState(new TextureStateShard(new ResourceLocation("create_shining_stage", "textures/particle/cold_spark.png"), false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(PARTICLES_TARGET)
                    .createCompositeState(false));
    private ColdSparkRenderTypes() { super("create_shining_stage:cold_spark_state", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {}); }
}
