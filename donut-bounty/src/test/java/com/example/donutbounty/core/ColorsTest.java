package com.example.donutbounty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class ColorsTest {
    @Test public void parsesRgbAndArgb() {
        assertEquals(0xFF112233, Colors.parse("#112233").getAsInt());
        assertEquals(0xFF112233, Colors.parse("112233").getAsInt());
        assertEquals(0x80112233, Colors.parse("#80112233").getAsInt());
        assertEquals(0xFFFFFFFF, Colors.parse("#ffffff").getAsInt());
    }

    @Test public void rejectsBad() {
        for (String s : new String[]{null, "", "#12", "#12345", "#1234567", "#GGGGGG", "red"}) {
            assertFalse(s, Colors.parse(s).isPresent());
        }
        assertEquals(0xFF00FF00, Colors.parseOr("nope", 0xFF00FF00));
    }

    @Test public void toHexRoundTrips() {
        assertEquals("#80112233", Colors.toHex(0x80112233));
        assertEquals(0x80112233, Colors.parse(Colors.toHex(0x80112233)).getAsInt());
    }
}
