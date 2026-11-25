package com.shiningstage.create_shining_stage;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.AngleHelper;
import com.mojang.math.Axis;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;

/** Places the range value box on the spotlight's tail (the face opposite the beam). */
public class SpotlightRangeValueBoxTransform extends ValueBoxTransform.Sided {
    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        return new Vec3(0.5, 0.5, 0.5).subtract(SpotlightBlock.beamDirection(state).scale(7.5 / 16.0));
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack pose) {
        pose.mulPose(SpotlightBlock.modelRotation(state));
        Direction tail = state.getValue(SpotlightBlock.FACING).getOpposite();
        pose.mulPose(Axis.YP.rotationDegrees(AngleHelper.horizontalAngle(tail) + 180));
        pose.mulPose(Axis.XP.rotationDegrees(tail == Direction.UP ? 90 : tail == Direction.DOWN ? 270 : 0));
    }

    @Override
    protected Vec3 getSouthLocation() {
        return new Vec3(0.5, 0.5, 15.5 / 16.0);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        Vec3 beam = SpotlightBlock.beamDirection(state);
        return Direction.getNearest(-beam.x, -beam.y, -beam.z) == direction;
    }
}
