package dev.fetchcli;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Set;
import java.util.regex.Pattern;

public class QualityExtractor implements ExtractorEngine {

    private static final Set<String> NOISE_TAGS = Set.of(
            "script", "style", "noscript", "nav", "footer", "header",
            "aside", "iframe", "svg", "form"
    );

    private static final Pattern POSITIVE_CLASS = Pattern.compile(
            "article|content|main|post|entry|text|body|page", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE_CLASS = Pattern.compile(
            "sidebar|nav|menu|comment|footer|header|ad|widget|social|share|related|promo",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNLIKELY_CLASS = Pattern.compile(
            "hidden|display-none|sidebar|ad-|promo|social|share", Pattern.CASE_INSENSITIVE);

    private static final int MIN_CONTENT_CHARS = 100;

    @Override
    public String extract(String html, OutputFormat format) {
        Document doc = Jsoup.parse(html);
        removeNoiseTags(doc);

        Element content = findContentElement(doc);
        if (content == null) {
            content = doc.body();
        }
        if (content == null) {
            return "";
        }

        cleanNestedNoise(content);

        if (format == OutputFormat.text) {
            return content.text();
        }
        return MarkdownRenderer.renderElement(content);
    }

    private void removeNoiseTags(Document doc) {
        for (String tag : NOISE_TAGS) {
            doc.select(tag).remove();
        }
    }

    Element findContentElement(Document doc) {
        // 1. Try single <article>
        Elements articles = doc.select("article");
        if (articles.size() == 1 && articles.first().text().length() >= MIN_CONTENT_CHARS) {
            return articles.first();
        }

        // 2. Try <main>
        Element main = doc.selectFirst("main");
        if (main != null && main.text().length() >= MIN_CONTENT_CHARS) {
            return main;
        }

        // 3. Try [role=main]
        Element roleMain = doc.selectFirst("[role=main]");
        if (roleMain != null && roleMain.text().length() >= MIN_CONTENT_CHARS) {
            return roleMain;
        }

        // 4. If multiple articles, pick largest
        if (articles.size() > 1) {
            Element largest = null;
            int maxLen = 0;
            for (Element a : articles) {
                int len = a.text().length();
                if (len > maxLen) {
                    maxLen = len;
                    largest = a;
                }
            }
            if (largest != null && maxLen >= MIN_CONTENT_CHARS) {
                return largest;
            }
        }

        // 5. Scoring heuristic on div/section/td
        return scoreCandidates(doc);
    }

    private Element scoreCandidates(Document doc) {
        Elements candidates = doc.select("div, section, td");
        Element best = null;
        double bestScore = 0;

        for (Element el : candidates) {
            double score = scoreElement(el);
            if (score > bestScore) {
                bestScore = score;
                best = el;
            }
        }

        if (best != null && best.text().length() >= MIN_CONTENT_CHARS) {
            return best;
        }
        return null; // fallback to body
    }

    double scoreElement(Element el) {
        String text = el.text();
        int textLen = text.length();

        // Base score = text length
        double score = textLen;

        // Paragraph bonus
        Elements paragraphs = el.select("p");
        int pCount = paragraphs.size();
        int pTextLen = 0;
        for (Element p : paragraphs) {
            pTextLen += p.text().length();
        }
        score += pCount * 50.0 + pTextLen * 0.5;

        // Link density penalty
        Elements links = el.select("a");
        int linkTextLen = 0;
        for (Element a : links) {
            linkTextLen += a.text().length();
        }
        double linkDensity = textLen > 0 ? (double) linkTextLen / textLen : 0;
        if (linkDensity > 0.3) {
            score *= 0.3;
        } else if (linkDensity > 0.2) {
            score *= 0.6;
        }

        // Class/ID bonus/penalty
        String classId = (el.className() + " " + el.id()).toLowerCase();
        if (POSITIVE_CLASS.matcher(classId).find()) {
            score += 500;
        }
        if (NEGATIVE_CLASS.matcher(classId).find()) {
            score -= 500;
        }

        return score;
    }

    private void cleanNestedNoise(Element content) {
        Elements children = content.select("*");
        for (Element child : children) {
            String classId = (child.className() + " " + child.id()).toLowerCase();
            if (UNLIKELY_CLASS.matcher(classId).find() && child.text().length() < 80) {
                child.remove();
            }
        }
    }
}
