package com.shiningstage.create_shining_stage;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-owned settings; both messages are strictly server to client. */
public final class StageNetwork {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(CreateShiningStage.MOD_ID, "settings"), () -> "1", "1"::equals, "1"::equals);

    private StageNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, StageLightingSettings.LightingSettingsPayload.class,
            StageLightingSettings.LightingSettingsPayload::encode,
            StageLightingSettings.LightingSettingsPayload::decode,
            StageLightingSettings::receiveSettings, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, PositionZeroItemPolicy.ItemPolicyPayload.class,
            PositionZeroItemPolicy.ItemPolicyPayload::encode,
            PositionZeroItemPolicy.ItemPolicyPayload::decode,
            PositionZeroItemPolicy::receivePolicy, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
