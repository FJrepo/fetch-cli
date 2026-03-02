package dev.fetchcli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

@TopCommand
@Command(
        name = "fetch",
        mixinStandardHelpOptions = true,
        version = "fetch-cli 1.0.0",
        description = "Fetch a URL and convert to clean markdown or plain text."
)
public class FetchCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "URL to fetch (http or https)")
    String url;

    @Option(names = "--json", description = "Output as JSON envelope")
    boolean json;

    @Option(names = {"-q", "--quiet"}, description = "Quiet mode — suppress success output")
    boolean quiet;

    @Option(names = "--timeout-ms", defaultValue = "10000", description = "HTTP timeout in milliseconds (default: ${DEFAULT-VALUE})")
    int timeoutMs;

    @Option(names = "--max-bytes", defaultValue = "2000000", description = "Max response body bytes (default: ${DEFAULT-VALUE})")
    int maxBytes;

    @Option(names = "--format", defaultValue = "markdown", description = "Output format: markdown or text (default: ${DEFAULT-VALUE})")
    OutputFormat format;

    @Option(names = "--engine", defaultValue = "auto", description = "Extraction engine: auto, quality, or fast (default: ${DEFAULT-VALUE})")
    FetchEngine engine;

    @Option(names = "-o", description = "Write output to file")
    String outputFile;

    @Inject
    FetchService fetchService;

    @Override
    public Integer call() {
        try {
            // Validate URL
            if (url == null || url.isBlank()) {
                return error("URL is required", 2);
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return error("URL must start with http:// or https://", 2);
            }

            // Validate options
            if (timeoutMs <= 0) {
                return error("--timeout-ms must be > 0", 2);
            }
            if (maxBytes <= 0) {
                return error("--max-bytes must be > 0", 2);
            }

            // Fetch
            FetchService.FetchResult result = fetchService.fetch(url, timeoutMs, maxBytes);

            // Extract content
            String content;
            String title = "";
            String normalizedContentType = result.contentType() == null
                    ? ""
                    : result.contentType().toLowerCase(Locale.ROOT);
            boolean isHtml = normalizedContentType.contains("text/html")
                    || normalizedContentType.contains("xhtml");

            if (isHtml) {
                // Extract title from HTML
                var doc = org.jsoup.Jsoup.parse(result.body());
                title = doc.title();

                content = extractContent(result.body());
                content = OutputNormalizer.normalize(content);
            } else {
                // Pass through raw body
                content = result.body();
            }

            // Truncate to maxBytes if needed (UTF-8 safe)
            boolean truncated = result.bodyTruncated();
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            if (contentBytes.length > maxBytes) {
                content = truncateUtf8(content, maxBytes);
                truncated = true;
            }

            String output;
            if (json) {
                int bytes = content.getBytes(StandardCharsets.UTF_8).length;
                output = buildJsonOutput(result, title, content, bytes, truncated);
            } else {
                output = content;
            }

            if (outputFile != null) {
                Files.writeString(Path.of(outputFile), output, StandardCharsets.UTF_8);
            } else if (!quiet) {
                System.out.print(output);
                if (!output.endsWith("\n")) {
                    System.out.println();
                }
            }

            return 0;

        } catch (FetchException e) {
            return error(e.getMessage(), e.getExitCode());
        } catch (Exception e) {
            return error(e.getMessage(), 1);
        }
    }

    private String extractContent(String html) {
        ExtractorEngine qualityExtractor = new QualityExtractor();
        ExtractorEngine fastExtractor = new FastExtractor();

        return switch (engine) {
            case quality -> qualityExtractor.extract(html, format);
            case fast -> fastExtractor.extract(html, format);
            case auto -> {
                String result = qualityExtractor.extract(html, format);
                if (result != null && result.strip().length() >= 50) {
                    yield result;
                }
                yield fastExtractor.extract(html, format);
            }
        };
    }

    private String buildJsonOutput(FetchService.FetchResult result, String title, String content, int bytes, boolean truncated) {
        return "{" +
                "\"url\":\"" + JsonUtil.escapeJson(url) + "\"," +
                "\"final_url\":\"" + JsonUtil.escapeJson(result.finalUrl().toString()) + "\"," +
                "\"status\":" + result.statusCode() + "," +
                "\"title\":\"" + JsonUtil.escapeJson(title) + "\"," +
                "\"content\":\"" + JsonUtil.escapeJson(content) + "\"," +
                "\"bytes\":" + bytes + "," +
                "\"truncated\":" + truncated +
                "}\n";
    }

    private String truncateUtf8(String text, int maxBytes) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return text;
        // Find a safe cut point (don't split a multi-byte character)
        int cut = maxBytes;
        while (cut > 0 && cut < bytes.length && (bytes[cut] & 0xC0) == 0x80) {
            cut--;
        }
        return new String(bytes, 0, cut, StandardCharsets.UTF_8);
    }

    private int error(String message, int exitCode) {
        if (json) {
            String jsonError = "{\"error\":\"" + JsonUtil.escapeJson(message) + "\"}\n";
            if (outputFile != null) {
                try {
                    Files.writeString(Path.of(outputFile), jsonError, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    System.err.println("Error writing output file '" + outputFile + "': " + e.getMessage());
                    System.out.print(jsonError);
                }
            } else {
                System.out.print(jsonError);
            }
        } else {
            System.err.println("Error: " + message);
        }
        return exitCode;
    }
}
