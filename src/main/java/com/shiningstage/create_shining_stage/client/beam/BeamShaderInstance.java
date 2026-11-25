package com.shiningstage.create_shining_stage.client.beam;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.opengl.GL30;

/** Shader instance with a per-draw, optional Iris target route. */
final class BeamShaderInstance extends ShaderInstance {
    private int routeFramebuffer;
    private int previousReadFramebuffer;
    private int previousDrawFramebuffer;
    private boolean routed;

    BeamShaderInstance(ResourceProvider resources, ResourceLocation shaderLocation, VertexFormat vertexFormat)
            throws IOException {
        super(resources, shaderLocation, vertexFormat);
    }

    @Override
    public void apply() {
        super.apply();
        OculusCompat.apply(this);
    }

    @Override
    public void clear() {
        try {
            super.clear();
        } finally {
            OculusCompat.restore(this);
        }
    }

    @Override
    public void close() {
        try {
            OculusCompat.restore(this);
        } finally {
            try {
                super.close();
            } finally {
                if (routeFramebuffer != 0) {
                    GlStateManager._glDeleteFramebuffers(routeFramebuffer);
                    routeFramebuffer = 0;
                }
            }
        }
    }

    int routeFramebuffer() {
        if (routeFramebuffer == 0) {
            routeFramebuffer = GlStateManager.glGenFramebuffers();
        }
        return routeFramebuffer;
    }

    boolean isRouted() {
        return routed;
    }

    void beginRoute(int readFramebuffer, int drawFramebuffer) {
        previousReadFramebuffer = readFramebuffer;
        previousDrawFramebuffer = drawFramebuffer;
        routed = true;
    }

    int previousReadFramebuffer() {
        return previousReadFramebuffer;
    }

    int previousDrawFramebuffer() {
        return previousDrawFramebuffer;
    }

    void recoverFramebufferBindings() {
        if (routed) {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            routed = false;
        }
    }

    void endRoute() {
        routed = false;
    }
}
