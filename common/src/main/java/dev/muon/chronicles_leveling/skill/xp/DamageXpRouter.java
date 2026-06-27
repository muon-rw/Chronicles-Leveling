package dev.muon.chronicles_leveling.skill.xp;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.Skills;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedExpression;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * Loader-agnostic damage XP routing. {@link #onDamageTaken} expects pre-mit
 * amount (callers skip fatal, shield-blocked, and totem-saved hits);
 * {@link #onDamageDealt} expects the post-mitigation, pre-absorption-split
 * inflicted amount (NeoForge {@code Post#getInflictedDamage()}; the Fabric
 * seams pass the same value). Source classification follows Combat-Attributes:
 * {@code #c:is_magic} → magic; {@code #minecraft:is_projectile} (and not
 * magic) → archery; otherwise → weaponry.
 */
public final class DamageXpRouter {

    private DamageXpRouter() {}

    private static final TagKey<DamageType> IS_MAGIC = TagKey.create(
            Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("c", "is_magic"));

    public static void onDamageTaken(LivingEntity victim, DamageSource source, float preMitAmount) {
        if (!(victim instanceof ServerPlayer player)) return;
        if (preMitAmount <= 0f) return;

        double d = clampForFormula(preMitAmount);
        if (d <= 0.0) return;

        Map<Character, Double> args = Map.of('d', d);
        PlayerSkillManager.grantXp(player, Skills.DEFENSE,
                Configs.SKILLS.defense.xpPerDamageTaken.evalSafe(args, 0.0));

        if (source.is(DamageTypeTags.IS_FALL)) {
            PlayerSkillManager.grantXp(player, Skills.ACROBATICS,
                    Configs.SKILLS.acrobatics.xpPerFallDamage.evalSafe(args, 0.0));
        }
    }

    public static void onDamageDealt(ServerPlayer attacker, LivingEntity victim, DamageSource source, float appliedAmount) {
        if (appliedAmount <= 0f) return;
        if (victim instanceof Player) return;

        var blacklist = Configs.SKILLS.entityXpBlacklist.get();
        if (!blacklist.isEmpty()) {
            Identifier victimId = EntityType.getKey(victim.getType());
            if (victimId != null && blacklist.contains(victimId.toString())) return;
        }

        double d = clampForFormula(appliedAmount);
        if (d <= 0.0) return;

        boolean magic = source.is(IS_MAGIC);
        boolean projectile = !magic && source.is(DamageTypeTags.IS_PROJECTILE);

        String skillId;
        ValidatedExpression formula;
        if (magic) {
            skillId = Skills.MAGIC;
            formula = Configs.SKILLS.magic.xpPerDamage;
        } else if (projectile) {
            skillId = Skills.ARCHERY;
            formula = Configs.SKILLS.archery.xpPerDamage;
        } else {
            skillId = Skills.WEAPONRY;
            formula = Configs.SKILLS.weaponry.xpPerDamage;
        }

        double xp = formula.evalSafe(Map.of('d', d), 0.0);
        if (Services.PLATFORM.getSpawnerOriginStore().isFromSpawner(victim)) {
            xp *= Configs.SKILLS.spawnerMobMultiplier.get();
        }
        PlayerSkillManager.grantXp(attacker, skillId, xp);
    }

    private static double clampForFormula(float damage) {
        if (Float.isNaN(damage) || damage <= 0f) return 0.0;
        return Math.min((double) damage, Configs.SKILLS.maxDamagePerHit.get());
    }
}
