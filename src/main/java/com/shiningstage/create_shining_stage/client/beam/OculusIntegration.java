package com.shiningstage.create_shining_stage.client.beam;

import com.mojang.blaze3d.platform.GlStateManager;
import java.lang.reflect.Field;
import java.util.Set;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.DepthColorStorage;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Oculus 1.8.0 target bridge. Oculus exposes no public binding API for this route. */
final class OculusIntegration {
    private static final int COLOR_ATTACHMENT_0 = GL30.GL_COLOR_ATTACHMENT0;
    private static final Field RENDER_TARGETS_FIELD = findRenderTargetsField();

    private OculusIntegration() {
    }

    static void apply(BeamShaderInstance shader) {
        if (shader.isRouted()) {
            return;
        }
        WorldRenderingPipeline worldPipeline = Iris.getPipelineManager().getPipelineNullable();
        if (!(worldPipeline instanceof IrisRenderingPipeline pipeline)
                || !pipeline.shouldOverrideShaders()
                || ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return;
        }

        RenderTargets targets = renderTargets(pipeline);
        if (targets == null) {
            return;
        }

        // The renderer stage determines which side of Iris' double-buffered color target
        // is current. Keep this phase check local so future callers at another stage can reuse it.
        Set<Integer> flipped = pipeline.isBeforeTranslucent
                ? pipeline.getFlippedAfterPrepare()
                : pipeline.getFlippedAfterTranslucent();
        RenderTarget target = targets.get(0);
        if (target == null) {
            return;
        }
        int colorTexture = flipped.contains(0) ? target.getAltTexture() : target.getMainTexture();
        int depthTexture = targets.getDepthTexture();
        if (colorTexture == 0 || depthTexture == 0 || target.getWidth() <= 0 || target.getHeight() <= 0) {
            return;
        }

        int previousRead = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean routeStarted = false;
        try {
            int framebuffer = shader.routeFramebuffer();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, COLOR_ATTACHMENT_0,
                    GL11.GL_TEXTURE_2D, colorTexture, 0);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, depthTexture, 0);
            // Keep the beam isolated to colortex0; no other shader-pack gbuffer is touched.
            GL11.glDrawBuffer(COLOR_ATTACHMENT_0);
            int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                OculusCompat.warnOnce("Oculus beam target framebuffer is incomplete (status 0x"
                        + Integer.toHexString(status) + "); Oculus routing is disabled");
                return;
            }

            shader.beginRoute(previousRead, previousDraw);
            routeStarted = true;
            // MixinShaderInstance disables both masks for ordinary ShaderInstance objects.
            // Unlock only after the private target is ready; Iris restores the RenderType masks.
            if (DepthColorStorage.isDepthColorLocked()) {
                DepthColorStorage.unlockDepthColor();
            }
        } finally {
            if (!routeStarted) {
                restoreFramebuffers(previousRead, previousDraw);
            }
        }
    }

    static void restore(BeamShaderInstance shader) {
        if (!shader.isRouted()) {
            return;
        }
        int read = shader.previousReadFramebuffer();
        int draw = shader.previousDrawFramebuffer();
        restoreFramebuffers(read, draw);
        shader.endRoute();
    }

    private static void restoreFramebuffers(int read, int draw) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
    }

    private static Field findRenderTargetsField() {
        try {
            Field field = IrisRenderingPipeline.class.getDeclaredField("renderTargets");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            OculusCompat.warnOnce(exception);
            return null;
        }
    }

    private static RenderTargets renderTargets(IrisRenderingPipeline pipeline) {
        if (RENDER_TARGETS_FIELD == null) {
            return null;
        }
        try {
            return (RenderTargets) RENDER_TARGETS_FIELD.get(pipeline);
        } catch (IllegalAccessException | RuntimeException exception) {
            OculusCompat.warnOnce(exception);
            return null;
        }
    }
}
