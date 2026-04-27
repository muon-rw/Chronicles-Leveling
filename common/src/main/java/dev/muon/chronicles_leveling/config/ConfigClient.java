package dev.muon.chronicles_leveling.config;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import net.minecraft.resources.Identifier;

/**
 * Client-only preferences. Loaded on the client and never synced from the server.
 *
 * @see ConfigServer for server-only, non-synced settings
 * @see ConfigSync   for server-authoritative settings that sync to clients
 */
public class ConfigClient extends Config {

    public ConfigClient() {
        super(Identifier.fromNamespaceAndPath(ChroniclesLeveling.MOD_ID, "client"));
    }

    @Comment("If true, the level-up screen plays a chime + shows a confirmation flash on level-up.")
    public ValidatedBoolean playLevelUpFeedback = new ValidatedBoolean(true);
}
