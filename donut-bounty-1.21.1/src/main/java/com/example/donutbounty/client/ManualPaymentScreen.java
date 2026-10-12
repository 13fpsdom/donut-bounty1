package com.example.donutbounty.client;

import com.example.donutbounty.BountyService;
import com.example.donutbounty.core.PaymentTracker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Manual confirmation: add a payment you saw yourself but the mod did not detect. Also lists the recent
 * payment-related messages the mod saw, so you can compare them with the configured patterns.
 */
public class ManualPaymentScreen extends UiScreen {
    private final Screen parent;
    private final List<Ui.Button> buttons = new ArrayList<>();
    private TextFieldWidget nameField;
    private TextFieldWidget amountField;
    private String nameText = "";
    private String amountText = "";
    private String status = "";
    private int px, py, pw, ph;

    public ManualPaymentScreen(Screen parent) {
        super(Text.literal("Add a payment manually"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(this.width - 16, 380);
        ph = Math.min(this.height - 8, 250);
        px = (this.width - pw) / 2;
        py = Math.max(4, (this.height - ph) / 2);
        int fw = pw - 20;

        nameField = new TextFieldWidget(this.textRenderer, px + 10, py + 52, fw, 16, Text.literal("Username"));
        nameField.setMaxLength(17);
        nameField.setPlaceholder(Text.literal("Minecraft username"));
        nameField.setText(nameText);
        nameField.setChangedListener(s -> nameText = s);
        addDrawableChild(nameField);
        setInitialFocus(nameField);

        amountField = new TextFieldWidget(this.textRenderer, px + 10, py + 86, fw, 16, Text.literal("Amount"));
        amountField.setMaxLength(20);
        amountField.setPlaceholder(Text.literal("e.g. 5000, $5,000, 2.5m"));
        amountField.setText(amountText);
        amountField.setChangedListener(s -> amountText = s);
        addDrawableChild(amountField);

        buttons.clear();
        int half = (fw - 4) / 2;
        Ui.Button add = new Ui.Button("Add payment", true, this::submit);
        add.x = px + 10;
        add.y = py + 110;
        add.w = half;
        add.h = 20;
        Ui.Button cancel = new Ui.Button("Cancel", false, this::close);
        cancel.x = px + 10 + half + 4;
        cancel.y = py + 110;
        cancel.w = fw - half - 4;
        cancel.h = 20;
        buttons.add(add);
        buttons.add(cancel);
    }

    private void submit() {
        PaymentTracker.Result r = DonutBountyClient.service().addManual(nameText, amountText);
        if (r.outcome() == PaymentTracker.Outcome.COUNTED) {
            close();
        } else {
            status = "Enter a valid Minecraft username and an amount such as 5000, $5,000 or 2.5m.";
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
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
        ctx.drawText(this.textRenderer, "Add a payment manually", px + 10, py + 10, Ui.TEXT, true);
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("Only add payments you saw yourself that were not detected.", pw - 20), px + 10, py + 24, Ui.MUTED, false);
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("Manual entries are trusted, so double-check the name and amount.", pw - 20), px + 10, py + 34, Ui.MUTED, false);
        ctx.drawText(this.textRenderer, "Username", px + 10, py + 44, Ui.DIM, false);
        ctx.drawText(this.textRenderer, "Amount", px + 10, py + 78, Ui.DIM, false);

        for (Ui.Button b : buttons) b.render(ctx, this.textRenderer, mx, my);

        if (!status.isEmpty()) {
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(status, pw - 20), px + 10, py + 134, Ui.BAD, false);
        }

        List<BountyService.Candidate> seen = DonutBountyClient.service().recentCandidates();
        int y = py + 150;
        ctx.drawText(this.textRenderer, "Recent payment-related messages (what the server sends):", px + 10, y, Ui.MUTED, false);
        y += 12;
        if (seen.isEmpty()) {
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("None yet. If a payment arrives and nothing shows here,", pw - 20), px + 10, y, Ui.DIM, false);
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("the server may not send it as a system message (see README).", pw - 20), px + 10, y + 11, Ui.DIM, false);
        }
        int maxLines = Math.max(0, (py + ph - y - 6) / 11);
        for (int i = 0; i < Math.min(maxLines, seen.size()); i++) {
            BountyService.Candidate c = seen.get(i);
            String line = "[" + c.verdict() + "] " + c.text();
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(line, pw - 20), px + 10, y + i * 11, 0xFFE5E7EB, false);
        }
    }
}
