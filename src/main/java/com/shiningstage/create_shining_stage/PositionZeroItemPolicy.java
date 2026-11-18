package com.shiningstage.create_shining_stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Keeps the server's extra Position Zero item allowlist available for accurate client prediction. */
public final class PositionZeroItemPolicy {
    private static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(
        CreateShiningStage.MOD_ID, "position_zero_item_policy");

    private static volatile Set<String> clientAdditionalItems = Set.of();

    private PositionZeroItemPolicy() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ItemPolicyPayload.TYPE, ItemPolicyPayload.STREAM_CODEC,
            PositionZeroItemPolicy::receivePolicy);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, currentPolicy());
        }
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        syncIfPositionZeroConfig(event.getConfig().getModId(), event.getConfig().getType());
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        syncIfPositionZeroConfig(event.getConfig().getModId(), event.getConfig().getType());
    }

    public static boolean allowsAdditionalItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return clientAdditionalItems.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static void receivePolicy(ItemPolicyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientAdditionalItems = Set.copyOf(payload.itemIds()));
    }

    private static void syncIfPositionZeroConfig(String modId, net.neoforged.fml.config.ModConfig.Type type) {
        if (!CreateShiningStage.MOD_ID.equals(modId) || type != net.neoforged.fml.config.ModConfig.Type.COMMON) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> server.getPlayerList().getPlayers().forEach(player ->
            PacketDistributor.sendToPlayer(player, currentPolicy())));
    }

    private static ItemPolicyPayload currentPolicy() {
        return new ItemPolicyPayload(new ArrayList<>(Config.POSITION_ZERO_ADDITIONAL_ITEMS.get()));
    }

    public record ItemPolicyPayload(List<String> itemIds) implements CustomPacketPayload {
        public static final Type<ItemPolicyPayload> TYPE = new Type<>(PAYLOAD_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemPolicyPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.itemIds().size());
                payload.itemIds().forEach(buf::writeUtf);
            }, buf -> {
                int size = buf.readVarInt();
                if (size < 0 || size > buf.readableBytes()) {
                    throw new DecoderException("Invalid Position Zero item policy size: " + size);
                }
                List<String> ids = new ArrayList<>(size);
                for (int i = 0; i < size; i++) ids.add(buf.readUtf());
                return new ItemPolicyPayload(List.copyOf(ids));
            });

        public ItemPolicyPayload {
            itemIds = List.copyOf(itemIds);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
