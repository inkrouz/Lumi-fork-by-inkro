package cn.nukkit.item;

import cn.nukkit.Nukkit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Stackable potions with vanilla icons.
 * <p>
 * Every potion type (aux 0..46) of each of the three potion kinds is sent to the client as its own
 * data-driven item with max_stack_size 64 and its own vanilla icon. On the server side the items stay
 * ordinary potions (same id and damage), the translation happens in {@link RuntimeItemMapping}.
 */
public final class PotionVariants {

    public static final int COUNT = 47;
    public static final String PREFIX = "ruppim:potion_";

    private static String mode;

    private PotionVariants() {
    }

    /** "variants" (default) or "legacy". Any other value in ruppim.properties is treated as "variants". */
    public static synchronized String mode() {
        if (mode != null) {
            return mode;
        }
        String value = "variants";
        try {
            File file = new File(Nukkit.DATA_PATH, "ruppim.properties");
            Properties props = new Properties();
            if (file.exists()) {
                try (InputStream in = new FileInputStream(file)) {
                    props.load(in);
                }
            } else {
                props.setProperty("potion-mode", "variants");
                try (OutputStream out = new FileOutputStream(file)) {
                    props.store(out, "Ruppim settings. potion-mode: variants | legacy");
                }
            }
            value = props.getProperty("potion-mode", "variants").trim().toLowerCase();
        } catch (Exception ignored) {
        }
        mode = value.equals("legacy") ? "legacy" : "variants";
        return mode;
    }

    public static boolean enabled() {
        return mode().equals("variants");
    }

    public static boolean isBase(String identifier) {
        return identifier.equals("minecraft:potion")
                || identifier.equals("minecraft:splash_potion")
                || identifier.equals("minecraft:lingering_potion");
    }

    public static String variantIdentifier(String base, int aux) {
        String kind = switch (base) {
            case "minecraft:splash_potion" -> "splash_";
            case "minecraft:lingering_potion" -> "lingering_";
            default -> "";
        };
        return PREFIX + kind + aux;
    }

    public static String iconKey(String base, int aux) {
        String kind = switch (base) {
            case "minecraft:splash_potion" -> "s";
            case "minecraft:lingering_potion" -> "l";
            default -> "p";
        };
        return "ruppim_" + kind + "_" + aux;
    }

    private static String effect(int aux) {
        return switch (aux) {
            case 5, 6 -> "nightVision";
            case 7, 8 -> "invisibility";
            case 9, 10, 11 -> "jump";
            case 12, 13 -> "fireResistance";
            case 14, 15, 16 -> "moveSpeed";
            case 17, 18, 42 -> "moveSlowdown";
            case 19, 20 -> "waterBreathing";
            case 21, 22 -> "heal";
            case 23, 24 -> "harm";
            case 25, 26, 27 -> "poison";
            case 28, 29, 30 -> "regeneration";
            case 31, 32, 33 -> "damageBoost";
            case 34, 35 -> "weakness";
            case 36 -> "wither";
            case 37, 38, 39 -> "turtleMaster";
            case 40, 41 -> "slowFalling";
            case 43 -> "windCharged";
            case 44 -> "weaving";
            case 45 -> "oozing";
            case 46 -> "infested";
            default -> "emptyPotion";
        };
    }

    /** Vanilla localization key for the item name. */
    public static String langKey(String base, int aux) {
        String suffix = switch (base) {
            case "minecraft:splash_potion" -> ".splash.name";
            case "minecraft:lingering_potion" -> ".linger.name";
            default -> ".name";
        };
        return switch (aux) {
            case 0 -> "potion.emptyPotion" + suffix;
            case 1 -> "potion.mundane" + suffix;
            case 2 -> "potion.mundane.extended" + suffix;
            case 3 -> "potion.thick" + suffix;
            case 4 -> "potion.awkward" + suffix;
            default -> "potion." + effect(aux) + suffix;
        };
    }
}
