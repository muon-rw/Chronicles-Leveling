package dev.muon.chronicles_leveling.skill.enchant;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.catalog.EnchantingSkill;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Essence Hoarder (Enchanting): a <b>dynamic</b> attribute bonus. For each enchantment on the player's worn
 * armor and held items, grant the configurable {@code essenceHoarderRegenPerEnchant} to Combat-Attributes
 * resources, gated by perk rank (1 = mana regen, 2 also stamina regen, 3 also health regen). The count is
 * level-independent (one per enchantment entry, not per enchant level).
 *
 * <p>Unlike static perks, which {@code SkillModifierApplier} materializes once at perk-walk time, this
 * depends on live equipment, so it's recomputed <b>event-driven</b>: {@link #onEquipmentChanged} fires from
 * vanilla's server-side equipment-change detection whenever a worn or held item changes (a hotbar selection, an
 * off-hand F-key swap, or the initial gear on login/respawn). A throttled {@link #tick} poll is
 * kept only as a fallback so any change the event misses still converges; written this way so the mechanism
 * generalises to attributes where latency matters more than it does for regen. A per-player snapshot of the
 * last applied {@code (rank, count)} keeps both entry points idempotent: attribute modifiers (and their client
 * sync) are rewritten only when the value actually changes. Modifiers are transient: they vanish cleanly on
 * logout and are re-derived on the next equipment event after login.
 */
public final class EssenceHoarderHandler {

    private EssenceHoarderHandler() {}

    private static final int POLL_INTERVAL_TICKS = 20;

    /** Worn + held slots whose enchantments count toward the bonus. */
    private static final EquipmentSlot[] SCANNED_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    /** CA regen attribute granted at each rank threshold: index i is unlocked once rank reaches {@code i + 1}. */
    private static final Identifier[] TIERED_ATTRIBUTES = {
            Identifier.fromNamespaceAndPath("combat_attributes", "mana_regen"),      // rank 1+
            Identifier.fromNamespaceAndPath("combat_attributes", "stamina_regen"),   // rank 2+
            Identifier.fromNamespaceAndPath("combat_attributes", "health_regen")     // rank 3
    };

    /** The last applied (rank, enchant-count); absent for players who don't hold the perk. */
    private record Applied(int tier, int count) {}
    private static final Map<UUID, Applied> STATE = new HashMap<>();

    /**
     * Event-driven entry point: vanilla detected a change to this player's worn or held equipment. Immediate
     * and idempotent (the snapshot skips the write if the resulting {@code (rank, count)} is unchanged).
     */
    public static void onEquipmentChanged(ServerPlayer player) {
        recompute(player);
    }

    /**
     * Fallback poll (throttled): converges any change the {@link #onEquipmentChanged} signal misses, and GCs
     * snapshots for players who have logged off. Called from the shared server-tick hook.
     */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % POLL_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            recompute(player);
        }
        STATE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    /** Drops a player's snapshot on logout (transient modifiers die with the entity). */
    public static void clear(UUID playerId) {
        STATE.remove(playerId);
    }

    private static void recompute(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int tier = (int) Math.floor(SkillEffects.get(player, EnchantingSkill.ESSENCE_HOARDER_TIER));
        Applied prev = STATE.get(uuid);

        if (tier <= 0) {
            if (prev != null) {
                // Perk was respec'd away: strip our modifiers and stop tracking this player.
                for (Identifier attribute : TIERED_ATTRIBUTES) {
                    writeModifier(player, attribute, 0.0);
                }
                STATE.remove(uuid);
            }
            return;
        }

        int count = countEnchantments(player);
        if (prev != null && prev.tier() == tier && prev.count() == count) {
            return;
        }
        double amount = Configs.SKILLS.enchanting.essenceHoarderRegenPerEnchant.get() * count;
        for (int i = 0; i < TIERED_ATTRIBUTES.length; i++) {
            writeModifier(player, TIERED_ATTRIBUTES[i], tier >= i + 1 ? amount : 0.0);
        }
        STATE.put(uuid, new Applied(tier, count));
    }

    private static int countEnchantments(ServerPlayer player) {
        int total = 0;
        for (EquipmentSlot slot : SCANNED_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                total += stack.getEnchantments().size();
            }
        }
        return total;
    }

    private static void writeModifier(ServerPlayer player, Identifier attribute, double amount) {
        AttributeInstance instance = instanceFor(player, attribute);
        if (instance == null) {
            return;   // CA absent, or this entity's supplier lacks the attribute: skip silently
        }
        Identifier id = ChroniclesLeveling.id("essence_hoarder/" + attribute.getPath());
        if (amount > 0.0) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        } else {
            instance.removeModifier(id);
        }
    }

    private static AttributeInstance instanceFor(ServerPlayer player, Identifier attributeId) {
        Optional<? extends Holder<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(
                ResourceKey.create(Registries.ATTRIBUTE, attributeId));
        return holder.map(player::getAttribute).orElse(null);
    }
}
