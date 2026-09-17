package com.shiningstage.create_shining_stage;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Outlines the microphone a held speaker item is bound to, the way a held mechanical arm marks the
 * blocks picked for it: the binding lives on the item (BOUND_MIC), so the world-space box is what
 * tells the player which microphone the speaker about to be placed will listen to. Drawn through
 * Create's own Outliner, which Ponder ticks and renders every frame, so the box gets the same
 * shape-following AABB, colour and line width as Create's binding outlines.
 */
public class SpeakerBindingOutliner {
    /**
     * Create's click-to-link selection colour (ClickToLinkBlockItem draws the block a linked item
     * points at in it), so a bound link reads as the same thing it does in Create.
     */
    private static final int COLOR = 0xFFCB74;

    /** Outline slot: one per player-visible binding, so nothing else's outline is overwritten. */
    private static final String OUTLINE_SLOT = CreateShiningStage.MOD_ID + ":bound_microphone";

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModBlocks.SPEAKER_ITEM.get())) {
            return;
        }
        GlobalPos mic = held.get(ModDataComponents.BOUND_MIC.get());
        if (mic == null || !mic.dimension().equals(level.dimension())) {
            return;
        }

        BlockPos pos = mic.pos();
        BlockState state = level.getBlockState(pos);
        // A bound microphone that is gone (broken, or its chunk is not loaded) has no shape to trace;
        // air's would be empty, but any other block there would only get a misleading box drawn on it.
        if (!(state.getBlock() instanceof MicrophoneBlock)) {
            return;
        }
        VoxelShape shape = state.getShape(level, pos);

        // Called every tick while the item is held: Outliner keeps an entry alive for one tick, and
        // fades it out over OutlineEntry.FADE_TICKS once this stops being called.
        Outliner.getInstance()
            .showAABB(OUTLINE_SLOT, shape.bounds()
                .move(pos))
            .colored(COLOR)
            .lineWidth(1 / 16f);
    }
}
