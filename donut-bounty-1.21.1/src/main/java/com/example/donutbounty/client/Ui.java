package com.example.donutbounty.client;

import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/** Shared look (dark panel, thin cyan top line) and the few drawing helpers that differ between Minecraft versions. */
final class Ui {
    static final int PANEL_BG = 0xF0101418;
    static final int PANEL_BORDER = 0xFF2A323C;
    static final int INSET = 0xFF0B0F14;
    static final int ACCENT = 0xFF4FC3F7;
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFF9CA3AF;
    static final int DIM = 0xFF6B7280;
    static final int GOOD = 0xFF4ADE80;
    static final int BAD = 0xFFF87171;

    private Ui() {}

    // ---- the only version-specific drawing code ----------------------------------------------------

    static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.drawBorder(x, y, w, h, color);
    }

    /** Moves the origin to (tx, ty) and scales everything drawn afterwards. Always pair with pop(). */
    static void push(DrawContext ctx, float tx, float ty, float scale) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(tx, ty, 0f);
        m.scale(scale, scale, 1f);
    }

    static void pop(DrawContext ctx) {
        ctx.getMatrices().pop();
    }

    // ------------------------------------------------------------------------------------------------

    static void panel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, PANEL_BG);
        outline(ctx, x, y, w, h, PANEL_BORDER);
        ctx.fill(x, y, x + w, y + 2, ACCENT);
    }

    /** Multiplies the alpha of an ARGB colour by f (0..1). */
    static int fade(int argb, float f) {
        float c = Math.max(0f, Math.min(1f, f));
        int a = Math.round((argb >>> 24) * c);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Lays buttons out left to right, wrapping when the next one would not fit. Returns the rows used. */
    static int flow(List<Button> buttons, int x, int y, int maxW, int gap, int rowH) {
        int cx = x;
        int row = 0;
        for (Button b : buttons) {
            if (cx > x && cx + b.w > x + maxW) {
                cx = x;
                row++;
            }
            b.x = cx;
            b.y = y + row * rowH;
            cx += b.w + gap;
        }
        return buttons.isEmpty() ? 0 : row + 1;
    }

    /** A flat custom-drawn button. The screen decides what a click does. */
    static final class Button {
        int x;
        int y;
        int w = 60;
        int h = 20;
        final String label;
        final boolean primary;
        final Runnable action;
        boolean enabled = true;

        Button(String label, boolean primary, Runnable action) {
            this.label = label;
            this.primary = primary;
            this.action = action;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        void render(DrawContext ctx, TextRenderer tr, int mx, int my) {
            boolean hover = enabled && contains(mx, my);
            int bg = !enabled ? 0xFF141A20
                    : primary ? (hover ? 0xFF8BDCFB : ACCENT)
                    : (hover ? 0xFF2B3744 : 0xFF1F2730);
            ctx.fill(x, y, x + w, y + h, bg);
            int fg = !enabled ? DIM : primary ? 0xFF0B0F14 : TEXT;
            ctx.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2, fg, false);
        }
    }
}
