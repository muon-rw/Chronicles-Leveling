package dev.muon.chronicles_leveling.skill.fishing;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.FishingSkill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class FishingHooks {

    private FishingHooks() {}

    /** Patient Angler: extra lure-speed ticks (faster bite) from BITE_SPEED + LURE_RANGE, added to the rod's lureSpeed. */
    public static int biteSpeedTicks(Player owner) {
        if (!(owner instanceof ServerPlayer player)) {
            return 0;
        }
        var cfg = Configs.SKILLS.fishing;
        double bite = SkillEffects.get(player, FishingSkill.BITE_SPEED);
        double lure = SkillEffects.get(player, FishingSkill.LURE_RANGE);
        return (int) Math.round(bite * cfg.biteSpeedReferenceTicks.get() + lure * cfg.lureRangeTicksPerBlock.get());
    }

    /**
     * Trident Master: multiply the loyalty-return acceleration by the throwing holder's bonus. Read on EITHER
     * logical side (server cache, else the pure client derive over synced perk data, mirroring PotionPerks) so the
     * trident's return motion, which {@code ThrownTrident.tick} self-simulates on the client, matches the server.
     */
    public static double tridentReturnAccel(Entity owner, double accel) {
        if (!(owner instanceof Player player)) {
            return accel;
        }
        double bonus;
        if (player instanceof ServerPlayer serverPlayer) {
            bonus = SkillEffects.get(serverPlayer, FishingSkill.TRIDENT_RETURN_SPEED);
        } else {
            Object value = SkillEffects.derive(player).capabilities()
                    .getOrDefault(FishingSkill.TRIDENT_RETURN_SPEED, FishingSkill.TRIDENT_RETURN_SPEED.absent());
            bonus = value instanceof Double d ? d : 0.0;
        }
        return bonus > 0 ? accel * (1.0 + bonus) : accel;
    }

    /** Harpoon: a holder's trident hit reels the struck living entity toward the thrower. */
    public static void reelTarget(Entity owner, Entity target) {
        if (!(owner instanceof ServerPlayer player) || !(target instanceof LivingEntity living) || living == player
                || !SkillEffects.has(player, FishingSkill.HARPOON_REEL)) {
            return;
        }
        Vec3 pull = player.position().subtract(living.position());
        if (pull.lengthSqr() < 1.0e-4) {
            return;
        }
        Vec3 velocity = pull.normalize().scale(Configs.SKILLS.fishing.harpoonReelStrength.get());
        living.push(velocity.x, velocity.y + 0.15, velocity.z);
        living.hurtMarked = true;
    }
}
