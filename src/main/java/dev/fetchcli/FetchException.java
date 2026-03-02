package dev.fetchcli;

public class FetchException extends RuntimeException {

    private final int exitCode;

    public FetchException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public FetchException(String message, int exitCode, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }
}
