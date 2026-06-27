package dev.muon.chronicles_leveling.skill.alchemy;

import dev.muon.chronicles_leveling.component.BrewPotency;
import dev.muon.chronicles_leveling.component.ModComponents;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.catalog.AlchemySkill;
import dev.muon.chronicles_leveling.skill.perk.SkillCapability;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Brewing-stand-side alchemy perks. {@code serverTick}/{@code doBrew} are static, playerless ticks, so each perk
 * reads its magnitude off the stand's recorded {@code owner} (the last player to load an ingredient, see
 * {@code AbstractContainerMenuMixin}). An absent or offline owner means no bonus.
 */
public final class BrewingPerks {

    private BrewingPerks() {}

    private static final double MAX_FRACTION = 0.90;

    public static ServerPlayer owner(BrewingStandBlockEntity stand) {
        Level level = stand.getLevel();
        if (level == null || level.isClientSide()) {
            return null;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }
        Optional<UUID> ownerId = Services.PLATFORM.getBrewingStationStore().get(stand).owner();
        return ownerId.map(id -> server.getPlayerList().getPlayer(id)).orElse(null);
    }

    public static int brewTime(int base, BrewingStandBlockEntity stand) {
        ServerPlayer owner = owner(stand);
        if (owner == null) {
            return base;
        }
        double speed = Mth.clamp(SkillEffects.get(owner, AlchemySkill.BREW_SPEED), 0.0, MAX_FRACTION);
        return Math.max(1, (int) Math.round(base * (1.0 - speed)));
    }

    public static int fuelUses(int base, BrewingStandBlockEntity stand) {
        ServerPlayer owner = owner(stand);
        if (owner == null) {
            return base;
        }
        double save = Mth.clamp(SkillEffects.get(owner, AlchemySkill.FUEL_SAVE), 0.0, MAX_FRACTION);
        return (int) Math.round(base / (1.0 - save));
    }

    public static double extraBrewChance(BrewingStandBlockEntity stand) {
        ServerPlayer owner = owner(stand);
        if (owner == null) {
            return 0.0;
        }
        return Mth.clamp(SkillEffects.get(owner, AlchemySkill.EXTRA_BREW_CHANCE), 0.0, 1.0);
    }

    /**
     * Empowers a just-brewed output stack off the owner's caps, identity-preserving on both axes (the base potion is
     * kept, so the brew stays re-brewable, splash/lingering-convertible, and recipe-matchable). Lingering Touch rides
     * {@code POTION_DURATION_SCALE}; the school/mastery amplifier bonuses ride the per-category {@link BrewPotency}
     * component, materialized into the effects only transiently at each delivery point. Each school grants one fresh
     * roll per finished brew at a chance equal to the owner's Alchemy level (mix() returns a component-less stack, so
     * rolls never accumulate across brew steps); its mastery adds an unconditional +1 on top.
     */
    public static void applyOutputPerks(BrewingStandBlockEntity stand, ItemStack result, Level level) {
        ServerPlayer owner = owner(stand);
        if (owner == null) {
            return;
        }
        PotionContents contents = result.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return;
        }

        double lingering = Math.max(0.0, SkillEffects.get(owner, AlchemySkill.LINGERING_TOUCH));
        if (lingering > 0.0) {
            float base = result.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F);
            result.set(DataComponents.POTION_DURATION_SCALE, base * (float) (1.0 + lingering));
        }

        double schoolChance = Math.min(1.0, PlayerSkillManager.get(owner).get(Skills.ALCHEMY).level() / 100.0);
        int beneficial = categoryAmp(owner, AlchemySkill.RESTORATION_SCHOOL, AlchemySkill.RESTORATION_MASTERY,
                schoolChance, level);
        int harmful = categoryAmp(owner, AlchemySkill.NEGATION_SCHOOL, AlchemySkill.NEGATION_MASTERY,
                schoolChance, level);
        if (beneficial != 0 || harmful != 0) {
            result.set(ModComponents.BREW_POTENCY, new BrewPotency(beneficial, harmful, 0));
        }
    }

    private static int categoryAmp(ServerPlayer owner, SkillCapability<Boolean> school, SkillCapability<Boolean> mastery,
            double schoolChance, Level level) {
        int amp = SkillEffects.has(owner, mastery) ? 1 : 0;
        if (SkillEffects.has(owner, school) && level.getRandom().nextDouble() < schoolChance) {
            amp++;
        }
        return amp;
    }
}
