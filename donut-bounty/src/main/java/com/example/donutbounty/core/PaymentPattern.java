package com.example.donutbounty.core;

/**
 * One configurable payment-message format.
 *
 * regex must contain two named groups: (?&lt;player&gt;...) and (?&lt;amount&gt;...). It is matched against the
 * WHOLE message (Matcher.matches), so a line with any extra text before or after, such as a chat
 * prefix, a rank tag or a trailing sentence, will not match.
 */
public record PaymentPattern(String name, String regex, boolean enabled) {
}
