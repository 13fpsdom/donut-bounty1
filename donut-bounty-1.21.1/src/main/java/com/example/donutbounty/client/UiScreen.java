package com.example.donutbounty.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Base for all of this mod's screens (Minecraft 1.21.1 flavour). It fixes the draw order (our panel first, then the
 * vanilla text fields, then overlays such as tooltips). The vanilla background (blur) is switched off because
 * the screens draw their own dimming and panel.
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
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // intentionally empty: drawLayer() paints the background
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        drawLayer(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
        drawOverlay(ctx, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return onClick(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
