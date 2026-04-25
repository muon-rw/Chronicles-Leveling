package dev.muon.chronicles_leveling.stat;

import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.util.Walkable;
import me.fzzyhmstrs.fzzy_config.validation.minecraft.ValidatedIdentifier;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * One row of "this stat grants X to attribute Y per point". Configurable via
 * FzzyConfig — pack authors edit these to retune the default mapping or add
 * mappings to {@code Combat-Attributes} attributes once that mod is on the
 * classpath.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@link #targetAttribute} — registry id of the attribute receiving the
 *       modifier. Must already be registered (vanilla or modded).</li>
 *   <li>{@link #amountPerPoint} — multiplied by the player's spent stat points
 *       to produce the modifier amount.</li>
 *   <li>{@link #operation} — vanilla {@link AttributeModifier.Operation}.
 *       {@code ADD_VALUE} adds flat; {@code ADD_MULTIPLIED_BASE/TOTAL} are
 *       fractional, so amount per point of e.g. {@code 0.05} reads as +5%.</li>
 * </ul>
 *
 * <p>Note this is a section, not a top-level config — it's meant to be embedded
 * inside lists keyed per stat in {@link dev.muon.chronicles_leveling.config.ConfigSync}.
 */
public class StatModifierSpec implements Walkable {

    @Comment("Registry id of the attribute receiving the modifier (e.g. minecraft:generic.attack_damage).")
    public ValidatedIdentifier targetAttribute;

    @Comment("Modifier amount granted per point spent. Multiplied by the player's allocation.")
    public ValidatedDouble amountPerPoint;

    @Comment("AttributeModifier operation. ADD_VALUE = flat; ADD_MULTIPLIED_BASE/TOTAL = fractional (0.05 = +5%).")
    public ValidatedEnum<AttributeModifier.Operation> operation;

    public StatModifierSpec() {
        // FzzyConfig requires a no-arg ctor for deserialization.
        this(Identifier.fromNamespaceAndPath("minecraft", "generic.attack_damage"),
                0.0, AttributeModifier.Operation.ADD_VALUE);
    }

    public StatModifierSpec(Identifier target, double amountPerPoint, AttributeModifier.Operation op) {
        this.targetAttribute = new ValidatedIdentifier(target);
        this.amountPerPoint = new ValidatedDouble(amountPerPoint, 1_000_000.0, -1_000_000.0);
        this.operation = new ValidatedEnum<>(op);
    }
}
