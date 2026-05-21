package com.genai.jmeter.plugin.listener.ui;

import com.genai.jmeter.plugin.ai.AIProvider;
import com.genai.jmeter.plugin.ai.AIProviderFactory;
import com.genai.jmeter.plugin.ai.AIResponse;
import com.genai.jmeter.plugin.listener.ChangeProposal;
import com.genai.jmeter.plugin.listener.RecordedReference;
import com.genai.jmeter.plugin.listener.ResultEntry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Right panel — shows request and response for a selected entry in a tabbed layout
 * mirroring JMeter's View Results Tree:
 *   • Sampler Result    — summary text (status, time, size, etc.)
 *   • Request           — headers + body
 *   • Response Data     — headers + body with Ctrl+F search, Send-to-AI, multi-group regex
 */
public class ResponseViewerPanel extends JPanel {

    private static final Highlighter.HighlightPainter MATCH_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 230, 100));
    private static final Highlighter.HighlightPainter CURRENT_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 150, 0));

    private final JTextArea summaryArea;
    private final JTextArea requestHeadersArea;
    private final JTextArea requestBodyArea;
    private final JTextArea responseHeadersArea;
    private final JTextArea responseBodyArea;

    // Search bar
    private final JPanel searchBar;
    private final JTextField searchField;
    private final JLabel searchStatus;
    private final List<int[]> searchHits = new ArrayList<>();
    private int currentHit = -1;

    private final JButton extractRegexBtn;
    private final JButton extractMultiBtn;
    private final JButton sendToAiBtn;
    private final JLabel selectionLabel;

    private ResultEntry currentEntry;
    private RecordedReference recordedRef;
    private BiConsumer<String, String> extractCallback;
    private Consumer<ResultEntry> sendToAiCallback;
    private Consumer<List<ChangeProposal>> proposalCallback;

    public ResponseViewerPanel() {
        super(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        summaryArea = createTextArea(false);
        summaryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        requestHeadersArea = createTextArea(false);
        requestBodyArea = createTextArea(false);
        responseHeadersArea = createTextArea(false);
        responseBodyArea = createTextArea(false);

        // Wire response body for right-click extraction
        attachExtractPopup(responseBodyArea);

        // Search bar (initially hidden)
        searchField = new JTextField();
        searchField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) prevHit(); else nextHit();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    toggleSearch(false);
                } else {
                    runSearch(searchField.getText());
                }
            }
        });
        searchStatus = new JLabel("");
        searchStatus.setForeground(Color.GRAY);
        searchStatus.setFont(searchStatus.getFont().deriveFont(10f));

        JButton prevBtn = new JButton("◀");
        prevBtn.setMargin(new Insets(2, 4, 2, 4));
        prevBtn.addActionListener(e -> prevHit());
        JButton nextBtn = new JButton("▶");
        nextBtn.setMargin(new Insets(2, 4, 2, 4));
        nextBtn.addActionListener(e -> nextHit());
        JButton closeSearchBtn = new JButton("✕");
        closeSearchBtn.setMargin(new Insets(2, 4, 2, 4));
        closeSearchBtn.addActionListener(e -> toggleSearch(false));

        searchBar = new JPanel(new BorderLayout(4, 0));
        searchBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        JPanel searchLeft = new JPanel(new BorderLayout(4, 0));
        JLabel findLbl = new JLabel("Find: ");
        findLbl.setFont(findLbl.getFont().deriveFont(11f));
        searchLeft.add(findLbl, BorderLayout.WEST);
        searchLeft.add(searchField, BorderLayout.CENTER);
        searchBar.add(searchLeft, BorderLayout.CENTER);

        JPanel searchRight = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        searchRight.add(prevBtn);
        searchRight.add(nextBtn);
        searchRight.add(searchStatus);
        searchRight.add(closeSearchBtn);
        searchBar.add(searchRight, BorderLayout.EAST);
        searchBar.setVisible(false);

        // Selection toolbar (under response body)
        selectionLabel = new JLabel("  Select text in response body to extract  ");
        selectionLabel.setFont(selectionLabel.getFont().deriveFont(11f));
        selectionLabel.setForeground(Color.GRAY);

        extractRegexBtn = Theme.coloredButton("Extract Selection", new Color(180, 100, 0));
        extractRegexBtn.setEnabled(false);
        extractRegexBtn.setFont(extractRegexBtn.getFont().deriveFont(11f));
        extractRegexBtn.addActionListener(e -> triggerExtract());

        extractMultiBtn = Theme.coloredButton("🪄 Multi-Group (AI)", new Color(100, 50, 150));
        extractMultiBtn.setEnabled(false);
        extractMultiBtn.setToolTipText("Select a region (e.g. an HTML table) and let AI build a JSR223 PostProcessor that extracts every value into separate vars");
        extractMultiBtn.setFont(extractMultiBtn.getFont().deriveFont(11f));
        extractMultiBtn.addActionListener(e -> triggerMultiGroupAI());

        sendToAiBtn = Theme.coloredButton("📎 Send Response to AI", new Color(0, 100, 160));
        sendToAiBtn.setEnabled(false);
        sendToAiBtn.setToolTipText("Attach this response data to the AI chat — so you can ask 'add an assertion for the booking confirmation text'");
        sendToAiBtn.setFont(sendToAiBtn.getFont().deriveFont(11f));
        sendToAiBtn.addActionListener(e -> {
            if (sendToAiCallback != null && currentEntry != null) sendToAiCallback.accept(currentEntry);
        });

        JButton findBtn = new JButton("🔍 Find (Ctrl+F)");
        findBtn.setFont(findBtn.getFont().deriveFont(11f));
        findBtn.addActionListener(e -> toggleSearch(true));

        JPanel extractBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        extractBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        extractBar.add(selectionLabel);
        extractBar.add(extractRegexBtn);
        extractBar.add(extractMultiBtn);
        extractBar.add(sendToAiBtn);
        extractBar.add(findBtn);
        // Reserve a minimum height so the toolbar can never be clipped, regardless
        // of how the parent JSplitPane is resized
        extractBar.setPreferredSize(new Dimension(0, 42));
        extractBar.setMinimumSize(new Dimension(0, 42));

        // ── Build tabs (Sampler Result / Request / Response Data) ─────────────
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 11f));

        tabs.addTab("Sampler Result", scrolled(summaryArea, null));

        JPanel reqTab = new JPanel(new BorderLayout(0, 4));
        JSplitPane reqSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                scrolled(requestHeadersArea, "Headers"), scrolled(requestBodyArea, "Body"));
        reqSplit.setDividerLocation(140);
        reqSplit.setResizeWeight(0.35);
        reqTab.add(reqSplit, BorderLayout.CENTER);
        tabs.addTab("Request", reqTab);

        JPanel respTab = new JPanel(new BorderLayout(0, 4));
        JSplitPane respSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                scrolled(responseHeadersArea, "Headers"),
                scrolled(responseBodyArea, "Body — right-click to extract"));
        respSplit.setDividerLocation(140);
        respSplit.setResizeWeight(0.25);
        respTab.add(searchBar, BorderLayout.NORTH);
        respTab.add(respSplit, BorderLayout.CENTER);
        respTab.add(extractBar, BorderLayout.SOUTH);
        tabs.addTab("Response Data", respTab);

        add(tabs, BorderLayout.CENTER);

        // Default to Response Data tab — that's where most work happens
        tabs.setSelectedIndex(2);

        // Ctrl+F keyboard shortcut from anywhere in this panel
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK), "find");
        getActionMap().put("find", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { toggleSearch(true); }
        });
    }

    // ── Extract popup ────────────────────────────────────────────────────────

    private void attachExtractPopup(JTextArea area) {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem extractItem = new JMenuItem("Extract Selection (Smart)");
        JMenuItem multiItem = new JMenuItem("Multi-Group Extract (AI)");
        JMenuItem sendItem = new JMenuItem("Send Response to AI Chat");
        popup.add(extractItem);
        popup.add(multiItem);
        popup.addSeparator();
        popup.add(sendItem);

        extractItem.addActionListener(e -> triggerExtract());
        multiItem.addActionListener(e -> triggerMultiGroupAI());
        sendItem.addActionListener(e -> {
            if (sendToAiCallback != null && currentEntry != null) sendToAiCallback.accept(currentEntry);
        });

        area.addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) {
                updateSelectionLabel();
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    popup.show(area, e.getX(), e.getY());
                }
            }
            @Override public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) popup.show(area, e.getX(), e.getY());
            }
        });
    }

    // ── Search ───────────────────────────────────────────────────────────────

    private void toggleSearch(boolean show) {
        searchBar.setVisible(show);
        if (show) {
            searchField.requestFocusInWindow();
            searchField.selectAll();
            runSearch(searchField.getText());
        } else {
            responseBodyArea.getHighlighter().removeAllHighlights();
            searchHits.clear();
            currentHit = -1;
            searchStatus.setText("");
        }
        revalidate();
    }

    private void runSearch(String needle) {
        responseBodyArea.getHighlighter().removeAllHighlights();
        searchHits.clear();
        currentHit = -1;
        if (needle == null || needle.isEmpty()) { searchStatus.setText(""); return; }

        String body = responseBodyArea.getText();
        String n = needle.toLowerCase();
        String bl = body.toLowerCase();
        int idx = 0;
        try {
            while ((idx = bl.indexOf(n, idx)) >= 0) {
                int end = idx + needle.length();
                responseBodyArea.getHighlighter().addHighlight(idx, end, MATCH_PAINTER);
                searchHits.add(new int[]{idx, end});
                idx = end;
                if (searchHits.size() > 5000) break;  // safety
            }
        } catch (BadLocationException ignored) {}

        if (searchHits.isEmpty()) {
            searchStatus.setText(" 0 matches");
            searchStatus.setForeground(Color.RED);
        } else {
            currentHit = 0;
            highlightCurrent();
            searchStatus.setText(" " + (currentHit + 1) + "/" + searchHits.size());
            searchStatus.setForeground(new Color(0, 100, 0));
        }
    }

    private void nextHit() {
        if (searchHits.isEmpty()) return;
        currentHit = (currentHit + 1) % searchHits.size();
        highlightCurrent();
        searchStatus.setText(" " + (currentHit + 1) + "/" + searchHits.size());
    }
    private void prevHit() {
        if (searchHits.isEmpty()) return;
        currentHit = (currentHit - 1 + searchHits.size()) % searchHits.size();
        highlightCurrent();
        searchStatus.setText(" " + (currentHit + 1) + "/" + searchHits.size());
    }

    private void highlightCurrent() {
        try {
            // Repaint regular highlights, then mark current with orange
            responseBodyArea.getHighlighter().removeAllHighlights();
            for (int i = 0; i < searchHits.size(); i++) {
                int[] hit = searchHits.get(i);
                Highlighter.HighlightPainter p = (i == currentHit) ? CURRENT_PAINTER : MATCH_PAINTER;
                responseBodyArea.getHighlighter().addHighlight(hit[0], hit[1], p);
            }
            int[] hit = searchHits.get(currentHit);
            responseBodyArea.setCaretPosition(hit[0]);
            // Scroll
            Rectangle r = responseBodyArea.modelToView2D(hit[0]).getBounds();
            responseBodyArea.scrollRectToVisible(r);
        } catch (BadLocationException ignored) {}
    }

    // ── Entry display ────────────────────────────────────────────────────────

    public void showEntry(ResultEntry entry) {
        this.currentEntry = entry;

        // Summary
        StringBuilder sm = new StringBuilder();
        sm.append("Sampler:       ").append(entry.getSamplerName()).append("\n");
        sm.append("URL:           ").append(entry.getUrl()).append("\n");
        sm.append("Response code: ").append(entry.getStatusCode()).append("\n");
        sm.append("Successful:    ").append(entry.isSuccess()).append("\n");
        sm.append("Elapsed time:  ").append(entry.getElapsedTime()).append(" ms\n");
        sm.append("Content type:  ").append(entry.getContentType()).append("\n");
        String body = entry.getResponseBody();
        sm.append("Response size: ").append(body != null ? body.length() : 0).append(" chars\n");
        if (!entry.getExtractorHints().isEmpty()) {
            sm.append("\nExtractor hints:\n");
            for (ResultEntry.ExtractorHint h : entry.getExtractorHints()) {
                sm.append("  • [").append(h.getType()).append("] ${").append(h.getVariableName())
                  .append("} → ").append(truncate(h.getExpression(), 80))
                  .append(h.isApplied() ? "  [applied]" : "").append("\n");
            }
        }
        summaryArea.setText(sm.toString());
        summaryArea.setCaretPosition(0);

        requestHeadersArea.setText(entry.getRequestHeaders());
        requestHeadersArea.setCaretPosition(0);

        String reqBody = entry.getRequestBody();
        requestBodyArea.setText(reqBody != null ? formatBody(reqBody, "application/json") : "");
        requestBodyArea.setCaretPosition(0);

        responseHeadersArea.setText(entry.getResponseHeaders());
        responseHeadersArea.setCaretPosition(0);

        String respBody = entry.getResponseBody();
        responseBodyArea.setText(respBody != null ? formatBody(respBody, entry.getContentType()) : "");
        responseBodyArea.setCaretPosition(0);

        selectionLabel.setText("  Select text in response to extract   |   " + entry.getSamplerName());
        extractRegexBtn.setEnabled(false);
        extractMultiBtn.setEnabled(false);
        sendToAiBtn.setEnabled(true);

        // Clear any prior search
        toggleSearch(false);
    }

    public void clear() {
        currentEntry = null;
        summaryArea.setText("");
        requestHeadersArea.setText("");
        requestBodyArea.setText("");
        responseHeadersArea.setText("");
        responseBodyArea.setText("");
        selectionLabel.setText("  Select text in response body to extract");
        extractRegexBtn.setEnabled(false);
        extractMultiBtn.setEnabled(false);
        sendToAiBtn.setEnabled(false);
        toggleSearch(false);
    }

    private void updateSelectionLabel() {
        String sel = responseBodyArea.getSelectedText();
        boolean has = sel != null && !sel.trim().isEmpty();
        if (has) {
            int len = sel.length();
            selectionLabel.setText("  Selected: \"" + truncate(sel.trim(), 35) + "\" (" + len + " chars)");
            selectionLabel.setForeground(new Color(0, 100, 180));
        } else {
            selectionLabel.setText("  Select text in response body to extract");
            selectionLabel.setForeground(Color.GRAY);
        }
        extractRegexBtn.setEnabled(has);
        extractMultiBtn.setEnabled(has);
    }

    // ── Extract single (Smart dialog) ────────────────────────────────────────

    private void triggerExtract() {
        String sel = responseBodyArea.getSelectedText();
        if (sel == null || sel.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select text in the response body first.",
                    "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String fullBody = responseBodyArea.getText();

        Window w = SwingUtilities.getWindowAncestor(this);
        Frame parent = (w instanceof Frame) ? (Frame) w : null;
        String samplerName = currentEntry != null ? currentEntry.getSamplerName() : null;
        ManualExtractorDialog dialog = new ManualExtractorDialog(parent, sel.trim(), fullBody, samplerName);

        dialog.setApplyCallback(hint -> {
            if (currentEntry != null) currentEntry.addExtractorHint(hint);
            if (extractCallback != null) extractCallback.accept(sel.trim(), fullBody);
        });
        dialog.setVisible(true);
    }

    // ── Multi-group AI extract (table / repeated structure) ──────────────────

    private void triggerMultiGroupAI() {
        String sel = responseBodyArea.getSelectedText();
        if (sel == null || sel.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select the region (e.g. a table) first.",
                    "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (currentEntry == null) return;

        AIProvider provider = AIProviderFactory.getInstance().getActiveProvider();
        if (provider == null || !provider.isConfigured()) {
            JOptionPane.showMessageDialog(this,
                    "Configure an AI provider first (Tools > GenAI Correlation Plugin > AI Settings)",
                    "AI Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        extractMultiBtn.setEnabled(false);
        extractMultiBtn.setText("AI thinking...");

        final String samplerName = currentEntry.getSamplerName();
        final String selection = sel;

        String system = "You are a JMeter correlation expert. The user selected a region of a response that " +
                "contains MULTIPLE values they want to extract into separate JMeter variables.\n" +
                "Generate a JSR223 PostProcessor (Groovy) that:\n" +
                "  1. Reads prev.getResponseDataAsString()\n" +
                "  2. Applies an appropriate regex (Pattern + Matcher) to capture each value\n" +
                "  3. Calls vars.put('varName', match) for each captured value with a meaningful name\n" +
                "Reply ONLY with JSON:\n" +
                "{\n" +
                "  \"variableNames\": [\"col1_v1\",\"col1_v2\",\"col2_v1\",...],\n" +
                "  \"script\": \"the full groovy script\",\n" +
                "  \"reasoning\": \"why this approach\"\n" +
                "}\n" +
                "Use the FULL response body to anchor your regex against stable markup around the selection.";
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Sampler: ").append(samplerName).append("\n\n");
        if (recordedRef != null && recordedRef.isLoaded()) {
            userPrompt.append(recordedRef.summariseForAI(50)).append("\n");
        }
        userPrompt.append("Selected region (extract every value as a separate var):\n").append(selection).append("\n\n");
        userPrompt.append("FULL response body for stable anchoring:\n").append(currentEntry.getResponseBody());
        String user = userPrompt.toString();

        SwingWorker<AIResponse, Void> worker = new SwingWorker<>() {
            @Override protected AIResponse doInBackground() {
                return provider.chat(system, user);
            }
            @Override protected void done() {
                extractMultiBtn.setEnabled(true);
                extractMultiBtn.setText("🪄 Multi-Group (AI)");
                try {
                    AIResponse r = get();
                    if (!r.isSuccess()) {
                        JOptionPane.showMessageDialog(ResponseViewerPanel.this,
                                "AI error: " + r.getErrorMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    String json = extractJson(r.getText());
                    if (json == null) {
                        JOptionPane.showMessageDialog(ResponseViewerPanel.this,
                                "AI did not return JSON:\n" + r.getText(), "Error", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    String script = obj.has("script") ? obj.get("script").getAsString() : "";
                    String reasoning = obj.has("reasoning") ? obj.get("reasoning").getAsString() : "";

                    ChangeProposal proposal = new ChangeProposal(ChangeProposal.Action.ADD_JSR223_POST, samplerName);
                    proposal.variableName = "Multi-Group Extractor (AI)";
                    proposal.script = script;
                    proposal.language = "groovy";
                    proposal.reasoning = reasoning;
                    proposal.explanation = "AI-generated JSR223 PostProcessor that extracts multiple values into JMeter variables.";
                    proposal.checkDuplicate();

                    if (proposalCallback != null) {
                        List<ChangeProposal> list = new ArrayList<>();
                        list.add(proposal);
                        proposalCallback.accept(list);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ResponseViewerPanel.this,
                            "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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

    // ── Body formatting ──────────────────────────────────────────────────────

    private String formatBody(String body, String contentType) {
        if (body == null) return "";
        if (contentType != null && contentType.contains("json")) return prettyJson(body);
        return body;
    }

    private String prettyJson(String json) {
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(json);
            return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(el);
        } catch (Exception e) {
            return json;
        }
    }

    private JScrollPane scrolled(JTextArea area, String title) {
        JScrollPane sp = new JScrollPane(area);
        if (title != null) {
            sp.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title));
        }
        return sp;
    }

    private JTextArea createTextArea(boolean editable) {
        JTextArea area = new JTextArea();
        area.setEditable(editable);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        area.setLineWrap(false);
        area.setBackground(Color.WHITE);
        area.setForeground(Color.BLACK);
        area.setCaretColor(Color.BLACK);
        return area;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    // ── Callbacks ────────────────────────────────────────────────────────────

    public void setExtractCallback(BiConsumer<String, String> cb) { this.extractCallback = cb; }
    public void setSendToAiCallback(Consumer<ResultEntry> cb) { this.sendToAiCallback = cb; }
    public void setProposalCallback(Consumer<List<ChangeProposal>> cb) { this.proposalCallback = cb; }
    public void setRecordedReference(RecordedReference ref) { this.recordedRef = ref; }
    public ResultEntry getCurrentEntry() { return currentEntry; }
    public String getSelectedResponseText() { return responseBodyArea.getSelectedText(); }
    public String getFullResponseBody() { return responseBodyArea.getText(); }
}
