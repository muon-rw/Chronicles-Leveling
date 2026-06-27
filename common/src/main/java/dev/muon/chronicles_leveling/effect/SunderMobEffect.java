package dev.muon.chronicles_leveling.effect;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Weaponry "Sunder": a blunt-hit debuff that shreds the target's armor while it lasts. Implemented
 * the standard vanilla way: a {@link MobEffect} carrying an {@code ARMOR} attribute modifier, so the
 * engine applies and removes it by id automatically (timed, synced, save-persisted).
 *
 * <p>{@code ADD_MULTIPLIED_TOTAL} of {@code -ARMOR_SHRED} per amplifier level. {@code ARMOR} is a
 * {@code RangedAttribute} clamped at 0, so this drives armor toward zero but never negative; a
 * "beyond-zero" punch would have to route through the damage pipeline instead (see resolutions Q3).
 */
public class SunderMobEffect extends MobEffect {

    private static final double DEFAULT_ARMOR_SHRED = 0.10;   // per-stack fallback if config isn't loaded yet

    public SunderMobEffect() {
        super(MobEffectCategory.HARMFUL, 0x6E6E6E);   // dull grey
        // Read once at registration (the attribute template is baked here); a config change needs a restart.
        // Guarded so a too-early construction can't NPE.
        double shred = Configs.SKILLS != null ? Configs.SKILLS.weaponry.sunderArmorShred.get() : DEFAULT_ARMOR_SHRED;
        addAttributeModifier(Attributes.ARMOR, ChroniclesLeveling.id("sunder"),
                -shred, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
