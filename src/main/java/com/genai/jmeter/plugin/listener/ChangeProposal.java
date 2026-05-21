package com.genai.jmeter.plugin.listener;

/**
 * Mutable description of a proposed change to the JMX tree.
 * Built by Auto-Correlate / AI Chat, edited by the user via ProposalPreviewDialog,
 * then applied via LiveJMXModifier.
 */
public class ChangeProposal {

    public enum Action {
        ADD_REGEX,
        ADD_JSONPATH,
        ADD_BOUNDARY,
        ADD_JSR223_PRE,
        ADD_JSR223_POST,
        ADD_TIMER,
        ADD_ASSERTION,
        SUBSTITUTE,
        EXPLAIN
    }

    public Action action;
    public String targetSampler;       // sampler name (null = global)
    public String variableName;        // for extractors
    public String expression;          // regex / jsonpath / etc.
    public String leftBoundary;
    public String rightBoundary;
    public String template = "$1$";    // regex template (multi-group: $1$$2$$3$)
    public int matchNumber = 1;
    public String script;              // JSR223
    public String language = "groovy";
    public long delayMs = 1000L;
    public String assertionContains;
    public String originalValue;       // for substitute
    public String reasoning;
    public String explanation;

    // UI state
    public boolean selected = true;
    public boolean isDuplicate;        // already present in tree
    public String duplicateOf;         // existing element name

    public ChangeProposal() {}

    public ChangeProposal(Action a, String sampler) {
        this.action = a;
        this.targetSampler = sampler;
    }

    public String displayAction() {
        return switch (action) {
            case ADD_REGEX -> "Regex Extractor";
            case ADD_JSONPATH -> "JSONPath Extractor";
            case ADD_BOUNDARY -> "Boundary Extractor";
            case ADD_JSR223_PRE -> "JSR223 PreProcessor";
            case ADD_JSR223_POST -> "JSR223 PostProcessor";
            case ADD_TIMER -> "Constant Timer";
            case ADD_ASSERTION -> "Response Assertion";
            case SUBSTITUTE -> "Substitute Value";
            case EXPLAIN -> "Info Only";
        };
    }

    public String displayDetail() {
        return switch (action) {
            case ADD_REGEX -> "${" + nullSafe(variableName) + "} = " + truncate(expression, 60);
            case ADD_JSONPATH -> "${" + nullSafe(variableName) + "} = " + truncate(expression, 60);
            case ADD_BOUNDARY -> "${" + nullSafe(variableName) + "} between [" + truncate(leftBoundary, 20) + "] and [" + truncate(rightBoundary, 20) + "]";
            case ADD_JSR223_PRE, ADD_JSR223_POST -> "Script (" + language + "): " + truncate(script, 50);
            case ADD_TIMER -> delayMs + " ms";
            case ADD_ASSERTION -> "Contains: " + truncate(assertionContains, 50);
            case SUBSTITUTE -> "Replace '" + truncate(originalValue, 30) + "' with ${" + nullSafe(variableName) + "}";
            case EXPLAIN -> truncate(explanation, 80);
        };
    }

    public LiveJMXModifier.ExtractorKind kind() {
        return switch (action) {
            case ADD_REGEX -> LiveJMXModifier.ExtractorKind.REGEX;
            case ADD_JSONPATH -> LiveJMXModifier.ExtractorKind.JSON_PATH;
            case ADD_BOUNDARY -> LiveJMXModifier.ExtractorKind.BOUNDARY;
            case ADD_JSR223_PRE -> LiveJMXModifier.ExtractorKind.JSR223_PRE;
            case ADD_JSR223_POST -> LiveJMXModifier.ExtractorKind.JSR223_POST;
            case ADD_TIMER -> LiveJMXModifier.ExtractorKind.TIMER;
            case ADD_ASSERTION -> LiveJMXModifier.ExtractorKind.ASSERTION;
            default -> null;
        };
    }

    public void checkDuplicate() {
        LiveJMXModifier.ExtractorKind k = kind();
        if (k == null || targetSampler == null) { isDuplicate = false; return; }
        String existing = LiveJMXModifier.existingExtractorName(targetSampler, variableName, k);
        if (existing != null) {
            isDuplicate = true;
            duplicateOf = existing;
            selected = false;  // default skip duplicates
        }
    }

    /** Apply this proposal via LiveJMXModifier. Returns true on success. */
    public boolean apply() {
        return switch (action) {
            case ADD_REGEX -> LiveJMXModifier.addRegexExtractor(
                    targetSampler, variableName, expression, "NOT_FOUND", matchNumber, template);
            case ADD_JSONPATH -> LiveJMXModifier.addJsonPathExtractor(targetSampler, variableName, expression);
            case ADD_BOUNDARY -> LiveJMXModifier.addBoundaryExtractor(
                    targetSampler, variableName, leftBoundary, rightBoundary);
            case ADD_JSR223_PRE -> LiveJMXModifier.addJsr223PreProcessor(
                    targetSampler, variableName != null ? variableName : "JSR223 PreProcessor", script, language);
            case ADD_JSR223_POST -> LiveJMXModifier.addJsr223PostProcessor(
                    targetSampler, variableName != null ? variableName : "JSR223 PostProcessor", script, language);
            case ADD_TIMER -> LiveJMXModifier.addConstantTimer(targetSampler, delayMs);
            case ADD_ASSERTION -> LiveJMXModifier.addResponseAssertion(targetSampler, assertionContains, 0);
            case SUBSTITUTE -> LiveJMXModifier.substituteValueWithVariable(originalValue, variableName) > 0;
            case EXPLAIN -> true;
        };
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        s = s.replace("\n", " ").replace("\r", "");
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
