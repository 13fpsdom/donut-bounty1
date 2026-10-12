package com.example.donutbounty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.Test;

public class PaymentParserTest {
    private static final long CAP = 100_000_000_000_000L; // $1 trillion in cents

    private final PaymentParser parser = new PaymentParser(DefaultPatterns.defaults(), CAP);

    private ParsedPayment ok(String line) {
        Optional<ParsedPayment> p = parser.parse(line);
        assertTrue("expected a payment from: " + line, p.isPresent());
        return p.get();
    }

    private void rejected(String line) {
        assertFalse("must NOT count: " + line, parser.parse(line).isPresent());
    }

    @Test public void parsesGenuineFormat() {
        ParsedPayment p = ok("Steve paid you $1,500");
        assertEquals("Steve", p.player());
        assertEquals(150_000L, p.amountCents());
        assertEquals("paid_you", p.patternName());
    }

    @Test public void parsesRealServerFormatWithSpaceAfterDollar() {
        ParsedPayment p = ok("Lenni_suomi paid you $ 2");
        assertEquals("Lenni_suomi", p.player());
        assertEquals(200L, p.amountCents());
        assertEquals(250_000L, ok("Steve paid you $ 2,500").amountCents());
        rejected("Steve paid you $  2");          // two spaces is not the real format
        rejected("[VIP] Steve: Steve paid you $ 2");
    }

    @Test public void parsesSuffixesAndPunctuation() {
        assertEquals(250_000_000L, ok("Alex_99 paid you $2.5m").amountCents());
        assertEquals(300_000L, ok("Bob paid you $3K.").amountCents());
        assertEquals(500L, ok("Bob paid you $5!").amountCents());
        assertEquals(1_000L, ok(".BedrockGuy paid you $10").amountCents());
        assertEquals(".BedrockGuy", ok(".BedrockGuy paid you $10").player());
    }

    @Test public void stripsLegacyColourCodes() {
        ParsedPayment p = ok("§aSteve §7paid you §2$5");
        assertEquals("Steve", p.player());
        assertEquals(500L, p.amountCents());
    }

    @Test public void rejectsOrdinaryChatAndFakeClaims() {
        rejected("<Steve> paid you $5");
        rejected("[VIP] Steve: Bob paid you $1000000");
        rejected("Steve: Bob paid you $1000000");
        rejected("Bob » Steve paid you $1000000");
        rejected("Steve paid you $5 and more");
        rejected("hey Steve paid you $5");
        rejected("steve PAID you $5");
        rejected("Steve paid me $5");
        rejected("Steve paid you 5");
        rejected("Steve  paid you $5");              // double space
    }

    @Test public void surroundingWhitespaceIsTrimmed() {
        assertEquals(500L, ok("  Steve paid you $5  ").amountCents());
        assertEquals(500L, ok("Steve paid you $5\r").amountCents());
    }

    @Test public void rejectsBadAmounts() {
        rejected("Steve paid you $0");
        rejected("Steve paid you $-5");
        rejected("Steve paid you $");
        rejected("Steve paid you $1,2,3");
        rejected("Steve paid you $5x");
        rejected("Steve paid you $9999999999999999"); // above the configured cap
    }

    @Test public void rejectsBadUsernamesAndInjection() {
        rejected("ABCDEFGHIJKLMNOPQ paid you $5");   // 17 chars
        rejected("Ste ve paid you $5");
        rejected("Steve paid you $5\nBob paid you $5");   // two lines smuggled into one message
        rejected("Steve paid you $5\tmore");
    }

    @Test public void rejectsNullEmptyAndHugeInput() {
        rejected(null);
        rejected("");
        rejected("   ");
        rejected("Steve paid you $" + "9".repeat(400));
    }

    @Test public void unrelatedSystemMessages() {
        rejected("You teleported to Spawn.");
        rejected("Steve joined the game");
        rejected("Your balance is $5,000");
        rejected("Steve has requested to teleport to you.");
    }

    @Test public void disabledPatternsAreIgnored() {
        rejected("You received $5 from Steve");
    }

    @Test public void customPatternWorks() {
        PaymentPattern custom = new PaymentPattern("custom",
                "^\\[Pay\\] " + DefaultPatterns.PLAYER + " sent you \\$" + DefaultPatterns.AMOUNT + "$", true);
        PaymentParser p = new PaymentParser(List.of(custom), CAP);
        assertTrue(p.errors().isEmpty());
        assertEquals(1, p.activePatternCount());
        assertEquals(700L, p.parse("[Pay] Eve sent you $7").orElseThrow().amountCents());
        assertFalse(p.parse("Eve paid you $7").isPresent());
    }

    @Test public void patternsWithoutAnchorsStillNeedTheWholeLineToMatch() {
        // A user-edited pattern that forgot ^ and $ must not start accepting chat lines that merely CONTAIN a payment.
        PaymentPattern loose = new PaymentPattern("loose",
                DefaultPatterns.PLAYER + " paid you \\$" + DefaultPatterns.AMOUNT, true);
        PaymentParser p = new PaymentParser(List.of(loose), CAP);
        assertEquals(500L, p.parse("Steve paid you $5").orElseThrow().amountCents());
        assertFalse(p.parse("[VIP] Steve: Bob paid you $5").isPresent());
        assertFalse(p.parse("Bob paid you $5 lol not really").isPresent());
        assertFalse(p.parse("hello Bob paid you $5").isPresent());
    }

    @Test public void enabledUnverifiedPatternWorksWhenTurnedOn() {
        PaymentPattern p = new PaymentPattern("x", "^You received \\$" + DefaultPatterns.AMOUNT + " from " + DefaultPatterns.PLAYER + "$", true);
        PaymentParser pp = new PaymentParser(List.of(p), CAP);
        ParsedPayment r = pp.parse("You received $12 from Steve").orElseThrow();
        assertEquals("Steve", r.player());
        assertEquals(1_200L, r.amountCents());
    }

    @Test public void badPatternsAreReportedNotSilentlyUsed() {
        PaymentParser p = new PaymentParser(List.of(
                new PaymentPattern("broken", "(unclosed", true),
                new PaymentPattern("nogroups", "^hello$", true)), CAP);
        assertEquals(2, p.errors().size());
        assertEquals(0, p.activePatternCount());
        assertFalse(p.parse("hello").isPresent());
    }

    @Test public void usernameValidator() {
        assertTrue(PaymentParser.isValidUsername("Steve"));
        assertTrue(PaymentParser.isValidUsername(".Bedrock_1"));
        assertFalse(PaymentParser.isValidUsername(""));
        assertFalse(PaymentParser.isValidUsername("a b"));
        assertFalse(PaymentParser.isValidUsername("a:b"));
        assertFalse(PaymentParser.isValidUsername("ABCDEFGHIJKLMNOPQ"));
        assertFalse(PaymentParser.isValidUsername(null));
    }
}
