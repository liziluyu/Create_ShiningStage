package com.shiningstage.create_shining_stage.client.beam;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/** Native Forge shader render type with float-valued beam data carried in UV0. */
@Mod.EventBusSubscriber(modid = "create_shining_stage", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BeamRenderTypes extends RenderType {
    public static final RenderType BEAM = create("create_shining_stage:spotlight_beam",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(BeamShaders::beam))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(PARTICLES_TARGET)
                    .createCompositeState(false));

    private BeamRenderTypes() {
        super("create_shining_stage:beam_state", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 1536, false, true, () -> {}, () -> {});
    }
}
