package com.example.donutbounty.core;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CsvExporterTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void exportsRankedRows() {
        Leaderboard lb = new Leaderboard();
        lb.record("Small", 500, 0);
        lb.record("Big", 250_000_000L, 86_400_000L);
        lb.record("Big", 100, 90_000_000L);
        String csv = CsvExporter.toCsv(lb.ranked());
        String expected = CsvExporter.HEADER + "\r\n"
                + "1,Big,2500001.00,2,1970-01-02T01:00:00Z,1.00\r\n"
                + "2,Small,5.00,1,1970-01-01T00:00:00Z,5.00\r\n";
        assertEquals(expected, csv);
    }

    @Test public void emptyLeaderboardIsJustTheHeader() {
        assertEquals(CsvExporter.HEADER + "\r\n", CsvExporter.toCsv(new Leaderboard().ranked()));
    }

    @Test public void fieldQuotingAndFormulaGuard() {
        assertEquals("plain", CsvExporter.field("plain"));
        assertEquals("\"a,b\"", CsvExporter.field("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.field("say \"hi\""));
        assertEquals("'=SUM(A1)", CsvExporter.field("=SUM(A1)"));
        assertEquals("'+1", CsvExporter.field("+1"));
        assertEquals("'@x", CsvExporter.field("@x"));
    }

    @Test public void writesFileAndCreatesFolders() throws IOException {
        Path f = tmp.getRoot().toPath().resolve("exports/out.csv");
        Leaderboard lb = new Leaderboard();
        lb.record("Steve", 1_000, 0);
        CsvExporter.write(f, lb.ranked());
        assertEquals(CsvExporter.toCsv(lb.ranked()), Files.readString(f, StandardCharsets.UTF_8));
    }
}
