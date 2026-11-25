package com.shiningstage.create_shining_stage;

import io.netty.handler.codec.DecoderException;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class StageLightingSettings {
    private static volatile int clientFillLightRange = 32;
    private StageLightingSettings() {}
    public static int clientFillLightRange() { return clientFillLightRange; }
    public static void resetClient() { clientFillLightRange = 32; }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    public static void onConfigLoading(ModConfigEvent.Loading event) { syncConfig(event.getConfig()); }
    public static void onConfigReloading(ModConfigEvent.Reloading event) { syncConfig(event.getConfig()); }
    private static void syncConfig(ModConfig config) {
        if (!CreateShiningStage.MOD_ID.equals(config.getModId()) || config.getType() != ModConfig.Type.COMMON) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> server.getPlayerList().getPlayers().forEach(StageLightingSettings::send));
    }
    private static void send(ServerPlayer player) {
        StageNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new LightingSettingsPayload(Config.FILL_LIGHT_RANGE.get()));
    }
    static void receiveSettings(LightingSettingsPayload payload, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> clientFillLightRange = payload.fillLightRange());
        ctx.setPacketHandled(true);
    }
    public record LightingSettingsPayload(int fillLightRange) {
        static void encode(LightingSettingsPayload payload, FriendlyByteBuf buf) { buf.writeVarInt(payload.fillLightRange()); }
        static LightingSettingsPayload decode(FriendlyByteBuf buf) {
            int range = buf.readVarInt();
            if (range < 1 || range > 32) throw new DecoderException("Invalid fill light range: " + range);
            return new LightingSettingsPayload(range);
        }
    }
}
