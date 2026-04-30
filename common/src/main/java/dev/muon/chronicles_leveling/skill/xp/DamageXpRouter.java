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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * Loader-agnostic routing for "damage just happened, who gets skill XP and
 * how much?". Both loaders' damage events feed in here; the router decides:
 *
 * <ol>
 *   <li>Damage <b>dealt</b> by a player to a living entity → weaponry, archery
 *       or magic XP based on damage source classification.</li>
 *   <li>Damage <b>taken</b> by a player → armor XP. If the source is fall
 *       damage, also acrobatics XP.</li>
 * </ol>
 *
 * <p>Source classification follows Combat-Attributes' convention:
 * {@code #c:is_magic} → magic; {@code #minecraft:is_projectile} (and not magic)
 * → archery; otherwise → weaponry. {@code #c:is_magic} doesn't ship in vanilla
 * but is a stable cross-mod tag in the Common namespace.
 *
 * <p>The blacklist + spawner multiplier from {@code ConfigSync} apply to the
 * "dealt" path only — taking damage from a spawner-spawned skeleton should
 * still train armor at full rate.
 */
public final class DamageXpRouter {

    private DamageXpRouter() {}

    private static final TagKey<DamageType> IS_MAGIC = TagKey.create(
            Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("c", "is_magic"));

    /**
     * Called once per damage event after the source/amount are settled but
     * before any mitigation reduces the amount. Both loaders feed in
     * pre-mitigation amount so combat XP is consistent regardless of armor.
     *
     * <p>This is a hot path — fires on every {@code LivingEntity}-vs-{@code LivingEntity}
     * hit in the world. The first early-return covers the mob-on-mob case so
     * the more expensive identifier/tag lookups in {@code handleDealt} only
     * run when a player is actually involved.
     */
    public static void onDamage(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0f) return;

        Entity attacker = source.getEntity();
        boolean attackerIsPlayer = attacker instanceof ServerPlayer;
        boolean victimIsPlayer = victim instanceof ServerPlayer;
        if (!attackerIsPlayer && !victimIsPlayer) return;

        if (attackerIsPlayer && !(victim instanceof Player)) {
            handleDealt((ServerPlayer) attacker, victim, source, amount);
        }

        if (victimIsPlayer) {
            handleTaken((ServerPlayer) victim, source, amount);
        }
    }

    private static void handleDealt(ServerPlayer attacker, LivingEntity victim, DamageSource source, float amount) {
        Identifier victimId = EntityType.getKey(victim.getType());
        if (victimId != null && Configs.SYNC.entitySkillXpBlacklist.get().contains(victimId.toString())) {
            return;
        }

        boolean magic = source.is(IS_MAGIC);
        boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);

        String skillId;
        ValidatedExpression formula;
        if (magic) {
            skillId = Skills.MAGIC;
            formula = Configs.MAGIC.xpPerDamage;
        } else if (projectile) {
            skillId = Skills.ARCHERY;
            formula = Configs.ARCHERY.xpPerDamage;
        } else {
            skillId = Skills.WEAPONRY;
            formula = Configs.WEAPONRY.xpPerDamage;
        }

        double xp = formula.evalSafe(Map.of('d', (double) amount), 0.0);
        if (Services.PLATFORM.getSpawnerOriginStore().isFromSpawner(victim)) {
            xp *= Configs.SYNC.spawnerMobSkillXpMultiplier.get();
        }
        grant(attacker, skillId, xp);
    }

    private static void handleTaken(ServerPlayer victim, DamageSource source, float amount) {
        // Armor: every hit trains, including fall, magic, projectile, etc.
        double armorXp = Configs.ARMOR.xpPerDamageTaken.evalSafe(Map.of('d', (double) amount), 0.0);
        grant(victim, Skills.ARMOR, armorXp);

        if (source.is(DamageTypeTags.IS_FALL)) {
            double acroXp = Configs.ACROBATICS.xpPerFallDamage.evalSafe(
                    Map.of('d', (double) amount), 0.0);
            grant(victim, Skills.ACROBATICS, acroXp);
        }
    }

    private static void grant(ServerPlayer player, String skillId, double xp) {
        if (xp <= 0) return;
        PlayerSkillManager.grantXp(player, skillId, (int) Math.round(xp));
    }
}
