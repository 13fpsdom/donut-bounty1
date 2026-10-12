package com.example.donutbounty.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads the leaderboard as a small tab-separated text file:
 *
 *   # donutbounty-leaderboard v1
 *   name TAB totalCents TAB count TAB lastPaymentEpochMillis TAB lastAmountCents
 *
 * Writes go to a temporary file first and are then moved into place, so a crash mid-write cannot
 * leave a half-written leaderboard behind.
 */
public final class LeaderboardStore {
    public static final String HEADER = "# donutbounty-leaderboard v1";

    private LeaderboardStore() {}

    public static void save(Path file, Leaderboard board) throws IOException {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (Leaderboard.Entry e : board.ranked()) {
            sb.append(e.name()).append('\t')
                    .append(e.totalCents()).append('\t')
                    .append(e.count()).append('\t')
                    .append(e.lastPaymentMillis()).append('\t')
                    .append(e.lastAmountCents()).append('\n');
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Returns an empty list if the file does not exist. Throws IOException if it exists but is malformed. */
    public static List<Leaderboard.Entry> load(Path file) throws IOException {
        List<Leaderboard.Entry> out = new ArrayList<>();
        if (!Files.exists(file)) return out;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals(HEADER)) {
            throw new IOException("Unrecognised leaderboard file (missing '" + HEADER + "' header)");
        }
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            String[] f = line.split("\t", -1);
            if (f.length != 5) throw new IOException("Line " + (i + 1) + ": expected 5 fields, found " + f.length);
            try {
                String name = f[0];
                long total = Long.parseLong(f[1]);
                int count = Integer.parseInt(f[2]);
                long last = Long.parseLong(f[3]);
                long lastAmount = Long.parseLong(f[4]);
                if (!PaymentParser.isValidUsername(name)) throw new IOException("Line " + (i + 1) + ": invalid username");
                if (total <= 0 || count <= 0 || lastAmount <= 0 || last < 0) throw new IOException("Line " + (i + 1) + ": invalid number");
                out.add(new Leaderboard.Entry(name, total, count, last, lastAmount));
            } catch (NumberFormatException e) {
                throw new IOException("Line " + (i + 1) + ": not a number", e);
            }
        }
        return out;
    }
}
