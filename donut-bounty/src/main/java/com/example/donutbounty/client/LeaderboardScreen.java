package com.example.donutbounty.client;

import com.example.donutbounty.BountyService;
import com.example.donutbounty.core.Leaderboard;
import com.example.donutbounty.core.Money;
import com.example.donutbounty.core.TimeAgo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Everyone who has paid you, ranked by total. Export CSV, add manually, clear (after confirmation). */
public class LeaderboardScreen extends UiScreen {
    private static final int ROW_H = 14;

    private final Screen parent;
    private final List<Ui.Button> buttons = new ArrayList<>();
    private int scroll;
    private String status = "";
    private int px, py, pw, ph, rowsTop, rowsBottom;

    public LeaderboardScreen(Screen parent) {
        super(Text.literal("Leaderboard"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(this.width - 16, 420);
        ph = Math.min(this.height - 8, 280);
        px = (this.width - pw) / 2;
        py = Math.max(4, (this.height - ph) / 2);
        rowsTop = py + 50;
        rowsBottom = py + ph - 44;

        BountyService svc = DonutBountyClient.service();
        buttons.clear();
        int gap = 4;
        int w = (pw - 20 - gap * 3) / 4;
        int y = py + ph - 28;
        add("Export CSV", false, px + 10, y, w, () -> {
            try {
                Path file = svc.exportCsv();
                this.client.keyboard.setClipboard(file.toString());
                status = "Saved " + file.getFileName() + " (full path copied to clipboard)";
            } catch (IOException e) {
                status = "Export failed: " + e.getMessage();
            }
        });
        add("Add manually", false, px + 10 + (w + gap), y, w, () -> this.client.setScreen(new ManualPaymentScreen(this)));
        add("Clear data", false, px + 10 + (w + gap) * 2, y, w, () ->
                this.client.setScreen(new ConfirmScreen(
                        confirmed -> {
                            if (confirmed) svc.clearLeaderboard();
                            this.client.setScreen(new LeaderboardScreen(parent));
                        },
                        Text.literal("Clear the leaderboard?"),
                        Text.literal("This permanently deletes all recorded payments. Exported CSV files are kept. Export first if you want a copy."))));
        add("Back", true, px + 10 + (w + gap) * 3, y, w, this::close);
    }

    private void add(String label, boolean primary, int x, int y, int w, Runnable r) {
        Ui.Button b = new Ui.Button(label, primary, r);
        b.x = x;
        b.y = y;
        b.w = w;
        b.h = 20;
        buttons.add(b);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    private int visibleRows() {
        return Math.max(1, (rowsBottom - rowsTop) / ROW_H);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int total = DonutBountyClient.service().leaderboard().playerCount();
        int maxScroll = Math.max(0, total - visibleRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount) * 2));
        return true;
    }

    @Override
    protected boolean onClick(double x, double y, int button) {
        if (button != 0) return false;
        for (Ui.Button b : new ArrayList<>(buttons)) {
            if (b.contains(x, y)) {
                b.action.run();
                return true;
            }
        }
        return false;
    }

    @Override
    protected void drawLayer(DrawContext ctx, int mx, int my) {
        ctx.fill(0, 0, this.width, this.height, 0x90000000);
        Ui.panel(ctx, px, py, pw, ph);

        BountyService svc = DonutBountyClient.service();
        Leaderboard board = svc.leaderboard();
        List<Leaderboard.Entry> ranked = board.ranked();

        ctx.drawText(this.textRenderer, "Leaderboard", px + 10, py + 10, Ui.TEXT, true);
        String summary = ranked.size() + (ranked.size() == 1 ? " player" : " players") + "  |  "
                + board.paymentCount() + (board.paymentCount() == 1 ? " payment" : " payments") + "  |  "
                + Money.format(board.totalCents()) + " total";
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(summary, pw - 20), px + 10, py + 24, Ui.MUTED, false);

        int tableX = px + 10;
        int tableW = pw - 20;
        int nameW = (int) (tableW * 0.28);
        int totalW = (int) (tableW * 0.22);
        int countW = (int) (tableW * 0.12);
        int c1 = tableX + 6;
        int c2 = tableX + 28;
        int c3 = c2 + nameW;
        int c4 = c3 + totalW;
        int c5 = c4 + countW;

        ctx.fill(tableX, py + 36, tableX + tableW, rowsBottom + 2, Ui.INSET);
        ctx.fill(tableX, py + 36, tableX + tableW, py + 48, 0xFF1F2730);
        ctx.drawText(this.textRenderer, "#", c1, py + 38, Ui.MUTED, false);
        ctx.drawText(this.textRenderer, "Player", c2, py + 38, Ui.MUTED, false);
        ctx.drawText(this.textRenderer, "Total paid", c3, py + 38, Ui.MUTED, false);
        ctx.drawText(this.textRenderer, "Payments", c4, py + 38, Ui.MUTED, false);
        ctx.drawText(this.textRenderer, "Last payment", c5, py + 38, Ui.MUTED, false);

        if (ranked.isEmpty()) {
            ctx.drawText(this.textRenderer, "No payments recorded yet.", tableX + 8, rowsTop + 6, Ui.MUTED, false);
            ctx.drawText(this.textRenderer, "Start a bounty and wait for payments, or use Add manually.", tableX + 8, rowsTop + 20, Ui.DIM, false);
        }

        int rows = visibleRows();
        int maxScroll = Math.max(0, ranked.size() - rows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        long now = System.currentTimeMillis();
        for (int i = 0; i < rows; i++) {
            int idx = scroll + i;
            if (idx >= ranked.size()) break;
            Leaderboard.Entry e = ranked.get(idx);
            int y = rowsTop + i * ROW_H;
            if (idx % 2 == 0) ctx.fill(tableX, y - 2, tableX + tableW, y + ROW_H - 2, 0x20FFFFFF);
            int rankColour = idx == 0 ? 0xFFFFD700 : idx == 1 ? 0xFFC0C0C0 : idx == 2 ? 0xFFCD7F32 : Ui.TEXT;
            ctx.drawText(this.textRenderer, String.valueOf(idx + 1), c1, y, rankColour, false);
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(e.name(), nameW - 6), c2, y, Ui.TEXT, false);
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(Money.format(e.totalCents()), totalW - 6), c3, y, Ui.GOOD, false);
            ctx.drawText(this.textRenderer, String.valueOf(e.count()), c4, y, Ui.TEXT, false);
            String last = TimeAgo.format(e.lastPaymentMillis(), now) + " (" + Money.compact(e.lastAmountCents()) + ")";
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(last, tableX + tableW - c5 - 4), c5, y, Ui.MUTED, false);
        }

        for (Ui.Button b : buttons) b.render(ctx, this.textRenderer, mx, my);

        String msg = status.isEmpty() ? svc.statusLine() : status;
        if (!msg.isEmpty()) {
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(msg, pw - 20), px + 10, py + ph - 40, 0xFFFFD166, false);
        }
    }
}
