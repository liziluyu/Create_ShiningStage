package com.shiningstage.create_shining_stage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.shiningstage.create_shining_stage.ClientStageLighting;
import com.shiningstage.create_shining_stage.SpotlightBlockEntity;

@Mixin(SpotlightBlockEntity.class)
public abstract class ClientSpotlightBlockEntityMixin {
    // The common tick returns early on the client; TAIL would only register server migrations.
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void createShiningStage$registerLight(CallbackInfo ci) {
        ClientStageLighting.register((SpotlightBlockEntity) (Object) this);
    }
}
