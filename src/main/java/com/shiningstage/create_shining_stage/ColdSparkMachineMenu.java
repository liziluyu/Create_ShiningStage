package com.shiningstage.create_shining_stage;

import com.simibubi.create.foundation.gui.menu.MenuBase;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The machine's one-slot container menu: the fuel slot above the player's own inventory, so fuel can be
 * loaded by hand instead of only by funnel.
 *
 * <p>The slot wraps the block entity's own {@code SmartInventory} rather than a menu-side copy, so there
 * is nothing to write back: a click lands in the very inventory the automation routes and the shot logic
 * use, and that inventory already marks the block entity dirty and syncs it. {@link #saveData} is
 * therefore empty, and closing the screen cannot lose or duplicate a stack.
 */
public class ColdSparkMachineMenu extends MenuBase<ColdSparkMachineBlockEntity> {
    /**
     * Panel texture size. 176 wide because that is the width of Create's shared player-inventory sheet,
     * which is drawn directly beneath the panel — the sheet is 176x108, so the panel has to match its
     * width or the two boxes would not line up. The panel's texture must be exactly this size too: it is
     * drawn 1:1, so a larger or smaller image shifts everything in it.
     */
    public static final int PANEL_W = 176;
    public static final int PANEL_H = 44;

    /**
     * Where the fuel slot sits inside the panel, and where the player's inventory block starts. The
     * panel's texture has the slot box drawn at exactly this offset, and the player rows use the same
     * offsets as Create's player-inventory sheet (first row 18px down, hotbar 58 rows below that), so
     * the picture and the slots agree by construction.
     */
    private static final int SLOT_X = 80;
    private static final int SLOT_Y = 22;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = PANEL_H + 18;

    public ColdSparkMachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenuTypes.COLD_SPARK_MACHINE.get(), id, inventory, extraData);
    }

    private ColdSparkMachineMenu(int id, Inventory inventory, ColdSparkMachineBlockEntity machine) {
        super(ModMenuTypes.COLD_SPARK_MACHINE.get(), id, inventory, machine);
    }

    public static ColdSparkMachineMenu create(int id, Inventory inventory, ColdSparkMachineBlockEntity machine) {
        return new ColdSparkMachineMenu(id, inventory, machine);
    }

    /**
     * Rebuilds the client side from what {@code SmartBlockEntity#sendToMenu} wrote: the machine's
     * position followed by its update tag. Note that a menu outlives its block if the block is broken
     * while the screen is open, so a missing block entity yields an empty menu — {@code MenuBase} then
     * reports it as no longer valid and the screen closes — instead of throwing here.
     */
    @Override
    protected ColdSparkMachineBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null
            || !(level.getBlockEntity(extraData.readBlockPos()) instanceof ColdSparkMachineBlockEntity machine)) {
            return null;
        }
        machine.readClient(extraData.readNbt(), extraData.registryAccess());
        return machine;
    }

    @Override
    protected void initAndReadInventory(ColdSparkMachineBlockEntity machine) {
    }

    @Override
    protected void addSlots() {
        // With no block entity there is no inventory to show; the player slots alone would be a menu
        // that cannot close properly. see createOnClient.
        if (contentHolder == null) {
            return;
        }
        // SlotItemHandler, not a plain Slot, and not for tidiness: vanilla Slot#mayPlace returns an
        // unconditional true in 1.21 and Slot#getMaxStackSize reads the Container, so a plain Slot would
        // both take stone into the fuel slot and report a max of 0 — a 0 that makes moveItemStackTo
        // place nothing. The fuel predicate and the slot limit both live on the item handler, which is
        // what SlotItemHandler asks.
        addSlot(new SlotItemHandler(contentHolder.getInventory(), 0, SLOT_X, SLOT_Y));
        addPlayerSlots(PLAYER_INV_X, PLAYER_INV_Y);
    }

    /**
     * Shift-click between the fuel slot and the player's inventory. Moving into the machine goes through
     * {@code Slot#mayPlace} and so through the inventory's fuel-only predicate, which is what makes a
     * shift-clicked non-fuel stack stay where it is instead of landing in the slot.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        boolean changed = index == 0
            ? moveItemStackTo(stack, 1, slots.size(), true)
            : moveItemStackTo(stack, 0, 1, false);
        if (!changed) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }

    /** Nothing to write back: slot edits already landed in the block entity's own inventory. */
    @Override
    protected void saveData(ColdSparkMachineBlockEntity machine) {
    }
}
