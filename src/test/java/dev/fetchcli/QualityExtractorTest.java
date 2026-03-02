package dev.fetchcli;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QualityExtractorTest {

    private final QualityExtractor extractor = new QualityExtractor();

    @Test
    void testExtractArticleElement() {
        String html = """
                <html><body>
                <nav>Navigation</nav>
                <article>
                <h1>Article Title</h1>
                <p>Article content that is long enough to be considered meaningful for the quality extractor test. It needs at least 100 characters of text content.</p>
                </article>
                <footer>Footer content</footer>
                </body></html>
                """;
        String result = extractor.extract(html, OutputFormat.markdown);
        assertTrue(result.contains("Article Title"), "Should extract article content");
        assertFalse(result.contains("Navigation"), "Should remove nav");
        assertFalse(result.contains("Footer content"), "Should remove footer");
    }

    @Test
    void testExtractMainElement() {
        String html = """
                <html><body>
                <div class="sidebar">Sidebar</div>
                <main>
                <h1>Main Content</h1>
                <p>This is the main content area of the page. It contains sufficient text to pass the minimum character threshold for the quality extractor.</p>
                </main>
                </body></html>
                """;
        String result = extractor.extract(html, OutputFormat.markdown);
        assertTrue(result.contains("Main Content"), "Should extract main content");
    }

    @Test
    void testExtractRoleMain() {
        String html = """
                <html><body>
                <div role="main">
                <h1>Role Main Content</h1>
                <p>This div has role=main and contains the primary content of the page. It should be detected by the quality extractor as the main content area.</p>
                </div>
                </body></html>
                """;
        String result = extractor.extract(html, OutputFormat.markdown);
        assertTrue(result.contains("Role Main Content"), "Should extract role=main content");
    }

    @Test
    void testScoringHeuristic() {
        String html = """
                <html><body>
                <div class="sidebar-nav">
                <a href="/1">Link 1</a><a href="/2">Link 2</a><a href="/3">Link 3</a>
                </div>
                <div class="article-content">
                <p>First paragraph of the main article content area. This is a long paragraph with real text.</p>
                <p>Second paragraph of the main article content area. Also long with meaningful text content.</p>
                <p>Third paragraph of the main article content. Even more text to make this the obvious winner.</p>
                </div>
                </body></html>
                """;
        Document doc = Jsoup.parse(html);

        Element contentDiv = doc.selectFirst(".article-content");
        Element navDiv = doc.selectFirst(".sidebar-nav");

        double contentScore = extractor.scoreElement(contentDiv);
        double navScore = extractor.scoreElement(navDiv);

        assertTrue(contentScore > navScore, "Content div should score higher than nav div");
    }

    @Test
    void testLinkDensityPenalty() {
        QualityExtractor ext = new QualityExtractor();
        String html = """
                <html><body>
                <div id="links">
                <a href="/1">Link text one that is quite long</a>
                <a href="/2">Another link with long text here</a>
                <a href="/3">Yet another link with text content</a>
                <a href="/4">And one more link to increase density</a>
                </div>
                </body></html>
                """;
        Document doc = Jsoup.parse(html);
        Element linksDiv = doc.selectFirst("#links");
        double score = ext.scoreElement(linksDiv);
        // High link density should get penalized
        assertTrue(score < linksDiv.text().length(), "High link density should reduce score below text length");
    }

    @Test
    void testTextFormat() {
        String html = """
                <html><body>
                <article>
                <h1>Title</h1>
                <p>A paragraph with enough text to pass the minimum threshold for quality extraction. This should be plain text output.</p>
                </article>
                </body></html>
                """;
        String result = extractor.extract(html, OutputFormat.text);
        assertTrue(result.contains("Title"), "Should contain title");
        assertFalse(result.contains("#"), "Text format should not have markdown");
    }
}
