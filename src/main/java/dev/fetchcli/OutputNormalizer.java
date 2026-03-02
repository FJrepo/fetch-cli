package dev.fetchcli;

public final class OutputNormalizer {

    private OutputNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // CRLF → LF
        String result = text.replace("\r\n", "\n").replace("\r", "\n");

        // Strip trailing whitespace per line
        String[] lines = result.split("\n", -1);
        var sb = new StringBuilder(result.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(stripTrailing(lines[i]));
        }
        result = sb.toString();

        // Ensure blank line before headings (markdown)
        result = result.replaceAll("([^\n])\n(#{1,6} )", "$1\n\n$2");

        // Collapse 3+ consecutive newlines → 2
        result = result.replaceAll("\n{3,}", "\n\n");

        // Trim leading/trailing whitespace, ensure trailing newline
        result = result.strip();
        if (!result.isEmpty()) {
            result = result + "\n";
        }

        return result;
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
            end--;
        }
        return line.substring(0, end);
    }
}
