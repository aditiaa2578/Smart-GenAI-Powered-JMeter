package com.genai.jmeter.plugin.listener;

import org.apache.jmeter.samplers.SampleResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a JMeter SampleResult with correlation metadata for display and analysis.
 */
public class ResultEntry {

    private final int index;
    private final SampleResult result;
    private final List<ExtractorHint> extractorHints = new ArrayList<>();
    private boolean correlated = false;

    public ResultEntry(int index, SampleResult result) {
        this.index = index;
        this.result = result;
    }

    public int getIndex() { return index; }
    public SampleResult getResult() { return result; }
    public String getSamplerName() { return result.getSampleLabel(); }
    public int getStatusCode() { return result.getResponseCode().isEmpty() ? 0 : parseCode(result.getResponseCode()); }
    public long getElapsedTime() { return result.getTime(); }
    public boolean isSuccess() { return result.isSuccessful(); }

    public String getRequestHeaders() {
        return result.getRequestHeaders() != null ? result.getRequestHeaders() : "";
    }
    public String getRequestBody() {
        return result.getSamplerData() != null ? result.getSamplerData() : "";
    }
    public String getResponseHeaders() {
        return result.getResponseHeaders() != null ? result.getResponseHeaders() : "";
    }
    public String getResponseBody() {
        return result.getResponseDataAsString();
    }
    public String getUrl() {
        return result.getUrlAsString() != null ? result.getUrlAsString() : "";
    }
    public String getContentType() {
        return result.getContentType() != null ? result.getContentType() : "";
    }

    public List<ExtractorHint> getExtractorHints() { return extractorHints; }
    public void addExtractorHint(ExtractorHint hint) { extractorHints.add(hint); }
    public boolean isCorrelated() { return correlated; }
    public void setCorrelated(boolean c) { this.correlated = c; }

    private int parseCode(String code) {
        try { return Integer.parseInt(code.trim()); } catch (Exception e) { return 0; }
    }

    /**
     * A correlation/extractor suggestion for this entry.
     */
    public static class ExtractorHint {
        public enum Type { REGEX, JSON_PATH, BOUNDARY, GROOVY }

        private final String variableName;
        private final Type type;
        private final String expression;
        private final String reasoning;
        private boolean applied = false;

        public ExtractorHint(String variableName, Type type, String expression, String reasoning) {
            this.variableName = variableName;
            this.type = type;
            this.expression = expression;
            this.reasoning = reasoning;
        }

        public String getVariableName() { return variableName; }
        public Type getType() { return type; }
        public String getExpression() { return expression; }
        public String getReasoning() { return reasoning; }
        public boolean isApplied() { return applied; }
        public void setApplied(boolean a) { this.applied = a; }

        @Override
        public String toString() {
            return String.format("[%s] %s → %s", type, variableName, expression);
        }
    }
}
