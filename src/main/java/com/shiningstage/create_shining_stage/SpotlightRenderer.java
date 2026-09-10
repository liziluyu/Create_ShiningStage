package com.shiningstage.create_shining_stage;

import java.awt.Color;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class SpotlightRenderer implements BlockEntityRenderer<SpotlightBlockEntity> {
    private static final float MAX_RANGE = 16f;
    /** Divergence of the cone; base half-size = span * HALF_ANGLE_TAN. */
    private static final float HALF_ANGLE_TAN = 0.6f;

    private static final RenderType BEAM = RenderType.create(
        "create_shining_stage:spotlight_beam",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLES,
        256, false, false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

    public SpotlightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SpotlightBlockEntity be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }

        Direction dir = be.getBlockState().getValue(DirectionalBlock.FACING);
        Vec3i normal = dir.getNormal();
        Vec3 center = Vec3.atCenterOf(be.getBlockPos());

        Vec3 start = center.add(Vec3.atLowerCornerOf(normal).scale(0.5));
        Vec3 end = start.add(Vec3.atLowerCornerOf(normal).scale(MAX_RANGE));
        BlockHitResult hit = be.getLevel().clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));

        double dist = hit.getType() == HitResult.Type.MISS
            ? end.distanceTo(center)
            : hit.getLocation().distanceTo(center);
        float beamLength = (float) (dist - 0.05);
        if (beamLength <= 0.1f) {
            return;
        }

        Color color = new Color(be.getLaserColor());
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(dir.getRotation());
        ms.mulPose(Axis.XP.rotationDegrees(-90f));
        // Local +Z now points along `dir`; origin at block center.
        ms.translate(0, 0, 0.5); // apex at the block face

        float span = beamLength - 0.5f;
        float half = span * HALF_ANGLE_TAN;
        float[][] corners = { {-half, -half}, {half, -half}, {half, half}, {-half, half} };

        VertexConsumer vc = buffer.getBuffer(BEAM);
        Matrix4f mat = ms.last().pose();
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i];
            float[] b2 = corners[(i + 1) % 4];
            vertex(vc, mat, 0, 0, 0, r, g, b, 1f);             // apex, full alpha
            vertex(vc, mat, a[0], a[1], span, r, g, b, 0f);     // base corner A, faded
            vertex(vc, mat, b2[0], b2[1], span, r, g, b, 0f);   // base corner B, faded
        }
        ms.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                               float r, float g, float b, float a) {
        vc.addVertex(mat, x, y, z).setColor(r, g, b, a);
    }
}
