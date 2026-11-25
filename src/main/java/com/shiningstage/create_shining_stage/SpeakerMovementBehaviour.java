package com.shiningstage.create_shining_stage;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import net.minecraft.world.item.ItemStack;

/**
 * Keeps a contraption-carried speaker playable while its structure moves.
 *
 * <p>A mounted speaker has no block entity in the level and no fixed position, so the relay cannot
 * play at the position it was bound by; this actor republishes it against the contraption, and the
 * relay asks the contraption entity where the speaker is now (see {@link SoundRelayHandler}).
 *
 * <p>{@link #canBeDisabledVia} returns null on purpose: Create's contraption controls are a
 * redstone-driven actor gate, and a stage has to keep making sound for as long as its structure is
 * assembled. Taking an actor off that gate also keeps it selectable — an empty filter disables every
 * actor that reports one, while the controls' filter slot only accepts Create's own controlled items.
 */
public class SpeakerMovementBehaviour implements MovementBehaviour {
    @Override
    public void tick(MovementContext context) {
        SoundRelayHandler.trackMountedSpeaker(context);
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
