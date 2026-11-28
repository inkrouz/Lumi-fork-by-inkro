package cn.nukkit.item;

import cn.nukkit.Nukkit;

import cn.nukkit.entity.effect.Effect;
import cn.nukkit.entity.effect.PotionType;

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

    private static String effectName(int id) {
        return switch (id) {
            case 1 -> "Скорость";
            case 2 -> "Замедление";
            case 3 -> "Спешка";
            case 4 -> "Усталость";
            case 5 -> "Сила";
            case 6 -> "Исцеление";
            case 7 -> "Мгновенный урон";
            case 8 -> "Прыгучесть";
            case 9 -> "Тошнота";
            case 10 -> "Регенерация";
            case 11 -> "Сопротивление";
            case 12 -> "Огнестойкость";
            case 13 -> "Подводное дыхание";
            case 14 -> "Невидимость";
            case 15 -> "Слепота";
            case 16 -> "Ночное зрение";
            case 17 -> "Голод";
            case 18 -> "Слабость";
            case 19 -> "Отравление";
            case 20 -> "Иссушение";
            case 24 -> "Левитация";
            case 27 -> "Медленное падение";
            default -> null;
        };
    }

    private static String potionOf(int aux) {
        return switch (aux) {
            case 5, 6 -> "ночного зрения";
            case 7, 8 -> "невидимости";
            case 9, 10, 11 -> "прыгучести";
            case 12, 13 -> "огнестойкости";
            case 14, 15, 16 -> "скорости";
            case 17, 18, 42 -> "замедления";
            case 19, 20 -> "подводного дыхания";
            case 21, 22 -> "исцеления";
            case 23, 24 -> "вреда";
            case 25, 26, 27 -> "яда";
            case 28, 29, 30 -> "регенерации";
            case 31, 32, 33 -> "силы";
            case 34, 35 -> "слабости";
            case 36 -> "иссушения";
            case 37, 38, 39 -> "черепашьего мастера";
            case 40, 41 -> "медленного падения";
            case 43 -> "заряда ветра";
            case 44 -> "плетения";
            case 45 -> "сочения";
            case 46 -> "заражения";
            default -> null;
        };
    }

    private static String roman(int level) {
        return switch (level) {
            case 2 -> " II";
            case 3 -> " III";
            case 4 -> " IV";
            case 5 -> " V";
            default -> level > 5 ? " " + level : "";
        };
    }

    private static String time(int ticks) {
        int sec = Math.max(0, ticks / 20);
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    /** Multi-line item name: title, effects with duration and what they do (like the vanilla tooltip). */
    public static String displayName(String base, int aux) {
        boolean splash = base.equals("minecraft:splash_potion");
        boolean lingering = base.equals("minecraft:lingering_potion");
        String prefix = splash ? "Взрывное " : lingering ? "Туманное " : "";

        String title;
        if (aux == 0) {
            title = splash ? "Взрывная бутылка с водой" : lingering ? "Туманная бутылка с водой" : "Бутылка с водой";
        } else if (aux == 1 || aux == 2) {
            title = (prefix.isEmpty() ? "Невзрачное" : prefix + "невзрачное") + " зелье" + (aux == 2 ? " (длинное)" : "");
        } else if (aux == 3) {
            title = (prefix.isEmpty() ? "Густое" : prefix + "густое") + " зелье";
        } else if (aux == 4) {
            title = (prefix.isEmpty() ? "Необычное" : prefix + "необычное") + " зелье";
        } else {
            String of = potionOf(aux);
            title = (prefix.isEmpty() ? "Зелье" : prefix + "зелье") + (of != null ? " " + of : "");
        }

        StringBuilder sb = new StringBuilder("§r").append(title);
        PotionType type = PotionType.get(aux);
        if (type == null) {
            return sb.toString();
        }
        java.util.List<Effect> effects;
        try {
            effects = type.effects().supplier().apply(splash);
        } catch (Exception e) {
            return sb.toString();
        }
        StringBuilder attr = new StringBuilder();
        for (Effect effect : effects) {
            String name = effectName(effect.getId());
            if (name == null) {
                name = effect.getName();
            }
            int level = effect.getAmplifier() + 1;
            int duration = lingering ? effect.getDuration() / 4 : effect.getDuration();
            sb.append("\n§9").append(name).append(roman(level));
            if (effect.getId() != 6 && effect.getId() != 7) {
                sb.append(" (").append(time(duration)).append(")");
            }
            switch (effect.getId()) {
                case 5 -> attr.append("\n§9+").append(3 * level).append(" к урону атаки");
                case 18 -> attr.append("\n§c-").append(4 * level).append(" к урону атаки");
                case 1 -> attr.append("\n§9+").append(20 * level).append("% к скорости");
                case 2 -> attr.append("\n§c-").append(15 * level).append("% к скорости");
                case 6 -> attr.append("\n§9Лечит на ").append(4 << (level - 1)).append(" здоровья");
                case 7 -> attr.append("\n§cУрон ").append(6 << (level - 1)).append(" здоровья");
                case 10 -> attr.append("\n§9+1 здоровья каждые ").append(level > 1 ? "1,2" : "2,5").append(" сек");
                case 19 -> attr.append("\n§c-1 здоровья каждые ").append(level > 1 ? "0,6" : "1,2").append(" сек");
                default -> {
                }
            }
        }
        if (attr.length() > 0) {
            sb.append("\n§5При применении:").append(attr);
        }
        return sb.toString();
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
