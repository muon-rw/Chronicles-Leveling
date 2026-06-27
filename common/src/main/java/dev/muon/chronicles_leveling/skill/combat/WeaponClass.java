package dev.muon.chronicles_leveling.skill.combat;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The weapon family a melee hit belongs to: the gate for Weaponry's three branch perks.
 *
 * <p>Classification is TAG-driven, never {@code instanceof}: 26.1.2 made weapons component-driven, so
 * {@code SwordItem}/{@code AxeItem} no longer exist as distinct classes, and tags are the only
 * future-weapon-friendly handle anyway. We ship three re-pointable wrapper tags that DEFAULT to the
 * vanilla {@code enchantable/*} families (a datapack can override them, and a mod's weapon inherits a
 * branch for free just by being in the right vanilla tag):
 * <ul>
 *   <li>{@link #SLASHING_WEAPONS} → {@code #minecraft:enchantable/sweeping} (swords) + {@code #minecraft:axes}</li>
 *   <li>{@link #PIERCING_WEAPONS} → {@code #minecraft:enchantable/trident} + {@code #minecraft:enchantable/lunge} (spears)</li>
 *   <li>{@link #BLUNT_WEAPONS}    → {@code #minecraft:enchantable/mace}</li>
 * </ul>
 *
 * <p>A projectile hit is always {@link #RANGED} regardless of the held item (a thrown trident is a
 * ranged hit; a trident stab is {@link #PIERCING}). Anything else falls through to the attacker's
 * main-hand item tag, or {@link #OTHER} (bare fists, a pickaxe, an unrecognised weapon).
 */
public enum WeaponClass {
    SLASHING,
    PIERCING,
    BLUNT,
    RANGED,
    OTHER;

    public static final TagKey<Item> SLASHING_WEAPONS = tag("slashing_weapons");
    public static final TagKey<Item> PIERCING_WEAPONS = tag("piercing_weapons");
    public static final TagKey<Item> BLUNT_WEAPONS = tag("blunt_weapons");

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ChroniclesLeveling.id(path));
    }

    /** Whether this class is a melee strike (everything but {@link #RANGED}). */
    public boolean isMelee() {
        return this != RANGED;
    }

    /** Classifies a hit: projectile → {@link #RANGED}; else by the attacker's main-hand weapon tag. */
    public static WeaponClass classify(LivingEntity attacker, DamageSource source) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return RANGED;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.is(SLASHING_WEAPONS)) {
            return SLASHING;
        }
        if (weapon.is(PIERCING_WEAPONS)) {
            return PIERCING;
        }
        if (weapon.is(BLUNT_WEAPONS)) {
            return BLUNT;
        }
        return OTHER;
    }
}
