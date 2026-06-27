package dev.muon.chronicles_leveling.platform.services;

import dev.muon.chronicles_leveling.level.PlayerLevelStore;
import dev.muon.chronicles_leveling.network.NetworkHelper;
import dev.muon.chronicles_leveling.skill.PlayerSkillStore;
import dev.muon.chronicles_leveling.skill.SkillContributor;
import dev.muon.chronicles_leveling.skill.enchant.TableUsageStore;
import dev.muon.chronicles_leveling.skill.gather.CampfireOwnerStore;
import dev.muon.chronicles_leveling.skill.gather.FurnaceOwnerStore;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationStore;
import dev.muon.chronicles_leveling.skill.xp.SpawnerOriginStore;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;
import java.util.Optional;

public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    /**
     * Loader-specific accessor for the player's level/xp/points attachment.
     * On NeoForge this is backed by a synced AttachmentType; on Fabric it's a
     * non-synced AttachmentRegistry entry that we sync manually for join-timing
     * reasons (mirroring Dynamic-Difficulty's choice).
     */
    PlayerLevelStore getPlayerLevelStore();

    /**
     * Loader-specific accessor for the player's skill levels + xp attachment.
     * Backed by a synced AttachmentType on both loaders so the owning client
     * always reflects the server-authoritative state.
     */
    PlayerSkillStore getPlayerSkillStore();

    /**
     * Loader-specific accessor for the brewing-stand BE attachment that holds
     * per-slot freshly-brewed flags for the alchemy XP grant. Persistent only,
     * never synced; clients don't need this state.
     */
    BrewingStationStore getBrewingStationStore();

    /**
     * Loader-specific accessor for the campfire owner BE attachment that records which player placed each cooking
     * slot's food, so Gardener's Infusion can credit the cook when the item pops. Persistent only, never synced.
     */
    CampfireOwnerStore getCampfireOwnerStore();

    /**
     * Loader-specific accessor for the furnace cook-owner BE attachment (who loaded the smelt), so Gardener's Infusion
     * can credit the cook when the result is assembled. Persistent only, never synced.
     */
    FurnaceOwnerStore getFurnaceOwnerStore();

    /**
     * Loader-specific accessor for the per-entity spawner-origin attachment
     * used by the skill XP router's spawner multiplier. Persistent only, not
     * synced; flag survives chunk unloads and server restarts so a mob spawned
     * by a spawner is still flagged when later killed.
     */
    SpawnerOriginStore getSpawnerOriginStore();

    /**
     * Loader-specific accessor for the per-player table-usage attachment behind Wizard's Study
     * ("most-used enchanting table"). Persistent only, server-side state, not synced.
     */
    TableUsageStore getTableUsageStore();

    /**
     * Loader-specific networking adapter for sending packets to clients.
     * Mirrors {@code dev.muon.dynamic_difficulty.platform.NetworkHelper}.
     */
    NetworkHelper getNetworkHelper();

    /**
     * Collects addon skill contributions for the registry-freeze phase, the loader-native
     * way: Fabric returns instances of the {@code chronicles_leveling:skills} entrypoint;
     * NeoForge posts {@code RegisterSkillContributionsEvent} on the mod bus and gathers the
     * registrants. Empty when no addon contributes. Called exactly once, before freeze.
     */
    List<SkillContributor> collectSkillContributors();

    /**
     * Returns the percent-display scale factor for the given attribute, if the
     * loader natively marks it as a percentage attribute. Used as a fallback
     * for percent rendering when Dynamic-Tooltips isn't loaded.
     *
     * <p>NeoForge: detects {@code net.neoforged.neoforge.common.PercentageAttribute};
     * Fabric: returns empty (no native equivalent).
     */
    default Optional<Double> percentScaleForAttribute(Holder<Attribute> holder) {
        return Optional.empty();
    }

    /**
     * Renders a single-modifier tooltip line ("+5 Max Health") via the loader's
     * native attribute formatter. NeoForge: routes through
     * {@code IAttributeExtension.toComponent} so {@code PercentageAttribute} and
     * any modded {@code Attribute} subclasses pick their own format. Fabric:
     * returns empty (callers fall back to vanilla translation keys).
     */
    default Optional<Component> modifierComponent(Holder<Attribute> holder, AttributeModifier modifier) {
        return Optional.empty();
    }

    /**
     * Renders the "base value" tooltip line ("12.5 Max Health") via the loader's
     * native attribute formatter. NeoForge: {@code IAttributeExtension.toBaseComponent}.
     * Fabric: returns empty.
     */
    default Optional<Component> baseValueComponent(Holder<Attribute> holder, double value) {
        return Optional.empty();
    }
}
