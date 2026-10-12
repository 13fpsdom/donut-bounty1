package com.example.donutbounty.client;

import com.example.donutbounty.BountyService;
import com.example.donutbounty.core.BountyTimer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Main screen: searchable icon grid of every item, duration chips, optional target, big Start button. */
public class BountyScreen extends UiScreen {
    private static final int CELL = 20;
    private static final String[] PRESETS = {"5m", "15m", "30m", "1h", "2h", "6h", "24h"};

    private final Screen parent;
    private TextFieldWidget searchField;
    private TextFieldWidget durationField;

    private String search = "";
    private String durationText;
    private String targetText;
    private String selectedId;
    private String status = "";
    private int statusColour = Ui.BAD;

    private List<ItemCatalog.Entry> shown = List.of();
    private int scrollRow;

    private final List<Ui.Button> chips = new ArrayList<>();
    private final List<Ui.Button> buttons = new ArrayList<>();
    private Ui.Button startBtn;
    private Ui.Button pauseBtn;
    private Ui.Button stopBtn;

    private int px, py, pw, ph;
    private int gridX, gridY, gridW, gridH, cols, rows;
    private int selY, chipsY, fieldY, actionY;

    public BountyScreen(Screen parent) {
        super(Text.literal("Auction"));
        this.parent = parent;
        var cfg = DonutBountyClient.service().config();
        this.selectedId = DonutBountyClient.service().itemId().isBlank() ? cfg.lastItemId : DonutBountyClient.service().itemId();
        this.durationText = cfg.lastDuration;
        this.targetText = cfg.lastTarget;
    }

    private static BountyService svc() {
        return DonutBountyClient.service();
    }

    @Override
    protected void init() {
        pw = Math.min(this.width - 16, 330);
        ph = Math.min(this.height - 8, 292);
        px = (this.width - pw) / 2;
        py = Math.max(4, (this.height - ph) / 2);

        int fixed = 46 + 130; // title+search above the grid; selected row, chips, fields, button and status below it
        gridH = Math.max(CELL * 2, ((ph - fixed) / CELL) * CELL);
        gridX = px + 10;
        gridW = pw - 20 - 6;
        cols = Math.max(1, gridW / CELL);
        rows = gridH / CELL;
        gridY = py + 46;
        selY = gridY + gridH + 6;
        chipsY = selY + 26;
        fieldY = chipsY + 22;
        actionY = fieldY + 34;

        searchField = new TextFieldWidget(this.textRenderer, px + 10, py + 25, pw - 20, 16, Text.literal("Search"));
        searchField.setMaxLength(40);
        searchField.setPlaceholder(Text.literal("Search items (elytra, enchanted golden apple...)"));
        searchField.setText(search);
        searchField.setChangedListener(s -> {
            search = s;
            scrollRow = 0;
            refilter();
        });
        addDrawableChild(searchField);
        setInitialFocus(searchField);

        int fw = pw - 20;
        durationField = new TextFieldWidget(this.textRenderer, px + 10, fieldY + 12, fw, 16, Text.literal("Duration"));
        durationField.setMaxLength(12);
        durationField.setText(durationText);
        durationField.setChangedListener(s -> durationText = s);
        addDrawableChild(durationField);


        chips.clear();
        for (String p : PRESETS) {
            Ui.Button b = new Ui.Button(p, false, () -> {
                durationText = p;
                durationField.setText(p);
            });
            b.w = 36;
            b.h = 16;
            chips.add(b);
        }
        Ui.flow(chips, px + 10, chipsY, pw - 20, 4, 18);

        buttons.clear();
        int bw = pw - 20;
        boolean active = svc().timerState() == BountyTimer.State.RUNNING || svc().timerState() == BountyTimer.State.PAUSED;
        startBtn = new Ui.Button("START AUCTION", true, this::start);
        startBtn.x = px + 10;
        startBtn.y = actionY;
        startBtn.w = bw;
        startBtn.h = 24;
        pauseBtn = new Ui.Button(svc().timerState() == BountyTimer.State.PAUSED ? "Resume" : "Pause", false, this::pauseResume);
        stopBtn = new Ui.Button("Stop", false, () -> {
            svc().stop();
            this.client.setScreen(new BountyScreen(parent));
        });
        pauseBtn.x = px + 10;
        pauseBtn.y = actionY;
        pauseBtn.w = (bw - 4) / 2;
        pauseBtn.h = 24;
        stopBtn.x = px + 10 + pauseBtn.w + 4;
        stopBtn.y = actionY;
        stopBtn.w = bw - pauseBtn.w - 4;
        stopBtn.h = 24;
        if (active) {
            buttons.add(pauseBtn);
            buttons.add(stopBtn);
        } else {
            buttons.add(startBtn);
        }

        // top-right link buttons
        Ui.Button lb = new Ui.Button("Leaderboard", false, () -> this.client.setScreen(new LeaderboardScreen(this)));
        Ui.Button sb = new Ui.Button("Settings", false, () -> this.client.setScreen(new SettingsScreen(this)));
        sb.w = 50;
        sb.h = 14;
        sb.x = px + pw - 10 - sb.w;
        sb.y = py + 7;
        lb.w = 68;
        lb.h = 14;
        lb.x = sb.x - 4 - lb.w;
        lb.y = py + 7;
        buttons.add(lb);
        buttons.add(sb);

        refilter();
    }

    private void refilter() {
        shown = ItemCatalog.filter(search);
    }

    private void start() {
        String err = svc().start(selectedId, durationText, "");
        if (err != null) {
            status = err;
            statusColour = Ui.BAD;
        } else {
            this.client.setScreen(null);
        }
    }

    private void pauseResume() {
        if (svc().timerState() == BountyTimer.State.PAUSED) svc().resume();
        else svc().pause();
        this.client.setScreen(new BountyScreen(parent));
    }

    @Override
    public void close() {
        svc().rememberInputs(selectedId, durationText, targetText);
        this.client.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= gridX && mouseX < gridX + gridW + 6 && mouseY >= gridY && mouseY < gridY + gridH) {
            int totalRows = (shown.size() + cols - 1) / cols;
            int max = Math.max(0, totalRows - rows);
            scrollRow = Math.max(0, Math.min(max, scrollRow - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int cellAt(double mx, double my) {
        if (mx < gridX || mx >= gridX + cols * CELL || my < gridY || my >= gridY + rows * CELL) return -1;
        int c = (int) ((mx - gridX) / CELL);
        int r = (int) ((my - gridY) / CELL);
        int idx = (scrollRow + r) * cols + c;
        return idx < shown.size() ? idx : -1;
    }

    @Override
    protected boolean onClick(double x, double y, int button) {
        if (button != 0) return false;
        int idx = cellAt(x, y);
        if (idx >= 0) {
            selectedId = shown.get(idx).id().toString();
            status = "";
            return true;
        }
        for (Ui.Button b : chips) {
            if (b.contains(x, y)) {
                b.action.run();
                return true;
            }
        }
        for (Ui.Button b : new ArrayList<>(buttons)) {
            if (b.enabled && b.contains(x, y)) {
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
        ctx.drawText(this.textRenderer, "Start an auction", px + 10, py + 10, Ui.TEXT, true);

        // grid
        ctx.fill(gridX - 1, gridY - 1, gridX + gridW + 7, gridY + gridH + 1, Ui.INSET);
        int hover = cellAt(mx, my);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (scrollRow + r) * cols + c;
                if (idx >= shown.size()) break;
                ItemCatalog.Entry e = shown.get(idx);
                int cx = gridX + c * CELL;
                int cy = gridY + r * CELL;
                boolean sel = e.id().toString().equals(selectedId);
                if (sel) {
                    ctx.fill(cx, cy, cx + CELL, cy + CELL, 0x604FC3F7);
                    Ui.outline(ctx, cx, cy, CELL, CELL, Ui.ACCENT);
                } else if (idx == hover) {
                    ctx.fill(cx, cy, cx + CELL, cy + CELL, 0x40FFFFFF);
                }
                ctx.drawItem(e.icon(), cx + 2, cy + 2);
            }
        }
        if (shown.isEmpty()) {
            ctx.drawText(this.textRenderer, "No items match.", gridX + 6, gridY + 6, Ui.MUTED, false);
        }
        // scrollbar
        int totalRows = (shown.size() + cols - 1) / cols;
        if (totalRows > rows) {
            int tx = gridX + gridW + 2;
            int th = Math.max(10, gridH * rows / totalRows);
            int ty = gridY + (gridH - th) * scrollRow / (totalRows - rows);
            ctx.fill(tx, gridY, tx + 3, gridY + gridH, 0xFF1F2730);
            ctx.fill(tx, ty, tx + 3, ty + th, Ui.MUTED);
        }

        // selected item
        var sel = ItemCatalog.byId(selectedId);
        ctx.fill(px + 10, selY - 2, px + pw - 10, selY + 20, Ui.INSET);
        if (sel.isPresent()) {
            ctx.drawItem(sel.get().icon(), px + 14, selY);
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(sel.get().name(), pw - 70), px + 36, selY + 1, Ui.TEXT, true);
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(sel.get().id().toString(), pw - 70), px + 36, selY + 11, Ui.DIM, false);
        } else {
            ctx.drawText(this.textRenderer, "Click an item above to select it", px + 14, selY + 6, Ui.MUTED, false);
        }

        // chips row (above the fields)
        for (Ui.Button b : chips) {
            b.render(ctx, this.textRenderer, mx, my);
            if (b.label.equals(durationText.trim())) Ui.outline(ctx, b.x, b.y, b.w, b.h, Ui.ACCENT);
        }

        ctx.drawText(this.textRenderer, "How long (30 = minutes, 45m, 1h30m, 90s)", px + 10, fieldY, Ui.MUTED, false);

        for (Ui.Button b : buttons) b.render(ctx, this.textRenderer, mx, my);

        String msg = status.isEmpty() ? svc().statusLine() : status;
        int col = status.isEmpty() ? Ui.MUTED : statusColour;
        if (!msg.isEmpty()) {
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(msg, pw - 20), px + 10, py + ph - 12, col, false);
        }
    }

    @Override
    protected void drawOverlay(DrawContext ctx, int mx, int my) {
        int idx = cellAt(mx, my);
        if (idx >= 0) ctx.drawTooltip(this.textRenderer, Text.literal(shown.get(idx).name()), mx, my);
    }
}
