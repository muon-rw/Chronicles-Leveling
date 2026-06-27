package dev.muon.chronicles_leveling.event;

import dev.muon.chronicles_leveling.skill.combat.CombatProcRouter;
import dev.muon.chronicles_leveling.skill.xp.DamageXpRouter;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric-side skill-XP routing for events that exist in the Fabric API.
 * Hooks without a Fabric API event ride mixins instead; see the
 * {@code dev.muon.chronicles_leveling.mixin} package on this side.
 */
public final class SkillXpEventsFabric {

    private SkillXpEventsFabric() {}

    public static void initLifecycle() {
        // Taken XP rides AFTER_DAMAGE. Dealt XP rides LivingEntityActuallyHurtMixin instead, since
        // AFTER_DAMAGE skips killing blows by API contract and its damageTaken isn't truly
        // post-mitigation. Dodge-style mid-pipeline zeroing isn't detectable here: most evasion mods
        // cancel ALLOW_DAMAGE upstream, so AFTER_DAMAGE doesn't fire in those cases.
        // The reactive combat procs (Riposte/Retribution) ride the PlayerMixinFabric setHealth seam instead,
        // because AFTER_DAMAGE's damageTaken is documented PRE-armor, which would make Retribution
        // reflect a different magnitude than the post-mitigation NeoForge path.
        // AFTER_DAMAGE fires at hurtServer TAIL gated on !isDeadOrDying, which is AFTER totem
        // processing, so a totem-saved victim arrives here alive. Taken XP is pre-mitigation, so a
        // totem-tanked lethal hit would grant runaway Defense XP; skip it (same policy as NeoForge).
        // Consume the mark before the blocked gate so a partially-blocked totem pop can't leave it stale.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!(entity instanceof ServerPlayer)) return;
            boolean totemSaved = TotemHitBridge.consumeSaved(entity.getId());
            if (blocked || totemSaved) return;
            DamageXpRouter.onDamageTaken(entity, source, baseDamageTaken);
        });

        // Mining + Herbalism block-break XP rides the common ServerPlayerGameModeMixin (productive-break seam),
        // shared with NeoForge; the Fabric AFTER callback fired on every break (creative + no-drop) and diverged.

        // Kill procs (Toxicologist) credited to the killer; AFTER_DEATH fires only on an actual
        // death (post totem revive).
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer killer) {
                CombatProcRouter.onKill(killer, entity);
            }
        });
    }
}
