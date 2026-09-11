package com.shiningstage.create_shining_stage;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Places the range value box on the microphone's top face. */
public class MicrophoneRangeValueBoxTransform extends ValueBoxTransform.Sided {
    @Override
    protected Vec3 getSouthLocation() {
        return new Vec3(0.5, 0.5, 15.5 / 16.0);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        return direction == Direction.UP;
    }
}
