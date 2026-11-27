package cn.nukkit.item.enchantment.book;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.inventory.AnvilInventory;
import cn.nukkit.inventory.CustomInventory;
import cn.nukkit.inventory.FakeBlockMenu;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.BlockEntityDataPacket;
import cn.nukkit.plugin.InternalPlugin;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Chest window that replaces the anvil screen. It only shows copies: no real item is ever
 * moved into it, and every transaction touching it is rejected.
 */
public final class AnvilMenu extends CustomInventory {
    private static final int PREVIEW_SLOT = 4;
    private static final int FIRST_BOOK_SLOT = 9;
    private static final int VANILLA_SLOT = 26;

    private final Position anvil;
    private final Vector3 chest;
    private final Item held;
    private final int heldSlot;
    private final List<CustomBooks.Option> options;
    private boolean used;

    private AnvilMenu(Position chestPosition, Position anvil, Item held, int heldSlot, List<CustomBooks.Option> options) {
        super(new FakeBlockMenu(null, chestPosition), InventoryType.CHEST);
        this.chest = chestPosition;
        this.anvil = anvil;
        this.held = held;
        this.heldSlot = heldSlot;
        this.options = options;

        Item preview = held.clone();
        this.slots.put(PREVIEW_SLOT, preview);
        for (int i = 0; i < shownBooks(); i++) {
            CustomBooks.Option option = options.get(i);
            Item icon = option.book().clone();
            icon.setCustomName(option.title());
            this.slots.put(FIRST_BOOK_SLOT + i, icon);
        }
        this.slots.put(VANILLA_SLOT, Item.get(Block.ANVIL).setCustomName("§eОбычная наковальня"));
    }

    private int shownBooks() {
        return Math.min(options.size(), VANILLA_SLOT - FIRST_BOOK_SLOT);
    }

    /** @return false if no menu can be shown, so the normal anvil should open. */
    static boolean open(Player player, Position anvil, Item held, List<CustomBooks.Option> options) {
        Level level = player.getLevel();
        Vector3 spot = null;
        // Only borrow an air block, so no real block entity is ever covered.
        for (int up = 3; up >= 2 && spot == null; up--) {
            int y = player.getFloorY() + up;
            if (y >= level.getMinBlockY() && y <= level.getMaxBlockY()
                    && level.getBlockIdAt(player.getFloorX(), y, player.getFloorZ()) == Block.AIR) {
                spot = new Vector3(player.getFloorX(), y, player.getFloorZ());
            }
        }
        if (spot == null) return false;

        Position chestPosition = new Position(spot.x, spot.y, spot.z, level);
        AnvilMenu menu = new AnvilMenu(chestPosition, anvil, held, player.getInventory().getHeldItemIndex(), options);

        Block fake = Block.get(Block.CHEST);
        fake.position(chestPosition);
        level.sendBlocks(new Player[]{player}, new Vector3[]{fake});

        BlockEntityDataPacket packet = new BlockEntityDataPacket();
        packet.x = chestPosition.getFloorX();
        packet.y = chestPosition.getFloorY();
        packet.z = chestPosition.getFloorZ();
        try {
            packet.namedTag = NBTIO.write(new CompoundTag()
                    .putString("id", "Chest")
                    .putString("CustomName", "Наковальня")
                    .putInt("x", packet.x)
                    .putInt("y", packet.y)
                    .putInt("z", packet.z), ByteOrder.LITTLE_ENDIAN, true);
        } catch (IOException e) {
            menu.restoreBlock(player);
            return false;
        }
        player.dataPacket(packet);

        // The client needs a moment to create the chest before it can be opened.
        later(player, 5, () -> {
            if (player.getLevel() != level || player.addWindow(menu) == -1) {
                menu.restoreBlock(player);
            }
        });
        return true;
    }

    private static void later(Player player, int ticks, Runnable task) {
        player.getServer().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> {
            if (player.isOnline()) task.run();
        }, ticks);
    }

    /** Called for a rejected transaction. The action itself runs on the next tick. */
    public void click(Player player, int slot) {
        if (used || !getViewers().contains(player)) return;
        int index = slot - FIRST_BOOK_SLOT;
        boolean vanilla = slot == VANILLA_SLOT;
        if (!vanilla && (index < 0 || index >= shownBooks())) return;
        used = true;

        later(player, 1, () -> {
            player.removeWindow(this);
            if (player.getLevel() != anvil.getLevel() || player.distanceSquared(anvil) > 49
                    || anvil.getLevel().getBlockIdAt(anvil.getFloorX(), anvil.getFloorY(), anvil.getFloorZ()) != Block.ANVIL) {
                return;
            }
            if (vanilla) {
                later(player, 10, () -> player.addWindow(
                        new AnvilInventory(player.getUIInventory(), anvil), Player.ANVIL_WINDOW_ID));
                return;
            }
            CustomBooks.Option option = options.get(index);
            // Check again: the real inventory may have changed while the menu was open.
            if (player.getInventory().getHeldItemIndex() != heldSlot
                    || !player.getInventory().getItemInHand().equalsExact(held)
                    || !player.getInventory().getItem(option.slot()).equalsExact(option.book())) {
                player.sendMessage("§cПредметы изменились, открой наковальню ещё раз");
                return;
            }
            CustomBooks.apply(player, option);
        });
    }

    @Override
    public void onClose(Player who) {
        super.onClose(who);
        restoreBlock(who);
    }

    private void restoreBlock(Player player) {
        Level level = (Level) ((Position) chest).getLevel();
        if (player.isOnline() && player.getLevel() == level) {
            level.sendBlocks(new Player[]{player}, new Vector3[]{level.getBlock(chest)});
        }
    }
}
