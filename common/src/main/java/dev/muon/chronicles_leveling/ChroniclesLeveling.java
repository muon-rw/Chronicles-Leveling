package dev.muon.chronicles_leveling;

import dev.muon.chronicles_leveling.compat.DynamicDifficultyCompat;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.platform.Services;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point for the Chronicles: Leveling mod.
 *
 * <p>Loader entry points (Fabric / NeoForge) call {@link #init()} after their
 * platform helper is bound. Order inside {@code init} matters:
 * <ol>
 *   <li>{@link Configs#register()} first — every other subsystem reads from it.</li>
 *   <li>{@link DynamicDifficultyCompat#init()} second — registers our provider with
 *       DD if DD is present; mod loaders are stable by this point.</li>
 * </ol>
 *
 * <p>Stat attribute registration and network channel registration both live in
 * loader-specific code because their APIs differ; common code only sees the
 * resulting {@link Services#PLATFORM platform helper}.
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
     * Flips a few vanilla attributes that we display to {@code syncable=true} so the
     * client actually sees their modified values. Vanilla leaves these off the sync
     * list because gameplay only consults them server-side; the player-facing
     * Attributes screen needs them on the client.
     *
     * <p>This is a global mutation of vanilla state and runs in common init so both
     * loaders agree. Adding more attributes here is safe — the cost is one extra
     * field per packet when the value changes.
     */
    private static void forceClientSyncableAttributes() {
        Attributes.KNOCKBACK_RESISTANCE.value().setSyncable(true);
    }
}
