package com.shiningstage.create_shining_stage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.shiningstage.create_shining_stage.SpotlightRenderer;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Contraption rendering is all-or-nothing: {@code ContraptionEntityRenderer.render} (and with it the
 * whole block-entity batch, spotlights included) is only reached while the contraption entity itself
 * passes culling. {@code AbstractContraptionEntity} never widens its culling box, so a beam reaching
 * up to 32 blocks out of the hull vanishes the moment the hull leaves the view frustum, even if the
 * beam is still on screen.
 *
 * <p>{@code @ModifyReturnValue} rather than a cancellable {@code @Inject}: this method runs for every
 * entity in view every frame, and a cancellable inject allocates a {@code CallbackInfoReturnable} on
 * every one of those calls, contraption or not.
 *
 * <p>This lives in its own package because a mixin config claims every class under the package it
 * declares — a mixin sharing the mod's main package would make all other mod classes unloadable.
 *
 * <p>The target is {@link Entity} rather than {@code AbstractContraptionEntity} because the
 * contraption only inherits this method; Mixin can only inject into a declared body. Every other
 * entity bails out on the {@code instanceof} below.
 */
@Mixin(Entity.class)
public abstract class EntityCullingMixin {
    @ModifyReturnValue(method = "getBoundingBoxForCulling", at = @At("RETURN"))
    private AABB createShiningStage$includeSpotlightBeams(AABB original) {
        if ((Object) this instanceof AbstractContraptionEntity contraption) {
            return SpotlightRenderer.contraptionCullingBox(contraption, original);
        }
        return original;
    }
}
