package dev.fetchcli;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.List;
import java.util.Set;

public final class MarkdownRenderer {

    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "section", "article", "main", "header", "footer",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "ul", "ol", "li", "blockquote", "pre", "table", "tr",
            "hr", "br", "figure", "figcaption", "details", "summary"
    );

    private MarkdownRenderer() {
    }

    public static String render(String html) {
        var doc = Jsoup.parseBodyFragment(html);
        var ctx = new RenderContext();
        renderChildren(doc.body(), ctx);
        return ctx.toString();
    }

    public static String renderElement(Element element) {
        var ctx = new RenderContext();
        renderChildren(element, ctx);
        return ctx.toString();
    }

    private static void renderChildren(Element parent, RenderContext ctx) {
        for (Node child : parent.childNodes()) {
            renderNode(child, ctx);
        }
    }

    private static void renderNode(Node node, RenderContext ctx) {
        if (node instanceof TextNode textNode) {
            String text = ctx.inPre ? textNode.getWholeText() : collapseWhitespace(textNode.getWholeText());
            if (!ctx.inPre && text.isBlank() && isBlockBoundary(textNode)) {
                return;
            }
            ctx.append(text);
            return;
        }

        if (!(node instanceof Element el)) {
            return;
        }

        String tag = el.tagName().toLowerCase();

        switch (tag) {
            case "script", "style", "noscript", "iframe", "svg", "template" -> {
                // skip
            }
            case "br" -> ctx.append("\n");
            case "hr" -> {
                ctx.ensureBlankLine();
                ctx.append("---");
                ctx.ensureBlankLine();
            }
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                int level = tag.charAt(1) - '0';
                ctx.ensureBlankLine();
                ctx.append("#".repeat(level) + " ");
                renderChildren(el, ctx);
                ctx.ensureBlankLine();
            }
            case "p" -> {
                ctx.ensureBlankLine();
                renderChildren(el, ctx);
                ctx.ensureBlankLine();
            }
            case "blockquote" -> {
                ctx.ensureBlankLine();
                var inner = new RenderContext();
                inner.inPre = ctx.inPre;
                renderChildren(el, inner);
                String content = inner.toString().strip();
                for (String line : content.split("\n", -1)) {
                    ctx.append("> " + line + "\n");
                }
                ctx.ensureNewline();
            }
            case "pre" -> {
                ctx.ensureBlankLine();
                Element code = el.selectFirst("code");
                String lang = "";
                if (code != null) {
                    for (String cls : code.classNames()) {
                        if (cls.startsWith("language-")) {
                            lang = cls.substring(9);
                            break;
                        } else if (cls.startsWith("lang-")) {
                            lang = cls.substring(5);
                            break;
                        }
                    }
                }
                ctx.append("```" + lang + "\n");
                boolean wasPre = ctx.inPre;
                ctx.inPre = true;
                if (code != null) {
                    renderChildren(code, ctx);
                } else {
                    renderChildren(el, ctx);
                }
                ctx.inPre = wasPre;
                ctx.ensureNewline();
                ctx.append("```");
                ctx.ensureBlankLine();
            }
            case "code" -> {
                if (!ctx.inPre) {
                    String text = el.text();
                    if (!text.isEmpty()) {
                        if (text.contains("`")) {
                            ctx.append("`` " + text + " ``");
                        } else {
                            ctx.append("`" + text + "`");
                        }
                    }
                } else {
                    renderChildren(el, ctx);
                }
            }
            case "strong", "b" -> {
                ctx.append("**");
                renderChildren(el, ctx);
                ctx.append("**");
            }
            case "em", "i" -> {
                ctx.append("*");
                renderChildren(el, ctx);
                ctx.append("*");
            }
            case "a" -> renderLink(el, ctx);
            case "img" -> renderImage(el, ctx);
            case "ul" -> renderList(el, ctx, false);
            case "ol" -> renderList(el, ctx, true);
            case "table" -> renderTable(el, ctx);
            case "div", "section", "article", "main", "span", "figure",
                 "figcaption", "details", "summary", "header", "footer",
                 "nav", "aside", "dd", "dt", "dl" -> {
                if (BLOCK_TAGS.contains(tag)) {
                    ctx.ensureNewline();
                }
                renderChildren(el, ctx);
                if (BLOCK_TAGS.contains(tag)) {
                    ctx.ensureNewline();
                }
            }
            default -> renderChildren(el, ctx);
        }
    }

    private static void renderLink(Element el, RenderContext ctx) {
        String href = el.attr("href").strip();
        if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:")) {
            renderChildren(el, ctx);
            return;
        }
        ctx.append("[");
        renderChildren(el, ctx);
        ctx.append("](" + href + ")");
    }

    private static void renderImage(Element el, RenderContext ctx) {
        String src = el.attr("src").strip();
        if (src.isEmpty()) return;
        String alt = el.attr("alt").strip();
        ctx.append("![" + alt + "](" + src + ")");
    }

    private static void renderList(Element el, RenderContext ctx, boolean ordered) {
        ctx.ensureBlankLine();
        int index = 1;
        for (Element child : el.children()) {
            if (!"li".equals(child.tagName().toLowerCase())) continue;
            String prefix = ordered ? (index++ + ". ") : "- ";
            String indent = "  ".repeat(ctx.listDepth);
            ctx.append(indent + prefix);
            ctx.listDepth++;
            var inner = new RenderContext();
            inner.listDepth = ctx.listDepth;
            inner.inPre = ctx.inPre;
            renderChildren(child, inner);
            String content = inner.toString().strip();
            // indent continuation lines
            String contIndent = "  ".repeat(ctx.listDepth);
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    ctx.append("\n");
                    if (!lines[i].isEmpty()) {
                        ctx.append(contIndent + lines[i]);
                    }
                } else {
                    ctx.append(lines[i]);
                }
            }
            ctx.append("\n");
            ctx.listDepth--;
        }
    }

    private static void renderTable(Element el, RenderContext ctx) {
        ctx.ensureBlankLine();

        // Collect all rows
        List<Element> rows = el.select("tr");
        if (rows.isEmpty()) return;

        // Determine columns count from first row
        Element firstRow = rows.get(0);
        List<Element> headerCells = firstRow.select("th, td");
        int cols = headerCells.size();
        if (cols == 0) return;

        // Render header
        ctx.append("|");
        for (Element cell : headerCells) {
            ctx.append(" " + cell.text().strip() + " |");
        }
        ctx.append("\n");

        // Separator
        ctx.append("|");
        for (int i = 0; i < cols; i++) {
            ctx.append(" --- |");
        }
        ctx.append("\n");

        // Body rows (skip first if it was the header)
        boolean firstIsHeader = !firstRow.select("th").isEmpty();
        for (int r = firstIsHeader ? 1 : 0; r < rows.size(); r++) {
            List<Element> cells = rows.get(r).select("th, td");
            ctx.append("|");
            for (int c = 0; c < cols; c++) {
                String text = c < cells.size() ? cells.get(c).text().strip() : "";
                ctx.append(" " + text + " |");
            }
            ctx.append("\n");
        }
        ctx.ensureNewline();
    }

    private static String collapseWhitespace(String text) {
        return text.replaceAll("[ \\t\\n\\r]+", " ");
    }

    private static boolean isBlockBoundary(TextNode textNode) {
        Node prev = textNode.previousSibling();
        Node next = textNode.nextSibling();
        boolean prevBlock = prev == null || (prev instanceof Element e && BLOCK_TAGS.contains(e.tagName().toLowerCase()));
        boolean nextBlock = next == null || (next instanceof Element e && BLOCK_TAGS.contains(e.tagName().toLowerCase()));
        return prevBlock || nextBlock;
    }

    static class RenderContext {
        private final StringBuilder sb = new StringBuilder();
        int listDepth = 0;
        boolean inPre = false;

        void append(String text) {
            sb.append(text);
        }

        void ensureNewline() {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                sb.append('\n');
            }
        }

        void ensureBlankLine() {
            if (sb.length() == 0) return;
            ensureNewline();
            if (sb.length() >= 2 && sb.charAt(sb.length() - 2) != '\n') {
                sb.append('\n');
            }
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
