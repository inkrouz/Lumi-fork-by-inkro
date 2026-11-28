package cn.nukkit.item.enchantment.custom;

import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.EnchantmentDrill;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;

import java.util.List;

/**
 * Shows custom enchantments as lore lines (name + description) and does not touch the item name.
 * Lines added by this class start with an invisible marker, so other lore stays intact.
 */
public final class CustomEnchantmentDisplayLore implements CustomEnchantmentDisplay {

    private static final String MARK = "§r§k§r";

    @Override
    public void apply(Item item, List<Enchantment> customEnchantments) {
        CompoundTag tag = !item.hasCompoundTag() ? new CompoundTag() : item.getNamedTag();
        CompoundTag display = tag.contains("display") && tag.get("display") instanceof CompoundTag
                ? tag.getCompound("display") : new CompoundTag();

        ListTag<StringTag> lore = new ListTag<>("Lore");
        if (display.contains("Lore")) {
            for (StringTag line : display.getList("Lore", StringTag.class).getAll()) {
                if (!line.data.startsWith(MARK)) {
                    lore.add(line);
                }
            }
        }

        for (Enchantment enchantment : customEnchantments) {
            lore.add(new StringTag("", MARK + "§r§7" + enchantment.getName()));
            if (enchantment instanceof EnchantmentDrill drill) {
                lore.add(new StringTag("", MARK + "§r§8" + drill.getDescription()));
            }
        }

        if (lore.size() > 0) {
            display.putList("Lore", lore);
        } else {
            display.remove("Lore");
        }

        if (display.isEmpty()) {
            tag.remove("display");
        } else {
            tag.putCompound("display", display);
        }
        item.setNamedTag(tag);
    }
}
