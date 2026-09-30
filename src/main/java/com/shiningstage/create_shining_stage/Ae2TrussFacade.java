package com.shiningstage.create_shining_stage;

import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import appeng.facade.FacadePart;
import appeng.items.parts.FacadeItem;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * AE2 integration: the truss covering a cable, the counterpart of the casing the same item forms
 * around a Create shaft (see {@link TrussBlock}).
 *
 * <p>AE2's own casing is the <em>facade</em> - a cover on one face of a cable bus, drawn as a 1/16
 * plate from the source block's own model - and AE2 makes the player craft one from the block before
 * it can be applied. This class skips that step by standing in for AE2's own
 * {@code FacadeItem#onItemUseFirst}: the truss item covers the clicked face directly.
 *
 * <p>Every AE2 type in this mod lives in this class, and the class is only ever entered from behind
 * the {@code ModList#isLoaded("ae2")} guard in {@link TrussBlockItem}. That guard is what makes AE2
 * optional: AE2 is <em>not</em> declared in {@code neoforge.mods.toml}, and with AE2 absent this class
 * is never loaded, so its constant pool - which names AE2 types - is never resolved.
 */
final class Ae2TrussFacade {
    private Ae2TrussFacade() {
    }

    /**
     * Answers {@link InteractionResult#PASS} unless the click really did cover a cable, and PASS is
     * the load-bearing half: it hands the click on to the ordinary block placement in
     * {@code BlockItem#useOn} instead of merely declining it, so aiming the truss at anything that is
     * not a cable behaves exactly as it did before this integration existed.
     */
    static InteractionResult tryPlace(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        // Resolves the block entity behind the clicked block, and answers null for everything that is
        // not a cable bus - so this one call is the whole test for "was a cable clicked".
        IPartHost host = PartHelper.getPartHost(level, pos);
        if (host == null) {
            return InteractionResult.PASS;
        }

        // The truss is used as a facade source exactly as a full block would be, which is why the
        // frame ends up drawn on the cable with no model work and no facade item of this mod's own.
        // The cost is that the truss must keep qualifying as a facade source - a full collision cube
        // with RenderShape.MODEL and no block entity, which is AE2's own condition in
        // FacadeItem#createFacadeForItem. Change any of those three on the block and the cover stops
        // rendering correctly; this is the place to notice.
        //
        // The state carries the covered face, which is what picks a model without the plane AE2 would
        // otherwise clamp onto the back of the cover as a second, visible frame. See TrussBlock.
        BlockState truss = TrussBlock.facadeSource(context.getClickedFace());
        IFacadePart facade = new FacadePart(truss, context.getClickedFace());

        // The gate AE2's own facade item asks before placing: a cable must sit in the centre of the
        // bus to hold the cover, and the face must be free. Declining with PASS rather than failing
        // leaves the click to whatever else may want it, as AE2 does.
        if (!FacadeItem.canPlaceFacade(host, facade)) {
            return InteractionResult.PASS;
        }
        if (!host.getFacadeContainer().addFacade(facade)) {
            return InteractionResult.PASS;
        }

        // A facade sounds like the block it is made of, not like a facading operation. The no-arg
        // BlockState#getSoundType is deprecated (NeoForge's replacement takes the level, position and
        // entity so a block can vary its sound), and this is the same call BlockItem#place makes for
        // a placement sound.
        SoundType sound = truss.getSoundType(level, pos, context.getPlayer());
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
            (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);

        // The facade was written to the block entity directly rather than through a block update, so
        // both the save and the client sync have to be asked for by hand.
        host.markForSave();
        host.markForUpdate();

        Player player = context.getPlayer();
        if (!level.isClientSide && player != null && !player.isCreative()) {
            stack.shrink(1);
        }

        // Both sides ran everything above - that is how AE2's own facade item is written - so both
        // sides have to agree the click is spent. Passing on the client instead would let the client
        // fall through to placing the truss as a block while the server did not: a ghost block.
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
