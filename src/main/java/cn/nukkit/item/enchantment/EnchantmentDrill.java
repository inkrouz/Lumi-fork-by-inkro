package cn.nukkit.item.enchantment;

import cn.nukkit.item.Item;

/**
 * Ruppim custom enchantment "Бур": the pickaxe / shovel mines a 3x3 area.
 */
public class EnchantmentDrill extends Enchantment {

    public static final String IDENTIFIER = "ruppim:drill";
    public static final String DISPLAY_NAME = "Бур";
    public static final String DESCRIPTION = "Копает область 3×3 блока";

    public EnchantmentDrill() {
        super(IDENTIFIER, DISPLAY_NAME, EnchantmentRarity.RARE, EnchantmentType.DIGGER);
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean canEnchant(Item item) {
        return item.isPickaxe() || item.isShovel();
    }

    public String getDescription() {
        return DESCRIPTION;
    }
}
