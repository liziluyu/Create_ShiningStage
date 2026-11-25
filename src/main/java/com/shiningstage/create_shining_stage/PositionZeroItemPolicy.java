package com.shiningstage.create_shining_stage;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.function.Supplier;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class PositionZeroItemPolicy {
    private static volatile Set<String> clientAdditionalItems = Set.of();
    private PositionZeroItemPolicy() {}
    public static void resetClient() { clientAdditionalItems = Set.of(); }
    public static boolean allowsAdditionalItem(ItemStack stack) {
        return !stack.isEmpty() && clientAdditionalItems.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    public static void onConfigLoading(ModConfigEvent.Loading event) { syncConfig(event.getConfig()); }
    public static void onConfigReloading(ModConfigEvent.Reloading event) { syncConfig(event.getConfig()); }
    private static void syncConfig(ModConfig config) {
        if (!CreateShiningStage.MOD_ID.equals(config.getModId()) || config.getType() != ModConfig.Type.COMMON) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> server.getPlayerList().getPlayers().forEach(PositionZeroItemPolicy::send));
    }
    private static void send(ServerPlayer player) {
        StageNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new ItemPolicyPayload(List.copyOf(Config.POSITION_ZERO_ADDITIONAL_ITEMS.get())));
    }
    static void receivePolicy(ItemPolicyPayload payload, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> clientAdditionalItems = Set.copyOf(payload.itemIds()));
        ctx.setPacketHandled(true);
    }
    public record ItemPolicyPayload(List<String> itemIds) {
        public ItemPolicyPayload { itemIds = List.copyOf(itemIds); }
        static void encode(ItemPolicyPayload payload, FriendlyByteBuf buf) {
            buf.writeVarInt(payload.itemIds().size());
            payload.itemIds().forEach(buf::writeUtf);
        }
        static ItemPolicyPayload decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > buf.readableBytes()) throw new DecoderException("Invalid item policy size: " + size);
            List<String> ids = new ArrayList<>(size);
            for (int i = 0; i < size; i++) ids.add(buf.readUtf());
            return new ItemPolicyPayload(ids);
        }
    }
}
