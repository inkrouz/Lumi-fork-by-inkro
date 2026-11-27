package cn.nukkit.rise;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Rise native core: loads the C++ library that runs compute-heavy kernels
 * (explosion ray casting, entity exposure) and exposes them to Java.
 * <p>
 * On startup the folder {@code <server>/rise/native/} is created on the host and the
 * library is placed there:
 * <ol>
 *   <li>{@code rise/native/override/<lib>} - a hand-made build wins if present;</li>
 *   <li>otherwise the prebuilt library bundled in the jar is extracted to {@code rise/native/<lib>};</li>
 *   <li>otherwise, if g++ exists on the host, bundled sources are extracted to
 *       {@code rise/native/src/} and compiled there.</li>
 * </ol>
 * If nothing works the server keeps running on the pure-Java implementation.
 * Disable with {@code -Drise.native=false} or env {@code RISE_NATIVE=false}.
 */
public final class NativeCore {

    public static final int EXPECTED_ABI = 1;
    private static final String[] SOURCES = {"rise_core.h", "rise_core.cpp", "rise_jni.cpp"};

    private static volatile boolean available;
    private static boolean initialized;

    private NativeCore() {
    }

    public static boolean isAvailable() {
        return available;
    }

    // ---- native methods (see native/rise_jni.cpp) ----
    private static native int abiVersion();

    private static native int[] explosionRays(float[] resist, int ox, int oy, int oz, int dx, int dy, int dz,
                                              double cx, double cy, double cz, double size, double fireChance,
                                              int minY, int maxY, long seed);

    private static native float[] blockDensityBatch(byte[] solid, int ox, int oy, int oz, int dx, int dy, int dz,
                                                    double sx, double sy, double sz, double[] bbs, int count);

    // ---- safe wrappers: return null when native is unavailable/failed ----

    /**
     * @param resist grid, index = (y * dz + z) * dx + x, value &lt; 0 = air
     * @return encoded cells (bit 31 = ignite) or null
     */
    public static int[] rays(float[] resist, int ox, int oy, int oz, int dx, int dy, int dz,
                             double cx, double cy, double cz, double size, double fireChance,
                             int minY, int maxY, long seed) {
        if (!available) return null;
        try {
            return explosionRays(resist, ox, oy, oz, dx, dy, dz, cx, cy, cz, size, fireChance, minY, maxY, seed);
        } catch (Throwable t) {
            disable(t);
            return null;
        }
    }

    /**
     * @return exposure per entity (same value as Level#getBlockDensity), -1 = use Java fallback; or null
     */
    public static float[] density(byte[] solid, int ox, int oy, int oz, int dx, int dy, int dz,
                                  double sx, double sy, double sz, double[] bbs, int count) {
        if (!available) return null;
        try {
            return blockDensityBatch(solid, ox, oy, oz, dx, dy, dz, sx, sy, sz, bbs, count);
        } catch (Throwable t) {
            disable(t);
            return null;
        }
    }

    private static void disable(Throwable t) {
        available = false;
        System.err.println("[Rise] native core failed at runtime, falling back to Java: " + t);
    }

    // ---- loading ----

    public static synchronized boolean init(File serverDir, Consumer<String> info, Consumer<String> warn) {
        if (initialized) return available;
        initialized = true;

        if ("false".equalsIgnoreCase(System.getProperty("rise.native"))
                || "false".equalsIgnoreCase(System.getenv("RISE_NATIVE"))) {
            info.accept("Rise native core disabled by configuration, using Java implementation");
            return false;
        }

        String os = osName();
        String arch = archName();
        if (os == null || arch == null) {
            warn.accept("Rise native core: unsupported platform, using Java implementation");
            return false;
        }
        String libName = libFileName(os);

        File dir = new File(serverDir, "rise" + File.separator + "native");
        try {
            Files.createDirectories(dir.toPath());
        } catch (IOException e) {
            warn.accept("Rise native core: cannot create " + dir + ": " + e);
            return false;
        }

        File lib = resolveLibrary(dir, libName, os, arch, info, warn);
        if (lib == null) {
            warn.accept("Rise native core not available, using Java implementation");
            return false;
        }

        try {
            System.load(lib.getAbsolutePath());
            int abi = abiVersion();
            if (abi != EXPECTED_ABI) {
                warn.accept("Rise native core ABI mismatch (library " + abi + ", expected " + EXPECTED_ABI
                        + "), using Java implementation. Delete " + lib + " and restart.");
                return false;
            }
            available = true;
            info.accept("Rise native core loaded: " + lib.getPath());
        } catch (Throwable t) {
            warn.accept("Rise native core failed to load (" + t + "), using Java implementation");
        }
        return available;
    }

    private static File resolveLibrary(File dir, String libName, String os, String arch,
                                       Consumer<String> info, Consumer<String> warn) {
        // 1. manual override
        File override = new File(new File(dir, "override"), libName);
        if (override.isFile()) {
            info.accept("Rise native core: using override " + override.getPath());
            return override;
        }

        File target = new File(dir, libName);
        String resource = "/rise-native/" + os + "-" + arch + "/" + libName;

        // 2. prebuilt library from the jar
        try (InputStream in = NativeCore.class.getResourceAsStream(resource)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                if (!target.isFile() || !sha256(Files.readAllBytes(target.toPath())).equals(sha256(bytes))) {
                    File tmp = new File(dir, libName + ".tmp");
                    Files.write(tmp.toPath(), bytes);
                    try {
                        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        // file may be locked (Windows); keep existing one if present
                        tmp.delete();
                        if (!target.isFile()) throw e;
                    }
                }
                return target;
            }
        } catch (Exception e) {
            warn.accept("Rise native core: cannot extract bundled library: " + e);
        }

        // 3. library already on disk (e.g. uploaded by hand)
        if (target.isFile()) return target;

        // 4. build from bundled sources if g++ is available
        return tryCompile(dir, libName, info, warn);
    }

    private static File tryCompile(File dir, String libName, Consumer<String> info, Consumer<String> warn) {
        File jniInclude = new File(System.getProperty("java.home"), "include");
        if (!jniInclude.isDirectory()) {
            warn.accept("Rise native core: no prebuilt library for this platform and no JDK headers to build one");
            return null;
        }
        try {
            File src = new File(dir, "src");
            Files.createDirectories(src.toPath());
            for (String name : SOURCES) {
                try (InputStream in = NativeCore.class.getResourceAsStream("/rise-native/src/" + name)) {
                    if (in == null) {
                        warn.accept("Rise native core: sources not bundled in jar");
                        return null;
                    }
                    Files.copy(in, new File(src, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            File out = new File(dir, libName);
            String osInc = new File(jniInclude, osIncludeDir()).getPath();
            ProcessBuilder pb = new ProcessBuilder("g++", "-O3", "-std=c++17", "-ffp-contract=off", "-shared", "-fPIC",
                    "-I" + jniInclude.getPath(), "-I" + osInc,
                    new File(src, "rise_core.cpp").getPath(), new File(src, "rise_jni.cpp").getPath(),
                    "-static-libstdc++", "-static-libgcc", "-o", out.getPath());
            pb.redirectErrorStream(true);
            pb.redirectOutput(new File(dir, "build.log"));
            info.accept("Rise native core: building from sources with g++ ...");
            Process p = pb.start();
            if (!p.waitFor(180, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                warn.accept("Rise native core: build timed out");
                return null;
            }
            if (p.exitValue() != 0 || !out.isFile()) {
                warn.accept("Rise native core: build failed, see " + new File(dir, "build.log"));
                return null;
            }
            return out;
        } catch (Exception e) {
            warn.accept("Rise native core: build not possible: " + e);
            return null;
        }
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("linux")) return "linux";
        return null;
    }

    private static String osIncludeDir() {
        return "windows".equals(osName()) ? "win32" : "linux";
    }

    private static String archName() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (a.equals("amd64") || a.equals("x86_64")) return "x86_64";
        if (a.equals("aarch64") || a.equals("arm64")) return "aarch64";
        return null;
    }

    private static String libFileName(String os) {
        return "windows".equals(os) ? "rise_native.dll" : "librise_native.so";
    }
}
