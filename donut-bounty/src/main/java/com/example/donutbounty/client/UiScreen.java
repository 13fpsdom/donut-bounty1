package com.example.donutbounty.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Base for all of this mod's screens. It fixes the draw order (our panel first, then the vanilla text fields,
 * then overlays such as tooltips) and hides the per-version differences in how mouse clicks are delivered.
 */
abstract class UiScreen extends Screen {
    protected UiScreen(Text title) {
        super(title);
    }

    /** Everything that sits behind the vanilla widgets: dimming, panel, grids, custom buttons. */
    protected abstract void drawLayer(DrawContext ctx, int mouseX, int mouseY);

    /** Drawn on top of the widgets, for example tooltips. */
    protected void drawOverlay(DrawContext ctx, int mouseX, int mouseY) {
    }

    /** Left/right/middle click at (x, y) that no vanilla widget handled. Return true if consumed. */
    protected boolean onClick(double x, double y, int button) {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        drawLayer(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
        drawOverlay(ctx, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        return onClick(click.x(), click.y(), click.button());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
