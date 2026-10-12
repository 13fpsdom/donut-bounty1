package com.example.donutbounty;

import com.example.donutbounty.core.DefaultPatterns;
import com.example.donutbounty.core.PaymentPattern;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** config/donutbounty/config.json. Missing fields fall back to the defaults below. */
public final class ModConfig {

    public static final class Hud {
        public boolean enabled = true;
        /** Horizontal shift from the exact top centre, in GUI pixels (negative = left). */
        public int offsetX = 0;
        /** Distance from the top edge, in GUI pixels. */
        public int offsetY = 6;
        public double scale = 1.0;
        /** Colours are #RRGGBB or #AARRGGBB. */
        public String background = "#D0101418";
        public String border = "#FF2A323C";
        public String text = "#FFFFFFFF";
        public String accent = "#FF4FC3F7";
        public String warning = "#FFF87171";
        public String track = "#FF2A323C";
        /** Colour for money amounts and the "top payer" line. */
        public String success = "#FF4ADE80";
        /** Show a "Name paid $amount" pop-up under the HUD when a payment is counted. */
        public boolean showPaymentNotices = true;
        /** How long each pop-up stays on screen. */
        public int noticeSeconds = 6;
    }

    public static final class Payments {
        public List<PaymentPattern> patterns = new ArrayList<>(DefaultPatterns.defaults());
        /** An identical message arriving again within this many ms is treated as a duplicate. 0 disables. Needs a restart. */
        public long dedupeWindowMillis = 1000;
        /** Only count payments while a bounty is RUNNING (not idle, paused or ended). */
        public boolean countOnlyWhileRunning = true;
        /** Payments above this many dollars are rejected as implausible. Needs a restart. */
        public long maxSingleAmountDollars = 1_000_000_000_000L;
        /** Only listen when the connected server address contains one of serverHosts. */
        public boolean requireServerMatch = true;
        public List<String> serverHosts = new ArrayList<>(List.of("donutsmp"));
        /**
         * Also listen on the player-chat channel (only for unsigned messages with no sender profile).
         * Off by default: system messages are the safer source. Turn on only if your server delivers payment
         * notices through the chat channel and nothing is detected otherwise.
         */
        public boolean acceptChatChannel = false;
    }

    public Hud hud = new Hud();
    public Payments payments = new Payments();
    public String lastItemId = "minecraft:diamond_block";
    public String lastDuration = "30m";
    public String lastTarget = "";

    public record LoadResult(ModConfig config, String warning) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static LoadResult load(Path file) {
        if (!Files.exists(file)) {
            ModConfig fresh = new ModConfig();
            try {
                fresh.save(file);
            } catch (IOException e) {
                return new LoadResult(fresh, "Could not write default config: " + e.getMessage());
            }
            return new LoadResult(fresh, null);
        }
        try {
            ModConfig c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ModConfig.class);
            if (c == null) throw new JsonParseException("file is empty");
            c.sanitize();
            return new LoadResult(c, null);
        } catch (IOException | JsonParseException e) {
            String warning = "config.json could not be read (" + e.getMessage() + "); defaults are in use.";
            try {
                Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                warning += " The broken file was kept as " + backup.getFileName() + ".";
            } catch (IOException ignored) {
                // keep going with defaults
            }
            return new LoadResult(new ModConfig(), warning);
        }
    }

    public void save(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
    }

    /** Clamps values into sane ranges and replaces missing pieces with defaults. */
    public void sanitize() {
        if (hud == null) hud = new Hud();
        if (payments == null) payments = new Payments();
        Hud d = new Hud();
        hud.scale = Double.isFinite(hud.scale) ? Math.max(0.5, Math.min(3.0, hud.scale)) : 1.0;
        hud.offsetX = Math.max(-2000, Math.min(2000, hud.offsetX));
        hud.offsetY = Math.max(-100, Math.min(1000, hud.offsetY));
        if (hud.background == null) hud.background = d.background;
        if (hud.border == null) hud.border = d.border;
        if (hud.text == null) hud.text = d.text;
        if (hud.accent == null) hud.accent = d.accent;
        if (hud.warning == null) hud.warning = d.warning;
        if (hud.track == null) hud.track = d.track;
        if (hud.success == null) hud.success = d.success;
        hud.noticeSeconds = Math.max(1, Math.min(30, hud.noticeSeconds));
        if (payments.patterns == null) payments.patterns = new ArrayList<>(DefaultPatterns.defaults());
        payments.patterns.removeIf(p -> p == null || p.name() == null || p.regex() == null);
        // upgrade the old built-in paid_you pattern (it did not allow the space in "$ 2")
        payments.patterns.replaceAll(p -> p.name().equals("paid_you") && p.regex().equals(DefaultPatterns.OLD_PAID_YOU)
                ? new PaymentPattern("paid_you", DefaultPatterns.defaults().get(0).regex(), p.enabled())
                : p);
        if (payments.serverHosts == null) payments.serverHosts = new ArrayList<>(List.of("donutsmp"));
        payments.dedupeWindowMillis = Math.max(0, Math.min(60_000, payments.dedupeWindowMillis));
        payments.maxSingleAmountDollars = Math.max(1, Math.min(1_000_000_000_000L, payments.maxSingleAmountDollars));
        if (lastItemId == null) lastItemId = "minecraft:diamond_block";
        if (lastDuration == null) lastDuration = "30m";
        if (lastTarget == null) lastTarget = "";
    }
}
