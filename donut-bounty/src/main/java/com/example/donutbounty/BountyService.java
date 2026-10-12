package com.example.donutbounty;

import com.example.donutbounty.core.BountyTimer;
import com.example.donutbounty.core.CsvExporter;
import com.example.donutbounty.core.DuplicateFilter;
import com.example.donutbounty.core.DurationParser;
import com.example.donutbounty.core.Leaderboard;
import com.example.donutbounty.core.LeaderboardStore;
import com.example.donutbounty.core.Money;
import com.example.donutbounty.core.PaymentParser;
import com.example.donutbounty.core.PaymentTracker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Everything the mod does that does not need Minecraft classes: config, timer, leaderboard, persistence,
 * payment decisions. The Fabric layer only feeds it text and draws what it reports.
 *
 * This class never sends chat, runs commands or makes payments; it only reads text it is given.
 */
public final class BountyService {

    public record Snapshot(BountyTimer.State state, long remainingMillis, double fractionRemaining,
                           String itemId, long targetCents, long raisedCents,
                           String topName, long topCents, int paymentCount) {}

    /** "Name paid $amount" pop-up, kept briefly so the HUD can show it. */
    public record Notice(String player, long amountCents, long atMillis) {}

    /** A recently seen message that mentions payments, kept so the player can see what the server really sends. */
    public record Candidate(String text, String verdict) {}

    private static final int MAX_CANDIDATES = 12;
    private static final int MAX_NOTICES = 5;

    private final Path dir;
    private final Path configFile;
    private final Path boardFile;
    private final Path exportDir;
    private final Supplier<String> serverAddress;
    private final LongSupplier mono = () -> System.nanoTime() / 1_000_000L;
    private final BountyTimer timer = new BountyTimer(mono);
    private final Leaderboard board = new Leaderboard();
    private final Deque<Candidate> candidates = new ArrayDeque<>();
    private final Deque<Notice> notices = new ArrayDeque<>();
    private final List<String> warnings = new ArrayList<>();
    private final PaymentTracker tracker;

    private volatile ModConfig config;
    private volatile PaymentParser parser;
    private String itemId = "";
    private long targetCents;
    private String statusLine = "";

    public BountyService(Path dir, Supplier<String> selfName, Supplier<String> serverAddress) {
        this.dir = dir;
        this.configFile = dir.resolve("config.json");
        this.boardFile = dir.resolve("leaderboard.tsv");
        this.exportDir = dir.resolve("exports");
        this.serverAddress = serverAddress;

        ModConfig.LoadResult lr = ModConfig.load(configFile);
        this.config = lr.config();
        if (lr.warning() != null) warnings.add(lr.warning());
        rebuildParser();

        try {
            board.replaceAll(LeaderboardStore.load(boardFile));
        } catch (IOException e) {
            String warn = "Leaderboard file could not be read (" + e.getMessage() + "). Starting empty.";
            try {
                Path backup = boardFile.resolveSibling(boardFile.getFileName() + ".corrupt-" + System.currentTimeMillis());
                Files.move(boardFile, backup);
                warn += " The old file was kept as " + backup.getFileName() + ".";
            } catch (IOException ignored) {
                // nothing more we can do
            }
            warnings.add(warn);
        }

        long cap = Math.multiplyExact(config.payments.maxSingleAmountDollars, Money.CENTS);
        DuplicateFilter dup = new DuplicateFilter(mono, config.payments.dedupeWindowMillis);
        this.tracker = new PaymentTracker(() -> parser, dup, board, timer, System::currentTimeMillis, selfName,
                () -> config.payments.countOnlyWhileRunning, cap);
    }

    private void rebuildParser() {
        long cap = Math.multiplyExact(config.payments.maxSingleAmountDollars, Money.CENTS);
        PaymentParser p = new PaymentParser(config.payments.patterns, cap);
        for (String e : p.errors()) warnings.add(e);
        this.parser = p;
    }

    // ---- incoming messages ----

    /**
     * Feed one incoming message. fromChatChannel is true for the player-chat event, false for system/game messages.
     * Returns what happened; only COUNTED changes the leaderboard.
     */
    public synchronized PaymentTracker.Result onIncoming(String text, boolean fromChatChannel) {
        if (text == null) return new PaymentTracker.Result(PaymentTracker.Outcome.NOT_A_PAYMENT, null);
        if (fromChatChannel && !config.payments.acceptChatChannel) {
            return new PaymentTracker.Result(PaymentTracker.Outcome.NOT_A_PAYMENT, null);
        }
        if (!serverMatches()) {
            noteCandidate(text, "ignored: not connected to a matching server");
            return new PaymentTracker.Result(PaymentTracker.Outcome.NOT_A_PAYMENT, null);
        }
        PaymentTracker.Result r = tracker.processIncoming(text);
        switch (r.outcome()) {
            case COUNTED -> {
                saveBoard();
                statusLine = "Counted " + Money.format(r.payment().amountCents()) + " from " + r.payment().player();
                addNotice(r.payment().player(), r.payment().amountCents());
                noteCandidate(text, "counted");
            }
            case NOT_A_PAYMENT -> noteCandidate(text, "no pattern matched");
            case SELF_PAYMENT -> noteCandidate(text, "ignored: from yourself");
            case NOT_RUNNING -> noteCandidate(text, "ignored: bounty not running");
            case DUPLICATE -> noteCandidate(text, "ignored: duplicate");
            case REJECTED -> noteCandidate(text, "rejected");
        }
        return r;
    }

    private boolean serverMatches() {
        if (!config.payments.requireServerMatch) return true;
        String addr = serverAddress.get();
        if (addr == null) return false;
        String a = addr.toLowerCase(Locale.ROOT);
        for (String h : config.payments.serverHosts) {
            if (h != null && !h.isBlank() && a.contains(h.toLowerCase(Locale.ROOT).trim())) return true;
        }
        return false;
    }

    private void noteCandidate(String text, String verdict) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (!(lower.contains("paid") || lower.contains("pay") || lower.contains("$"))) return;
        String shown = text.length() > 160 ? text.substring(0, 160) + "..." : text;
        candidates.addFirst(new Candidate(shown, verdict));
        while (candidates.size() > MAX_CANDIDATES) candidates.removeLast();
    }

    public synchronized List<Candidate> recentCandidates() {
        return new ArrayList<>(candidates);
    }

    private void addNotice(String player, long cents) {
        notices.addFirst(new Notice(player, cents, System.currentTimeMillis()));
        while (notices.size() > MAX_NOTICES) notices.removeLast();
    }

    /** Pop-ups younger than lifetimeMillis, newest first. */
    public synchronized List<Notice> recentNotices(long nowMillis, long lifetimeMillis) {
        List<Notice> out = new ArrayList<>();
        for (Notice n : notices) {
            if (nowMillis - n.atMillis() < lifetimeMillis) out.add(n);
        }
        return out;
    }

    // ---- bounty controls ----

    /** Returns an error message, or null on success. */
    public synchronized String start(String item, String durationText, String targetText) {
        if (item == null || item.isBlank()) return "Pick an item first.";
        OptionalLong d = DurationParser.parseMillis(durationText);
        if (d.isEmpty()) return "Invalid duration. Examples: 30 (minutes), 45m, 1h30m, 90s, 1:30:00";
        long target = 0;
        if (targetText != null && !targetText.isBlank()) {
            OptionalLong t = Money.parseCents(targetText);
            if (t.isEmpty()) return "Invalid target amount. Examples: 5000, $5,000, 2.5m (or leave empty)";
            target = t.getAsLong();
        }
        if (!timer.start(d.getAsLong())) return "A bounty is already running. Stop it first.";
        this.itemId = item;
        this.targetCents = target;
        tracker.resetSession();
        notices.clear();
        rememberInputs(item, durationText, targetText);
        return null;
    }

    public synchronized boolean pause() { return timer.pause(); }
    public synchronized boolean resume() { return timer.resume(); }
    public synchronized boolean stop() { return timer.stop(); }
    public BountyTimer.State timerState() { return timer.state(); }

    public synchronized Snapshot snapshot() {
        var top = tracker.sessionTop();
        return new Snapshot(timer.state(), timer.remainingMillis(), timer.fractionRemaining(),
                itemId, targetCents, tracker.sessionRaisedCents(),
                top.map(t -> t.name()).orElse(""), top.map(t -> t.totalCents()).orElse(0L),
                tracker.sessionPaymentCount());
    }

    public synchronized String itemId() { return itemId; }

    // ---- leaderboard ----

    public Leaderboard leaderboard() { return board; }

    public synchronized PaymentTracker.Result addManual(String player, String amountText) {
        OptionalLong cents = Money.parseCents(amountText);
        if (cents.isEmpty()) return new PaymentTracker.Result(PaymentTracker.Outcome.REJECTED, null);
        PaymentTracker.Result r = tracker.addManual(player == null ? "" : player.trim(), cents.getAsLong());
        if (r.outcome() == PaymentTracker.Outcome.COUNTED) {
            saveBoard();
            addNotice(r.payment().player(), cents.getAsLong());
            statusLine = "Manually added " + Money.format(cents.getAsLong()) + " from " + r.payment().player();
        }
        return r;
    }

    public synchronized void clearLeaderboard() {
        board.clear();
        saveBoard();
        statusLine = "Leaderboard cleared";
    }

    /** Writes a timestamped CSV and returns its path. */
    public synchronized Path exportCsv() throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = exportDir.resolve("leaderboard-" + stamp + ".csv");
        CsvExporter.write(file, board.ranked());
        return file;
    }

    private void saveBoard() {
        try {
            LeaderboardStore.save(boardFile, board);
        } catch (IOException e) {
            warnings.add("Could not save leaderboard: " + e.getMessage());
        }
    }

    // ---- config ----

    public ModConfig config() { return config; }
    public Path configFile() { return configFile; }
    public Path directory() { return dir; }

    public synchronized void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            warnings.add("Could not save config: " + e.getMessage());
        }
    }

    /** Re-reads config.json (after the player edited the payment patterns, for example). */
    public synchronized void reloadConfig() {
        warnings.clear();
        ModConfig.LoadResult lr = ModConfig.load(configFile);
        this.config = lr.config();
        if (lr.warning() != null) warnings.add(lr.warning());
        rebuildParser();
        statusLine = "Config reloaded (" + parser.activePatternCount() + " payment pattern(s) active)";
    }

    public synchronized void resetHud() {
        config.hud = new ModConfig.Hud();
        saveConfig();
    }

    public synchronized void rememberInputs(String item, String duration, String target) {
        config.lastItemId = item == null ? "" : item;
        config.lastDuration = duration == null ? "" : duration;
        config.lastTarget = target == null ? "" : target;
        saveConfig();
    }

    public synchronized List<String> warnings() { return new ArrayList<>(warnings); }
    public synchronized String statusLine() { return statusLine; }
    public int activePatternCount() { return parser.activePatternCount(); }
}
