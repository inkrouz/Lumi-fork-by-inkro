package cn.nukkit.level.light;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Block light flood fill: C++ when the library loads, identical Java code otherwise. */
public final class NativeLight {
    private static final boolean AVAILABLE = load();

    private NativeLight() {
    }

    private static boolean load() {
        if (!System.getProperty("os.name").toLowerCase().contains("linux")) {
            return false;
        }
        String arch = System.getProperty("os.arch");
        String libPath;
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            libPath = "/native/linux-x64/liblumilight.so";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            libPath = "/native/linux-aarch64/liblumilight.so";
        } else {
            return false;
        }
        try (InputStream in = NativeLight.class.getResourceAsStream(libPath)) {
            if (in == null) return false;
            Path file = Files.createTempFile("lumilight", ".so");
            file.toFile().deleteOnExit();
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            System.load(file.toAbsolutePath().toString());
            return true;
        } catch (Exception | UnsatisfiedLinkError e) {
            return false;
        }
    }

    private static native void flood0(byte[] light, byte[] filter);

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** Arrays are indexed (y << 8) | (z << 4) | x, with y starting at 0. */
    public static void flood(byte[] light, byte[] filter) {
        if (AVAILABLE) flood0(light, filter);
        else floodJava(light, filter);
    }

    static void floodJava(byte[] light, byte[] filter) {
        int size = light.length, height = size >> 8;
        // Each cell is queued at most once per level it is raised to.
        int[][] buckets = new int[16][16];
        int[] count = new int[16];
        for (int i = 0; i < size; i++) {
            if (light[i] > 1) push(buckets, count, light[i], i);
        }
        for (int level = 15; level > 1; level--) {
            for (int k = 0; k < count[level]; k++) {
                int i = buckets[level][k];
                if (light[i] != level) continue;
                int x = i & 15, z = (i >> 4) & 15, y = i >> 8;
                for (int side = 0; side < 6; side++) {
                    int n = switch (side) {
                        case 0 -> x > 0 ? i - 1 : -1;
                        case 1 -> x < 15 ? i + 1 : -1;
                        case 2 -> z > 0 ? i - 16 : -1;
                        case 3 -> z < 15 ? i + 16 : -1;
                        case 4 -> y > 0 ? i - 256 : -1;
                        default -> y < height - 1 ? i + 256 : -1;
                    };
                    if (n < 0) continue;
                    int next = level - Math.max(1, filter[n]);
                    if (next > light[n]) {
                        light[n] = (byte) next;
                        if (next > 1) push(buckets, count, next, n);
                    }
                }
            }
        }
    }

    private static void push(int[][] buckets, int[] count, int level, int index) {
        if (count[level] == buckets[level].length) {
            buckets[level] = java.util.Arrays.copyOf(buckets[level], count[level] * 2);
        }
        buckets[level][count[level]++] = index;
    }
}
