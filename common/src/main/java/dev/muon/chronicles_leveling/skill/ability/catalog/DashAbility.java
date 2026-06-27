package dev.muon.chronicles_leveling.skill.ability.catalog;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.Skills;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbstractAbility;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Acrobatics: a forward lunge with a brief i-frame window. Proves the pipeline: a stamina cost gate,
 * a one-shot impulse in {@link #run}, a server-authoritative i-frame window (enforced by the
 * {@code isInvulnerableTo} mixin), and a cooldown stamp, all with no per-player state on the ability.
 */
public final class DashAbility extends AbstractAbility {

    public static final Identifier ID = ChroniclesLeveling.id("ability/dash");

    public DashAbility() {
        super(ID, Skills.ACROBATICS, 100, AbilityCost.stamina(15f));   // ~5s cooldown; placeholder tuning
    }

    @Override
    public int durationTicks() {
        return Configs.SKILLS.acrobatics.dashIframeTicks.get();
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        return !player.isPassenger();
    }

    @Override
    public Component activationError(ServerPlayer player) {
        return Component.translatable("chronicles_leveling.ability.error.riding");
    }

    @Override
    public void run(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(new Vec3(look.x, 0, look.z).normalize().scale(1.4).add(0, 0.25, 0));
        player.hurtMarked = true;   // push the velocity change to the client
        AbilityWindowStore.open(player, AbilityWindowStore.WindowKind.IFRAME, durationTicks());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.4f);
    }
}
