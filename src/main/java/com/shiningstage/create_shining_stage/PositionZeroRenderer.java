package com.shiningstage.create_shining_stage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;

/** Draws the stored flat item model as the thrusting marker on the plate. */
public class PositionZeroRenderer implements BlockEntityRenderer<PositionZeroBlockEntity> {
    /** Position and size of the upright display, with the point just entering the plate. */
    private static final float DISPLAY_Y = 0.5125f;
    private static final float DISPLAY_SCALE = 0.88f;

    public PositionZeroRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PositionZeroBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (be.getStored().isEmpty()) return;
        pose.pushPose();
        pose.translate(0.5, DISPLAY_Y, 0.5);
        // Keep the item model upright in its XY plane. The typical weapon tip points upper-right
        // (+45 degrees); -135 degrees around Z turns that tip straight down into the marker.
        float facingYaw = switch (be.getBlockState().getValue(PositionZeroBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(facingYaw + 90));
        pose.mulPose(Axis.ZP.rotationDegrees(-135));
        pose.scale(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(be.getStored(), ItemDisplayContext.NONE,
            packedLight, packedOverlay, pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }
}
