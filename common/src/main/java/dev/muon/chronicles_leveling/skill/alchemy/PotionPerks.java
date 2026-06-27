package dev.muon.chronicles_leveling.skill.alchemy;

import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.AlchemySkill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Player-side alchemy potion perks, read off the actor: the drinker, a thrown potion's owner, or the entity an
 * effect is being applied to. A null or non-player actor (e.g. a dispenser-thrown potion) yields no bonus.
 */
public final class PotionPerks {

    private PotionPerks() {}

    /** Iron Stomach: fraction of duration removed from HARMFUL effects applied to the entity. */
    public static double harmfulDurationReduction(LivingEntity entity) {
        return entity instanceof ServerPlayer player
                ? Mth.clamp(SkillEffects.get(player, AlchemySkill.IRON_STOMACH), 0.0, 1.0) : 0.0;
    }

    /** Empowered Splash: fractional radius bonus for splash/lingering potions the player throws. */
    public static double empoweredSplash(Entity thrower) {
        return thrower instanceof ServerPlayer player
                ? Math.max(0.0, SkillEffects.get(player, AlchemySkill.EMPOWERED_SPLASH)) : 0.0;
    }

    /**
     * Quick Quaff drink-speed fraction for the drinker. Read on EITHER logical side (server cache for a ServerPlayer,
     * else the pure client derive over synced perk data, mirroring {@code EnchantingPerks}) because the drink
     * use-duration must shorten identically on the client (animation) and server (finish timing).
     */
    public static double quickQuaff(LivingEntity drinker) {
        if (!(drinker instanceof Player player)) {
            return 0.0;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return Math.max(0.0, SkillEffects.get(serverPlayer, AlchemySkill.QUICK_QUAFF));
        }
        Object value = SkillEffects.derive(player).capabilities()
                .getOrDefault(AlchemySkill.QUICK_QUAFF, AlchemySkill.QUICK_QUAFF.absent());
        return value instanceof Double quaff ? Math.max(0.0, quaff) : 0.0;
    }
}
