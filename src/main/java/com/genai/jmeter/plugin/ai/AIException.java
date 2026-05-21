package com.genai.jmeter.plugin.ai;

public class AIException extends RuntimeException {

    private final String provider;
    private final int httpStatus;

    public AIException(String provider, String message) {
        super(message);
        this.provider = provider;
        this.httpStatus = -1;
    }

    public AIException(String provider, int httpStatus, String message) {
        super(message);
        this.provider = provider;
        this.httpStatus = httpStatus;
    }

    public AIException(String provider, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.httpStatus = -1;
    }

    public String getProvider() { return provider; }
    public int getHttpStatus() { return httpStatus; }

    @Override
    public String toString() {
        return String.format("AIException[%s, http=%d]: %s", provider, httpStatus, getMessage());
    }
}
