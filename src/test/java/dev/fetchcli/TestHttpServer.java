package dev.fetchcli;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * Embedded HTTP server for integration tests. Uses random port.
 */
public class TestHttpServer {

    private final HttpServer server;
    private final int port;

    public TestHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        setupHandlers();
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int getPort() {
        return port;
    }

    public String baseUrl() {
        return "http://localhost:" + port;
    }

    private void setupHandlers() {
        // Full HTML article page
        server.createContext("/article", exchange -> {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Test Article</title></head>
                    <body>
                    <nav><a href="/">Home</a></nav>
                    <article>
                    <h1>Test Article Title</h1>
                    <p>This is the first paragraph of the test article. It contains enough text to be meaningful for content extraction testing purposes.</p>
                    <p>This is the second paragraph with a <a href="https://example.com">link to example</a> and some <strong>bold text</strong> and <em>italic text</em>.</p>
                    <h2>Section Two</h2>
                    <p>More content in section two. This paragraph also has sufficient length for the quality extractor to consider it meaningful content.</p>
                    </article>
                    <footer>Copyright 2024</footer>
                    </body>
                    </html>
                    """;
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // Plain text response
        server.createContext("/plain", exchange -> {
            String text = "This is plain text content.\nLine two.\nLine three.";
            byte[] body = text.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // JSON API response
        server.createContext("/json-api", exchange -> {
            String json = "{\"key\":\"value\",\"count\":42}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // 404 Not Found
        server.createContext("/not-found", exchange -> {
            String html = "<html><body><h1>Not Found</h1></body></html>";
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // 500 Internal Server Error
        server.createContext("/error", exchange -> {
            String html = "<html><body><h1>Server Error</h1></body></html>";
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // 301 Redirect → /article
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl() + "/article");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });

        // Large response (5MB)
        server.createContext("/large", exchange -> {
            StringBuilder sb = new StringBuilder();
            sb.append("<html><body><article><h1>Large Page</h1>");
            String para = "<p>" + "A".repeat(1000) + "</p>\n";
            for (int i = 0; i < 5000; i++) {
                sb.append(para);
            }
            sb.append("</article></body></html>");
            byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // Formatted page (tables, lists, code, blockquotes)
        server.createContext("/formatted", exchange -> {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Formatted Page</title></head>
                    <body>
                    <article>
                    <h1>Formatted Content</h1>
                    <p>Intro paragraph.</p>
                    <h2>Code Example</h2>
                    <pre><code class="language-java">public class Hello {
                        public static void main(String[] args) {
                            System.out.println("Hello");
                        }
                    }</code></pre>
                    <h2>A List</h2>
                    <ul>
                    <li>First item</li>
                    <li>Second item</li>
                    <li>Third item</li>
                    </ul>
                    <h2>A Table</h2>
                    <table>
                    <tr><th>Name</th><th>Value</th></tr>
                    <tr><td>Alpha</td><td>100</td></tr>
                    <tr><td>Beta</td><td>200</td></tr>
                    </table>
                    <blockquote><p>This is a blockquote.</p></blockquote>
                    </article>
                    </body>
                    </html>
                    """;
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // ISO-8859-1 (Latin-1) charset page
        server.createContext("/latin1", exchange -> {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Latin-1 Page</title></head>
                    <body>
                    <article>
                    <h1>Caf\u00e9 M\u00e9nu</h1>
                    <p>Cr\u00e8me br\u00fbl\u00e9e and na\u00efve pi\u00f1ata.</p>
                    </article>
                    </body>
                    </html>
                    """;
            Charset latin1 = Charset.forName("ISO-8859-1");
            byte[] body = html.getBytes(latin1);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=iso-8859-1");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // Mixed-case HTML content type
        server.createContext("/html-mixed-case", exchange -> {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Mixed Case</title></head>
                    <body><article><h1>Mixed Case Heading</h1><p>Body content.</p></article></body>
                    </html>
                    """;
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "Text/HTML; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // Combined content-encoding token value
        server.createContext("/gzip-combined", exchange -> {
            String text = "Compressed text payload.";
            byte[] compressed;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(text.getBytes(StandardCharsets.UTF_8));
                gzip.finish();
                compressed = baos.toByteArray();
            }

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Encoding", "gzip, identity");
            exchange.sendResponseHeaders(200, compressed.length);
            exchange.getResponseBody().write(compressed);
            exchange.close();
        });
    }
}
