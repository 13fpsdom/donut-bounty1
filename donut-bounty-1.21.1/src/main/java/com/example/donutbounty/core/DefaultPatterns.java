package com.example.donutbounty.core;

import java.util.List;

/**
 * Built-in payment formats.
 *
 * The enabled default matches the line seen in a real game on DonutSMP: "Name paid you $ 2" (a space after the $
 * is allowed, and amounts such as $2.5m). Other payment types may print differently. If your server prints something different, edit the patterns in config/donutbounty/config.json.
 */
public final class DefaultPatterns {
    public static final String PLAYER = "(?<player>\\.?[A-Za-z0-9_]{1,16})";
    public static final String AMOUNT = "(?<amount>[0-9][0-9,]*(?:\\.[0-9]+)?[KkMmBbTt]?)";

    /** The paid_you regex shipped in the first builds (no space allowed after the $). Upgraded automatically. */
    public static final String OLD_PAID_YOU = "^" + PLAYER + " paid you \\$" + AMOUNT + "[.!]?$";

    private DefaultPatterns() {}

    public static List<PaymentPattern> defaults() {
        return List.of(
                new PaymentPattern("paid_you", "^" + PLAYER + " paid you \\$ ?" + AMOUNT + "[.!]?$", true),
                // Unverified guess kept disabled as a template; enable it only if your server prints this form.
                new PaymentPattern("received_from_UNVERIFIED", "^You received \\$" + AMOUNT + " from " + PLAYER + "[.!]?$", false));
    }
}
