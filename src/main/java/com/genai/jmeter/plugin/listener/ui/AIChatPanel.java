package com.genai.jmeter.plugin.listener.ui;

import com.genai.jmeter.plugin.ai.AIProvider;
import com.genai.jmeter.plugin.ai.AIProviderFactory;
import com.genai.jmeter.plugin.ai.AIResponse;
import com.genai.jmeter.plugin.listener.ChangeProposal;
import com.genai.jmeter.plugin.listener.LiveJMXModifier;
import com.genai.jmeter.plugin.listener.RecordedReference;
import com.genai.jmeter.plugin.listener.ResultEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI Chat panel — left sidebar in the listener.
 * Sends conversational requests to the configured AI provider; receives back a
 * structured JSON list of proposed actions (one or many). The user reviews
 * via ProposalPreviewDialog before anything touches the JMX tree.
 */
public class AIChatPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(AIChatPanel.class);

    private static final String SYSTEM_PROMPT =
        "You are an AI assistant embedded in a JMeter Smart Correlation Listener. " +
        "The user has a loaded JMX test plan being executed live. You help them improve the test plan.\n" +
        "\n" +
        "You will receive: the user's request, a list of all sampler names in the tree, and possibly " +
        "the full response data of one or more captured samplers (the user can paste these in).\n" +
        "\n" +
        "You decide what to do and reply ONLY with a single JSON object — no markdown, no prose outside.\n" +
        "Schema:\n" +
        "{\n" +
        "  \"actions\": [\n" +
        "    {\n" +
        "      \"action\": \"add_regex|add_jsonpath|add_boundary|add_jsr223_pre|add_jsr223_post|add_timer|add_assertion|explain\",\n" +
        "      \"targetSampler\": \"exact sampler name from the tree (or omit for current)\",\n" +
        "      \"variableName\": \"varName for extractors / element display name\",\n" +
        "      \"expression\": \"regex or jsonpath\",\n" +
        "      \"template\": \"$1$ (or $1$$2$$3$ for multi-group regex)\",\n" +
        "      \"matchNumber\": 1,\n" +
        "      \"leftBoundary\": \"...\",\n" +
        "      \"rightBoundary\": \"...\",\n" +
        "      \"script\": \"groovy script body\",\n" +
        "      \"language\": \"groovy\",\n" +
        "      \"delayMs\": 1000,\n" +
        "      \"assertionContains\": \"text\",\n" +
        "      \"reasoning\": \"why this and not something else\"\n" +
        "    }\n" +
        "  ],\n" +
        "  \"explanation\": \"high-level summary for the user\"\n" +
        "}\n" +
        "\n" +
        "Key rules:\n" +
        "1. Use `actions` as an ARRAY — emit one entry per sampler when the user asks for bulk operations.\n" +
        "   If the user says 'add an assertion to all book appointment samplers', loop through every sampler whose " +
        "   name contains 'book appointment' and emit one action per match.\n" +
        "2. When the user says 'PostProcessor', use add_jsr223_post. When they say 'PreProcessor', use add_jsr223_pre. " +
        "   Never use the wrong one.\n" +
        "3. For Groovy/Beanshell scripts, always set `language` correctly ('groovy' or 'beanshell'). Prefer Groovy.\n" +
        "4. When the user asks for a value like 'success' assertion and you've been given the response, look in the " +
        "   response for the actual confirmation text and use it.\n" +
        "5. For multi-value extraction from an HTML table or repeated structure, you have two choices:\n" +
        "   a) Emit one add_regex with template like '$1$ | $2$ | $3$' if a single regex captures all in one match.\n" +
        "   b) Emit ONE add_jsr223_post whose `script` runs a multi-group regex over `prev.getResponseDataAsString()` " +
        "      and sets multiple vars via `vars.put('name1', m.group(1)); vars.put('name2', m.group(2)); ...`.\n" +
        "6. Never produce duplicate actions — if asked for 'an assertion on response code 200' twice, emit it once.";

    private final JTextPane chatArea;
    private final JTextArea inputField;
    private final JButton sendBtn;
    private final JLabel statusLabel;

    private final List<ChatMessage> history = new ArrayList<>();
    private ResultEntry contextEntry;
    private final Map<String, String> attachedResponses = new HashMap<>();  // sampler name → response text
    private RecordedReference recordedReference;
    private Consumer<List<ChangeProposal>> proposalCallback;
    private Consumer<ChatAction> actionCallback;
    private java.util.function.Supplier<List<ResultEntry>> allResultsSupplier;
    // Cap per-response chars sent to AI. -1 = unlimited (sends the whole thing).
    private int responseCharLimit = -1;
    private boolean attachAllResponses = false;

    private final StyleContext styleCtx = new StyleContext();
    private final Style userStyle;
    private final Style aiStyle;
    private final Style systemStyle;
    private final Style codeStyle;
    private final Style successStyle;

    public AIChatPanel() {
        super(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        userStyle = styleCtx.addStyle("user", null);
        StyleConstants.setForeground(userStyle, new Color(0, 80, 160));
        StyleConstants.setBold(userStyle, true);

        aiStyle = styleCtx.addStyle("ai", null);
        StyleConstants.setForeground(aiStyle, new Color(30, 100, 30));

        systemStyle = styleCtx.addStyle("system", null);
        StyleConstants.setForeground(systemStyle, new Color(120, 120, 120));
        StyleConstants.setItalic(systemStyle, true);
        StyleConstants.setFontSize(systemStyle, 11);

        codeStyle = styleCtx.addStyle("code", null);
        StyleConstants.setFontFamily(codeStyle, Font.MONOSPACED);
        StyleConstants.setFontSize(codeStyle, 10);
        StyleConstants.setBackground(codeStyle, new Color(240, 240, 240));
        StyleConstants.setForeground(codeStyle, new Color(60, 0, 60));

        successStyle = styleCtx.addStyle("success", null);
        StyleConstants.setForeground(successStyle, new Color(0, 120, 0));
        StyleConstants.setBold(successStyle, true);

        // ── Chat area ─────────────────────────────────────────────────────────
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        chatArea.setMargin(new Insets(4, 4, 4, 4));
        chatArea.setBackground(Color.WHITE);
        chatArea.setForeground(Color.BLACK);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        chatScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));

        // ── Input area (multi-line) ───────────────────────────────────────────
        inputField = new JTextArea(3, 0);
        inputField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        inputField.setLineWrap(true);
        inputField.setWrapStyleWord(true);
        inputField.setMargin(new Insets(4, 4, 4, 4));
        inputField.setBackground(Color.WHITE);
        inputField.setForeground(Color.BLACK);
        inputField.setCaretColor(Color.BLACK);
        inputField.setToolTipText("Type your request — Enter to send, Shift+Enter for newline");
        inputField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendMessage();
                }
            }
        });
        JScrollPane inputScroll = new JScrollPane(inputField);
        inputScroll.setPreferredSize(new Dimension(0, 70));
        inputScroll.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 220), 1));

        sendBtn = Theme.coloredButton("Send", new Color(0, 120, 215));
        sendBtn.setFont(sendBtn.getFont().deriveFont(Font.BOLD));
        sendBtn.setPreferredSize(new Dimension(70, 0));
        sendBtn.addActionListener(e -> sendMessage());

        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.add(inputScroll, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);

        statusLabel = new JLabel(" Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(10f));
        statusLabel.setForeground(Color.GRAY);

        // ── Quick actions ─────────────────────────────────────────────────────
        JPanel quickActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        quickActions.setOpaque(false);
        addQuickButton(quickActions, "+Response", null);          // attach current response only
        addQuickButton(quickActions, "📦 Attach All", "__ATTACH_ALL__");  // attach every captured response
        addQuickButton(quickActions, "Timer", "Add a 1000ms constant timer before the current sampler");
        addQuickButton(quickActions, "Assert each", "Look at every sampler's response in the attached data, and add a Response Assertion with the most unique success text per sampler (e.g. login → 'Welcome', booking → 'Booking confirmed'). One assertion per sampler.");
        addQuickButton(quickActions, "Clear", "__CLEAR__");

        JPanel south = new JPanel(new BorderLayout(0, 3));
        south.add(quickActions, BorderLayout.NORTH);
        south.add(inputRow, BorderLayout.CENTER);
        south.add(statusLabel, BorderLayout.SOUTH);

        JLabel header = Theme.coloredLabel("  AI Chat — proposes changes for your review",
                new Color(80, 60, 130), Color.WHITE);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 4));

        add(header, BorderLayout.NORTH);
        add(chatScroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        appendSystem("AI Chat ready. Click a captured row first to set context.\n" +
                "Examples:\n" +
                "  • 'Add a Response Assertion containing the booking confirmation text to all book-appointment samplers'\n" +
                "  • 'Extract all td values in the order table using a multi-group regex'\n" +
                "  • 'Add a JSR223 PostProcessor that writes the response body to C:/tmp/log.txt'\n" +
                "Use +Response to send the current response data to the AI so it can read it.");
    }

    private void addQuickButton(JPanel parent, String label, String prompt) {
        JButton b = new JButton(label);
        b.setFont(b.getFont().deriveFont(10f));
        b.setMargin(new Insets(2, 6, 2, 6));
        b.setFocusable(false);
        b.addActionListener(e -> {
            if (prompt == null) { attachCurrentResponse(); return; }
            if ("__CLEAR__".equals(prompt)) { clearHistory(); return; }
            if ("__ATTACH_ALL__".equals(prompt)) { attachAllResponsesNow(); return; }
            inputField.setText(prompt);
            sendMessage();
        });
        parent.add(b);
    }

    /** Attach every captured response so AI sees the whole run. */
    private void attachAllResponsesNow() {
        if (allResultsSupplier == null) {
            appendSystem("⚠ No result supplier wired.");
            return;
        }
        List<ResultEntry> all = allResultsSupplier.get();
        if (all == null || all.isEmpty()) {
            appendSystem("⚠ No captured results yet. Run your test first.");
            return;
        }
        int attached = 0;
        long totalChars = 0;
        for (ResultEntry e : all) {
            String body = e.getResponseBody();
            if (body == null || body.isEmpty()) continue;
            // Disambiguate same sampler hit multiple times
            String key = "#" + e.getIndex() + " " + e.getSamplerName();
            attachedResponses.put(key, body);
            attached++;
            totalChars += body.length();
        }
        attachAllResponses = true;
        appendSystem(String.format(
                "📦 Attached %d responses (%,d chars total). AI will see all of them on your next message.",
                attached, totalChars));
    }

    private void attachCurrentResponse() {
        if (contextEntry == null) {
            appendSystem("⚠ Select a captured row first to attach its response.");
            return;
        }
        String body = contextEntry.getResponseBody();
        if (body == null || body.isEmpty()) {
            appendSystem("⚠ Current sampler has no response body.");
            return;
        }
        attachedResponses.put(contextEntry.getSamplerName(), body);
        appendSystem("📎 Attached response of '" + contextEntry.getSamplerName() + "' ("
                + body.length() + " chars). It will be sent with your next message.");
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.setText("");
        appendUser("You: " + text);
        history.add(new ChatMessage("user", text));

        AIProvider provider = AIProviderFactory.getInstance().getActiveProvider();
        if (provider == null || !provider.isConfigured()) {
            appendSystem("⚠ No AI provider configured. Open Tools > GenAI Correlation Plugin > AI Settings.");
            return;
        }

        statusLabel.setText(" Thinking...");
        statusLabel.setForeground(new Color(80, 80, 180));
        sendBtn.setEnabled(false);

        String userPrompt = buildPromptWithContext(text);

        SwingWorker<AIResponse, Void> worker = new SwingWorker<>() {
            @Override protected AIResponse doInBackground() {
                return provider.chat(SYSTEM_PROMPT, userPrompt);
            }
            @Override protected void done() {
                try {
                    AIResponse resp = get();
                    if (resp.isSuccess()) {
                        history.add(new ChatMessage("assistant", resp.getText()));
                        processAIResponse(resp.getText());
                    } else {
                        appendSystem("⚠ AI error: " + resp.getErrorMessage());
                    }
                } catch (Exception e) {
                    appendSystem("⚠ Error: " + e.getMessage());
                } finally {
                    attachedResponses.clear();  // attachments are one-shot
                    statusLabel.setText(" Ready");
                    statusLabel.setForeground(Color.GRAY);
                    sendBtn.setEnabled(true);
                    inputField.requestFocusInWindow();
                }
            }
        };
        worker.execute();
    }

    private String buildPromptWithContext(String userMessage) {
        StringBuilder sb = new StringBuilder();

        // Always include the full list of sampler names so AI can target them
        List<String> samplers = LiveJMXModifier.getAllSamplerNames();
        sb.append("All samplers in the current JMX tree (").append(samplers.size()).append("):\n");
        for (String s : samplers) sb.append("  - ").append(s).append("\n");
        sb.append("\n");

        // Recorded baseline (if loaded) — gives AI the original recorder values
        if (recordedReference != null && recordedReference.isLoaded()) {
            sb.append(recordedReference.summariseForAI(100));
            sb.append("\n");
        }

        if (contextEntry != null) {
            sb.append("Currently selected sampler:\n");
            sb.append("  Name:   ").append(contextEntry.getSamplerName()).append("\n");
            sb.append("  URL:    ").append(contextEntry.getUrl()).append("\n");
            sb.append("  Status: ").append(contextEntry.getStatusCode()).append("\n");
            String body = contextEntry.getResponseBody();
            if (body != null && !body.isEmpty()) {
                sb.append("  Response headers:\n").append(contextEntry.getResponseHeaders()).append("\n");
                sb.append("  Response body:\n");
                appendCapped(sb, body);
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!attachedResponses.isEmpty()) {
            sb.append("Additional attached responses:\n");
            for (Map.Entry<String, String> en : attachedResponses.entrySet()) {
                sb.append("===== BEGIN ").append(en.getKey()).append(" =====\n");
                appendCapped(sb, en.getValue());
                sb.append("\n===== END ").append(en.getKey()).append(" =====\n\n");
            }
        }

        sb.append("User request: ").append(userMessage);
        return sb.toString();
    }

    /** Append a response body, optionally truncated by responseCharLimit (-1 = full). */
    private void appendCapped(StringBuilder sb, String text) {
        if (text == null) return;
        if (responseCharLimit < 0 || text.length() <= responseCharLimit) {
            sb.append(text);
        } else {
            sb.append(text, 0, responseCharLimit)
              .append("\n... (truncated, ").append(text.length() - responseCharLimit).append(" more chars)");
        }
    }

    public void setResponseCharLimit(int limit) { this.responseCharLimit = limit; }
    public void setRecordedReference(RecordedReference ref) { this.recordedReference = ref; }
    public void setAllResultsSupplier(java.util.function.Supplier<List<ResultEntry>> supplier) {
        this.allResultsSupplier = supplier;
    }

    private void processAIResponse(String text) {
        String json = extractJson(text);
        if (json == null) {
            appendAI("AI: " + text);
            return;
        }

        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String explanation = getStr(obj, "explanation", "");
            if (!explanation.isEmpty()) appendAI("AI: " + explanation);

            JsonArray actions;
            if (obj.has("actions") && obj.get("actions").isJsonArray()) {
                actions = obj.getAsJsonArray("actions");
            } else if (obj.has("action")) {
                // Legacy single-action — wrap into array
                actions = new JsonArray();
                actions.add(obj);
            } else {
                appendAI("AI: " + text);
                return;
            }

            List<ChangeProposal> proposals = new ArrayList<>();
            for (JsonElement el : actions) {
                if (!el.isJsonObject()) continue;
                ChangeProposal p = buildProposalFromJson(el.getAsJsonObject());
                if (p != null) {
                    p.checkDuplicate();
                    proposals.add(p);
                }
            }

            if (proposals.isEmpty()) {
                appendSystem("AI suggested no concrete actions.");
                return;
            }

            appendAI("AI proposed " + proposals.size() + " action(s) — opening review dialog...");
            if (proposalCallback != null) proposalCallback.accept(proposals);

        } catch (Exception e) {
            log.warn("Failed to process AI response: {}", e.getMessage());
            appendAI("AI: " + text);
        }
    }

    private ChangeProposal buildProposalFromJson(JsonObject obj) {
        String actionStr = getStr(obj, "action", "explain").toLowerCase();
        ChangeProposal p = new ChangeProposal();
        p.action = switch (actionStr) {
            case "add_regex" -> ChangeProposal.Action.ADD_REGEX;
            case "add_jsonpath" -> ChangeProposal.Action.ADD_JSONPATH;
            case "add_boundary" -> ChangeProposal.Action.ADD_BOUNDARY;
            case "add_jsr223_pre", "add_preprocessor" -> ChangeProposal.Action.ADD_JSR223_PRE;
            case "add_jsr223_post", "add_postprocessor" -> ChangeProposal.Action.ADD_JSR223_POST;
            case "add_timer" -> ChangeProposal.Action.ADD_TIMER;
            case "add_assertion" -> ChangeProposal.Action.ADD_ASSERTION;
            default -> ChangeProposal.Action.EXPLAIN;
        };
        p.targetSampler = resolveSampler(getStr(obj, "targetSampler", ""));
        p.variableName = getStr(obj, "variableName", null);
        p.expression = getStr(obj, "expression", "");
        p.leftBoundary = getStr(obj, "leftBoundary", "");
        p.rightBoundary = getStr(obj, "rightBoundary", "");
        p.template = getStr(obj, "template", "$1$");
        p.matchNumber = obj.has("matchNumber") ? safeInt(obj.get("matchNumber"), 1) : 1;
        p.script = getStr(obj, "script", "");
        p.language = getStr(obj, "language", "groovy");
        p.delayMs = obj.has("delayMs") ? safeLong(obj.get("delayMs"), 1000L) : 1000L;
        p.assertionContains = getStr(obj, "assertionContains", "");
        p.reasoning = getStr(obj, "reasoning", "");

        // If explain-only with no real change, skip
        if (p.action == ChangeProposal.Action.EXPLAIN) return null;

        // Auto variable name if missing
        if (p.variableName == null || p.variableName.isEmpty()) {
            p.variableName = switch (p.action) {
                case ADD_REGEX, ADD_JSONPATH, ADD_BOUNDARY -> "extracted";
                case ADD_JSR223_PRE -> "JSR223 PreProcessor (AI)";
                case ADD_JSR223_POST -> "JSR223 PostProcessor (AI)";
                case ADD_TIMER -> "Constant Timer";
                case ADD_ASSERTION -> "Response Assertion";
                default -> "Element";
            };
        }
        return p;
    }

    /** If AI returns 'current' or a partial name, try to resolve it. */
    private String resolveSampler(String name) {
        if (name == null || name.isEmpty() || "current".equalsIgnoreCase(name)) {
            return contextEntry != null ? contextEntry.getSamplerName() : null;
        }
        if (LiveJMXModifier.findSampler(name) != null) return name;
        // Try partial match
        List<String> matches = LiveJMXModifier.findSamplerNamesMatching(name);
        return matches.isEmpty() ? name : matches.get(0);
    }

    private int safeInt(JsonElement el, int def) {
        try { return el.getAsInt(); } catch (Exception e) { return def; }
    }
    private long safeLong(JsonElement el, long def) {
        try { return el.getAsLong(); } catch (Exception e) { return def; }
    }

    private String getStr(JsonObject obj, String key, String def) {
        try { return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def; }
        catch (Exception e) { return def; }
    }

    private void appendUser(String text) { appendStyled(text + "\n", userStyle); }
    private void appendAI(String text)   { appendStyled(text + "\n", aiStyle); }
    private void appendSystem(String text){ appendStyled(text + "\n", systemStyle); }

    private void appendStyled(String text, Style style) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = chatArea.getStyledDocument();
                doc.insertString(doc.getLength(), text, style);
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            } catch (BadLocationException e) { log.warn("Chat append error", e); }
        });
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : null;
    }

    public void setContextEntry(ResultEntry entry) {
        this.contextEntry = entry;
        if (entry != null) {
            appendSystem("Context → " + entry.getSamplerName() + " [" + entry.getStatusCode() + "]");
        }
    }

    public void sendResponseToAI(ResultEntry entry, String userPrompt) {
        if (entry == null) return;
        String body = entry.getResponseBody();
        if (body == null || body.isEmpty()) {
            appendSystem("⚠ This sampler has no response body to send.");
            return;
        }
        attachedResponses.put(entry.getSamplerName(), body);
        appendSystem("📎 Attached response of '" + entry.getSamplerName() + "'");
        if (userPrompt != null && !userPrompt.isEmpty()) {
            inputField.setText(userPrompt);
            sendMessage();
        }
    }

    public void setProposalCallback(Consumer<List<ChangeProposal>> cb) { this.proposalCallback = cb; }
    public void setActionCallback(Consumer<ChatAction> cb) { this.actionCallback = cb; }

    public void clearHistory() {
        history.clear();
        attachedResponses.clear();
        chatArea.setText("");
        appendSystem("Chat cleared. Ready for new session.");
    }

    public void focusInput() {
        SwingUtilities.invokeLater(() -> inputField.requestFocusInWindow());
    }

    private record ChatMessage(String role, String content) {}

    public static class ChatAction {
        public final String action;
        public final String elementType;
        public final String targetSampler;
        public final JsonObject properties;
        public final String jmxFragment;
        public final String explanation;

        public ChatAction(String action, String elementType, String targetSampler,
                JsonObject properties, String jmxFragment, String explanation) {
            this.action = action;
            this.elementType = elementType;
            this.targetSampler = targetSampler;
            this.properties = properties;
            this.jmxFragment = jmxFragment;
            this.explanation = explanation;
        }
    }
}
