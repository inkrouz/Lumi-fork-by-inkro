package cn.nukkit.resourcepacks.loader;

import cn.nukkit.Nukkit;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.resourcepacks.ZippedResourcePack;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Loads the resource pack bundled inside the core jar.
 */
@Log4j2
public class BuiltinResourcePackLoader implements ResourcePackLoader {

    private static final String NAME = "ruppim_icons.mcpack";

    @Override
    public List<ResourcePack> loadPacks() {
        try (InputStream stream = BuiltinResourcePackLoader.class.getClassLoader().getResourceAsStream(NAME)) {
            if (stream == null) {
                log.warn("Built-in resource pack {} is missing from the jar", NAME);
                return List.of();
            }
            Path folder = Path.of(Nukkit.DATA_PATH, ".ruppim");
            Files.createDirectories(folder);
            Path file = folder.resolve(NAME);
            Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING);
            ResourcePack pack = new ZippedResourcePack(file.toFile());
            log.info("Ruppim built-in resource pack loaded: {} v{}", pack.getPackName(), pack.getPackVersion());
            return List.of(pack);
        } catch (Exception e) {
            log.error("Unable to load built-in resource pack", e);
            return List.of();
        }
    }
}
