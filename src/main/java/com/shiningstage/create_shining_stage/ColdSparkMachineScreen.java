package com.shiningstage.create_shining_stage;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Draws the machine's panel and leaves the player's inventory to Create.
 *
 * <p>Only the panel is this mod's own art: the inventory below it comes from Create's shared
 * player-inventory sheet, so this screen adds one small texture instead of a full container background,
 * and the player's half looks exactly like it does in every other Create GUI.
 */
public class ColdSparkMachineScreen extends AbstractSimiContainerScreen<ColdSparkMachineMenu> {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(
        CreateShiningStage.MOD_ID, "textures/gui/cold_spark_machine.png");

    public ColdSparkMachineScreen(ColdSparkMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        setWindowSize(ColdSparkMachineMenu.PANEL_W,
            ColdSparkMachineMenu.PANEL_H + AllGuiTextures.PLAYER_INVENTORY.getHeight());
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // The 9-argument overload, not the 7-argument one, and that is not a style choice: the short one
        // hardcodes the texture size as 256x256, so it reads the panel's UVs as fractions of a 256-pixel
        // square. This panel is 176x44, so the short call would sample only its top-left 121x7.5 pixels
        // and stretch them over the whole quad — no slot box (it sits far below the sampled strip), no
        // right or bottom border, a black and a white band smeared across the top. The texture's own size
        // is passed as the last two arguments instead, so the panel is drawn 1:1.
        graphics.blit(PANEL, leftPos, topPos, 0f, 0f,
            ColdSparkMachineMenu.PANEL_W, ColdSparkMachineMenu.PANEL_H,
            ColdSparkMachineMenu.PANEL_W, ColdSparkMachineMenu.PANEL_H);
        graphics.drawString(font, title, leftPos + 8, topPos + 6, 4210752, false);
        // Flush against the panel, no gap: the two boxes each draw their own frame, and anything left
        // between them is a strip of the world showing through the middle of the GUI.
        renderPlayerInventory(graphics, leftPos, topPos + ColdSparkMachineMenu.PANEL_H);
    }
}
