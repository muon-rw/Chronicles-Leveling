package dev.muon.chronicles_leveling.config;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigSection;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.minecraft.ValidatedIdentifier;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Client-only preferences. Loaded on the client and never synced from the server.
 * @see ConfigStats   for server-authoritative settings that sync to clients
 */
public class ConfigClient extends Config {

    public ConfigClient() {
        super(Identifier.fromNamespaceAndPath(ChroniclesLeveling.MOD_ID, "client"));
    }

    @Comment("If true, the level-up screen plays a chime + shows a confirmation flash on level-up.")
    public ValidatedBoolean playLevelUpFeedback = new ValidatedBoolean(true);

    public AttributePages attributePages = new AttributePages();

    /**
     * Per-category attribute lists for the Attributes screen. Each entry is a
     * registry id; unregistered IDs are silently dropped at render time and
     * logged once per JVM session.
     *
     * <p>Fzzy_config's {@link ValidatedIdentifier} validates the resource-location
     * format only — not registry membership — because Combat-Attributes IDs
     * register too late to be resolvable when the config is first deserialized.
     */
    public static class AttributePages extends ConfigSection {

        @Comment("Attributes shown under the Melee category, in order.")
        public ValidatedList<Identifier> melee = list(
                "minecraft:attack_damage",
                "minecraft:attack_speed",
                "combat_attributes:melee_crit_chance",
                "combat_attributes:melee_crit_damage"
        );

        @Comment("Attributes shown under the Ranged category, in order.")
        public ValidatedList<Identifier> ranged = list(
                "combat_attributes:ranged_damage",
                "combat_attributes:draw_speed",
                "combat_attributes:arrow_velocity",
                "combat_attributes:ranged_crit_chance",
                "combat_attributes:ranged_crit_damage"
        );

        @Comment("Attributes shown under the Defense category, in order.")
        public ValidatedList<Identifier> defense = list(
                "minecraft:max_health",
                "minecraft:armor",
                "minecraft:armor_toughness",
                "minecraft:knockback_resistance",
                "combat_attributes:magic_defense",
                "combat_attributes:evasion"
        );

        @Comment("Attributes shown under the Magic category, in order.")
        public ValidatedList<Identifier> magic = list(
                "combat_attributes:magic_power",
                "combat_attributes:magic_crit_chance",
                "combat_attributes:magic_crit_damage",
                "combat_attributes:max_mana",
                "combat_attributes:mana_regen",
                "combat_attributes:mana_cost"
        );

        @Comment("Attributes shown under the Mobility category, in order.")
        public ValidatedList<Identifier> mobility = list(
                "combat_attributes:max_stamina",
                "combat_attributes:stamina_regen",
                "combat_attributes:stamina_cost",
                "minecraft:movement_speed",
                "minecraft:jump_strength"
        );

        @Comment("Attributes shown under the Misc category, in order.")
        public ValidatedList<Identifier> misc = list(
                "minecraft:luck",
                "minecraft:block_interaction_range",
                "minecraft:entity_interaction_range",
                "combat_attributes:life_steal",
                "combat_attributes:experience_gain"
        );

        private static ValidatedList<Identifier> list(String... initial) {
            Identifier placeholder = Identifier.fromNamespaceAndPath("minecraft", "generic.attack_damage");
            List<Identifier> ids = Arrays.stream(initial)
                    .map(Identifier::tryParse)
                    .filter(Objects::nonNull)
                    .toList();
            return new ValidatedIdentifier(placeholder).toList(ids);
        }
    }
}
