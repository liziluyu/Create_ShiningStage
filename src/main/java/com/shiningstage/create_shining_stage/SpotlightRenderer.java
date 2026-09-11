package com.shiningstage.create_shining_stage;

import java.awt.Color;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.util.SableDistUtil;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class SpotlightRenderer implements BlockEntityRenderer<SpotlightBlockEntity> {
    /** Divergence of the frustum; bottom half-size = top + span * HALF_ANGLE_TAN. */
    static final float HALF_ANGLE_TAN = 0.3f;
    /** Half-size of the top (emitter) face; matches the laser pointer's 0.48 cross-section. */
    static final float TOP_HALF = 0.24f;
    /** Alpha at the emitter end (most opaque), scaled by the redstone control. */
    static final float MAX_ALPHA = 0.5f;
    /** Alpha at the far end (most transparent), scaled by the redstone control. */
    static final float MIN_ALPHA = 0.3f;
    /** Length of the transparent fade-out at the beam tip (matches the laser pointer). */
    static final float END_FADE = 0.5f;

    private static final RenderType BEAM = RenderType.create(
        "create_shining_stage:spotlight_beam",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        256, false, true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderStateShard.CULL)
            // Never write depth: crossing translucent beams would depth-cull each other.
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

    public SpotlightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(SpotlightBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(SpotlightBlockEntity be) {
        Vec3i normal = be.getBlockState().getValue(DirectionalBlock.FACING).getNormal();
        return new AABB(be.getBlockPos())
            .expandTowards(Vec3.atLowerCornerOf(normal).scale(SpotlightBlockEntity.MAX_RANGE))
            .inflate(TOP_HALF + SpotlightBlockEntity.MAX_RANGE * HALF_ANGLE_TAN);
    }

    @Override
    public void render(SpotlightBlockEntity be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }

        BlockPos pos = be.getBlockPos();
        Direction dir = be.getBlockState().getValue(DirectionalBlock.FACING);

        // Redstone can't be read per-face; a single signal drives both convergence and
        // opacity together. 0 = widest frustum + transparent, 15 = prism + full opacity.
        float control = level.getBestNeighborSignal(pos) / 15f;
        if (control <= 0f) {
            return;
        }

        Vec3i normal = dir.getNormal();
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 start = center.add(Vec3.atLowerCornerOf(normal).scale(0.5));
        Vec3 end = start.add(Vec3.atLowerCornerOf(normal).scale(be.getRange()));
        BlockHitResult hit = level.clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));

        // A hit in another physical space (ship/sub-level) sits at far-away coordinates, so a
        // vanilla distanceTo explodes and the beam looks infinite. Sable maps both points into a
        // common space for the true distance, exactly like the laser pointer does.
        double dist = hit.getType() == HitResult.Type.MISS
            ? end.distanceTo(center)
            : Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(
                SableDistUtil.getClientLevel(), center, hit.getLocation()));
        float beamLength = Math.min((float) (dist - 0.05), be.getRange());
        if (beamLength <= 0.1f) {
            return;
        }

        Color color = new Color(be.getLaserColor());
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        float span = beamLength - 0.5f;
        float bottomHalf = TOP_HALF + span * HALF_ANGLE_TAN * (1f - control);
        // Fade the last END_FADE blocks out to fully transparent, like the laser pointer's tip.
        float fadeStart = Math.max(0f, span - END_FADE);
        float midHalf = TOP_HALF + fadeStart * HALF_ANGLE_TAN * (1f - control);

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(dir.getRotation());
        ms.mulPose(Axis.XP.rotationDegrees(-90f));
        // Local +Z now points along `dir`; origin at block center.
        ms.translate(0, 0, 0.5); // top face sits at the block face

        float[][] top = { {-TOP_HALF, -TOP_HALF}, {TOP_HALF, -TOP_HALF}, {TOP_HALF, TOP_HALF}, {-TOP_HALF, TOP_HALF} };
        float[][] mid = { {-midHalf, -midHalf}, {midHalf, -midHalf}, {midHalf, midHalf}, {-midHalf, midHalf} };
        float[][] bottom = { {-bottomHalf, -bottomHalf}, {bottomHalf, -bottomHalf}, {bottomHalf, bottomHalf}, {-bottomHalf, bottomHalf} };

        VertexConsumer vc = buffer.getBuffer(BEAM);
        Matrix4f mat = ms.last().pose();
        float topAlpha = control * MAX_ALPHA;
        float bottomAlpha = control * MIN_ALPHA;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            // Main segment: emitter -> fade start, with the redstone-driven alpha gradient.
            vertex(vc, mat, top[i][0], top[i][1], 0, r, g, b, topAlpha);
            vertex(vc, mat, top[j][0], top[j][1], 0, r, g, b, topAlpha);
            vertex(vc, mat, mid[j][0], mid[j][1], fadeStart, r, g, b, bottomAlpha);
            vertex(vc, mat, mid[i][0], mid[i][1], fadeStart, r, g, b, bottomAlpha);
            // Taper segment: fade start -> tip, fading out to transparent.
            vertex(vc, mat, mid[i][0], mid[i][1], fadeStart, r, g, b, bottomAlpha);
            vertex(vc, mat, mid[j][0], mid[j][1], fadeStart, r, g, b, bottomAlpha);
            vertex(vc, mat, bottom[j][0], bottom[j][1], span, r, g, b, 0f);
            vertex(vc, mat, bottom[i][0], bottom[i][1], span, r, g, b, 0f);
        }
        ms.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                               float r, float g, float b, float a) {
        vc.addVertex(mat, x, y, z).setColor(r, g, b, a);
    }
}
