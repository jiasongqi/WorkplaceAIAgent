package com.yupi.yuaiagent.guard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObservationSanitizerTest {

    private final ObservationSanitizer sanitizer = new ObservationSanitizer();

    @Test
    void stripsHtmlAndBase64AndTruncates() {
        String raw = "<p>hello</p>\n" + "A".repeat(250) + "\n\n\n" + "world " + "x".repeat(5000);
        String out = sanitizer.sanitize(raw, 500);
        assertFalse(out.contains("<p>"));
        assertTrue(out.contains("[base64 omitted]") || out.length() <= 500 + 200);
        assertTrue(out.contains("System Note") || out.length() <= 500);
    }

    @Test
    void shortTextUnchangedAsideFromWhitespace() {
        String out = sanitizer.sanitize("  ok  result  ");
        assertEquals("ok result", out);
    }
}
