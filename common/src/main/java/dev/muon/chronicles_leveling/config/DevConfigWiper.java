package dev.muon.chronicles_leveling.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Dev-only wipe of the per-mod FzzyConfig directories listed in {@link #TARGETS}.
 * Each loader's {@code MixinConfigPlugin.onLoad} guards the call with its own
 * {@code isDevelopmentEnvironment} check and then invokes {@link #wipe()} so
 * the deletion happens before any mod's {@code registerAndLoadConfig} runs —
 * otherwise a sibling muon-mod whose entrypoint sorts ahead of ours would
 * already have read the old toml.
 *
 * <p>To preserve configs for a single dev session (e.g. to verify load/save
 * behavior), pass {@code -Dchronicles_leveling.dev.preserveConfigs=true} as a
 * VM option on the run config.
 */
public final class DevConfigWiper {

    private static final Logger LOG = LogManager.getLogger("ChroniclesLeveling-DevConfigWiper");
    private static final String PRESERVE_FLAG = "chronicles_leveling.dev.preserveConfigs";

    private static final List<String> TARGETS = List.of(
            "chronicles_leveling",
            "combat_attributes",
            "dynamic_difficulty",
            "dynamictooltips"
    );

    private DevConfigWiper() {}

    public static void wipe() {
        if (Boolean.getBoolean(PRESERVE_FLAG)) return;
        Path configDir = Path.of("config");
        for (String modId : TARGETS) {
            wipeDir(configDir.resolve(modId));
        }
    }

    private static void wipeDir(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    LOG.warn("Failed to delete {}: {}", p, e.toString());
                }
            });
            LOG.info("Wiped dev configs at {}", dir);
        } catch (IOException e) {
            LOG.warn("Failed to walk {}: {}", dir, e.toString());
        }
    }
}
