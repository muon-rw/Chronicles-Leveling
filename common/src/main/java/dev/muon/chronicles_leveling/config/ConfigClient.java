package dev.muon.chronicles_leveling.config;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import net.minecraft.resources.Identifier;

import java.util.List;

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

    @Comment("Curated list of attribute IDs shown on the Attributes tab, in order. Combat-relevant by default.")
    public ValidatedList<String> attributesPageEntries = ValidatedList.ofString(List.of(
            // Vanilla baseline that's always present.
            "minecraft:generic.max_health",
            "minecraft:generic.armor",
            "minecraft:generic.armor_toughness",
            "minecraft:generic.attack_damage",
            "minecraft:generic.attack_speed",
            "minecraft:generic.movement_speed",
            "minecraft:generic.luck",
            // Combat-Attributes (only renders if registered).
            "combat_attributes:melee_crit_chance",
            "combat_attributes:melee_crit_damage",
            "combat_attributes:ranged_damage",
            "combat_attributes:ranged_crit_chance",
            "combat_attributes:ranged_crit_damage",
            "combat_attributes:magic_crit_chance",
            "combat_attributes:magic_crit_damage",
            "combat_attributes:evasion",
            "combat_attributes:lifesteal"
    ));
}
