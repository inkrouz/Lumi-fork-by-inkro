package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.event.inventory.LoomItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.LoomInventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.LoomItemAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.ItemNamespaceId;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;

import java.util.List;

public class LoomTransaction extends InventoryTransaction {

    private Item outputItem;

    public LoomTransaction(Player source, List<InventoryAction> actions) {
        super(source, actions);
    }

    @Override
    public void addAction(InventoryAction action) {
        super.addAction(action);

        if (action instanceof LoomItemAction) {
            outputItem = action.getSourceItem();
        }
    }

    @Override
    public boolean canExecute() {
        if (!super.canExecute()) {
            return false;
        }

        Inventory inventory = getSource().getWindowById(Player.LOOM_WINDOW_ID);
        if (!(inventory instanceof LoomInventory)) {
            return false;
        }

        if (outputItem == null) {
            return false;
        }

        LoomInventory loomInventory = (LoomInventory) inventory;
        Item banner = loomInventory.getBanner();
        Item dye = loomInventory.getDye();
        if (banner.getId() != Item.BANNER || !(dye instanceof ItemDye) || banner.getDamage() != outputItem.getDamage()) {
            return false;
        }

        Item pattern = loomInventory.getPattern();
        if (pattern.getId() != 0 && (pattern.getId() != ItemID.BANNER_PATTERN && !pattern.getNamespaceId().equals(ItemNamespaceId.GUSTER_BANNER_PATTERN) && !pattern.getNamespaceId().equals(ItemNamespaceId.FLOW_BANNER_PATTERN))) {
            return false;
        }

        CompoundTag bannerTag = snapshotNamedTag(banner);
        if (bannerTag == null) {
            bannerTag = new CompoundTag();
        }
        if (bannerTag.contains("Patterns") && !(bannerTag.get("Patterns") instanceof ListTag<?>)) {
            return false;
        }
        ListTag<?> bannerPatterns = bannerTag.getList("Patterns");
        bannerTag.remove("Patterns");

        long outputCount = 0;
        for (InventoryAction action : this.actions) {
            if (!(action instanceof LoomItemAction)) {
                continue;
            }
            Item output = action.getSourceItem();
            if (output.getId() != Item.BANNER || output.isNull() || banner.getDamage() != output.getDamage()) {
                return false;
            }
            outputCount += output.getCount();
            if (outputCount > banner.getCount() || outputCount > dye.getCount()) {
                return false;
            }
            CompoundTag outputTag = snapshotNamedTag(output);
            if (outputTag == null || !(outputTag.get("Patterns") instanceof ListTag<?> outputPatterns)
                    || outputPatterns.size() > 6 || bannerPatterns.size() + 1 != outputPatterns.size()) {
                return false;
            }
            for (int i = 0; i < bannerPatterns.size(); i++) {
                if (!bannerPatterns.get(i).equals(outputPatterns.get(i))) {
                    return false;
                }
            }
            outputTag.remove("Patterns");
            if (!bannerTag.equals(outputTag)) {
                return false;
            }
        }
        return true;
    }

    private static CompoundTag snapshotNamedTag(Item item) {
        if (!item.hasCompoundTag()) {
            return null;
        }
        CompoundTag snapshot = item.getNamedTag().copy();
        snapshot.setName("");
        return snapshot;
    }

    @Override
    protected boolean callExecuteEvent() {
        LoomInventory inventory = (LoomInventory) getSource().getWindowById(Player.LOOM_WINDOW_ID);
        LoomItemEvent event = new LoomItemEvent(inventory, this.outputItem, this.source);
        if (!event.call()) {
            this.source.removeAllWindows(false);
            this.sendInventories();
            return false;
        }
        return true;
    }

    @Override
    protected void sendInventories() {
        this.source.removeAllWindows(false);
        super.sendInventories();
    }

    public Item getOutputItem() {
        return this.outputItem;
    }

    public static boolean isIn(List<InventoryAction> actions) {
        for (InventoryAction action : actions) {
            if (action instanceof LoomItemAction) return true;
        }
        return false;
    }


    public boolean checkForItemPart(List<InventoryAction> actions) {
        return actions.stream().anyMatch(it-> it instanceof LoomItemAction);
    }
}
