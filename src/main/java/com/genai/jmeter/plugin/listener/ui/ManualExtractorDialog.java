package com.genai.jmeter.plugin.listener.ui;

import com.genai.jmeter.plugin.ai.AIProvider;
import com.genai.jmeter.plugin.ai.AIProviderFactory;
import com.genai.jmeter.plugin.ai.AIResponse;
import com.genai.jmeter.plugin.listener.LiveJMXModifier;
import com.genai.jmeter.plugin.listener.ResultEntry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Smart extractor dialog with explicit left boundary + value + right boundary.
 * Auto-detects bounds from response, optionally asks AI to refine.
 * Applies the extractor LIVE to the loaded JMeter tree under the matching sampler.
 */
public class ManualExtractorDialog extends JDialog {

    private final String selectedText;
    private final String fullResponseBody;
    private final String samplerName;
    private Consumer<ResultEntry.ExtractorHint> applyCallback;

    private JTextField varNameField;
    private JComboBox<String> typeCombo;
    private JTextField leftBoundField;
    private JTextField valueField;
    private JTextField rightBoundField;
    private JTextArea expressionArea;
    private JLabel previewLabel;
    private JTextArea contextPreview;
    private JCheckBox applyToTreeCheck;
    private JCheckBox substituteCheck;
    private JButton aiSuggestBtn;
    private JLabel aiStatusLabel;

    public ManualExtractorDialog(Frame parent, String selectedText, String fullResponseBody, String samplerName) {
        super(parent, "Smart Extractor — " + (samplerName != null ? samplerName : ""), true);
        this.selectedText = selectedText != null ? selectedText.trim() : "";
        this.fullResponseBody = fullResponseBody != null ? fullResponseBody : "";
        this.samplerName = samplerName;
        setSize(740, 640);
        setLocationRelativeTo(parent);
        build();
        autoFillBoundaries();
        rebuildExpression();
    }

    private void build() {
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── Top: selected text & sampler ───────────────────────────────────────
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Selected from response"));

        JTextField selectedField = new JTextField(selectedText);
        selectedField.setEditable(false);
        selectedField.setBackground(new Color(255, 255, 200));
        selectedField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        topPanel.add(selectedField, BorderLayout.CENTER);

        JLabel samplerLabel = new JLabel(" Sampler: " + (samplerName != null ? samplerName : "(unknown)"));
        samplerLabel.setForeground(new Color(80, 80, 120));
        samplerLabel.setFont(samplerLabel.getFont().deriveFont(Font.ITALIC, 11f));
        topPanel.add(samplerLabel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        // ── Center: form ───────────────────────────────────────────────────────
        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Variable name
        g.gridx = 0; g.gridy = row; g.weightx = 0;
        centerPanel.add(new JLabel("Variable Name:"), g);
        varNameField = new JTextField(suggestVarName(selectedText), 30);
        g.gridx = 1; g.weightx = 1; g.gridwidth = 2;
        centerPanel.add(varNameField, g);
        g.gridwidth = 1;
        row++;

        // Extractor type
        g.gridx = 0; g.gridy = row; g.weightx = 0;
        centerPanel.add(new JLabel("Extractor Type:"), g);
        typeCombo = new JComboBox<>(new String[]{"Regex Extractor", "Boundary Extractor", "JSONPath Extractor"});
        typeCombo.addActionListener(e -> {
            autoFillBoundaries();
            rebuildExpression();
        });
        g.gridx = 1; g.weightx = 1; g.gridwidth = 2;
        centerPanel.add(typeCombo, g);
        g.gridwidth = 1;
        row++;

        // ── Boundaries panel (the heart of smart regex) ───────────────────────
        JPanel boundsPanel = new JPanel(new GridBagLayout());
        boundsPanel.setBorder(BorderFactory.createTitledBorder("Boundaries (used for Regex / Boundary extractor)"));
        GridBagConstraints b = new GridBagConstraints();
        b.insets = new Insets(3, 5, 3, 5);
        b.fill = GridBagConstraints.HORIZONTAL;

        b.gridx = 0; b.gridy = 0; b.weightx = 0;
        boundsPanel.add(new JLabel("Left:"), b);
        leftBoundField = new JTextField();
        leftBoundField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        leftBoundField.setToolTipText("Text that appears IMMEDIATELY BEFORE the value in the response");
        b.gridx = 1; b.weightx = 1;
        boundsPanel.add(leftBoundField, b);

        b.gridx = 0; b.gridy = 1; b.weightx = 0;
        boundsPanel.add(new JLabel("Value:"), b);
        valueField = new JTextField(selectedText);
        valueField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        valueField.setBackground(new Color(255, 255, 220));
        valueField.setToolTipText("The dynamic value to capture (pre-filled from your selection)");
        b.gridx = 1; b.weightx = 1;
        boundsPanel.add(valueField, b);

        b.gridx = 0; b.gridy = 2; b.weightx = 0;
        boundsPanel.add(new JLabel("Right:"), b);
        rightBoundField = new JTextField();
        rightBoundField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        rightBoundField.setToolTipText("Text that appears IMMEDIATELY AFTER the value in the response");
        b.gridx = 1; b.weightx = 1;
        boundsPanel.add(rightBoundField, b);

        // Auto-detect / AI buttons
        JPanel boundsBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton autoBtn = new JButton("🔍 Auto-Detect");
        autoBtn.setToolTipText("Detect left/right from response text around the value");
        autoBtn.addActionListener(e -> { autoFillBoundaries(); rebuildExpression(); });
        boundsBtnPanel.add(autoBtn);

        aiSuggestBtn = Theme.coloredButton("🤖 AI Suggest", new Color(100, 50, 150));
        aiSuggestBtn.setToolTipText("Ask the AI to pick the smartest boundaries / regex");
        aiSuggestBtn.addActionListener(e -> aiSuggest());
        boundsBtnPanel.add(aiSuggestBtn);

        aiStatusLabel = new JLabel("");
        aiStatusLabel.setFont(aiStatusLabel.getFont().deriveFont(Font.ITALIC, 10f));
        aiStatusLabel.setForeground(Color.GRAY);
        boundsBtnPanel.add(aiStatusLabel);

        b.gridx = 0; b.gridy = 3; b.gridwidth = 2; b.weightx = 1;
        boundsPanel.add(boundsBtnPanel, b);
        b.gridwidth = 1;

        // Hook bound fields to rebuild expression
        java.awt.event.KeyAdapter rebuild = new java.awt.event.KeyAdapter() {
            @Override public void keyReleased(java.awt.event.KeyEvent e) { rebuildExpression(); }
        };
        leftBoundField.addKeyListener(rebuild);
        valueField.addKeyListener(rebuild);
        rightBoundField.addKeyListener(rebuild);

        g.gridx = 0; g.gridy = row; g.gridwidth = 3; g.weightx = 1;
        centerPanel.add(boundsPanel, g);
        g.gridwidth = 1;
        row++;

        // Expression (read-only display, edited via bounds)
        g.gridx = 0; g.gridy = row; g.weightx = 0;
        centerPanel.add(new JLabel("Expression:"), g);
        expressionArea = new JTextArea(2, 40);
        expressionArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        expressionArea.setLineWrap(true);
        g.gridx = 1; g.weightx = 1;
        centerPanel.add(new JScrollPane(expressionArea), g);

        JButton testBtn = new JButton("Test");
        testBtn.addActionListener(e -> testExpression());
        g.gridx = 2; g.weightx = 0;
        centerPanel.add(testBtn, g);
        row++;

        // Preview result
        previewLabel = new JLabel(" ");
        previewLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        previewLabel.setForeground(new Color(0, 128, 0));
        g.gridx = 1; g.gridy = row; g.gridwidth = 2; g.weightx = 1;
        centerPanel.add(previewLabel, g);
        g.gridwidth = 1;
        row++;

        // Context preview
        g.gridx = 0; g.gridy = row; g.weightx = 0;
        centerPanel.add(new JLabel("Context:"), g);
        contextPreview = new JTextArea(3, 40);
        contextPreview.setEditable(false);
        contextPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        contextPreview.setBackground(new Color(248, 248, 248));
        contextPreview.setLineWrap(true);
        contextPreview.setWrapStyleWord(false);
        g.gridx = 1; g.weightx = 1; g.gridwidth = 2;
        centerPanel.add(new JScrollPane(contextPreview), g);
        g.gridwidth = 1;
        row++;

        // Apply options
        applyToTreeCheck = new JCheckBox("Add this extractor LIVE to JMX tree under the sampler", true);
        applyToTreeCheck.setFont(applyToTreeCheck.getFont().deriveFont(Font.BOLD));
        applyToTreeCheck.setForeground(new Color(0, 100, 0));
        g.gridx = 0; g.gridy = row; g.gridwidth = 3; g.weightx = 1;
        centerPanel.add(applyToTreeCheck, g);
        g.gridwidth = 1;
        row++;

        substituteCheck = new JCheckBox("Replace hard-coded value in subsequent samplers with ${" + suggestVarName(selectedText) + "}", true);
        substituteCheck.setForeground(new Color(120, 60, 0));
        varNameField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyReleased(java.awt.event.KeyEvent e) {
                substituteCheck.setText("Replace hard-coded value in subsequent samplers with ${" + varNameField.getText() + "}");
            }
        });
        g.gridx = 0; g.gridy = row; g.gridwidth = 3; g.weightx = 1;
        centerPanel.add(substituteCheck, g);
        g.gridwidth = 1;

        add(new JScrollPane(centerPanel), BorderLayout.CENTER);

        // ── Buttons ───────────────────────────────────────────────────────────
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        JButton applyBtn = Theme.coloredButton("Apply Extractor", new Color(0, 120, 60));
        applyBtn.setFont(applyBtn.getFont().deriveFont(Font.BOLD));
        applyBtn.addActionListener(e -> applyExtractor());

        btnPanel.add(cancelBtn);
        btnPanel.add(applyBtn);
        add(btnPanel, BorderLayout.SOUTH);
    }

    // ── Boundary auto-detection ───────────────────────────────────────────────

    private void autoFillBoundaries() {
        if (selectedText.isEmpty() || fullResponseBody.isEmpty()) return;

        int pos = fullResponseBody.indexOf(selectedText);
        if (pos < 0) return;

        // Use 24 chars left / 24 chars right for context preview
        int ctxStart = Math.max(0, pos - 24);
        int ctxEnd = Math.min(fullResponseBody.length(), pos + selectedText.length() + 24);
        String context = fullResponseBody.substring(ctxStart, ctxEnd)
                .replace("\n", "\\n").replace("\r", "");
        if (contextPreview != null) contextPreview.setText("..." + context + "...");

        // Smart boundary detection: walk backwards/forwards for stable anchor
        String left = pickLeftBoundary(fullResponseBody, pos);
        String right = pickRightBoundary(fullResponseBody, pos + selectedText.length());

        if (leftBoundField != null) leftBoundField.setText(left);
        if (rightBoundField != null) rightBoundField.setText(right);
        if (valueField != null && valueField.getText().isEmpty()) valueField.setText(selectedText);
    }

    /**
     * Pick a "good" left boundary — usually a quote, colon, equals, or a short keyword.
     * Walks backwards looking for a stable anchor.
     */
    private String pickLeftBoundary(String body, int valueStart) {
        if (valueStart <= 0) return "";
        // Common JSON pattern: "key":"value"  →  look for "name":"
        // Find nearest opening quote/equals/colon up to ~30 chars back
        int lookback = Math.min(30, valueStart);
        String window = body.substring(valueStart - lookback, valueStart);

        // Look for JSON-style key
        Matcher jsonKey = Pattern.compile("(\"[^\"]+\"\\s*:\\s*\"?)$").matcher(window);
        if (jsonKey.find()) return jsonKey.group(1);

        // Look for HTML attribute key
        Matcher htmlAttr = Pattern.compile("(\\w+\\s*=\\s*\"?)$").matcher(window);
        if (htmlAttr.find()) return htmlAttr.group(1);

        // Look for header style
        Matcher header = Pattern.compile("([A-Za-z\\-]+:\\s*)$").matcher(window);
        if (header.find()) return header.group(1);

        // Fall back to the previous 8 chars trimmed
        String tail = window.substring(Math.max(0, window.length() - 8));
        return tail;
    }

    private String pickRightBoundary(String body, int valueEnd) {
        if (valueEnd >= body.length()) return "";
        int lookahead = Math.min(30, body.length() - valueEnd);
        String window = body.substring(valueEnd, valueEnd + lookahead);

        // Look for closing JSON/HTML markers
        Matcher closer = Pattern.compile("^(\"|<|,|}|\\]|&|;|\\s)").matcher(window);
        if (closer.find()) return closer.group(1);

        return window.substring(0, Math.min(4, window.length()));
    }

    // ── Expression building (live preview) ────────────────────────────────────

    private void rebuildExpression() {
        if (expressionArea == null) return;
        String type = (String) typeCombo.getSelectedItem();
        String value = valueField.getText();
        String left = leftBoundField.getText();
        String right = rightBoundField.getText();

        String expr;
        if ("JSONPath Extractor".equals(type)) {
            expr = guessJsonPath(fullResponseBody, value);
        } else if ("Boundary Extractor".equals(type)) {
            expr = left + " || " + right;
        } else {
            // Regex: combine left + capture + right
            expr = Pattern.quote(left) + "(.+?)" + Pattern.quote(right);
            // Java's Pattern.quote uses \Q...\E — replace for cleaner output
            expr = expr.replace("\\Q", "").replace("\\E", "");
            // Now escape regex metacharacters in left/right manually
            expr = escape(left) + "(.+?)" + escape(right);
        }
        expressionArea.setText(expr);
    }

    private String escape(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ("\\.[]{}()*+?^$|".indexOf(c) >= 0) sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    // ── Test live ────────────────────────────────────────────────────────────

    private void testExpression() {
        String expr = expressionArea.getText().trim();
        String type = (String) typeCombo.getSelectedItem();

        try {
            if ("JSONPath Extractor".equals(type)) {
                previewLabel.setText("JSONPath syntax — will be validated by JMeter at runtime: " + expr);
                previewLabel.setForeground(Color.BLUE);
                return;
            }
            if ("Boundary Extractor".equals(type)) {
                String[] parts = expr.split("\\s*\\|\\|\\s*", 2);
                if (parts.length == 2) {
                    int li = fullResponseBody.indexOf(parts[0]);
                    if (li >= 0) {
                        int vs = li + parts[0].length();
                        int ri = fullResponseBody.indexOf(parts[1], vs);
                        if (ri >= 0) {
                            String captured = fullResponseBody.substring(vs, ri);
                            previewLabel.setText("✓ Captured: " + truncate(captured, 60));
                            previewLabel.setForeground(new Color(0, 128, 0));
                            return;
                        }
                    }
                }
                previewLabel.setText("✗ Boundaries not found in response");
                previewLabel.setForeground(Color.RED);
                return;
            }

            // Regex
            Matcher m = Pattern.compile(expr, Pattern.DOTALL).matcher(fullResponseBody);
            if (m.find()) {
                String captured = m.groupCount() > 0 ? m.group(1) : m.group(0);
                previewLabel.setText("✓ Captured: " + truncate(captured, 60));
                previewLabel.setForeground(new Color(0, 128, 0));
            } else {
                previewLabel.setText("✗ No match in current response");
                previewLabel.setForeground(Color.RED);
            }
        } catch (PatternSyntaxException e) {
            previewLabel.setText("✗ Invalid regex: " + e.getDescription());
            previewLabel.setForeground(Color.RED);
        }
    }

    // ── AI suggestion ────────────────────────────────────────────────────────

    private void aiSuggest() {
        AIProvider provider = AIProviderFactory.getInstance().getActiveProvider();
        if (provider == null || !provider.isConfigured()) {
            aiStatusLabel.setText(" ⚠ Configure AI in Tools menu");
            aiStatusLabel.setForeground(Color.RED);
            return;
        }
        aiSuggestBtn.setEnabled(false);
        aiStatusLabel.setText(" Asking AI...");
        aiStatusLabel.setForeground(new Color(80, 80, 180));

        String system = "You are a JMeter correlation expert. Given a target value and the FULL HTTP response body " +
                "where it appears, propose the most reliable left boundary, right boundary, and complete regex " +
                "to extract that value. " +
                "Reply ONLY with this JSON:\n" +
                "{\"left\":\"...\",\"right\":\"...\",\"regex\":\"...\",\"reasoning\":\"...\"}\n" +
                "Pick boundaries that are stable across runs (avoid timestamps, IDs in boundaries themselves). " +
                "If multiple occurrences exist, pick the one with the most stable surrounding text.";
        String user = "Value to extract: " + selectedText +
                "\n\nSampler: " + (samplerName != null ? samplerName : "(unknown)") +
                "\n\nFULL response body:\n" + fullResponseBody;

        SwingWorker<AIResponse, Void> worker = new SwingWorker<>() {
            @Override protected AIResponse doInBackground() {
                return provider.chat(system, user);
            }
            @Override protected void done() {
                aiSuggestBtn.setEnabled(true);
                try {
                    AIResponse r = get();
                    if (!r.isSuccess()) {
                        aiStatusLabel.setText(" ⚠ " + r.getErrorMessage());
                        aiStatusLabel.setForeground(Color.RED);
                        return;
                    }
                    String json = extractJson(r.getText());
                    if (json == null) {
                        aiStatusLabel.setText(" ⚠ AI did not return JSON");
                        aiStatusLabel.setForeground(Color.RED);
                        return;
                    }
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    if (obj.has("left")) leftBoundField.setText(obj.get("left").getAsString());
                    if (obj.has("right")) rightBoundField.setText(obj.get("right").getAsString());
                    if (obj.has("regex") && "Regex Extractor".equals(typeCombo.getSelectedItem())) {
                        expressionArea.setText(obj.get("regex").getAsString());
                    } else {
                        rebuildExpression();
                    }
                    String reasoning = obj.has("reasoning") ? obj.get("reasoning").getAsString() : "";
                    aiStatusLabel.setText(" ✓ AI: " + truncate(reasoning, 80));
                    aiStatusLabel.setForeground(new Color(0, 100, 0));
                } catch (Exception ex) {
                    aiStatusLabel.setText(" ⚠ " + ex.getMessage());
                    aiStatusLabel.setForeground(Color.RED);
                }
            }
        };
        worker.execute();
    }

    private String extractJson(String text) {
        int s = text.indexOf('{');
        int e = text.lastIndexOf('}');
        return (s >= 0 && e > s) ? text.substring(s, e + 1) : null;
    }

    // ── Apply ────────────────────────────────────────────────────────────────

    private void applyExtractor() {
        String varName = varNameField.getText().trim();
        if (varName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Variable name is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String expr = expressionArea.getText().trim();
        if (expr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Expression cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String type = (String) typeCombo.getSelectedItem();
        ResultEntry.ExtractorHint.Type hintType = switch (type) {
            case "JSONPath Extractor" -> ResultEntry.ExtractorHint.Type.JSON_PATH;
            case "Boundary Extractor" -> ResultEntry.ExtractorHint.Type.BOUNDARY;
            default -> ResultEntry.ExtractorHint.Type.REGEX;
        };
        ResultEntry.ExtractorHint hint = new ResultEntry.ExtractorHint(varName, hintType, expr,
                "Manually created via Smart Extractor dialog");
        hint.setApplied(true);
        if (applyCallback != null) applyCallback.accept(hint);

        // Build a proposal so the user sees confirmation + duplicate check
        com.genai.jmeter.plugin.listener.ChangeProposal proposal = new com.genai.jmeter.plugin.listener.ChangeProposal();
        proposal.targetSampler = samplerName;
        proposal.variableName = varName;
        switch (hintType) {
            case JSON_PATH -> {
                proposal.action = com.genai.jmeter.plugin.listener.ChangeProposal.Action.ADD_JSONPATH;
                proposal.expression = expr;
            }
            case BOUNDARY -> {
                proposal.action = com.genai.jmeter.plugin.listener.ChangeProposal.Action.ADD_BOUNDARY;
                String[] parts = expr.split("\\s*\\|\\|\\s*", 2);
                proposal.leftBoundary = parts.length > 0 ? parts[0] : leftBoundField.getText();
                proposal.rightBoundary = parts.length > 1 ? parts[1] : rightBoundField.getText();
                proposal.expression = expr;
            }
            default -> {
                proposal.action = com.genai.jmeter.plugin.listener.ChangeProposal.Action.ADD_REGEX;
                proposal.expression = expr;
            }
        }
        proposal.reasoning = "Manual extract from response selection";
        proposal.checkDuplicate();

        if (proposal.isDuplicate) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "An extractor for ${" + varName + "} already exists under '" + samplerName + "':\n  "
                            + proposal.duplicateOf + "\n\nAdd this one anyway?",
                    "Duplicate detected", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) { dispose(); return; }
        }

        boolean liveApplied = false;
        if (applyToTreeCheck.isSelected() && samplerName != null && LiveJMXModifier.isJMeterGuiAvailable()) {
            liveApplied = proposal.apply();
        }

        int substitutions = 0;
        if (substituteCheck.isSelected() && !valueField.getText().isEmpty()) {
            substitutions = LiveJMXModifier.substituteValueWithVariable(valueField.getText(), varName);
        }

        StringBuilder msg = new StringBuilder("Extractor created: ${" + varName + "}\n");
        if (liveApplied) msg.append("✓ Added to JMX tree under sampler: ").append(samplerName).append("\n");
        else if (applyToTreeCheck.isSelected()) msg.append("⚠ Could not add to JMX tree (sampler not found)\n");
        if (substitutions > 0) msg.append("✓ Replaced ").append(substitutions).append(" hard-coded occurrence(s) with ${").append(varName).append("}\n");

        JOptionPane.showMessageDialog(this, msg.toString(), "Applied", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String guessJsonPath(String body, String value) {
        if (body == null || value == null || value.isEmpty()) return "$.";
        try {
            Pattern p = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"?" + Pattern.quote(value) + "\"?");
            Matcher m = p.matcher(body);
            if (m.find()) return "$.." + m.group(1);
        } catch (Exception ignored) {}
        return "$.";
    }

    private String suggestVarName(String text) {
        if (text == null) return "extracted_var";
        if (text.length() > 30) text = text.substring(0, 30);
        String name = text.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "").toLowerCase();
        return name.isEmpty() ? "extracted_var" : name;
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    public void setApplyCallback(Consumer<ResultEntry.ExtractorHint> cb) { this.applyCallback = cb; }
}
