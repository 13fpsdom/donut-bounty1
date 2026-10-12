package com.example.donutbounty.client;

import com.example.donutbounty.BountyService;
import com.example.donutbounty.ModConfig;
import com.example.donutbounty.core.BountyTimer;
import com.example.donutbounty.core.Colors;
import com.example.donutbounty.core.DurationParser;
import com.example.donutbounty.core.Money;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/**
 * Top-centre panel in the style of an auction display:
 * item icon + name, "Time remaining", top payer, payment count / raised, plus "Name paid $amount" pop-ups below it.
 */
public final class BountyHud {
    private static final int PANEL_W = 190;

    private static String cachedId = null;
    private static ItemStack cachedIcon = ItemStack.EMPTY;
    private static String cachedName = "";

    private BountyHud() {}

    public static void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        BountyService svc = DonutBountyClient.service();
        if (svc == null || mc.options.hudHidden || mc.player == null) return;
        if (mc.currentScreen instanceof UiScreen) return; // our own screens would overlap the HUD
        ModConfig.Hud hud = svc.config().hud;
        if (!hud.enabled) return;
        BountyService.Snapshot snap = svc.snapshot();
        if (snap.state() == BountyTimer.State.IDLE) return;
        long now = System.currentTimeMillis();
        List<BountyService.Notice> notices = hud.showPaymentNotices
                ? svc.recentNotices(now, Math.max(1, Math.min(30, hud.noticeSeconds)) * 1000L)
                : List.of();
        draw(context, mc.textRenderer, snap, hud, mc.getWindow().getScaledWidth(), notices, now);
    }

    /** Example data for the settings preview. */
    public static BountyService.Snapshot sample() {
        return new BountyService.Snapshot(BountyTimer.State.RUNNING, 754_000L, 0.42,
                "minecraft:elytra", 500_000_000L, 182_500_000L, "Steve", 120_000_000L, 4);
    }

    public static List<BountyService.Notice> sampleNotices(long now) {
        List<BountyService.Notice> l = new ArrayList<>();
        l.add(new BountyService.Notice("Alex", 25_000_000_00L, now));
        return l;
    }

    public static void draw(DrawContext ctx, TextRenderer tr, BountyService.Snapshot s, ModConfig.Hud hud, int screenWidth,
                            List<BountyService.Notice> notices, long now) {
        updateCache(s.itemId());

        int bg = Colors.parseOr(hud.background, 0xD0101418);
        int border = Colors.parseOr(hud.border, 0xFF2A323C);
        int text = Colors.parseOr(hud.text, 0xFFFFFFFF);
        int accent = Colors.parseOr(hud.accent, 0xFF4FC3F7);
        int warning = Colors.parseOr(hud.warning, 0xFFF87171);
        int track = Colors.parseOr(hud.track, 0xFF2A323C);
        int good = Colors.parseOr(hud.success, 0xFF4ADE80);
        int dim = (text & 0x00FFFFFF) | 0xB0000000;

        boolean hasTarget = false; // auctions have no target
        boolean low = s.state() == BountyTimer.State.RUNNING && s.remainingMillis() < 60_000L;
        boolean ended = s.state() == BountyTimer.State.FINISHED;
        int h = ended ? 78 : 66;
        int x0 = -PANEL_W / 2;
        int barL = x0 + 8;
        int barR = x0 + PANEL_W - 8;

        String timerText;
        int timerColour = text;
        switch (s.state()) {
            case PAUSED -> { timerText = DurationParser.format(s.remainingMillis()) + " (paused)"; timerColour = dim; }
            case FINISHED -> { timerText = "Ended"; timerColour = warning; }
            default -> { timerText = DurationParser.format(s.remainingMillis()); if (low) timerColour = warning; }
        }

        Ui.push(ctx, screenWidth / 2.0f + hud.offsetX, (float) hud.offsetY, (float) hud.scale);

        ctx.fill(x0, 0, x0 + PANEL_W, h, bg);
        Ui.outline(ctx, x0, 0, PANEL_W, h, border);
        ctx.fill(x0, 0, x0 + PANEL_W, 2, accent);

        // row 1: icon, "AUCTION", item name
        if (!cachedIcon.isEmpty()) ctx.drawItem(cachedIcon, x0 + 8, 6);
        else ctx.fill(x0 + 8, 6, x0 + 24, 22, track);
        ctx.drawText(tr, "AUCTION", x0 + 30, 7, accent, false);
        ctx.drawText(tr, tr.trimToWidth(cachedName, PANEL_W - 38), x0 + 30, 16, text, true);

        // row 2: time remaining
        int y = 28;
        ctx.drawText(tr, "Time remaining", x0 + 8, y, dim, false);
        ctx.drawText(tr, timerText, barR - tr.getWidth(timerText), y, timerColour, true);
        y += 11;
        ctx.fill(barL, y, barR, y + 2, track);
        ctx.fill(barL, y, barL + (int) Math.round((barR - barL) * s.fractionRemaining()), y + 2, low ? warning : accent);
        y += 6;

        // row 3: top payer
        ctx.drawText(tr, ended ? "Winning payment" : "Highest payment", x0 + 8, y, dim, false);
        String top = s.topName().isEmpty() ? "none yet" : Money.compact(s.topCents()) + "  " + s.topName();
        ctx.drawText(tr, tr.trimToWidth(top, PANEL_W - 70), barR - Math.min(tr.getWidth(top), PANEL_W - 70), y,
                s.topName().isEmpty() ? dim : good, true);
        y += 11;

        // row 4: payments and raised
        String raised = s.paymentCount() + (s.paymentCount() == 1 ? " payment" : " payments");
        ctx.drawText(tr, raised, x0 + 8, y, dim, false);
        String rt = Money.compact(s.raisedCents()) + (hasTarget ? " / " + Money.compact(s.targetCents()) : " raised");
        ctx.drawText(tr, rt, barR - tr.getWidth(rt), y, text, false);
        if (hasTarget) {
            y += 10;
            double frac = Math.min(1.0, s.raisedCents() / (double) s.targetCents());
            ctx.fill(barL, y, barR, y + 2, track);
            ctx.fill(barL, y, barL + (int) Math.round((barR - barL) * frac), y + 2, frac >= 1.0 ? good : accent);
        }

        if (ended) {
            y += 11;
            String won = s.topName().isEmpty() ? "No payments - no winner" : s.topName() + " won";
            ctx.drawText(tr, tr.trimToWidth(won, PANEL_W - 16), x0 + 8, y, s.topName().isEmpty() ? dim : good, true);
        }

        // pop-ups: "Name paid $amount", newest first, fading near the end
        int ny = h + 4;
        long life = Math.max(1, Math.min(30, hud.noticeSeconds)) * 1000L;
        for (BountyService.Notice n : notices) {
            long age = now - n.atMillis();
            float alpha = age > life - 800 ? Math.max(0f, (life - age) / 800f) : 1f;
            String who = n.player();
            String paid = " paid ";
            String amt = Money.format(n.amountCents());
            int w = tr.getWidth(who) + tr.getWidth(paid) + tr.getWidth(amt) + 16;
            int nx = -w / 2;
            ctx.fill(nx, ny, nx + w, ny + 16, Ui.fade(bg, alpha));
            Ui.outline(ctx, nx, ny, w, 16, Ui.fade(border, alpha));
            ctx.fill(nx, ny, nx + 2, ny + 16, Ui.fade(good, alpha));
            int tx = nx + 8;
            ctx.drawText(tr, who, tx, ny + 4, Ui.fade(text, alpha), true);
            tx += tr.getWidth(who);
            ctx.drawText(tr, paid, tx, ny + 4, Ui.fade(dim, alpha), false);
            tx += tr.getWidth(paid);
            ctx.drawText(tr, amt, tx, ny + 4, Ui.fade(good, alpha), true);
            ny += 19;
        }

        Ui.pop(ctx);
    }

    private static void updateCache(String itemId) {
        if (itemId != null && itemId.equals(cachedId)) return;
        cachedId = itemId;
        var entry = ItemCatalog.byId(itemId);
        if (entry.isPresent()) {
            cachedIcon = entry.get().icon();
            cachedName = entry.get().name();
        } else {
            cachedIcon = ItemStack.EMPTY;
            cachedName = itemId == null ? "" : itemId;
        }
    }
}
