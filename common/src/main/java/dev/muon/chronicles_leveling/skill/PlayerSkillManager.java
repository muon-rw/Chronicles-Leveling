package dev.muon.chronicles_leveling.skill;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.ability.AbilitySlots;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import dev.muon.chronicles_leveling.skill.perk.SkillPerk;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-side facade for reading + writing player skill state.
 *
 * <p>All routes that mutate skill levels/xp go through this class so we have
 * exactly one place to dispatch persistence + sync via the loader-specific
 * {@link PlayerSkillStore}. Read-side accessors are safe on the client too;
 * the attachment is synced to the owning client by the platform layer.
 *
 * <p>XP gain hooks (combat, brewing, etc.) call {@link #grantXp} which handles
 * level-up rollover via {@link SkillCurve}.
 */
public final class PlayerSkillManager {

    private PlayerSkillManager() {}

    public static PlayerSkillData get(Player player) {
        return Services.PLATFORM.getPlayerSkillStore().get(player);
    }

    public static PlayerSkillData.SkillEntry getSkill(Player player, String skillId) {
        return get(player).get(skillId);
    }

    public static void set(ServerPlayer player, PlayerSkillData data) {
        Services.PLATFORM.getPlayerSkillStore().set(player, data);
    }

    public static void setSkill(ServerPlayer player, String skillId, PlayerSkillData.SkillEntry entry) {
        set(player, get(player).with(skillId, entry));
    }

    /**
     * Single write path for an ability cooldown stamp (game-time end-tick). Pure data write, NO
     * recompute (recompute is for perk ranks only); the stamp rides the existing attachment auto-sync.
     */
    public static void setAbilityCooldown(ServerPlayer player, Identifier abilityId, long endTick) {
        set(player, get(player).withAbilityCooldown(abilityId.toString(), endTick, player.level().getGameTime()));
    }

    /** Clears all of a player's ability cooldowns (debug). Pure data write; rides the attachment auto-sync. */
    public static void clearAbilityCooldowns(ServerPlayer player) {
        PlayerSkillData data = get(player);
        set(player, new PlayerSkillData(data.skills(), Map.of(), data.abilitySlots()));
    }

    /** Single write path for an action-bar slot binding ({@code null}/blank clears). Pure data write, NO recompute. */
    public static void setAbilitySlot(ServerPlayer player, int slot, String abilityId) {
        set(player, get(player).withAbilitySlot(slot, abilityId));
    }

    /**
     * Bank XP into the given skill, rolling level-ups as the curve dictates.
     * No-ops on zero/negative grants and unknown skill ids; keeps every gain
     * hook from having to check those itself.
     *
     * <p>Single write to the store at the end (one persist + one sync) even if
     * the grant covers multiple levels.
     */
    public static void grantXp(ServerPlayer player, String skillId, int amount) {
        // Gate on the frozen registry (not the hardcoded core set) so addon-registered skills
        // can also gain XP. Server-side post-login callers only, so the registry is always frozen.
        if (amount <= 0 || SkillRegistry.get(skillId) == null) return;
        PlayerSkillData.SkillEntry entry = getSkill(player, skillId);
        int cap = Configs.SKILLS.maxSkillLevel.get();
        boolean capped = cap > 0;
        int level = entry.level();
        long xp = (long) entry.xp() + amount;
        int xpForNext = SkillCurve.xpToNext(skillId, level);
        // Bound work-per-grant: a degenerate curve that always returns 1, paired with a
        // huge grant, would otherwise loop for billions of iterations. The residual xp is
        // clamped to int range below before the truncating cast persists + syncs it.
        int rolled = 0;
        while (!(capped && level >= cap) && xp >= xpForNext && rolled++ < MAX_LEVELS_PER_GRANT) {
            xp -= xpForNext;
            level++;
            xpForNext = SkillCurve.xpToNext(skillId, level);
        }
        if (capped && level >= cap) {
            level = cap;
            xp = SkillCurve.xpToNext(skillId, cap);   // maxed: park at full so the bar reads 100%
        } else {
            xp = Math.min(xp, Integer.MAX_VALUE);      // guard the (int) cast if the loop hit its iteration ceiling
        }
        int nextXp = (int) xp;
        if (level == entry.level() && nextXp == entry.xp()) {
            return;   // already maxed (xp parked at the cap), nothing changed, skip the write + sync
        }
        // Preserve spentPoints + perkRanks; a level/xp write must never reset the player's tree.
        setSkill(player, skillId, entry.withLevel(level).withXp(nextXp));
        // Passive magnitudes scale by skill level, so a level change must re-materialize the
        // perk modifiers (and invalidate the capability cache). Guarded so an xp-only grant is free.
        if (level != entry.level()) {
            SkillModifierApplier.recompute(player);
        }
    }

    /** Round-and-grant convenience for handlers that compute XP as a {@code double}. */
    public static void grantXp(ServerPlayer player, String skillId, double amount) {
        if (amount <= 0) return;
        grantXp(player, skillId, (int) Math.round(amount));
    }

    /**
     * Self-heals a player's stored skill state against the frozen registry + current
     * config; run as the FIRST step at login, before any recompute or spend path is
     * reachable. Drops orphaned perk ranks (perks whose addon was removed), clamps each
     * surviving rank to its perk's {@code maxRank}, clamps each skill level to the live
     * {@code maxSkillLevel}, and recomputes {@code spentPoints} from only the surviving,
     * priceable ranks, so available points self-correct and no points are stranded.
     * A no-op (single read, no write) when nothing needs correcting.
     */
    public static void reconcile(ServerPlayer player) {
        if (!SkillRegistry.isFrozen()) {
            throw new IllegalStateException("reconcile() called before SkillRegistry was frozen; bootstrap ordering bug");
        }
        PlayerSkillData data = get(player);
        int cap = Configs.SKILLS.maxSkillLevel.get();
        Map<String, PlayerSkillData.SkillEntry> updated = new HashMap<>(data.skills());
        boolean changed = false;

        for (Map.Entry<String, PlayerSkillData.SkillEntry> e : data.skills().entrySet()) {
            String skillId = e.getKey();
            PlayerSkillData.SkillEntry entry = e.getValue();
            SkillDefinition def = SkillRegistry.get(skillId);

            int level = entry.level();
            int xp = entry.xp();
            if (cap > 0 && level > cap) {
                level = cap;
                xp = SkillCurve.xpToNext(skillId, cap);   // park at full, mirroring grantXp's cap behavior
            }

            Map<String, Integer> survivors = new HashMap<>();
            int spent = 0;
            for (Map.Entry<String, Integer> r : entry.perkRanks().entrySet()) {
                SkillPerk perk = def == null ? null : def.perk(r.getKey());
                if (perk == null) continue;                                 // orphan perk: drop
                int rank = Math.min(Math.max(0, r.getValue()), perk.maxRank());
                if (rank <= 0) continue;
                survivors.put(perk.id(), rank);
                spent += perk.costThroughRank(rank);
            }

            if (level != entry.level() || xp != entry.xp()
                    || spent != entry.spentPoints() || !survivors.equals(entry.perkRanks())) {
                updated.put(skillId, new PlayerSkillData.SkillEntry(level, xp, spent, survivors));
                changed = true;
            }
        }

        if (changed) {
            set(player, new PlayerSkillData(updated, data.abilityCooldownEnds(), data.abilitySlots()));
        }
    }

    /**
     * Drops cooldown stamps + slot bindings the player can no longer use: abilities not in their unlocked
     * set (relocked by a respec, orphaned by an uninstalled addon, or never owned) and any out-of-range
     * slot index. Pure data write, NO recompute. Run AFTER a recompute (login, respec) so the unlocked set
     * reflects current perks. A no-op when nothing needs dropping.
     */
    public static void reconcileAbilityBindings(ServerPlayer player) {
        PlayerSkillData data = get(player);
        Set<String> unlocked = new HashSet<>();
        for (Identifier id : SkillEffects.derive(player).abilities()) {
            unlocked.add(id.toString());
        }

        // Keep any STILL-RUNNING cooldown, even for an ability that isn't currently unlocked: otherwise a
        // respec drops the cooldown and a reskill hands back a fresh, off-cooldown ability (the reset exploit).
        // Expired entries fall away here on the next reconcile, so this never accumulates.
        long now = player.level().getGameTime();
        Map<String, Long> cooldowns = new HashMap<>();
        data.abilityCooldownEnds().forEach((id, end) -> {
            if (end > now) {
                cooldowns.put(id, end);
            }
        });
        Map<Integer, String> slots = new HashMap<>();
        data.abilitySlots().forEach((slot, id) -> {
            if (unlocked.contains(id) && AbilitySlots.isValid(slot)) {
                slots.put(slot, id);
            }
        });

        if (cooldowns.size() != data.abilityCooldownEnds().size()
                || slots.size() != data.abilitySlots().size()) {
            set(player, new PlayerSkillData(data.skills(), cooldowns, slots));
        }
    }

    private static final int MAX_LEVELS_PER_GRANT = 1000;
}
