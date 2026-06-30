package dev.muon.chronicles_leveling.skill.fishing;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.FishingSkill;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

public final class FishingHooks {

    private FishingHooks() {}

    /** Patient Angler: fraction the bobber's bite wait is cut by (rank 1/2/3 = 20/40/60%), capped below a full wait. */
    public static double biteSpeedFraction(Player owner) {
        if (!(owner instanceof ServerPlayer player)) {
            return 0.0;
        }
        return Math.min(0.95, SkillEffects.get(player, FishingSkill.BITE_SPEED));
    }

    /**
     * Worthy: shape the loyalty-return acceleration for the throwing holder. Rank 1 multiplies the base accel;
     * rank 2+ ("instant") returns the full gap to the owner's eye so the trident snaps back in a tick. Read on
     * EITHER logical side (server cache, else the pure client derive over synced perk data) so the return motion,
     * which {@code ThrownTrident.tick} self-simulates on the client, matches the server.
     */
    public static double tridentReturnAccel(ThrownTrident trident, double accel) {
        if (!(trident.getOwner() instanceof Player player)) {
            return accel;
        }
        if (ownerHas(player, FishingSkill.TRIDENT_INSTANT_RETURN)) {
            return player.getEyePosition().subtract(trident.position()).length() + 1.0;
        }
        double bonus = ownerValue(player, FishingSkill.TRIDENT_RETURN_SPEED);
        return bonus > 0 ? accel * (1.0 + bonus) : accel;
    }

    /** Worthy rank 3: trident returns even without a Loyalty enchant, and survives the void to do so. */
    public static boolean isTridentAutoReturn(Player owner) {
        return ownerHas(owner, FishingSkill.TRIDENT_AUTO_RETURN);
    }

    /** Storm God rank 3: Riptide works with no water or rain. */
    public static boolean canStormRiptide(Player owner) {
        return ownerHas(owner, FishingSkill.STORM_RIPTIDE);
    }

    /**
     * Storm God: strike lightning on a Channelling trident's hit under the holder's weather rule. Server-side and
     * de-duped against vanilla Channelling (which already strikes during a thunderstorm under open sky). Rank 1
     * needs rain; rank 2+ strikes in any weather. Open sky is always required (no lightning under a roof).
     */
    public static void stormGodLightning(ThrownTrident trident, Entity victim) {
        if (!(trident.level() instanceof ServerLevel level) || !(trident.getOwner() instanceof ServerPlayer owner)) {
            return;
        }
        boolean always = SkillEffects.has(owner, FishingSkill.STORM_ALWAYS_LIGHTNING);
        if (!always && !SkillEffects.has(owner, FishingSkill.STORM_RAIN_CHANNEL)) {
            return;
        }
        if (level.isThundering() || (!always && !level.isRaining())) {
            return;   // thunder: vanilla Channelling already strikes; rank 1 only extends Channelling to rain
        }
        BlockPos pos = victim.blockPosition();
        if (!level.canSeeSky(pos) || !hasChannelling(trident, level)) {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.spawn(level, pos, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setCause(owner);
        }
    }

    private static boolean hasChannelling(ThrownTrident trident, ServerLevel level) {
        Holder<Enchantment> channelling = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.CHANNELING);
        return EnchantmentHelper.getItemEnchantmentLevel(channelling, trident.getWeaponItem()) > 0;
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

    private static boolean ownerHas(Player player, SkillCapability<Boolean> capability) {
        if (player instanceof ServerPlayer serverPlayer) {
            return SkillEffects.has(serverPlayer, capability);
        }
        Object value = SkillEffects.derive(player).capabilities().getOrDefault(capability, capability.absent());
        return value instanceof Boolean flag && flag;
    }

    private static double ownerValue(Player player, SkillCapability<Double> capability) {
        if (player instanceof ServerPlayer serverPlayer) {
            return SkillEffects.get(serverPlayer, capability);
        }
        Object value = SkillEffects.derive(player).capabilities().getOrDefault(capability, capability.absent());
        return value instanceof Double number ? number : 0.0;
    }
}
