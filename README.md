# fetch-cli

Agent-friendly CLI that fetches a URL and converts it to clean markdown or plain text. No browser, no headless Chrome — just HTTP + Jsoup + a custom markdown renderer.

Part of the [agent-cli-toolkit](../README.md).

## Setup

```bash
# Build native image
./mvnw package -Dnative -DskipTests

# Copy to PATH
sudo cp target/fetch-cli-1.0.0-SNAPSHOT-runner /usr/local/bin/fetch

# Optional: JVM run (no native build)
java -jar target/quarkus-app/quarkus-run.jar --help
```

## Usage

```bash
fetch [options] <url>
```

### Options

| Option | Default | Description |
|--------|---------|-------------|
| `--format` | `markdown` | Output format: `markdown` or `text` |
| `--engine` | `auto` | Extraction engine: `auto`, `quality`, or `fast` |
| `--json` | — | Output as JSON envelope |
| `--timeout-ms` | `10000` | HTTP timeout in milliseconds |
| `--max-bytes` | `2000000` | Max response body bytes (2MB) |
| `-o FILE` | — | Write output to file |
| `-q, --quiet` | — | Quiet mode — suppress success output |
| `-h, --help` | — | Show help |
| `-V, --version` | — | Print version |

### Examples

```bash
# Fetch as markdown (default)
fetch https://example.com

# Fetch as plain text
fetch --format text https://example.com

# JSON envelope output
fetch --json https://example.com

# Use fast extraction engine
fetch --engine fast https://example.com

# Save to file
fetch -o output.md https://example.com

# With custom timeout and size limit
fetch --timeout-ms 5000 --max-bytes 500000 https://example.com
```

### JSON Output Format

```json
{
  "url": "https://example.com",
  "final_url": "https://example.com",
  "status": 200,
  "title": "Example Domain",
  "content": "# Example Domain\n\n...",
  "bytes": 168,
  "truncated": false
}
```

Error response:
```json
{"error": "HTTP 404 for https://example.com/missing"}
```

### Extraction Engines

- **quality** — Readability-style: finds `<article>`, `<main>`, or scores candidate elements. Strips nav, footer, sidebar, ads. Best for articles and documentation.
- **fast** — Strips noise tags, renders full `<body>`. Good when quality extraction over-trims.
- **auto** (default) — Uses quality first, falls back to fast if result is too short (<50 chars).

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Runtime error (HTTP error, network failure) |
| 2 | Validation error (bad URL, bad options) |

## Agent Integration

Add to your `CLAUDE.md` or agent system prompt:

```markdown
## Web Fetching
Use `fetch` CLI to read web pages as clean text:
- `fetch <url>` — returns markdown (default)
- `fetch --format text <url>` — returns plain text
- `fetch --json <url>` — structured JSON envelope with url, title, content, bytes
- `fetch --engine fast <url>` — full body rendering (when quality over-trims)
- `fetch -o file.md <url>` — save to file
- `fetch --max-bytes 500000 <url>` — limit response size
```

## Build

Requires Java 21+ and Maven.

```bash
# Run tests
./mvnw test

# Build JVM jar
./mvnw package -DskipTests

# Build native image (requires Docker for Mandrel builder)
./mvnw package -Dnative -DskipTests
```

The native binary is at `target/fetch-cli-1.0.0-SNAPSHOT-runner` (~40MB).

## Stack

- Quarkus 3.32.1 + Picocli
- Jsoup 1.18.3 (HTML parsing)
- Java 21 HttpClient (HTTP, gzip, redirects)
- Custom MarkdownRenderer (tree-walking Jsoup → markdown)

## License

MIT
