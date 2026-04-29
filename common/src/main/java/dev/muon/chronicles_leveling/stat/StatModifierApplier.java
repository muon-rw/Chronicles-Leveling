package dev.muon.chronicles_leveling.stat;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
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
 * Materializes player leveling state onto vanilla {@link AttributeInstance}s.
 *
 * <p>Two stages, both driven from {@link #recompute}:
 * <ol>
 *   <li><b>Allocations</b> — for each stat, write a single permanent
 *       {@code ADD_VALUE} modifier on the stat attribute itself, with amount =
 *       {@link PlayerLevelData#allocation(String)}. Stable id
 *       {@code chronicles_leveling:allocation/<stat>}. Source of truth lives in
 *       the attachment, not on the attribute base; external sources (rings,
 *       potions, {@code /attribute modifier} commands) layer cleanly on top.</li>
 *   <li><b>Secondary modifiers</b> — for each stat, walk its
 *       {@link StatModifierSpec} list and write one modifier per (stat, target)
 *       pair on the target attribute. Amount scales with the player's <i>total</i>
 *       stat value (post-allocation, post-external), so a +5-Dexterity ring
 *       does pump downstream attributes. Stable id
 *       {@code chronicles_leveling:stat/<stat>/<target-ns>/<target-path>}.</li>
 * </ol>
 *
 * <p>Stable ids let us cleanly remove the previous modifier before re-adding the
 * scaled one — no "ghost stacks" if a config reload changes the amount or the
 * player's allocation drops on respec.
 */
public final class StatModifierApplier {

    private StatModifierApplier() {}

    /**
     * Wipes and re-applies every stat-driven modifier on the player.
     * Call after any of: stat allocation changes, config reload, player join /
     * respawn / clone / dimension change.
     */
    public static void recompute(Player player) {
        applyAllocations(player);
        for (ModStats.Entry stat : ModStats.ALL) {
            recomputeSecondariesForStat(player, stat);
        }
    }

    /**
     * Re-applies the allocation modifier for every stat on the player. Cheaper
     * than full {@link #recompute} when only the allocation changed and we
     * separately want to refresh secondaries against the new total.
     */
    public static void applyAllocations(Player player) {
        PlayerLevelData data = PlayerLevelManager.get(player);
        for (ModStats.Entry stat : ModStats.ALL) {
            applyAllocation(player, stat.id(), data.allocation(stat.id()));
        }
    }

    private static void applyAllocation(Player player, String statId, int amount) {
        AttributeInstance instance = player.getAttribute(ModStats.get(statId));
        if (instance == null) return;

        Identifier id = allocationModifierId(statId);
        instance.removeModifier(id);
        if (amount <= 0) return;

        instance.addPermanentModifier(new AttributeModifier(
                id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void recomputeSecondariesForStat(Player player, ModStats.Entry stat) {
        AttributeInstance statInstance = player.getAttribute(ModStats.get(stat.id()));
        if (statInstance == null) return;
        // Total (allocation + external buffs), not just allocation — equipment-driven
        // stat boosts should pump downstream attributes too.
        int spent = (int) Math.floor(statInstance.getValue());

        List<StatModifierSpec> specs = Configs.SYNC.getStatModifierSpecs(stat.id());
        for (StatModifierSpec spec : specs) {
            applySecondary(player, stat.id(), spec, spent);
        }
    }

    private static void applySecondary(Player player, String statId, StatModifierSpec spec, int spent) {
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

        Identifier modifierId = secondaryModifierId(statId, targetId);
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
     * Stable id for the per-stat allocation modifier (lives on the stat attribute).
     */
    public static Identifier allocationModifierId(String statId) {
        return ChroniclesLeveling.id("allocation/" + statId);
    }

    /**
     * Stable id for one (stat, target attribute) secondary modifier (lives on the
     * target attribute). Exposed so tests / tooltips can match what
     * {@link #applySecondary} writes.
     */
    public static Identifier secondaryModifierId(String statId, Identifier targetId) {
        return ChroniclesLeveling.id(
                "stat/" + statId + "/" + targetId.getNamespace() + "/" + targetId.getPath()
        );
    }
}
