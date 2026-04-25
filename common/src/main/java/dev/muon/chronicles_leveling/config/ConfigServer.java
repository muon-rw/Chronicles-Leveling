package dev.muon.chronicles_leveling.config;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import net.minecraft.resources.Identifier;

/**
 * Server-only configuration. Loaded on the logical server and never sent to clients.
 *
 * @see ConfigClient for client-only, non-synced settings
 * @see ConfigSync   for server-authoritative settings that sync to clients
 */
public class ConfigServer extends Config {

    public ConfigServer() {
        super(Identifier.fromNamespaceAndPath(ChroniclesLeveling.MOD_ID, "server"));
    }

    @Comment("Emit verbose server-side debug logging for this mod.")
    public ValidatedBoolean verboseLogging = new ValidatedBoolean(false);
}
