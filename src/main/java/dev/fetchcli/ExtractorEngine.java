package dev.fetchcli;

public interface ExtractorEngine {

    String extract(String html, OutputFormat format);
}
