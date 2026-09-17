package com.shiningstage.create_shining_stage;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import net.minecraft.world.item.ItemStack;

/**
 * Keeps a contraption-carried microphone listening while its structure moves.
 *
 * <p>Assembly tears the microphone's block entity out of the level and into the contraption's block
 * data, so the relay can no longer find it the way it finds a placed one; this actor re-registers it
 * against the contraption instead (see {@link SoundRelayHandler}).
 *
 * <p>{@link #canBeDisabledVia} returns null on purpose: Create's contraption controls are a
 * redstone-driven actor gate, and a stage has to keep making sound for as long as its structure is
 * assembled. Taking an actor off that gate also keeps it selectable — an empty filter disables every
 * actor that reports one, while the controls' filter slot only accepts Create's own controlled items.
 */
public class MicrophoneMovementBehaviour implements MovementBehaviour {
    @Override
    public void tick(MovementContext context) {
        SoundRelayHandler.trackMountedMicrophone(context);
    }

    @Override
    public void stopMoving(MovementContext context) {
        SoundRelayHandler.forgetMounted(context);
    }

    @Override
    public ItemStack canBeDisabledVia(MovementContext context) {
        return null;
    }
}
