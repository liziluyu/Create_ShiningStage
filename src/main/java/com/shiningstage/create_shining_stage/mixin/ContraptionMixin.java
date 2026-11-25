package com.shiningstage.create_shining_stage.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.shiningstage.create_shining_stage.SpotlightBeams;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * Captures spotlight metadata once, when contraption block data is read, so the independent render
 * and lighting passes do not scan every block in the structure every frame.
 *
 * <p>Injected at the tail of {@code readNBT}: every contraption reaches it (through
 * {@code Contraption.fromNBT} on the client, or through any of the nine subclass overrides that call
 * {@code super.readNBT}), and the method runs again whenever a contraption is re-read, so the capture
 * follows the structure. The injection point matters — {@code readNBT} fills {@code blocks} through
 * {@code readBlocksCompound}, which clears the map and then re-populates it, so capturing anywhere
 * but the tail could observe a half-filled structure.
 *
 * <p>The field is volatile because block data is read on the network thread while the render and
 * lighting passes read the capture on the client thread.
 */
@Mixin(value = Contraption.class, remap = false)
public abstract class ContraptionMixin implements SpotlightBeams {
    @Unique
    private volatile List<SpotlightBeams.Beam> createShiningStage$spotlightBeams;

    @Unique
    private volatile List<SpotlightBeams.Beam> createShiningStage$fillLightBeams;

    @Inject(
        method = "readNBT(Lnet/minecraft/world/level/Level;Lnet/minecraft/nbt/CompoundTag;Z)V",
        remap = false,
        at = @At("TAIL"))
    private void createShiningStage$captureSpotlights(Level world, CompoundTag nbt, boolean spawnData, CallbackInfo ci) {
        createShiningStage$spotlightBeams = SpotlightBeams.scan((Contraption) (Object) this);
        createShiningStage$fillLightBeams = SpotlightBeams.scanFillLights((Contraption) (Object) this);
    }

    @Override
    public List<SpotlightBeams.Beam> createShiningStage$spotlightBeams() {
        List<SpotlightBeams.Beam> beams = createShiningStage$spotlightBeams;
        return beams == null ? List.of() : beams;
    }

    @Override
    public List<SpotlightBeams.Beam> createShiningStage$fillLightBeams() {
        List<SpotlightBeams.Beam> beams = createShiningStage$fillLightBeams;
        return beams == null ? List.of() : beams;
    }
}
