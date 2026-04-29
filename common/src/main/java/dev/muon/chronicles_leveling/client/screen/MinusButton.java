package dev.muon.chronicles_leveling.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * Sprite-backed {@code -} button. Mirrors {@link PlusButton} but draws from the
 * minus column on the gui.png sheet, and adds a {@code forceHoveredCheck} that
 * lets the screen pin the button into its hovered visual for reasons outside
 * the widget's own bounds — currently used by the orb-of-regret reset flow so
 * the button reflects the row's selection AND the row's mouse-hover, not just
 * its own pixel-rect.
 *
 * <p>State table:
 * <ul>
 *   <li>{@code activeCheck} false → disabled row (grayed glyph).</li>
 *   <li>{@code activeCheck} true, mouse off, not force-hovered → idle row.</li>
 *   <li>{@code activeCheck} true, mouse on or force-hovered → hover row.</li>
 * </ul>
 */
public class MinusButton extends AbstractButton {

    private final Runnable action;
    private final BooleanSupplier activeCheck;
    private final BooleanSupplier forceHoveredCheck;

    public MinusButton(int x, int y, Component narration,
                       BooleanSupplier activeCheck, BooleanSupplier forceHoveredCheck,
                       Runnable action) {
        super(x, y, ChroniclesSprites.BUTTON_W, ChroniclesSprites.BUTTON_H, narration);
        this.action = action;
        this.activeCheck = activeCheck;
        this.forceHoveredCheck = forceHoveredCheck;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (!activeCheck.getAsBoolean()) return;
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean enabled = activeCheck.getAsBoolean();
        this.active = enabled;

        boolean hovered = isHoveredOrFocused() || forceHoveredCheck.getAsBoolean();
        int v = ChroniclesSprites.buttonV(enabled, hovered);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ChroniclesTextures.GUI,
                getX(), getY(),
                ChroniclesSprites.minusButtonU(), v,
                ChroniclesSprites.BUTTON_W, ChroniclesSprites.BUTTON_H,
                ChroniclesSprites.SHEET_SIZE, ChroniclesSprites.SHEET_SIZE
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
