package dev.fetchcli;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusMainTest
class FetchCommandTest {

    static TestHttpServer server;
    static String base;

    @BeforeAll
    static void startServer() throws IOException {
        server = new TestHttpServer();
        server.start();
        base = server.baseUrl();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    @Launch({"--help"})
    void testHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().contains("fetch"));
    }

    @Test
    @Launch({"--version"})
    void testVersion(LaunchResult result) {
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().contains("1.0.0"));
    }

    @Test
    void testInvalidUrl(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("not-a-url");
        assertEquals(2, result.exitCode());
    }

    @Test
    void testBadTimeout(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--timeout-ms", "-5", "https://example.com");
        assertEquals(2, result.exitCode());
    }

    @Test
    void testFetchArticleMarkdown(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch(base + "/article");
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("# Test Article Title"), "Should contain h1 as markdown heading");
        assertTrue(output.contains("## Section Two"), "Should contain h2 as markdown heading");
        assertTrue(output.contains("[link to example](https://example.com)"), "Should contain markdown link");
        assertTrue(output.contains("**bold text**"), "Should contain bold markdown");
    }

    @Test
    void testFetchArticleText(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--format", "text", base + "/article");
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Test Article Title"), "Should contain title text");
        assertFalse(output.contains("# "), "Should NOT contain markdown headings");
    }

    @Test
    void testJsonOutput(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--json", base + "/article");
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("\"url\""), "JSON should contain url field");
        assertTrue(output.contains("\"final_url\""), "JSON should contain final_url field");
        assertTrue(output.contains("\"status\":200"), "JSON should contain status 200");
        assertTrue(output.contains("\"title\":\"Test Article\""), "JSON should contain title");
        assertTrue(output.contains("\"content\""), "JSON should contain content field");
        assertTrue(output.contains("\"bytes\""), "JSON should contain bytes field");
        assertTrue(output.contains("\"truncated\":false"), "Should not be truncated");
    }

    @Test
    void testPlainTextPassthrough(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch(base + "/plain");
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("This is plain text content."), "Should pass through plain text");
        assertTrue(output.contains("Line two."), "Should contain all lines");
    }

    @Test
    void testRedirectFollowing(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--json", base + "/redirect");
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("\"status\":200"), "Should follow redirect to 200");
        assertTrue(output.contains("/article"), "Final URL should be /article");
    }

    @Test
    void test404Error(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch(base + "/not-found");
        assertEquals(1, result.exitCode());
        assertTrue(result.getErrorOutput().contains("Error: HTTP 404"), "Text errors should be on stderr");
    }

    @Test
    void testJsonErrorOnStdout(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--json", base + "/not-found");
        assertEquals(1, result.exitCode());
        String stdout = result.getOutput().trim();
        assertTrue(stdout.startsWith("{") && stdout.endsWith("}"), "JSON error must be emitted on stdout");
        assertTrue(stdout.contains("\"error\":\"HTTP 404"), "JSON error should contain HTTP status");
        assertTrue(result.getErrorOutput().isBlank(), "JSON error mode should not emit duplicate stderr output");
    }

    @Test
    void testMaxBytesTruncation(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--json", "--max-bytes", "200", base + "/article");
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("\"truncated\":true"), "Output should indicate truncation");
        Matcher bytesMatcher = Pattern.compile("\"bytes\":(\\d+)").matcher(output);
        assertTrue(bytesMatcher.find(), "Should contain bytes field");
        int bytes = Integer.parseInt(bytesMatcher.group(1));
        assertTrue(bytes <= 200, "Truncated payload should respect max-bytes");
    }

    @Test
    void testFileOutput(QuarkusMainLauncher launcher, @TempDir Path tempDir) throws IOException {
        Path outFile = tempDir.resolve("output.md");
        LaunchResult result = launcher.launch("-o", outFile.toString(), base + "/article");
        assertEquals(0, result.exitCode());
        assertTrue(Files.exists(outFile), "Output file should exist");
        String content = Files.readString(outFile);
        assertTrue(content.contains("Test Article Title"), "File should contain article content");
    }

    @Test
    void testQuietMode(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("-q", base + "/article");
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().isBlank(), "Quiet mode should produce no output");
    }

    @Test
    void testQuietWithFileOutput(QuarkusMainLauncher launcher, @TempDir Path tempDir) throws IOException {
        Path outFile = tempDir.resolve("quiet.md");
        LaunchResult result = launcher.launch("--quiet", "-o", outFile.toString(), base + "/article");
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().isBlank(), "Quiet mode should suppress stdout");
        assertTrue(Files.exists(outFile), "Quiet mode should still write output files");
        assertTrue(Files.readString(outFile).contains("Test Article Title"), "Written file should contain content");
    }

    @Test
    void testQuietStillShowsErrors(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--quiet", "not-a-url");
        assertEquals(2, result.exitCode());
        assertTrue(result.getErrorOutput().contains("Error: URL must start with http:// or https://"));
    }

    @Test
    void testEngineFast(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--engine", "fast", base + "/article");
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Test Article Title"), "Fast engine should extract content");
    }

    @Test
    void testEngineQuality(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--engine", "quality", base + "/article");
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Test Article Title"), "Quality engine should extract content");
        // Quality engine should strip nav/footer
        assertFalse(output.contains("Home"), "Quality engine should strip nav");
        assertFalse(output.contains("Copyright"), "Quality engine should strip footer");
    }

    @Test
    void testFormattedPage(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch(base + "/formatted");
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("```"), "Should contain code fences");
        assertTrue(output.contains("- First item"), "Should contain list items");
        assertTrue(output.contains("|"), "Should contain table pipes");
        assertTrue(output.contains(">"), "Should contain blockquote");
    }

    @Test
    void testLatin1Charset(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch(base + "/latin1");
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Café"), "Should decode Latin-1 correctly");
        assertTrue(output.contains("brûlée"), "Should decode Latin-1 diacritics");
    }

    @Test
    void testHtmlDetectionIsCaseInsensitive(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch(base + "/html-mixed-case");
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().contains("# Mixed Case Heading"), "Mixed-case text/html should be treated as HTML");
    }

    @Test
    void testCombinedContentEncodingHeader(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch(base + "/gzip-combined");
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().contains("Compressed text payload."), "Combined encoding token should still decode");
    }
}
