package dev.muon.chronicles_leveling.skill.combat;

import net.minecraft.world.phys.Vec3;

/**
 * Duck interface mixed into every {@code AbstractArrow} (by {@code AbstractArrowMixin}) so the archery
 * perks can carry transient per-arrow state without a Forge-only {@code getPersistentData()} (which
 * Fabric lacks on vanilla entities) and without NBT bookkeeping for state that only matters in flight.
 *
 * <ul>
 *   <li>{@link #chronicles_leveling$launchPos()}: where the shot started, for Far Shot's travel-distance scaling.</li>
 *   <li>{@link #chronicles_leveling$isSecondary()}: set on Multishot/Ricochet-spawned arrows so they don't
 *       recursively spawn more.</li>
 *   <li>{@link #chronicles_leveling$ricochetBudget()}: remaining bounces. A primary arrow (and each Multishot extra)
 *       gets the shooter's Ricochet rank; each bounce spawns a bolt with one less, so the chain length IS the rank.</li>
 *   <li>{@link #chronicles_leveling$accelerateDespawn(int)}: shortens an un-pickup-able clone's stuck-lifespan.</li>
 * </ul>
 *
 * <p>Piercing Shot needs no field here; it's read straight off the shooter's capabilities by boosting
 * {@code AbstractArrow.getPierceLevel()}'s return value, so Multishot extras and Ricochet bounces inherit it
 * for free via their shared owner.
 */
public interface ChroniclesArrow {

    Vec3 chronicles_leveling$launchPos();

    void chronicles_leveling$setLaunchPos(Vec3 pos);

    boolean chronicles_leveling$isSecondary();

    void chronicles_leveling$markSecondary();

    /** The arrow's base damage (vanilla exposes only the setter), so spawned bolts can inherit it. */
    double chronicles_leveling$baseDamage();

    /** Remaining ricochet bounces this arrow may make (0 = none). */
    int chronicles_leveling$ricochetBudget();

    void chronicles_leveling$setRicochetBudget(int budget);

    /**
     * Pre-age this projectile so it despawns roughly {@code groundTicks} after it sticks, instead of vanilla's
     * 60s; keeps un-pickup-able Multishot/Ricochet clones from littering. Vanilla's life counter only advances
     * while the arrow is stuck in a block ({@code AbstractArrow#tickDespawn}), so this shortens the stuck-time,
     * never the in-flight time; passing vanilla's full 1200 is a no-op.
     */
    void chronicles_leveling$accelerateDespawn(int groundTicks);
}
