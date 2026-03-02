package dev.fetchcli;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.Set;

public class FastExtractor implements ExtractorEngine {

    private static final Set<String> NOISE_TAGS = Set.of(
            "script", "style", "noscript", "nav", "footer", "header",
            "aside", "iframe", "svg", "form"
    );

    @Override
    public String extract(String html, OutputFormat format) {
        Document doc = Jsoup.parse(html);
        removeNoiseTags(doc);
        var body = doc.body();
        if (body == null) {
            return "";
        }
        if (format == OutputFormat.text) {
            return body.text();
        }
        return MarkdownRenderer.renderElement(body);
    }

    static void removeNoiseTags(Document doc) {
        for (String tag : NOISE_TAGS) {
            doc.select(tag).remove();
        }
    }
}
