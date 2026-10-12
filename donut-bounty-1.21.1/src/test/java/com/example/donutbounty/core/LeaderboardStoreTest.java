package com.example.donutbounty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LeaderboardStoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void roundTripKeepsEverything() throws IOException {
        Path f = tmp.getRoot().toPath().resolve("sub/leaderboard.tsv");
        Leaderboard a = new Leaderboard();
        a.record("Steve", 150_000, 1_700_000_000_000L);
        a.record("Steve", 50_000, 1_700_000_100_000L);
        a.record(".Bedrock", 999, 5);
        LeaderboardStore.save(f, a);

        Leaderboard b = new Leaderboard();
        b.replaceAll(LeaderboardStore.load(f));
        assertEquals(a.ranked(), b.ranked());
        assertEquals(200_000, b.get("steve").orElseThrow().totalCents());
        assertFalse("temp file must not be left behind", Files.exists(f.resolveSibling("leaderboard.tsv.tmp")));
    }

    @Test public void missingFileLoadsEmpty() throws IOException {
        assertTrue(LeaderboardStore.load(tmp.getRoot().toPath().resolve("nope.tsv")).isEmpty());
    }

    @Test public void saveOverwritesPreviousData() throws IOException {
        Path f = tmp.newFile("lb.tsv").toPath();
        Leaderboard a = new Leaderboard();
        a.record("Old", 100, 1);
        LeaderboardStore.save(f, a);
        a.clear();
        a.record("New", 200, 2);
        LeaderboardStore.save(f, a);
        List<Leaderboard.Entry> loaded = LeaderboardStore.load(f);
        assertEquals(1, loaded.size());
        assertEquals("New", loaded.get(0).name());
    }

    @Test public void corruptFilesAreRejectedNotSilentlyAccepted() throws IOException {
        String[] bad = {
                "not a leaderboard\n",
                LeaderboardStore.HEADER + "\nSteve\t100\n",
                LeaderboardStore.HEADER + "\nSteve\tabc\t1\t1\t1\n",
                LeaderboardStore.HEADER + "\nSteve\t-5\t1\t1\t1\n",
                LeaderboardStore.HEADER + "\nSte ve\t100\t1\t1\t100\n",
                LeaderboardStore.HEADER + "\nSteve\t0\t1\t1\t100\n",
        };
        for (String content : bad) {
            Path f = tmp.newFile().toPath();
            Files.writeString(f, content, StandardCharsets.UTF_8);
            try {
                LeaderboardStore.load(f);
                fail("should have rejected: " + content.replace("\n", "\\n"));
            } catch (IOException expected) {
                // good
            }
        }
    }
}
