package dev.muon.chronicles_leveling.platform;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

public class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    // Each platform supplies its implementation through a META-INF/services file named for the service
    // interface, whose contents are the fully qualified implementation class for that loader.
    public static <T> T load(Class<T> clazz) {

        final T loadedService = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        ChroniclesLeveling.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}