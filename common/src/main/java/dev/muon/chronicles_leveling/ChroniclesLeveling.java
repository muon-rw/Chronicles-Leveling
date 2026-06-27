package dev.muon.chronicles_leveling;

import dev.muon.chronicles_leveling.compat.DynamicDifficultyCompat;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.platform.Services;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point. Order inside {@link #init()} matters: {@link Configs#register()} runs
 * first because every other subsystem reads from it, then {@link DynamicDifficultyCompat#init()}.
 */
public class ChroniclesLeveling {

    public static final String MOD_ID = "chronicles_leveling";
    public static final String MOD_NAME = "ChroniclesLeveling";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void init() {
        Configs.register();
        DynamicDifficultyCompat.init();
        forceClientSyncableAttributes();

        LOG.info("Chronicles: Leveling initialized on {} ({})",
                Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
    }

    /**
     * Flips a few vanilla attributes we display to {@code syncable=true} so the client sees their
     * modified values. Vanilla leaves these off the sync list because gameplay only consults them
     * server-side; the player-facing Attributes screen needs them on the client. The cost of adding
     * more here is one extra field per packet when the value changes.
     */
    private static void forceClientSyncableAttributes() {
        Attributes.KNOCKBACK_RESISTANCE.value().setSyncable(true);
    }
}
