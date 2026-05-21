package com.genai.jmeter.plugin.correlate;

import com.genai.jmeter.plugin.scanner.DynamicValue;

/**
 * AI agent's decision on how to correlate a specific dynamic value.
 */
public class CorrelationDecision {

    public enum ExtractorType {
        REGEX, JSON_PATH, XPATH, BOUNDARY, GROOVY, SKIP
    }

    public enum Confidence { HIGH, MEDIUM, LOW }

    private final DynamicValue dynamicValue;
    private final ExtractorType extractorType;
    private final String extractorExpression;   // the actual regex, jsonpath, etc.
    private final String variableName;
    private final String extractionEntryIndex;  // which response to extract from
    private final String reasoning;
    private final Confidence confidence;
    private boolean validated = false;

    public CorrelationDecision(DynamicValue dv, ExtractorType type, String expression,
            String variableName, String entryIndex, String reasoning, Confidence confidence) {
        this.dynamicValue = dv;
        this.extractorType = type;
        this.extractorExpression = expression;
        this.variableName = variableName;
        this.extractionEntryIndex = entryIndex;
        this.reasoning = reasoning;
        this.confidence = confidence;
    }

    public DynamicValue getDynamicValue() { return dynamicValue; }
    public ExtractorType getExtractorType() { return extractorType; }
    public String getExtractorExpression() { return extractorExpression; }
    public String getVariableName() { return variableName; }
    public String getExtractionEntryIndex() { return extractionEntryIndex; }
    public String getReasoning() { return reasoning; }
    public Confidence getConfidence() { return confidence; }
    public boolean isValidated() { return validated; }
    public void setValidated(boolean v) { this.validated = v; }

    public boolean shouldSkip() { return extractorType == ExtractorType.SKIP; }

    @Override
    public String toString() {
        return String.format("[%s] %s → %s(%s) [%s]",
                confidence, variableName, extractorType, extractorExpression, validated ? "VALIDATED" : "PENDING");
    }
}
