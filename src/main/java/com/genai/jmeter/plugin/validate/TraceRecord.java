package com.genai.jmeter.plugin.validate;

import java.time.Instant;

/**
 * A single event in the TRACE chronological execution log.
 */
public class TraceRecord {

    public enum Level { INFO, SUCCESS, WARNING, ERROR }
    public enum EventType {
        EXTRACTOR_FIRED, EXTRACTOR_FAILED, SUBSTITUTION_APPLIED,
        REQUEST_SENT, RESPONSE_RECEIVED, VALIDATION_PASSED, VALIDATION_FAILED,
        SESSION_STARTED, SESSION_ENDED
    }

    private final Instant timestamp;
    private final Level level;
    private final EventType eventType;
    private final String entryIndex;
    private final String message;
    private final String detail;
    private final String variableName;
    private final String capturedValue;

    public TraceRecord(Level level, EventType eventType, String entryIndex,
            String message, String detail, String variableName, String capturedValue) {
        this.timestamp = Instant.now();
        this.level = level;
        this.eventType = eventType;
        this.entryIndex = entryIndex;
        this.message = message;
        this.detail = detail;
        this.variableName = variableName;
        this.capturedValue = capturedValue;
    }

    public static TraceRecord extractorFired(String entryIndex, String varName, String value) {
        return new TraceRecord(Level.SUCCESS, EventType.EXTRACTOR_FIRED, entryIndex,
                "Extractor fired: " + varName + " = " + truncate(value),
                "Captured value length: " + (value != null ? value.length() : 0), varName, value);
    }

    public static TraceRecord extractorFailed(String entryIndex, String varName, String reason) {
        return new TraceRecord(Level.ERROR, EventType.EXTRACTOR_FAILED, entryIndex,
                "Extractor failed: " + varName, reason, varName, null);
    }

    public static TraceRecord substitutionApplied(String entryIndex, String varName, String location) {
        return new TraceRecord(Level.INFO, EventType.SUBSTITUTION_APPLIED, entryIndex,
                "Substitution applied: ${" + varName + "} in " + location, null, varName, null);
    }

    public static TraceRecord validationPassed(String entryIndex, int statusCode) {
        return new TraceRecord(Level.SUCCESS, EventType.VALIDATION_PASSED, entryIndex,
                "Request validated — HTTP " + statusCode, null, null, null);
    }

    public static TraceRecord validationFailed(String entryIndex, int statusCode, String reason) {
        return new TraceRecord(Level.ERROR, EventType.VALIDATION_FAILED, entryIndex,
                "Validation failed — HTTP " + statusCode, reason, null, null);
    }

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 40 ? s.substring(0, 40) + "..." : s;
    }

    public Instant getTimestamp() { return timestamp; }
    public Level getLevel() { return level; }
    public EventType getEventType() { return eventType; }
    public String getEntryIndex() { return entryIndex; }
    public String getMessage() { return message; }
    public String getDetail() { return detail; }
    public String getVariableName() { return variableName; }
    public String getCapturedValue() { return capturedValue; }

    @Override
    public String toString() {
        return String.format("[%s] [%s] Entry %s: %s%s",
                level, eventType, entryIndex, message,
                detail != null ? " — " + detail : "");
    }
}
