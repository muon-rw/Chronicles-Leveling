package dev.muon.chronicles_leveling.skill.combat;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.WeaponrySkill;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quick Blade (Weaponry), a slashing "flurry": each consecutive slashing hit adds an ATTACK_SPEED stack
 * worth the perk's per-rank bonus, ramping up to a cap and decaying after a stretch without a hit. The
 * combat router drives {@link #onSlashingHit} on a qualifying melee hit; a throttled tick poll decays
 * stale streaks, an equipment change drops it when the player can no longer flurry, and logout clears it.
 * A per-player snapshot of the applied amount keeps the modifier (and its client sync) rewritten only on
 * a real change.
 */
public final class QuickBladeHandler {

    private QuickBladeHandler() {}

    private static final int POLL_INTERVAL_TICKS = 20;
    private static final Identifier MODIFIER_ID = ChroniclesLeveling.id("quick_blade");

    private record Flurry(int stacks, long tick) {}

    private static final Map<UUID, Flurry> FLURRY = new HashMap<>();
    private static final Map<UUID, Double> APPLIED = new HashMap<>();

    public static void onSlashingHit(ServerPlayer player) {
        double perStack = SkillEffects.get(player, WeaponrySkill.QUICK_BLADE);
        if (perStack <= 0) {
            return;
        }
        var w = Configs.SKILLS.weaponry;
        int maxStacks = Math.max(1, (int) Math.floor(SkillEffects.get(player, WeaponrySkill.QUICK_BLADE_MAX_STACKS)));
        long now = player.level().getGameTime();
        Flurry current = FLURRY.get(player.getUUID());
        boolean continuing = current != null && now - current.tick() <= w.quickBladeFlurryResetTicks.get();
        int stacks = continuing ? Math.min(maxStacks, current.stacks() + 1) : 1;
        FLURRY.put(player.getUUID(), new Flurry(stacks, now));
        applyModifier(player, perStack * stacks);
    }

    public static void onEquipmentChanged(ServerPlayer player) {
        boolean canFlurry = player.getMainHandItem().is(WeaponClass.SLASHING_WEAPONS)
                || SkillEffects.has(player, WeaponrySkill.ADAPTIVE_ARSENAL);
        if (!canFlurry) {
            clearModifier(player);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % POLL_INTERVAL_TICKS != 0) {
            return;
        }
        long resetTicks = Configs.SKILLS.weaponry.quickBladeFlurryResetTicks.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Flurry current = FLURRY.get(player.getUUID());
            if (current != null && player.level().getGameTime() - current.tick() > resetTicks) {
                clearModifier(player);
            }
        }
        FLURRY.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        APPLIED.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    public static void clear(UUID playerId) {
        FLURRY.remove(playerId);
        APPLIED.remove(playerId);
    }

    private static void applyModifier(ServerPlayer player, double amount) {
        UUID uuid = player.getUUID();
        Double prev = APPLIED.get(uuid);
        if (prev != null && prev == amount) {
            return;
        }
        AttributeInstance instance = player.getAttribute(Attributes.ATTACK_SPEED);
        if (instance == null) {
            return;
        }
        instance.addOrUpdateTransientModifier(
                new AttributeModifier(MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        APPLIED.put(uuid, amount);
    }

    private static void clearModifier(ServerPlayer player) {
        UUID uuid = player.getUUID();
        FLURRY.remove(uuid);
        if (APPLIED.remove(uuid) == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(Attributes.ATTACK_SPEED);
        if (instance != null) {
            instance.removeModifier(MODIFIER_ID);
        }
    }
}
