package com.genai.jmeter.plugin.assess;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured quality report produced by SCOUT.
 */
public class QualityReport {

    public enum Grade { A, B, C, D, F }

    private final double overallScore;
    private final Grade overallGrade;
    private final int totalRequests;
    private final int correlatedRequests;
    private final int uncorrelatedDynamicValues;
    private final boolean productionReady;
    private final List<RiskFlag> riskFlags;
    private final List<Recommendation> recommendations;
    private final List<RequestCoverage> requestCoverages;

    public QualityReport(Builder b) {
        this.overallScore = b.overallScore;
        this.overallGrade = scoreToGrade(b.overallScore);
        this.totalRequests = b.totalRequests;
        this.correlatedRequests = b.correlatedRequests;
        this.uncorrelatedDynamicValues = b.uncorrelatedDynamicValues;
        this.productionReady = b.overallScore >= 0.85;
        this.riskFlags = b.riskFlags;
        this.recommendations = b.recommendations;
        this.requestCoverages = b.requestCoverages;
    }

    private Grade scoreToGrade(double score) {
        if (score >= 0.90) return Grade.A;
        if (score >= 0.80) return Grade.B;
        if (score >= 0.65) return Grade.C;
        if (score >= 0.50) return Grade.D;
        return Grade.F;
    }

    public double getOverallScore() { return overallScore; }
    public Grade getOverallGrade() { return overallGrade; }
    public int getTotalRequests() { return totalRequests; }
    public int getCorrelatedRequests() { return correlatedRequests; }
    public int getUncorrelatedDynamicValues() { return uncorrelatedDynamicValues; }
    public boolean isProductionReady() { return productionReady; }
    public List<RiskFlag> getRiskFlags() { return riskFlags; }
    public List<Recommendation> getRecommendations() { return recommendations; }
    public List<RequestCoverage> getRequestCoverages() { return requestCoverages; }

    public String buildTextReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════╗\n");
        sb.append("║     SCOUT QUALITY ASSESSMENT REPORT  ║\n");
        sb.append("╚══════════════════════════════════════╝\n\n");

        sb.append(String.format("Overall Score:    %.0f%%  (Grade: %s)\n", overallScore * 100, overallGrade));
        sb.append(String.format("Production Ready: %s\n", productionReady ? "✓ YES" : "✗ NO"));
        sb.append(String.format("Requests:         %d total, %d correlated\n", totalRequests, correlatedRequests));
        sb.append(String.format("Uncorrelated:     %d dynamic values remain\n\n", uncorrelatedDynamicValues));

        if (!riskFlags.isEmpty()) {
            sb.append("RISK FLAGS:\n");
            for (RiskFlag flag : riskFlags) {
                sb.append(String.format("  [%s] %s — %s\n", flag.severity(), flag.title(), flag.description()));
            }
            sb.append("\n");
        }

        if (!recommendations.isEmpty()) {
            sb.append("RECOMMENDATIONS:\n");
            for (Recommendation rec : recommendations) {
                sb.append(String.format("  • %s\n    → %s\n", rec.title(), rec.action()));
            }
            sb.append("\n");
        }

        if (!requestCoverages.isEmpty()) {
            sb.append("REQUEST COVERAGE:\n");
            for (RequestCoverage rc : requestCoverages) {
                sb.append(String.format("  Entry %s: %s — %.0f%%\n", rc.entryIndex(), rc.url(), rc.coverageScore() * 100));
            }
        }
        return sb.toString();
    }

    public record RiskFlag(String severity, String title, String description, String entryIndex) {}
    public record Recommendation(String title, String action, String entryIndex) {}
    public record RequestCoverage(String entryIndex, String url, double coverageScore, int issueCount) {}

    public static class Builder {
        double overallScore;
        int totalRequests, correlatedRequests, uncorrelatedDynamicValues;
        List<RiskFlag> riskFlags = new ArrayList<>();
        List<Recommendation> recommendations = new ArrayList<>();
        List<RequestCoverage> requestCoverages = new ArrayList<>();

        public Builder overallScore(double v) { overallScore = v; return this; }
        public Builder totalRequests(int v) { totalRequests = v; return this; }
        public Builder correlatedRequests(int v) { correlatedRequests = v; return this; }
        public Builder uncorrelatedDynamicValues(int v) { uncorrelatedDynamicValues = v; return this; }
        public Builder addRiskFlag(RiskFlag f) { riskFlags.add(f); return this; }
        public Builder addRecommendation(Recommendation r) { recommendations.add(r); return this; }
        public Builder addRequestCoverage(RequestCoverage rc) { requestCoverages.add(rc); return this; }
        public QualityReport build() { return new QualityReport(this); }
    }
}
