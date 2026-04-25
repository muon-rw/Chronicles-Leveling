package dev.muon.chronicles_leveling.stat;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;

/**
 * Recomputes secondary-attribute modifiers for one player based on their
 * current stat allocations and the per-stat spec lists in
 * {@link ConfigSync#statModifiers}.
 *
 * <p>One {@link AttributeModifier} per (stat, target) pair, with a stable id of
 * {@code chronicles_leveling:stat/<stat>/<target-namespace>/<target-path>}.
 * Stable ids let us cleanly remove the previous modifier before re-adding the
 * scaled one — no "ghost stacks" if a config reload changes the amount.
 */
public final class StatModifierApplier {

    private StatModifierApplier() {}

    /**
     * Wipes and re-applies every stat-driven modifier on the player. Call after
     * any of: stat allocation changes, config reload, player join/respawn,
     * dimension change.
     */
    public static void recompute(Player player) {
        for (ModStats.Entry stat : ModStats.ALL) {
            recomputeForStat(player, stat);
        }
    }

    private static void recomputeForStat(Player player, ModStats.Entry stat) {
        AttributeInstance statInstance = player.getAttribute(ModStats.get(stat.id()));
        if (statInstance == null) return;
        int spent = (int) Math.floor(statInstance.getValue());

        List<StatModifierSpec> specs = Configs.SYNC.getStatModifierSpecs(stat.id());
        for (StatModifierSpec spec : specs) {
            applyOne(player, stat.id(), spec, spent);
        }
    }

    private static void applyOne(Player player, String statId, StatModifierSpec spec, int spent) {
        Identifier targetId = spec.targetAttribute.get();
        Optional<? extends Holder<Attribute>> targetHolder = BuiltInRegistries.ATTRIBUTE.get(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ATTRIBUTE, targetId)
        );
        if (targetHolder.isEmpty()) {
            ChroniclesLeveling.LOG.debug("Stat '{}' targets unknown attribute '{}', skipping", statId, targetId);
            return;
        }

        AttributeInstance targetInstance = player.getAttribute(targetHolder.get());
        if (targetInstance == null) return;

        Identifier modifierId = ChroniclesLeveling.id(
                "stat/" + statId + "/" + targetId.getNamespace() + "/" + targetId.getPath()
        );
        targetInstance.removeModifier(modifierId);

        if (spent <= 0 || spec.amountPerPoint.get() == 0.0) {
            return;
        }

        AttributeModifier modifier = new AttributeModifier(
                modifierId,
                spec.amountPerPoint.get() * spent,
                spec.operation.get()
        );
        targetInstance.addPermanentModifier(modifier);
    }

    /**
     * Convenience for {@link Configs#register()}-time wiring: returns a stable
     * key for one (stat, target attribute) pair, so callers in tests or
     * tooltips can match what {@link #applyOne} would write.
     */
    public static Identifier modifierId(String statId, Identifier targetId) {
        return ChroniclesLeveling.id(
                "stat/" + statId + "/" + targetId.getNamespace() + "/" + targetId.getPath()
        );
    }
}
