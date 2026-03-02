package dev.fetchcli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownRendererTest {

    @Test
    void testHeadings() {
        String html = "<h1>Title</h1><h2>Subtitle</h2><h3>Section</h3>";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("# Title"));
        assertTrue(md.contains("## Subtitle"));
        assertTrue(md.contains("### Section"));
    }

    @Test
    void testParagraphs() {
        String html = "<p>First paragraph.</p><p>Second paragraph.</p>";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("First paragraph."));
        assertTrue(md.contains("Second paragraph."));
        // Should have blank line between paragraphs
        assertTrue(md.contains("First paragraph.\n\nSecond paragraph."));
    }

    @Test
    void testLinks() {
        String html = "<p>Visit <a href=\"https://example.com\">Example</a> site.</p>";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("[Example](https://example.com)"));
    }

    @Test
    void testEmptyLinks() {
        String html = "<p>Text <a href=\"#\">anchor</a> and <a href=\"javascript:void(0)\">js</a></p>";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("anchor"));
        assertTrue(md.contains("js"));
        assertFalse(md.contains("[anchor](#)"));
        assertFalse(md.contains("javascript:"));
    }

    @Test
    void testBoldAndItalic() {
        String html = "<p><strong>bold</strong> and <em>italic</em></p>";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("**bold**"));
        assertTrue(md.contains("*italic*"));
    }

    @Test
    void testInlineCode() {
        String html = "<p>Use <code>System.out</code> to print.</p>";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("`System.out`"));
    }

    @Test
    void testCodeBlock() {
        String html = "<pre><code class=\"language-java\">int x = 1;</code></pre>";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("```java"));
        assertTrue(md.contains("int x = 1;"));
        assertTrue(md.contains("```"));
    }

    @Test
    void testUnorderedList() {
        String html = "<ul><li>Apple</li><li>Banana</li><li>Cherry</li></ul>";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("- Apple"));
        assertTrue(md.contains("- Banana"));
        assertTrue(md.contains("- Cherry"));
    }

    @Test
    void testOrderedList() {
        String html = "<ol><li>First</li><li>Second</li><li>Third</li></ol>";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("1. First"));
        assertTrue(md.contains("2. Second"));
        assertTrue(md.contains("3. Third"));
    }

    @Test
    void testTable() {
        String html = "<table><tr><th>Name</th><th>Age</th></tr><tr><td>Alice</td><td>30</td></tr></table>";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("| Name | Age |"));
        assertTrue(md.contains("| --- | --- |"));
        assertTrue(md.contains("| Alice | 30 |"));
    }

    @Test
    void testBlockquote() {
        String html = "<blockquote><p>Quoted text here.</p></blockquote>";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("> Quoted text here."));
    }

    @Test
    void testImage() {
        String html = "<img src=\"photo.jpg\" alt=\"A photo\">";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("![A photo](photo.jpg)"));
    }

    @Test
    void testHr() {
        String html = "<p>Before</p><hr><p>After</p>";
        String md = MarkdownRenderer.render(html);
        assertTrue(md.contains("---"));
    }

    @Test
    void testScriptAndStyleRemoved() {
        String html = "<p>Text</p><script>alert('x')</script><style>.x{}</style><p>More</p>";
        String md = MarkdownRenderer.render(html);
        assertFalse(md.contains("alert"));
        assertFalse(md.contains(".x{}"));
        assertTrue(md.contains("Text"));
        assertTrue(md.contains("More"));
    }
}
