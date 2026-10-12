package com.example.donutbounty.client;

import com.example.donutbounty.BountyService;
import com.example.donutbounty.ModConfig;
import com.example.donutbounty.core.Colors;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** HUD position, scale and colours (with a live preview at the top), plus a few payment-listening switches. */
public class SettingsScreen extends UiScreen {
    private static final int ROW = 21;

    private record Label(int x, int y, String text) {}

    private final Screen parent;
    private final List<Label> labels = new ArrayList<>();
    private int px, py, pw, ph;

    public SettingsScreen(Screen parent) {
        super(Text.literal("HUD & Payment Settings"));
        this.parent = parent;
    }

    private static BountyService svc() {
        return DonutBountyClient.service();
    }

    @Override
    protected void init() {
        labels.clear();
        pw = Math.min(this.width - 16, 340);
        ph = 8 * ROW + 24 + 32 + 14;
        px = (this.width - pw) / 2;
        py = Math.max(2, this.height - ph - 4);
        int colW = (pw - 20 - 6) / 2;
        int leftX = px + 10;
        int rightX = px + 10 + colW + 6;
        int y0 = py + 22;

        // row 0
        toggle(leftX, y0, colW, "HUD", () -> svc().config().hud.enabled, v -> svc().config().hud.enabled = v);
        toggle(rightX, y0, colW, "Pay pop-ups", () -> svc().config().hud.showPaymentNotices, v -> svc().config().hud.showPaymentNotices = v);

        // row 1
        addDrawableChild(new ValueSlider(leftX, y0 + ROW, colW, 20, "Scale", 0.5, 3.0, svc().config().hud.scale, "%.2f",
                v -> svc().config().hud.scale = v));
        addDrawableChild(new ValueSlider(rightX, y0 + ROW, colW, 20, "X offset", -400, 400, svc().config().hud.offsetX, "%.0f",
                v -> svc().config().hud.offsetX = (int) Math.round(v)));

        // row 2
        addDrawableChild(new ValueSlider(leftX, y0 + ROW * 2, colW, 20, "Y offset", 0, 300, svc().config().hud.offsetY, "%.0f",
                v -> svc().config().hud.offsetY = (int) Math.round(v)));
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset HUD"), b -> {
            svc().resetHud();
            this.client.setScreen(new SettingsScreen(parent));
        }).dimensions(rightX, y0 + ROW * 2, colW, 20).build());

        // row 3
        toggle(leftX, y0 + ROW * 3, colW, "Only while running",
                () -> svc().config().payments.countOnlyWhileRunning, v -> svc().config().payments.countOnlyWhileRunning = v);
        toggle(rightX, y0 + ROW * 3, colW, "Require DonutSMP",
                () -> svc().config().payments.requireServerMatch, v -> svc().config().payments.requireServerMatch = v);

        // rows 4-7: colours (+ reload)
        colourField(leftX, y0 + ROW * 4, colW, "Background", () -> svc().config().hud.background, v -> svc().config().hud.background = v);
        colourField(rightX, y0 + ROW * 4, colW, "Border", () -> svc().config().hud.border, v -> svc().config().hud.border = v);
        colourField(leftX, y0 + ROW * 5, colW, "Text", () -> svc().config().hud.text, v -> svc().config().hud.text = v);
        colourField(rightX, y0 + ROW * 5, colW, "Accent", () -> svc().config().hud.accent, v -> svc().config().hud.accent = v);
        colourField(leftX, y0 + ROW * 6, colW, "Warning", () -> svc().config().hud.warning, v -> svc().config().hud.warning = v);
        colourField(rightX, y0 + ROW * 6, colW, "Bar track", () -> svc().config().hud.track, v -> svc().config().hud.track = v);
        colourField(leftX, y0 + ROW * 7, colW, "Payment", () -> svc().config().hud.success, v -> svc().config().hud.success = v);
        addDrawableChild(ButtonWidget.builder(Text.literal("Reload config file"), b -> {
            svc().reloadConfig();
            this.client.setScreen(new SettingsScreen(parent));
        }).dimensions(rightX, y0 + ROW * 7, colW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(this.width / 2 - 60, y0 + ROW * 8 + 2, 120, 20).build());
    }

    private void toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
        addDrawableChild(ButtonWidget.builder(toggleText(label, get.getAsBoolean()), b -> {
            boolean now = !get.getAsBoolean();
            set.accept(now);
            b.setMessage(toggleText(label, now));
        }).dimensions(x, y, w, 20).build());
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    private void colourField(int x, int y, int w, String label, Supplier<String> get, Consumer<String> set) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x + 64, y, w - 64, 18, Text.literal(label));
        field.setMaxLength(9);
        field.setText(get.get());
        field.setChangedListener(s -> {
            if (Colors.parse(s).isPresent()) {
                set.accept(s);
                field.setEditableColor(0xFFFFFFFF);
            } else {
                field.setEditableColor(0xFFFF5555); // red while the value is not a valid #RRGGBB / #AARRGGBB
            }
        });
        addDrawableChild(field);
        labels.add(new Label(x, y + 5, label));
    }

    @Override
    public void close() {
        svc().saveConfig();
        this.client.setScreen(parent);
    }

    @Override
    protected void drawLayer(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, 0x60000000);
        long now = System.currentTimeMillis();
        BountyHud.draw(ctx, this.textRenderer, BountyHud.sample(), svc().config().hud, this.width, BountyHud.sampleNotices(now), now);
        Ui.panel(ctx, px, py, pw, ph);
        ctx.drawText(this.textRenderer, "HUD & payment settings (live preview at the top)", px + 10, py + 8, Ui.TEXT, true);
        for (Label l : labels) {
            ctx.drawText(this.textRenderer, l.text(), l.x(), l.y(), Ui.MUTED, false);
        }
        ctx.drawText(this.textRenderer,
                this.textRenderer.trimToWidth("Payment patterns: edit config/donutbounty/config.json, then Reload.", pw - 20),
                px + 10, py + ph - 12, Ui.DIM, false);
    }

    /** Slider that maps 0..1 onto [min, max] and reports the real value. */
    private static final class ValueSlider extends SliderWidget {
        private final String label;
        private final double min;
        private final double max;
        private final String format;
        private final DoubleConsumer onChange;

        ValueSlider(int x, int y, int w, int h, String label, double min, double max, double current,
                    String format, DoubleConsumer onChange) {
            super(x, y, w, h, Text.empty(), (Math.max(min, Math.min(max, current)) - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.format = format;
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (label == null) return; // called by the superclass before our fields are set
            setMessage(Text.literal(label + ": " + String.format(format, min + (max - min) * this.value)));
        }

        @Override
        protected void applyValue() {
            onChange.accept(min + (max - min) * this.value);
        }
    }
}
