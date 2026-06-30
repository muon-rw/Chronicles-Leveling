package dev.muon.chronicles_leveling.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;

import java.util.function.BooleanSupplier;

/**
 * Sprite-backed {@code +} button drawn from the gui.png cell at
 * {@link ChroniclesSprites#plusButtonU()}. Three states (idle / hover /
 * disabled) come from three rows on the sheet; we don't draw any text.
 *
 * <p>{@code activeCheck} is sampled at render and click time so the screen
 * doesn't have to push state changes back into the widget; it just declares
 * "this button is live when X is true".
 */
public class PlusButton extends AbstractButton {

    private final Runnable action;
    private final BooleanSupplier activeCheck;
    private final Holder<SoundEvent> downSound;

    public PlusButton(int x, int y, Component narration, Holder<SoundEvent> downSound, BooleanSupplier activeCheck, Runnable action) {
        super(x, y, ChroniclesSprites.BUTTON_W, ChroniclesSprites.BUTTON_H, narration);
        this.action = action;
        this.activeCheck = activeCheck;
        this.downSound = downSound;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (!activeCheck.getAsBoolean()) return;
        action.run();
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        // Per-button down-sound: sp_spend for buying a level, ui.button.click for allocating a stat.
        soundManager.play(SimpleSoundInstance.forUI(downSound, 1.0f));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean enabled = activeCheck.getAsBoolean();
        this.active = enabled;

        int v = ChroniclesSprites.buttonV(enabled, isHoveredOrFocused());
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ChroniclesTextures.GUI,
                getX(), getY(),
                ChroniclesSprites.plusButtonU(), v,
                ChroniclesSprites.BUTTON_W, ChroniclesSprites.BUTTON_H,
                ChroniclesSprites.SHEET_SIZE, ChroniclesSprites.SHEET_SIZE
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
