package com.genai.jmeter.plugin.scanner;

import com.genai.jmeter.plugin.har.HARModel;

import java.util.*;

/**
 * Structured map of how dynamic data flows through the application.
 * The output of Phase 2 (Scan) that feeds into Phase 3 (Correlate).
 */
public class WorldView {

    private final List<HARModel.Entry> entries;
    private final List<DynamicValue> dynamicValues = new ArrayList<>();
    private final Map<String, String> detectedFrameworks = new LinkedHashMap<>();
    private final Map<String, String> tokenFormats = new LinkedHashMap<>();
    private final String baseDomain;
    private long scanDurationMs;

    public WorldView(List<HARModel.Entry> entries, String baseDomain) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.baseDomain = baseDomain;
    }

    public void addDynamicValue(DynamicValue dv) { dynamicValues.add(dv); }
    public void addFramework(String name, String evidence) { detectedFrameworks.put(name, evidence); }
    public void addTokenFormat(String value, String format) { tokenFormats.put(value, format); }
    public void setScanDurationMs(long ms) { scanDurationMs = ms; }

    public List<HARModel.Entry> getEntries() { return entries; }
    public List<DynamicValue> getDynamicValues() { return Collections.unmodifiableList(dynamicValues); }
    public Map<String, String> getDetectedFrameworks() { return Collections.unmodifiableMap(detectedFrameworks); }
    public Map<String, String> getTokenFormats() { return Collections.unmodifiableMap(tokenFormats); }
    public String getBaseDomain() { return baseDomain; }
    public long getScanDurationMs() { return scanDurationMs; }

    /** Only values that actually appear in subsequent requests */
    public List<DynamicValue> getCorrelationCandidates() {
        List<DynamicValue> candidates = new ArrayList<>();
        for (DynamicValue dv : dynamicValues) {
            if (dv.isUsed()) candidates.add(dv);
        }
        return candidates;
    }

    public String buildSummaryReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== WORLD VIEW SCAN REPORT ===\n");
        sb.append("Base Domain: ").append(baseDomain).append("\n");
        sb.append("Total Entries: ").append(entries.size()).append("\n");
        sb.append("Dynamic Values Found: ").append(dynamicValues.size()).append("\n");
        sb.append("Correlation Candidates: ").append(getCorrelationCandidates().size()).append("\n");
        sb.append("Scan Duration: ").append(scanDurationMs).append("ms\n\n");

        if (!detectedFrameworks.isEmpty()) {
            sb.append("Detected Frameworks:\n");
            detectedFrameworks.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }

        if (!tokenFormats.isEmpty()) {
            sb.append("Token Format Signatures:\n");
            tokenFormats.forEach((k, v) -> sb.append("  ").append(v).append(": ")
                    .append(k.length() > 30 ? k.substring(0, 30) + "..." : k).append("\n"));
            sb.append("\n");
        }

        sb.append("Correlation Candidates:\n");
        for (DynamicValue dv : getCorrelationCandidates()) {
            sb.append("  ").append(dv).append("\n");
            for (DynamicValue.UsagePoint up : dv.getUsages()) {
                sb.append("    → Entry ").append(up.getEntryIndex())
                        .append(" @ ").append(up.getLocation()).append("\n");
            }
        }
        return sb.toString();
    }
}
