package dev.fetchcli;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

@ApplicationScoped
public class FetchService {

    private static final String USER_AGENT = "fetch-cli/1.0 (agent-tool)";
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset\\s*=\\s*\"?([\\w-]+)", Pattern.CASE_INSENSITIVE);

    private final SSLContext sslContext;
    private final ConcurrentHashMap<Integer, HttpClient> clientsByTimeout = new ConcurrentHashMap<>();

    public FetchService() {
        this.sslContext = buildSslContext();
    }

    public record FetchResult(
            URI finalUrl,
            int statusCode,
            String contentType,
            String body,
            int bodyBytes,
            boolean bodyTruncated
    ) {
    }

    private record BodyReadResult(byte[] bytes, boolean truncated) {
    }

    public FetchResult fetch(String url, int timeoutMs, int maxBytes) {
        try {
            HttpClient client = clientsByTimeout.computeIfAbsent(timeoutMs, this::buildClient);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate")
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            int status = response.statusCode();
            if (status >= 400) {
                throw new FetchException("HTTP " + status + " for " + url, 1);
            }

            String contentType = response.headers()
                    .firstValue("content-type")
                    .orElse("text/html");

            String contentEncoding = response.headers()
                    .firstValue("content-encoding")
                    .orElse("");

            BodyReadResult bodyRead;
            try (InputStream responseBody = response.body();
                 InputStream decodedBody = decodeContentStream(responseBody, contentEncoding)) {
                bodyRead = readWithLimit(decodedBody, maxBytes);
            }
            byte[] bodyBytes = bodyRead.bytes();
            Charset charset = parseCharset(contentType);
            String body = new String(bodyBytes, charset);

            return new FetchResult(
                    response.uri(),
                    status,
                    contentType,
                    body,
                    bodyBytes.length,
                    bodyRead.truncated()
            );
        } catch (FetchException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Request interrupted: " + url, 1, e);
        } catch (Exception e) {
            throw new FetchException("Failed to fetch " + url + ": " + e.getMessage(), 1, e);
        }
    }

    private HttpClient buildClient(int timeoutMs) {
        var clientBuilder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(timeoutMs));

        if (sslContext != null) {
            clientBuilder.sslContext(sslContext);
        }
        return clientBuilder.build();
    }

    private InputStream decodeContentStream(InputStream bodyStream, String contentEncoding) throws IOException {
        if (contentEncoding == null || contentEncoding.isBlank()) {
            return bodyStream;
        }

        String[] encodingTokens = contentEncoding.split(",");
        for (String token : encodingTokens) {
            String encoding = normalizeEncodingToken(token);
            if (encoding.isEmpty() || "identity".equals(encoding)
                    || "gzip".equals(encoding) || "x-gzip".equals(encoding) || "deflate".equals(encoding)) {
                continue;
            }
            return bodyStream;
        }

        InputStream decoded = bodyStream;
        for (int i = encodingTokens.length - 1; i >= 0; i--) {
            String encoding = normalizeEncodingToken(encodingTokens[i]);
            if (encoding.isEmpty() || "identity".equals(encoding)) {
                continue;
            }
            if ("gzip".equals(encoding) || "x-gzip".equals(encoding)) {
                decoded = new GZIPInputStream(decoded);
            } else if ("deflate".equals(encoding)) {
                decoded = new InflaterInputStream(decoded);
            }
        }
        return decoded;
    }

    private String normalizeEncodingToken(String token) {
        String value = token.strip().toLowerCase(Locale.ROOT);
        int separator = value.indexOf(';');
        if (separator >= 0) {
            value = value.substring(0, separator).strip();
        }
        return value;
    }

    private SSLContext buildSslContext() {
        // Try well-known CA bundle paths (host system certs)
        String[] caBundlePaths = {
                "/etc/ssl/certs/java/cacerts",                         // Debian/Ubuntu
                "/etc/pki/ca-trust/extracted/java/cacerts",            // RHEL/Fedora
                "/etc/pki/java/cacerts",                               // CentOS
        };
        for (String path : caBundlePaths) {
            if (Files.exists(Path.of(path))) {
                try (FileInputStream fis = new FileInputStream(path)) {
                    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                    ks.load(fis, "changeit".toCharArray());
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(ks);
                    SSLContext ctx = SSLContext.getInstance("TLS");
                    ctx.init(null, tmf.getTrustManagers(), null);
                    return ctx;
                } catch (Exception e) {
                    // try next
                }
            }
        }
        return null; // use JVM default
    }

    private BodyReadResult readWithLimit(InputStream in, int maxBytes) throws IOException {
        var out = new ByteArrayOutputStream(Math.min(maxBytes, 65536));
        byte[] buf = new byte[8192];
        int totalRead = 0;
        boolean truncated = false;
        int n;
        while ((n = in.read(buf)) != -1) {
            int remaining = maxBytes - totalRead;
            if (remaining <= 0) {
                truncated = true;
                break;
            }
            int toWrite = Math.min(n, remaining);
            out.write(buf, 0, toWrite);
            totalRead += toWrite;
            if (toWrite < n) {
                truncated = true;
                break;
            }
            if (totalRead >= maxBytes) {
                int nextByte = in.read();
                if (nextByte != -1) {
                    truncated = true;
                }
                break;
            }
        }
        return new BodyReadResult(out.toByteArray(), truncated);
    }

    private Charset parseCharset(String contentType) {
        if (contentType == null) return StandardCharsets.UTF_8;
        Matcher m = CHARSET_PATTERN.matcher(contentType);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (Exception e) {
                // fallback
            }
        }
        return StandardCharsets.UTF_8;
    }
}
