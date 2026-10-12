package com.example.donutbounty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.donutbounty.core.BountyTimer;
import com.example.donutbounty.core.PaymentTracker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BountyServiceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private String server = "play.donutsmp.net";

    private BountyService service(Path dir) {
        return new BountyService(dir, () -> "Me", () -> server);
    }

    @Test public void endToEndCountsPersistsAndRestores() throws IOException {
        Path dir = tmp.getRoot().toPath().resolve("cfg");
        BountyService s = service(dir);
        assertNull(s.start("minecraft:diamond_block", "30m", "5k"));
        assertEquals(PaymentTracker.Outcome.COUNTED, s.onIncoming("Steve paid you $1,000", false).outcome());
        assertEquals(PaymentTracker.Outcome.COUNTED, s.onIncoming("Alex paid you $2.5k", false).outcome());
        assertEquals(350_000, s.snapshot().raisedCents());
        assertEquals(500_000, s.snapshot().targetCents());

        // "restart Minecraft": a new service reading the same folder sees the same leaderboard
        BountyService again = service(dir);
        assertEquals(2, again.leaderboard().playerCount());
        assertEquals(250_000, again.leaderboard().get("alex").orElseThrow().totalCents());
        assertEquals("minecraft:diamond_block", again.config().lastItemId);
        assertEquals(BountyTimer.State.IDLE, again.timerState());
    }

    @Test public void ignoresPaymentsWhenNotOnTheRightServer() {
        BountyService s = service(tmp.getRoot().toPath());
        s.start("minecraft:stone", "10", "");
        server = "mc.hypixel.net";
        assertEquals(PaymentTracker.Outcome.NOT_A_PAYMENT, s.onIncoming("Steve paid you $5", false).outcome());
        server = null; // singleplayer
        assertEquals(PaymentTracker.Outcome.NOT_A_PAYMENT, s.onIncoming("Steve paid you $5", false).outcome());
        assertEquals(0, s.leaderboard().playerCount());
        assertFalse(s.recentCandidates().isEmpty());
        assertTrue(s.recentCandidates().get(0).verdict().contains("server"));
    }

    @Test public void chatChannelIsIgnoredByDefault() {
        BountyService s = service(tmp.getRoot().toPath());
        s.start("minecraft:stone", "10", "");
        assertEquals(PaymentTracker.Outcome.NOT_A_PAYMENT, s.onIncoming("Steve paid you $5", true).outcome());
        assertEquals(0, s.leaderboard().playerCount());
    }

    @Test public void startValidatesInputs() {
        BountyService s = service(tmp.getRoot().toPath());
        assertNotNull(s.start("", "10", ""));   // "Pick an item first."
        assertNotNull(s.start("minecraft:stone", "abc", ""));
        assertNotNull(s.start("minecraft:stone", "10", "lots"));
        assertEquals(BountyTimer.State.IDLE, s.timerState());
        assertNull(s.start("minecraft:stone", "10", ""));
        assertNotNull("second start while running", s.start("minecraft:stone", "10", ""));
        assertTrue(s.pause());
        assertEquals(BountyTimer.State.PAUSED, s.timerState());
        assertTrue(s.resume());
        assertTrue(s.stop());
        assertEquals(BountyTimer.State.IDLE, s.timerState());
    }

    @Test public void topPayerCountAndPopUpNotices() {
        BountyService s = service(tmp.getRoot().toPath());
        assertNull(s.start("minecraft:elytra", "30m", ""));
        s.onIncoming("Steve paid you $1,000", false);
        s.onIncoming("Alex paid you $2.5k", false);
        s.onIncoming("Steve paid you $2,000", false);   // Steve now leads with 3,000 total
        BountyService.Snapshot snap = s.snapshot();
        assertEquals("minecraft:elytra", snap.itemId());
        assertEquals("Steve", snap.topName());
        assertEquals(300_000, snap.topCents());
        assertEquals(3, snap.paymentCount());
        assertEquals(550_000, snap.raisedCents());

        long now = System.currentTimeMillis();
        var live = s.recentNotices(now, 6_000);
        assertEquals(3, live.size());
        assertEquals("Steve", live.get(0).player());            // newest first
        assertEquals(200_000, live.get(0).amountCents());
        assertTrue(s.recentNotices(now + 10_000, 6_000).isEmpty());   // expired

        // a new bounty starts from zero
        s.stop();
        assertNull(s.start("minecraft:enchanted_golden_apple", "30m", ""));
        assertEquals(0, s.snapshot().paymentCount());
        assertEquals("", s.snapshot().topName());
        assertTrue(s.recentNotices(now, 6_000).isEmpty());
    }

    @Test public void manualConfirmationAndClear() {
        BountyService s = service(tmp.getRoot().toPath());
        assertEquals(PaymentTracker.Outcome.COUNTED, s.addManual("Steve", "$2.5k").outcome());
        assertEquals(PaymentTracker.Outcome.REJECTED, s.addManual("Steve", "nope").outcome());
        assertEquals(PaymentTracker.Outcome.REJECTED, s.addManual("bad name", "5").outcome());
        assertEquals(250_000, s.leaderboard().totalCents());
        s.clearLeaderboard();
        assertEquals(0, s.leaderboard().playerCount());
        assertEquals(0, service(tmp.getRoot().toPath()).leaderboard().playerCount()); // cleared on disk too
    }

    @Test public void exportCreatesCsvFile() throws IOException {
        BountyService s = service(tmp.getRoot().toPath());
        s.addManual("Steve", "100");
        Path f = s.exportCsv();
        String csv = Files.readString(f, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("rank,username,total_paid"));
        assertTrue(csv.contains("1,Steve,100.00,1,"));
    }

    @Test public void corruptLeaderboardIsBackedUpNotSilentlyLost() throws IOException {
        Path dir = tmp.getRoot().toPath();
        Files.writeString(dir.resolve("leaderboard.tsv"), "garbage", StandardCharsets.UTF_8);
        BountyService s = service(dir);
        assertEquals(0, s.leaderboard().playerCount());
        assertFalse(s.warnings().isEmpty());
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("leaderboard.tsv.corrupt-")));
        }
    }

    @Test public void brokenConfigFallsBackToDefaultsAndKeepsBackup() throws IOException {
        Path dir = tmp.getRoot().toPath();
        Files.writeString(dir.resolve("config.json"), "{ not json", StandardCharsets.UTF_8);
        BountyService s = service(dir);
        assertTrue(s.config().hud.enabled);
        assertFalse(s.warnings().isEmpty());
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("config.json.broken-")));
        }
    }

    @Test public void configRoundTripAndSanitising() throws IOException {
        Path f = tmp.getRoot().toPath().resolve("config.json");
        ModConfig c = new ModConfig();
        c.hud.scale = 99;
        c.hud.accent = "#FF112233";
        c.payments.dedupeWindowMillis = -5;
        c.hud.noticeSeconds = 999;
        c.save(f);
        ModConfig back = ModConfig.load(f).config();
        assertEquals(3.0, back.hud.scale, 0.0001);                 // clamped
        assertEquals("#FF112233", back.hud.accent);
        assertEquals(0, back.payments.dedupeWindowMillis);          // clamped
        assertEquals(30, back.hud.noticeSeconds);                   // clamped
        assertTrue(back.hud.showPaymentNotices);
        assertEquals("#FF4ADE80", back.hud.success);
        assertEquals(2, back.payments.patterns.size());
        assertEquals("paid_you", back.payments.patterns.get(0).name());
        assertTrue(back.payments.patterns.get(0).enabled());
        assertFalse(back.payments.patterns.get(1).enabled());
    }

    @Test public void oldBuiltInPaidYouPatternIsUpgradedOnLoad() throws IOException {
        Path f = tmp.getRoot().toPath().resolve("config.json");
        ModConfig c = new ModConfig();
        c.payments.patterns = new java.util.ArrayList<>(java.util.List.of(
                new com.example.donutbounty.core.PaymentPattern("paid_you", com.example.donutbounty.core.DefaultPatterns.OLD_PAID_YOU, true)));
        c.save(f);
        ModConfig back = ModConfig.load(f).config();
        assertEquals(com.example.donutbounty.core.DefaultPatterns.defaults().get(0).regex(), back.payments.patterns.get(0).regex());
        assertTrue(back.payments.patterns.get(0).enabled());
    }

    @Test public void reloadPicksUpEditedPatterns() throws IOException {
        Path dir = tmp.getRoot().toPath();
        BountyService s = service(dir);
        s.start("minecraft:stone", "10", "");
        assertEquals(PaymentTracker.Outcome.NOT_A_PAYMENT, s.onIncoming("[Pay] Eve sent you $7", false).outcome());

        String json = Files.readString(dir.resolve("config.json"), StandardCharsets.UTF_8);
        String custom = "{\"name\":\"custom\",\"regex\":\"^\\\\[Pay\\\\] (?<player>[A-Za-z0-9_]{1,16}) sent you \\\\$(?<amount>[0-9][0-9,]*(?:\\\\.[0-9]+)?[KkMmBbTt]?)$\",\"enabled\":true},";
        json = json.replaceFirst("\"patterns\": \\[", "\"patterns\": [" + java.util.regex.Matcher.quoteReplacement(custom));
        Files.writeString(dir.resolve("config.json"), json, StandardCharsets.UTF_8);
        s.reloadConfig();

        assertEquals(PaymentTracker.Outcome.COUNTED, s.onIncoming("[Pay] Eve sent you $7", false).outcome());
        assertEquals(700, s.leaderboard().get("eve").orElseThrow().totalCents());
    }
}
