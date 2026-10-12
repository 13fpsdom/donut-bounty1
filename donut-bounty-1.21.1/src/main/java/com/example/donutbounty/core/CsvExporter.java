package com.example.donutbounty.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** RFC 4180 CSV export of the ranked leaderboard. Totals are plain decimal dollars; times are UTC ISO-8601. */
public final class CsvExporter {
    public static final String HEADER = "rank,username,total_paid,payments,last_payment_utc,last_payment_amount";

    private CsvExporter() {}

    public static String toCsv(List<Leaderboard.Entry> ranked) {
        StringBuilder sb = new StringBuilder(HEADER).append("\r\n");
        int rank = 1;
        for (Leaderboard.Entry e : ranked) {
            sb.append(rank++).append(',')
                    .append(field(e.name())).append(',')
                    .append(Money.plainDollars(e.totalCents())).append(',')
                    .append(e.count()).append(',')
                    .append(Instant.ofEpochMilli(e.lastPaymentMillis())).append(',')
                    .append(Money.plainDollars(e.lastAmountCents())).append("\r\n");
        }
        return sb.toString();
    }

    public static void write(Path file, List<Leaderboard.Entry> ranked) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(file, toCsv(ranked), StandardCharsets.UTF_8);
    }

    /** Quotes when needed and neutralises spreadsheet formula injection (cells starting with = + - @). */
    static String field(String value) {
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
