package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.event.inventory.EnchantItemEvent;
import cn.nukkit.inventory.EnchantInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.transaction.action.EnchantingAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.ItemBookEnchanted;
import cn.nukkit.item.ItemArmor;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.ItemLapisLazuli;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EnchantTransaction extends InventoryTransaction {

    private Item inputItem;
    private Item outputItem;
    private int cost = -1;

    public EnchantTransaction(Player source, List<InventoryAction> actions) {
        super(source, actions);
        for (InventoryAction action : actions) {
            if (action instanceof SlotChangeAction slotChangeAction) {
                if (slotChangeAction.getInventory() instanceof EnchantInventory && slotChangeAction.getSlot() == 0) {
                    this.outputItem = slotChangeAction.getTargetItem();
                }
            }
        }
    }

    @Override
    public boolean canExecute() {
        if (this.invalid || !super.canExecute()) return false;
        Inventory inv = getSource().getWindowById(Player.ENCHANT_WINDOW_ID);
        if (inv == null) return false;
        EnchantInventory eInv = (EnchantInventory) inv;
        if (!getSource().isCreative()) {
            if (cost == -1 || !eInv.getReagentSlot().equals(new ItemLapisLazuli(), true, false) || eInv.getReagentSlot().count < cost)
                return false;
        }
        return inputItem != null && outputItem != null
                && inputItem.equals(eInv.getInputSlot(), true, true)
                && (inputItem.getId() == outputItem.getId() || (inputItem.getId() == Item.BOOK && outputItem.getId() == Item.ENCHANTED_BOOK))
                && (inputItem.getCount() == outputItem.getCount()
                        || (outputItem.getId() == Item.ENCHANTED_BOOK && outputItem.getCount() == 1))
                && this.checkEnchantValid();
    }

    @Override
    public boolean execute() {
        // This will validate the enchant conditions
        if (this.hasExecuted || !this.canExecute()) {
            source.removeAllWindows(false);
            this.sendInventories();
            return false;
        }
        EnchantInventory inv = (EnchantInventory) getSource().getWindowById(Player.ENCHANT_WINDOW_ID);
        EnchantItemEvent ev = new EnchantItemEvent(inv, inputItem, outputItem, cost, source);
        if (!ev.call()) {
            source.removeAllWindows(false);
            this.sendInventories();
            // Cancelled by plugin, means handled OK
            return true;
        }
        // This will process all the slot changes
        for (InventoryAction a : this.actions) {
            if (a.execute(source)) {
                a.onExecuteSuccess(source);
            } else {
                a.onExecuteFail(source);
            }
        }

        if (!ev.getNewItem().equals(this.outputItem, true, true)) {
            // Plugin changed item, so the previous slot change is going to be invalid
            // Send the replaced item to the enchant inventory manually
            inv.setItem(0, ev.getNewItem(), true);
        }

        if (!source.isCreative()) {
            source.setExperience(source.getExperience(), source.getExperienceLevel() - ev.getXpCost());
        }
        return true;
    }

    @Override
    public void addAction(InventoryAction action) {
        super.addAction(action);
        if (action instanceof EnchantingAction) {
            switch (((EnchantingAction) action).getType()) {
                case NetworkInventoryAction.SOURCE_TYPE_ENCHANT_INPUT:
                    this.inputItem = action.getTargetItem(); // Input sent as newItem
                    break;
                case NetworkInventoryAction.SOURCE_TYPE_ENCHANT_OUTPUT:
                    this.outputItem = action.getSourceItem(); // Output sent as oldItem
                    break;
                case NetworkInventoryAction.SOURCE_TYPE_ENCHANT_MATERIAL:
                    if (action.getTargetItem().equals(Item.get(Item.AIR), false, false)) {
                        this.cost = action.getSourceItem().count;
                    } else {
                        this.cost = action.getSourceItem().count - action.getTargetItem().count;
                    }
                    break;
            }

        }
    }

    public boolean checkForEnchantPart(List<InventoryAction> actions) {
        for (InventoryAction action : actions) {
            if (action instanceof EnchantingAction) return true;
        }
        return false;
    }

    public boolean checkEnchantValid() {
        if (!(outputItem instanceof ItemTool || outputItem instanceof ItemArmor || outputItem instanceof ItemBookEnchanted)) {
            source.getServer().getLogger().debug("Non-enchantable item");
            return false;
        }

        if (this.inputItem.hasEnchantments()) {
            source.getServer().getLogger().debug("Illegal enchantment input has enchantments");
            return false;
        }

        Enchantment[] enchantments = this.outputItem.getEnchantments();
        if (enchantments.length < 1 || enchantments.length > 4) {
            source.getServer().getLogger().debug("Illegal enchantment count: " + enchantments.length);
            return false;
        }

        IntOpenHashSet added = new IntOpenHashSet(4, 1);
        for (Enchantment e : enchantments) {
            if (e.isTreasure()) {
                source.getServer().getLogger().debug("Illegal treasure enchantment: " + e.getId());
                return false;
            }

            if (!added.add(e.getId())) {
                source.getServer().getLogger().debug("Illegal duplicate enchantment: " + e.getId());
                return false;
            }

            if (e.getLevel() < 1 || e.getLevel() > e.getMaxLevel()) {
                source.getServer().getLogger().debug("Illegal enchantment level " + e.getLevel() + " for " + e.getId());
                return false;
            }

            if (this.inputItem.getId() != Item.BOOK && !e.canEnchant(this.inputItem)) {
                source.getServer().getLogger().debug("Illegal incompatible enchantment: " + e.getId());
                return false;
            }

            for (Enchantment e2 : enchantments) {
                if (e == e2) {
                    continue;
                }

                if (!e.isCompatibleWith(e2)) {
                    source.getServer().getLogger().debug("Illegal enchantment " + e.getId() + " incompatible with " + e2.getId());
                    return false;
                }
            }
        }

        CompoundTag a = this.inputItem.getNamedTag();
        a = a == null ? new CompoundTag() : a.copy().remove("ench");
        CompoundTag b = this.outputItem.getNamedTag();
        b = b == null ? new CompoundTag() : b.copy().remove("ench");
        if (!a.equals(b)) {
            if (cn.nukkit.Nukkit.DEBUG > 1) {
                source.getServer().getLogger().debug("NBT check failed: input=" + a + ", output=" + b);
            }
            return false;
        }

        return true;
    }
}
