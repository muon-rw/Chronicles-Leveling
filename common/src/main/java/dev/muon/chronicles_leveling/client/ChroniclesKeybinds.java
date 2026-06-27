package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.client.screen.ChroniclesTab;
import dev.muon.chronicles_leveling.network.NetworkDispatcher;
import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillRegistry;
import dev.muon.chronicles_leveling.skill.ability.AbilitySlots;
import dev.muon.chronicles_leveling.skill.ability.CastMode;
import dev.muon.chronicles_leveling.skill.ability.SkillAbility;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

/**
 * {@link KeyMapping} instances are created here; loader code registers them (NeoForge:
 * {@code RegisterKeyMappingsEvent}, Fabric: {@code KeyMappingHelper#registerKeyMapping}) and calls
 * {@link #tick()} from a per-tick hook to drain queued presses.
 *
 * <p>{@link #OPEN_STATS} defaults to {@code G} (PlayerEx parity). The {@link #ABILITY_SLOTS} action-bar keys
 * default to {@code Z X C V} (no vanilla-hotbar conflict); abilities are assigned to slots in the skill
 * screen, and the player can rebind in Controls.
 */
public final class ChroniclesKeybinds {

    private ChroniclesKeybinds() {}

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(ChroniclesLeveling.id("key.categories.chronicles_leveling"));

    public static final KeyMapping OPEN_STATS = new KeyMapping(
            "key.chronicles_leveling.open_stats", GLFW.GLFW_KEY_G, CATEGORY);

    /** Default cast key per action-bar ability slot (Z X C V); extra slots beyond these fall back to unbound. */
    private static final int[] SLOT_DEFAULT_KEYS = {
            GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V
    };

    public static final KeyMapping[] ABILITY_SLOTS = buildSlotKeys();

    private static KeyMapping[] buildSlotKeys() {
        KeyMapping[] keys = new KeyMapping[AbilitySlots.COUNT];
        for (int i = 0; i < keys.length; i++) {
            int defaultKey = i < SLOT_DEFAULT_KEYS.length ? SLOT_DEFAULT_KEYS[i] : GLFW.GLFW_KEY_UNKNOWN;
            keys[i] = new KeyMapping(
                    "key.chronicles_leveling.ability_slot_" + (i + 1), defaultKey, CATEGORY);
        }
        return keys;
    }

    /** Per-slot client belief of an in-progress held cast (the id we last sent a start for), for edge detection. */
    private static final Identifier[] heldActive = new Identifier[AbilitySlots.COUNT];

    public static void tick() {
        while (OPEN_STATS.consumeClick()) {
            ChroniclesTab.LEVELS.open();
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            Arrays.fill(heldActive, null);   // left the world; drop any held belief
            return;
        }
        PlayerSkillData data = PlayerSkillManager.get(player);
        for (int slot = 0; slot < ABILITY_SLOTS.length; slot++) {
            handleSlot(data, slot);
        }
    }

    /** Routes a slot key by its bound ability's cast mode: instant fires on press, held casts start/release on key down/up. */
    private static void handleSlot(PlayerSkillData data, int slot) {
        KeyMapping key = ABILITY_SLOTS[slot];
        String bound = data.slotAbility(slot);
        Identifier abilityId = bound == null ? null : Identifier.tryParse(bound);

        // Release a held cast whose slot was rebound or cleared.
        if (heldActive[slot] != null && !heldActive[slot].equals(abilityId)) {
            NetworkDispatcher.sendCastRelease(heldActive[slot]);
            heldActive[slot] = null;
        }
        if (abilityId == null) {
            drain(key);
            return;
        }

        SkillAbility ability = SkillRegistry.ability(abilityId);
        CastMode mode = ability != null ? ability.castMode() : CastMode.INSTANT;
        if (mode == CastMode.INSTANT) {
            boolean pressed = false;
            while (key.consumeClick()) {
                pressed = true;
            }
            if (pressed) {
                NetworkDispatcher.sendCastAbility(abilityId);
            }
            return;
        }

        // Held cast: start on key-down edge, release on key-up edge. A sub-tick tap (press AND release between
        // ticks) leaves isDown false but a queued click, so fire a paired start+release; a hold-scaled charge then
        // still gets a minimal cast (matching how the instant path uses the click queue to survive fast taps).
        boolean tapped = false;
        while (key.consumeClick()) {
            tapped = true;
        }
        boolean down = key.isDown();
        if (down && heldActive[slot] == null) {
            NetworkDispatcher.sendCastStart(abilityId);
            heldActive[slot] = abilityId;
        } else if (!down && heldActive[slot] != null) {
            NetworkDispatcher.sendCastRelease(abilityId);
            heldActive[slot] = null;
        } else if (!down && tapped && heldActive[slot] == null) {
            NetworkDispatcher.sendCastStart(abilityId);
            NetworkDispatcher.sendCastRelease(abilityId);
        }
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // discard queued clicks; held casts track raw key state via isDown
        }
    }
}
