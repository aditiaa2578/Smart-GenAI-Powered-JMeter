package com.genai.jmeter.plugin.scanner;

/**
 * Represents a single dynamic value found during the deterministic scan phase.
 * Tracks where the value originates (extraction point) and where it is consumed (injection points).
 */
public class DynamicValue {

    public enum ValueType {
        SESSION_TOKEN, JWT, UUID, CSRF_TOKEN, AUTH_HEADER, FORM_FIELD,
        QUERY_PARAM, COOKIE, TIMESTAMP, DYNAMIC_ID, BASE64, UNKNOWN
    }

    private final String value;
    private final String sourceEntryIndex;    // HAR entry index where value first appeared
    private final String sourceLocation;      // e.g., "response.header.Set-Cookie" or "response.body.$.token"
    private final ValueType valueType;
    private final String variableName;        // generated JMeter variable name

    // Where this value is used in subsequent requests
    private java.util.List<UsagePoint> usages = new java.util.ArrayList<>();

    public DynamicValue(String value, String sourceEntryIndex, String sourceLocation, ValueType valueType) {
        this.value = value;
        this.sourceEntryIndex = sourceEntryIndex;
        this.sourceLocation = sourceLocation;
        this.valueType = valueType;
        this.variableName = generateVariableName(sourceLocation, valueType);
    }

    private String generateVariableName(String location, ValueType type) {
        String base = location
                .replaceAll("response\\.(body|header)\\.", "")
                .replaceAll("\\$\\.", "")
                .replaceAll("[^a-zA-Z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (base.isEmpty()) base = type.name().toLowerCase();
        return base.length() > 40 ? base.substring(0, 40) : base;
    }

    public String getValue() { return value; }
    public String getSourceEntryIndex() { return sourceEntryIndex; }
    public String getSourceLocation() { return sourceLocation; }
    public ValueType getValueType() { return valueType; }
    public String getVariableName() { return variableName; }
    public java.util.List<UsagePoint> getUsages() { return usages; }
    public void addUsage(UsagePoint usage) { usages.add(usage); }
    public boolean isUsed() { return !usages.isEmpty(); }

    @Override
    public String toString() {
        return String.format("[%s] %s → %s (%d usages)", valueType, variableName,
                value.length() > 20 ? value.substring(0, 20) + "..." : value, usages.size());
    }

    /**
     * Where a dynamic value is consumed in a subsequent request.
     */
    public static class UsagePoint {
        private final String entryIndex;
        private final String location;   // e.g., "request.header.Authorization" or "request.body"
        private final String context;    // surrounding text for regex extraction

        public UsagePoint(String entryIndex, String location, String context) {
            this.entryIndex = entryIndex;
            this.location = location;
            this.context = context;
        }

        public String getEntryIndex() { return entryIndex; }
        public String getLocation() { return location; }
        public String getContext() { return context; }
    }
}
