package com.example.donutbounty.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Turns one incoming message into a verified payment, or nothing.
 *
 * Safety rules, all enforced here:
 *  - the message must match a configured pattern in FULL (no prefix or suffix allowed),
 *  - the message must be a single line with no control characters,
 *  - the payer must look like a real Minecraft username (optional leading '.' for Bedrock players),
 *  - the amount must be positive and not above the configured cap.
 */
public final class PaymentParser {
    /** Java Edition usernames are 3-16 chars of [A-Za-z0-9_]; Floodgate/Bedrock names get a leading '.'. */
    private static final Pattern USERNAME = Pattern.compile("\\.?[A-Za-z0-9_]{1,16}");
    private static final Pattern LEGACY_COLOUR = Pattern.compile("§.");

    private record Compiled(String name, Pattern pattern) {}

    private final List<Compiled> compiled = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final long maxAmountCents;

    public PaymentParser(List<PaymentPattern> patterns, long maxAmountCents) {
        this.maxAmountCents = maxAmountCents;
        for (PaymentPattern p : patterns) {
            if (p == null || !p.enabled()) continue;
            try {
                Pattern pat = Pattern.compile(p.regex());
                // make sure the named groups exist so a typo is reported up front, not silently ignored
                Matcher probe = pat.matcher("");
                probe.matches();
                if (!p.regex().contains("(?<player>") || !p.regex().contains("(?<amount>")) {
                    errors.add("Pattern '" + p.name() + "' must contain (?<player>...) and (?<amount>...) groups");
                    continue;
                }
                compiled.add(new Compiled(p.name(), pat));
            } catch (PatternSyntaxException e) {
                errors.add("Pattern '" + p.name() + "' is invalid: " + e.getDescription());
            }
        }
    }

    /** Problems found while compiling the configured patterns. */
    public List<String> errors() {
        return List.copyOf(errors);
    }

    public int activePatternCount() {
        return compiled.size();
    }

    /** Removes legacy section-sign colour codes and surrounding whitespace. */
    public static String normalise(String raw) {
        if (raw == null) return "";
        return LEGACY_COLOUR.matcher(raw).replaceAll("").trim();
    }

    public Optional<ParsedPayment> parse(String raw) {
        String text = normalise(raw);
        if (text.isEmpty() || text.length() > 256) return Optional.empty();
        for (int i = 0; i < text.length(); i++) {
            if (Character.isISOControl(text.charAt(i))) return Optional.empty(); // newlines, tabs, etc.
        }
        for (Compiled c : compiled) {
            Matcher m = c.pattern().matcher(text);
            if (!m.matches()) continue;
            String player;
            String amountText;
            try {
                player = m.group("player");
                amountText = m.group("amount");
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (player == null || amountText == null) continue;
            if (!isValidUsername(player)) continue;
            OptionalLong cents = Money.parseCents(amountText);
            if (cents.isEmpty() || cents.getAsLong() > maxAmountCents) continue;
            return Optional.of(new ParsedPayment(player, cents.getAsLong(), c.name()));
        }
        return Optional.empty();
    }

    public static boolean isValidUsername(String name) {
        return name != null && USERNAME.matcher(name).matches();
    }
}
