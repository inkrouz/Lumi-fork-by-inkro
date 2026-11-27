package cn.nukkit.item.enchantment.book;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.EnchantmentRarity;
import cn.nukkit.item.enchantment.EnchantmentType;
import cn.nukkit.level.Position;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.registry.Registries;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import cn.nukkit.utils.Identifier;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Custom enchanted books described in custom_books.yml. */
@Log4j2
public final class CustomBooks {

    private record Definition(Identifier id, String name, int maxLevel, EnchantmentType type, int cost, Map<String, Object> nbt) {
    }

    private static final Map<String, Definition> BOOKS = new LinkedHashMap<>();

    private static final String EXAMPLE = """
            books:
              "rise:vampirism":
                name: "§cВампиризм"
                max-level: 3
                type: SWORD       # ALL, SWORD, DIGGER, BOW, ARMOR, ARMOR_HEAD ...
                cost: 5           # experience levels taken at the anvil
                nbt:              # extra tags written to the book and the item
                  rise_rarity: "epic"
            """;

    private CustomBooks() {
    }

    public static void load(Server server) {
        File file = new File(server.getDataPath(), "custom_books.yml");
        try {
            if (!file.exists()) {
                Files.writeString(file.toPath(), EXAMPLE, StandardCharsets.UTF_8);
            }
            ConfigSection books = new Config(file, Config.YAML).getSection("books");
            for (String key : books.getKeys(false)) {
                try {
                    register(key, books.getSection(key));
                } catch (Exception e) {
                    log.error("custom_books.yml: cannot load '{}'", key, e);
                }
            }
        } catch (Exception e) {
            log.error("Cannot load custom_books.yml", e);
        }
    }

    private static void register(String key, ConfigSection section) {
        Identifier id = Identifier.tryParse(key);
        if (id == null || !key.contains(":") || id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            throw new IllegalArgumentException("Use your own namespace:name");
        }
        int maxLevel = Math.max(1, Math.min(10, section.getInt("max-level", 1)));
        EnchantmentType type = EnchantmentType.valueOf(section.getString("type", "ALL").toUpperCase(java.util.Locale.ROOT));
        Definition definition = new Definition(id, section.getString("name", key), maxLevel, type,
                Math.max(0, section.getInt("cost", 1)), new LinkedHashMap<>(section.getSection("nbt")));
        Registries.ENCHANTMENT.registerCustom(new BookEnchantment(definition), false);
        BOOKS.put(id.toString(), definition);
    }

    private static final class BookEnchantment extends Enchantment {
        private final int maxLevel;

        private BookEnchantment(Definition d) {
            super(d.id(), d.name(), EnchantmentRarity.VERY_RARE, d.type());
            this.maxLevel = d.maxLevel();
        }

        @Override
        public int getMaxLevel() {
            return maxLevel;
        }

        @Override
        public boolean isTreasure() {
            return true;
        }
    }

    /** @return the book, or null for an unknown ID or a level out of range. */
    public static Item createBook(String id, int level) {
        Definition d = BOOKS.get(id);
        if (d == null || level < 1 || level > d.maxLevel()) return null;
        Item book = Item.get(Item.ENCHANTED_BOOK);
        enchant(book, d, level);
        return book;
    }

    public static String ids() {
        return String.join(", ", BOOKS.keySet());
    }

    private static void enchant(Item item, Definition d, int level) {
        item.addEnchantment(Enchantment.get(d.id().toString()).setLevel(level));
        CompoundTag tag = item.getNamedTag();
        d.nbt().forEach((name, value) -> {
            // Never let the config overwrite tags the core relies on.
            if (name.equals("ench") || name.equals("custom_ench") || name.equals("display")) return;
            if (value instanceof Integer number) tag.putInt(name, number);
            else if (value instanceof Boolean flag) tag.putBoolean(name, flag);
            else if (value instanceof Double number) tag.putDouble(name, number);
            else tag.putString(name, String.valueOf(value));
        });
        item.setNamedTag(tag);
    }

    record Option(int slot, Item book, Definition definition, int result) {
        String title() {
            return definition.name() + " §r" + Enchantment.getLevelString(result) + "\n§7Цена: " + definition.cost() + " ур.";
        }
    }

    /** Every book in the inventory that would upgrade the item. */
    private static List<Option> options(Player player, Item held) {
        List<Option> options = new ArrayList<>();
        if (held.isNull() || held.getCount() != 1 || held.getId() == Item.ENCHANTED_BOOK) return options;
        for (Map.Entry<Integer, Item> entry : player.getInventory().getContents().entrySet()) {
            Item book = entry.getValue();
            if (book.getId() != Item.ENCHANTED_BOOK || book.getCount() != 1) continue;
            for (Definition d : BOOKS.values()) {
                int bookLevel = book.getCustomEnchantmentLevel(d.id());
                if (bookLevel < 1 || !d.type().canEnchantItem(held)) continue;
                int current = held.getCustomEnchantmentLevel(d.id());
                int result = Math.min(d.maxLevel(), current == bookLevel ? current + 1 : Math.max(current, bookLevel));
                if (result > current) options.add(new Option(entry.getKey(), book.clone(), d, result));
            }
        }
        return options;
    }

    static void apply(Player player, Option option) {
        Item held = player.getInventory().getItemInHand();
        Definition d = option.definition();
        if (!player.isCreative()) {
            if (player.getExperienceLevel() < d.cost()) {
                player.sendMessage("§cНужно уровней опыта: " + d.cost());
                return;
            }
            player.setExperience(player.getExperience(), player.getExperienceLevel() - d.cost());
        }
        Item upgraded = held.clone();
        enchant(upgraded, d, option.result());
        player.getInventory().setItem(option.slot(), Item.get(Item.AIR));
        player.getInventory().setItemInHand(upgraded);
        player.sendMessage("§aКнига применена: " + d.name() + " §f" + Enchantment.getLevelString(option.result()));
    }

    /** Quick apply (sneak + tap): uses the first suitable book. */
    public static boolean applyAtAnvil(Player player) {
        List<Option> options = options(player, player.getInventory().getItemInHand());
        if (options.isEmpty()) return false;
        apply(player, options.get(0));
        return true;
    }

    /** @return false if there is nothing to offer, so the normal anvil should open. */
    public static boolean openMenu(Player player, Position anvil) {
        Item held = player.getInventory().getItemInHand().clone();
        List<Option> options = options(player, held);
        return !options.isEmpty() && AnvilMenu.open(player, anvil, held, options);
    }
}
