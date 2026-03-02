package dev.fetchcli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutputNormalizerTest {

    @Test
    void testCrlfToLf() {
        String input = "line1\r\nline2\r\nline3";
        String result = OutputNormalizer.normalize(input);
        assertFalse(result.contains("\r"), "Should not contain CR");
        assertTrue(result.contains("line1\nline2\nline3"));
    }

    @Test
    void testStripTrailingWhitespace() {
        String input = "line1   \nline2\t\nline3";
        String result = OutputNormalizer.normalize(input);
        assertFalse(result.contains("   \n"));
        assertFalse(result.contains("\t\n"));
    }

    @Test
    void testCollapseNewlines() {
        String input = "para1\n\n\n\n\npara2";
        String result = OutputNormalizer.normalize(input);
        // Should be at most 2 consecutive newlines
        assertFalse(result.contains("\n\n\n"), "Should collapse 3+ newlines to 2");
        assertTrue(result.contains("para1\n\npara2"));
    }

    @Test
    void testBlankLineBeforeHeadings() {
        String input = "Some text.\n# Heading";
        String result = OutputNormalizer.normalize(input);
        assertTrue(result.contains("Some text.\n\n# Heading"), "Should add blank line before heading");
    }

    @Test
    void testTrimAndTrailingNewline() {
        String input = "\n\n  Content  \n\n";
        String result = OutputNormalizer.normalize(input);
        assertEquals("Content\n", result);
    }

    @Test
    void testEmptyInput() {
        assertEquals("", OutputNormalizer.normalize(""));
        assertEquals("", OutputNormalizer.normalize(null));
    }

    @Test
    void testPreserveSingleBlankLines() {
        String input = "para1\n\npara2";
        String result = OutputNormalizer.normalize(input);
        assertTrue(result.contains("para1\n\npara2"), "Should preserve single blank lines");
    }
}
