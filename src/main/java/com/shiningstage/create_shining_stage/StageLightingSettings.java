package com.shiningstage.create_shining_stage;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Server-owned lighting settings needed by the client's virtual light calculation. */
public final class StageLightingSettings {
    private static final int DEFAULT_FILL_LIGHT_RANGE = 32;
    private static volatile int clientFillLightRange = DEFAULT_FILL_LIGHT_RANGE;

    private StageLightingSettings() {}

    public static int clientFillLightRange() {
        return clientFillLightRange;
    }

    public static void resetClient() {
        clientFillLightRange = DEFAULT_FILL_LIGHT_RANGE;
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(LightingSettingsPayload.TYPE, LightingSettingsPayload.STREAM_CODEC,
            StageLightingSettings::receiveSettings);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, currentSettings());
        }
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        syncConfig(event.getConfig());
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        syncConfig(event.getConfig());
    }

    private static void syncConfig(ModConfig config) {
        if (!CreateShiningStage.MOD_ID.equals(config.getModId()) || config.getType() != ModConfig.Type.COMMON) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            LightingSettingsPayload settings = currentSettings();
            server.getPlayerList().getPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, settings));
        });
    }

    private static LightingSettingsPayload currentSettings() {
        return new LightingSettingsPayload(Config.FILL_LIGHT_RANGE.get());
    }

    private static void receiveSettings(LightingSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientFillLightRange = payload.fillLightRange());
    }

    public record LightingSettingsPayload(int fillLightRange) implements CustomPacketPayload {
        public static final Type<LightingSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            CreateShiningStage.MOD_ID, "stage_lighting_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LightingSettingsPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeVarInt(payload.fillLightRange()), buf -> {
                int range = buf.readVarInt();
                if (range < 1 || range > 32) throw new DecoderException("Invalid fill light range: " + range);
                return new LightingSettingsPayload(range);
            });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
