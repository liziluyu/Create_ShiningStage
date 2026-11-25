package com.shiningstage.create_shining_stage.client;

import com.shiningstage.create_shining_stage.CreateShiningStage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Short, always-visible usage hints shared by all stage items, including hidden variants. */
@Mod.EventBusSubscriber(modid = CreateShiningStage.MOD_ID, value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StageItemTooltips {
    private StageItemTooltips() {}

    @SubscribeEvent
    public static void appendUsage(ItemTooltipEvent event) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id == null || !CreateShiningStage.MOD_ID.equals(id.getNamespace())) return;

        for (int line = 1; line <= 3; line++) {
            // Keep the optional integration hint within the same three-line limit.
            if (id.getPath().equals("truss") && line == 3 && !ModList.get().isLoaded("ae2")) continue;
            String key = "tooltip." + id.getNamespace() + "." + id.getPath() + "." + line;
            if (I18n.exists(key)) {
                event.getToolTip().add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
