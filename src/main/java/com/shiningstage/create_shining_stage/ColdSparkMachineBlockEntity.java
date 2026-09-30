package com.shiningstage.create_shining_stage;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.item.SmartInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Server-owned state of the cold spark machine: the spray height (a value box on the front face, i.e.
 * the same behaviour a spotlight uses for its beam length), the one fuel slot, and the shot timer.
 *
 * <p>A shot is a pulse-shaped thing: a rising redstone edge while idle spends one fuel and sprays for
 * {@link #SPRAY_DURATION} ticks, and that is the only way in — the line is sampled throughout, so a
 * signal that arrives mid-spray is swallowed and the spray cannot be extended or restarted. A held
 * line therefore gives exactly one shot, and the next one needs the line to go low and rise again.
 */
public class ColdSparkMachineBlockEntity extends SmartBlockEntity implements MenuProvider {
    /** Spray height bounds, in blocks between the machine's top face and the fountain's tip. */
    public static final int MIN_SPRAY_HEIGHT = 1;
    public static final int MAX_SPRAY_HEIGHT = 16;
    /** Initial spray height on placement. */
    public static final int DEFAULT_SPRAY_HEIGHT = 4;

    /** How long one shot lasts, in ticks (two seconds). One shot costs one fuel, paid when it starts. */
    public static final int SPRAY_DURATION = 2 * 20;

    /**
     * The single fuel slot. The stack predicate is what {@code ItemStackHandler#insertItem} consults, so
     * every automation route (funnel, chute, arm, pipe) gets the same "fuel only" answer without each of
     * them having to ask; and with one slot the extract side can only ever hand fuel back.
     */
    private final SmartInventory inventory;

    private ScrollValueBehaviour sprayHeight;

    /**
     * Ticks left in the current shot; 0 means idle. Server state: persisted so a save mid-spray resumes
     * it, but kept out of the client sync (see {@link #write}) — the client runs its own clock off
     * {@link #sprayStartTick} rather than counting down a value it would only be told once.
     */
    private int sprayTicks;
    /**
     * World game time the current (or most recent) shot began, and how many shots this machine has
     * fired. Both are synced: {@link ColdSparkMachineRenderer} derives the whole spray from the start
     * time, so a client that joins mid-shot draws the sparks that are still in the air instead of
     * starting from nothing, and the serial seeds the per-spark randomness so every client draws the
     * same fountain.
     */
    private long sprayStartTick;
    private int spraySerial;
    /** Whether the last read of the redstone line found it powered. */
    private boolean linePowered;
    /**
     * Cleared until the line has been read once: the level a machine comes up on (placed, or loaded from
     * disk) is a baseline, not the rising edge that starts a shot.
     */
    private boolean lineSampled;

    public ColdSparkMachineBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.COLD_SPARK_MACHINE.get(), pos, blockState);
        inventory = new SmartInventory(1, this, (slot, stack) -> stack.is(ModItems.COLD_SPARK_FUEL.get()));
    }

    /**
     * The one item handler this machine exposes, on its four sides only: the spray leaves through the top
     * face and the machine stands on its bottom, so neither is an inventory face. Returning null leaves
     * those two faces without a capability, which is what makes a funnel on top refuse the machine
     * instead of feeding it.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntityTypes.COLD_SPARK_MACHINE.get(),
            (be, context) -> context != null && context.getAxis().isHorizontal() ? be.inventory : null);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        sprayHeight = new ScrollValueBehaviour(
            Component.translatable("block.create_shining_stage.cold_spark_machine.spray_height"),
            this, new ColdSparkMachineValueBoxTransform()) {
            // Holding a placeable block means "attach it to the front", not "adjust the height":
            // let the click fall through to block placement instead of opening the value box.
            @Override
            public boolean bypassesInput(ItemStack mainhandItem) {
                return mainhandItem.getItem() instanceof BlockItem;
            }
        }.between(MIN_SPRAY_HEIGHT, MAX_SPRAY_HEIGHT);
        sprayHeight.value = DEFAULT_SPRAY_HEIGHT; // start low, not max
        behaviours.add(sprayHeight);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        if (sprayTicks > 0) {
            sprayTicks--;
        }

        // The line is read every tick, spraying or not, and the sample is what defines an edge: a signal
        // that arrives during the two seconds is dropped, and because the sample keeps up to date it
        // cannot fire the moment the spray ends either.
        boolean lineNow = level.getBestNeighborSignal(worldPosition) > 0;
        if (!lineSampled) {
            lineSampled = true;
        } else if (lineNow && !linePowered && sprayTicks == 0) {
            startSpray();
        }
        linePowered = lineNow;
    }

    /** Spends one fuel and starts the shot; a machine with an empty slot stays idle. */
    private void startSpray() {
        if (inventory.getStackInSlot(0).isEmpty()) {
            return;
        }
        // Extraction goes through the inventory, so the change is marked dirty and synced like any other.
        inventory.extractItem(0, 1, false);
        sprayTicks = SPRAY_DURATION;
        sprayStartTick = level.getGameTime();
        spraySerial++;
        // The renderer's whole input is "which shot, begun when", so the start has to reach the client.
        sendData();
    }

    /** True while a shot is running; the machine ignores the redstone line for as long as it is. */
    public boolean isSpraying() {
        return sprayTicks > 0;
    }

    /** Ticks left in the current shot, {@link #SPRAY_DURATION} down to 0. Server-side countdown. */
    public int getSprayTicks() {
        return sprayTicks;
    }

    /**
     * World time the most recent shot began, or 0 if this machine has never fired. Read by
     * {@link ColdSparkMachineRenderer} on both sides — the server holds it in its tick loop, the client
     * receives it, and neither has to count anything down.
     */
    public long getSprayStartTick() {
        return sprayStartTick;
    }

    /** Shots fired so far; 0 means never. Seeds the renderer's per-spark randomness. */
    public int getSpraySerial() {
        return spraySerial;
    }

    /** Spray height in blocks (MIN_SPRAY_HEIGHT..MAX_SPRAY_HEIGHT), adjusted via the front value box. */
    public int getSprayHeight() {
        return sprayHeight == null ? DEFAULT_SPRAY_HEIGHT : sprayHeight.getValue();
    }

    /**
     * Handing the machine to the GUI. The position and update tag that let the client rebuild this menu
     * are written by {@code SmartBlockEntity#sendToMenu}, which the block passes to
     * {@code Player#openMenu}.
     */
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return ColdSparkMachineMenu.create(id, inventory, this);
    }

    /** The GUI's title, and the block's own name — one string, so it is already translated. */
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.create_shining_stage.cold_spark_machine");
    }

    public SmartInventory getInventory() {
        return inventory;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putLong("SprayStartTick", sprayStartTick);
        tag.putInt("SpraySerial", spraySerial);
        // Save-only: the countdown is the server's own clock. The client is sent the start time above and
        // derives everything from it, so shipping the remaining ticks would be a second, staler source of
        // truth for the same fact.
        if (!clientPacket) {
            tag.putInt("SprayTicks", sprayTicks);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        sprayStartTick = tag.getLong("SprayStartTick");
        spraySerial = tag.getInt("SpraySerial");
        if (!clientPacket) {
            sprayTicks = tag.getInt("SprayTicks");
        }
    }
}
