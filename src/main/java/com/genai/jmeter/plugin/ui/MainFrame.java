package com.genai.jmeter.plugin.ui;

import com.genai.jmeter.plugin.core.PluginConstants;
import com.genai.jmeter.plugin.ui.panels.AISettingsPanel;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.net.URI;

/**
 * Main plugin window (opened from Tools menu).
 * Only purpose: configure AI provider and show usage instructions.
 *
 * Actual correlation work happens in the SmartCorrelationListener
 * (Add > Listeners > GenAI Smart Correlation Listener).
 */
public class MainFrame extends JFrame {

    private static final String GITHUB_URL = "https://github.com/Sam-Richard-007";

    public MainFrame() {
        super(PluginConstants.PLUGIN_NAME + " v" + PluginConstants.PLUGIN_VERSION);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(720, 620);
        setMinimumSize(new Dimension(640, 520));
        setLocationRelativeTo(null);
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        // ── Toolbar ────────────────────────────────────────────────────────────
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(45, 45, 48));

        JLabel brand = new JLabel("  " + PluginConstants.PLUGIN_NAME + "  ");
        brand.setForeground(Color.WHITE);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 13f));
        toolbar.add(brand);
        toolbar.addSeparator();

        JButton aiSettings = new JButton("⚙ AI Settings");
        aiSettings.setForeground(Color.WHITE);
        aiSettings.setBackground(new Color(70, 70, 80));
        aiSettings.setOpaque(true);
        aiSettings.setBorderPainted(false);
        aiSettings.setFocusPainted(false);
        aiSettings.addActionListener(e -> new AISettingsPanel(this).setVisible(true));
        toolbar.add(aiSettings);

        toolbar.add(Box.createHorizontalGlue());
        JLabel ver = new JLabel("v" + PluginConstants.PLUGIN_VERSION + "   ");
        ver.setForeground(new Color(150, 150, 150));
        toolbar.add(ver);

        add(toolbar, BorderLayout.NORTH);

        // ── Help / About content ───────────────────────────────────────────────
        JEditorPane help = new JEditorPane();
        help.setContentType("text/html");
        help.setEditable(false);
        help.setBackground(Color.WHITE);
        help.setText(helpHtml());
        help.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try { Desktop.getDesktop().browse(new URI(e.getURL().toString())); } catch (Exception ignored) {}
            }
        });
        JScrollPane scroll = new JScrollPane(help);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        // ── Footer with credit ─────────────────────────────────────────────────
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(245, 245, 247));
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));

        JLabel credit = new JLabel(
                "<html>Created by <b>Sam Richard</b> &nbsp;|&nbsp; " +
                "<a href='" + GITHUB_URL + "'>" + GITHUB_URL + "</a></html>");
        credit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        credit.setForeground(new Color(70, 70, 70));
        credit.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                try { Desktop.getDesktop().browse(new URI(GITHUB_URL)); } catch (Exception ignored) {}
            }
        });
        footer.add(credit, BorderLayout.WEST);
        add(footer, BorderLayout.SOUTH);
    }

    private String helpHtml() {
        return "<html><body style='font-family:Segoe UI,Arial,sans-serif; padding:16px; color:#222;'>" +

            "<h2 style='color:#005ea2; margin-top:0;'>GenAI Smart Correlation Plugin</h2>" +
            "<p>Live, AI-assisted correlation and test-plan editing directly inside JMeter. " +
            "No HAR conversion, no separate JMX file. Open your existing script, attach the " +
            "<b>GenAI Smart Correlation Listener</b>, and the plugin helps you correlate, " +
            "fix errors, add assertions, and generate scripts — all reviewed before any " +
            "change touches your tree.</p>" +

            "<h3 style='color:#005ea2;'>Quick Start</h3>" +
            "<ol>" +
            "<li><b>Configure AI</b> &mdash; click <i>⚙ AI Settings</i> above. " +
            "Groq's free tier is the fastest path to start (<code>llama-3.3-70b-versatile</code>).</li>" +
            "<li><b>Open your JMX</b> in JMeter (File &gt; Open).</li>" +
            "<li><b>Add the listener</b> &mdash; right-click your Thread Group &gt; " +
            "<i>Add &gt; Listeners &gt; GenAI Smart Correlation Listener</i>.</li>" +
            "<li><b>Run</b> — every response is captured in the listener.</li>" +
            "<li><b>Correlate / fix / assert</b> using the toolbar buttons (below).</li>" +
            "<li><b>Save</b> (Ctrl+S) — your script now contains all applied changes.</li>" +
            "</ol>" +

            "<h3 style='color:#005ea2;'>Listener Toolbar</h3>" +
            "<table border='0' cellpadding='4' cellspacing='0'>" +
            "<tr><td valign='top'><b>◀ AI Chat</b></td><td>Show/hide the left sidebar where you talk to AI in natural language.</td></tr>" +
            "<tr><td valign='top'><b>⚡ Auto-Correlate</b></td><td>One-click AI pass over all captured samples. Suggests Regex / JSONPath / Boundary extractors per sampler. Duplicates are flagged amber and deselected.</td></tr>" +
            "<tr><td valign='top'><b>🔧 Fix Errors (AI)</b></td><td>Scans every failed sample (non-2xx, assertion failures) and asks AI to propose concrete fixes — add missing auth header, correlate a stale token, fix a URL, add a Response Assertion. Each proposal is reviewable.</td></tr>" +
            "<tr><td valign='top'><b>🗑 Clear</b></td><td>Drop all captured results. Does not modify the JMX.</td></tr>" +
            "<tr><td valign='top'><b>⋮ Recorded Ref</b></td><td>Load a recorded <code>.jmx</code> (from the Test Script Recorder) or a <code>.har</code> as a <b>baseline</b>. AI will see both your live response and the original recorded values — so it can compare and pinpoint exactly what changed.</td></tr>" +
            "</table>" +

            "<h3 style='color:#005ea2;'>AI Chat Sidebar</h3>" +
            "<p>Type natural-language requests. The AI sees: all sampler names in your tree, " +
            "the currently selected sampler's full response, any attached responses, and the recorded baseline.</p>" +
            "<p><b>Quick-action buttons:</b></p>" +
            "<ul>" +
            "<li><b>+Response</b> — attach the currently selected sampler's full response to your next message.</li>" +
            "<li><b>📦 Attach All</b> — attach <b>every captured response</b>. After this, you can ask " +
            "<i>'Add a unique success assertion to each sampler based on its actual response text'</i> and AI will read each one and propose tailored assertions.</li>" +
            "<li><b>Timer</b> — quick request for a 1000ms constant timer.</li>" +
            "<li><b>Assert each</b> — preset prompt that uses the attached responses to add a per-sampler success assertion.</li>" +
            "<li><b>Clear</b> — clear chat history and attachments.</li>" +
            "</ul>" +
            "<p><b>Example prompts:</b></p>" +
            "<ul>" +
            "<li><i>Add a Response Assertion containing 'Welcome' to every Login sampler.</i></li>" +
            "<li><i>Extract the order ID from the response of the Checkout sampler into ${order_id}.</i></li>" +
            "<li><i>Add a JSR223 PostProcessor (Groovy) to the Cart sampler that writes the response body to C:/tmp/cart.txt.</i></li>" +
            "<li><i>Add a 2 second timer before every Book Appointment sampler.</i></li>" +
            "<li><i>Look at all attached responses and add unique success assertions per sampler.</i> (after clicking 📦 Attach All)</li>" +
            "</ul>" +

            "<h3 style='color:#005ea2;'>Response Viewer Tabs</h3>" +
            "<p>Mirrors JMeter's <i>View Results Tree</i>:</p>" +
            "<ul>" +
            "<li><b>Sampler Result</b> — status, time, size, applied hints.</li>" +
            "<li><b>Request</b> — headers + body.</li>" +
            "<li><b>Response Data</b> — headers + body with the smart extraction toolbar at the bottom.</li>" +
            "</ul>" +
            "<p><b>Bottom toolbar inside Response Data:</b></p>" +
            "<ul>" +
            "<li><b>Extract Selection</b> — select text → opens the Smart Extractor dialog (Left Boundary / Value / Right Boundary fields, AI Suggest, Test, Apply). The extractor is added live to your loaded JMX under the matching sampler.</li>" +
            "<li><b>🪄 Multi-Group (AI)</b> — select a region (e.g. an HTML table). AI generates a single JSR223 PostProcessor that captures every value into separate variables.</li>" +
            "<li><b>📎 Send Response to AI</b> — pushes the full response into the AI chat as context.</li>" +
            "<li><b>🔍 Find (Ctrl+F)</b> — incremental search in the response body; matches highlighted yellow, current match orange.</li>" +
            "</ul>" +

            "<h3 style='color:#005ea2;'>Confirmation Before Changes</h3>" +
            "<p>Every AI-suggested change (Auto-Correlate, Script Fixer, AI Chat actions, Multi-Group, manual Extract) " +
            "is shown in a <b>Proposal Preview Dialog</b> with:</p>" +
            "<ul>" +
            "<li>Checkbox per row — apply or skip individually.</li>" +
            "<li>Editable variable name — rename right there.</li>" +
            "<li><span style='background:#fff0c8; padding:1px 4px;'>Amber row</span> = duplicate already in tree (deselected by default).</li>" +
            "<li>Details pane showing reasoning, script body, and target sampler.</li>" +
            "<li>Select All / Deselect All / Cancel / Apply Selected.</li>" +
            "</ul>" +
            "<p>Nothing is written to your JMX without your approval.</p>" +

            "<h3 style='color:#005ea2;'>AI Providers</h3>" +
            "<ul>" +
            "<li><b>Groq</b> (free tier) &mdash; default. Production models: <code>llama-3.3-70b-versatile</code>, " +
            "<code>llama-3.1-8b-instant</code>, <code>openai/gpt-oss-120b</code>, <code>openai/gpt-oss-20b</code>, " +
            "<code>qwen/qwen3-32b</code>, <code>groq/compound</code>, <code>meta-llama/llama-4-scout-17b-16e-instruct</code>.</li>" +
            "<li><b>Google Gemini</b> &mdash; <code>gemini-2.5-flash</code>, <code>gemini-2.5-flash-lite</code>, " +
            "<code>gemini-2.5-pro</code>, <code>gemini-flash-latest</code>, <code>gemini-pro-latest</code>.</li>" +
            "<li><b>Meta Llama</b> &mdash; official <code>api.llama.com</code> or Together AI.</li>" +
            "</ul>" +
            "<p>The <b>Test Active Provider</b> button in AI Settings verifies your API key works before you start using it.</p>" +

            "<h3 style='color:#005ea2;'>Tips</h3>" +
            "<ul>" +
            "<li>Always click a captured row before chatting — that sets the AI's <i>current sampler</i> context.</li>" +
            "<li>Use 📦 Attach All before bulk operations like \"add an assertion to every sampler\".</li>" +
            "<li>Loading a Recorded Reference (⋮ menu) dramatically improves correlation quality because AI sees the original values.</li>" +
            "<li>If JMeter's L&F merges button text into the background, the plugin's coloured buttons re-apply their colours on every L&F switch — switch L&F and back if anything looks off.</li>" +
            "<li>Press <b>Ctrl+S</b> in JMeter after applying changes — the in-memory tree is updated, but the file isn't until you save.</li>" +
            "</ul>" +

            "<p style='color:#888; margin-top:18px; font-size:11px; border-top:1px solid #ddd; padding-top:8px;'>" +
            "Built by Sam Richard &nbsp;·&nbsp; " +
            "<a href='https://github.com/Sam-Richard-007/Smart-GenAI-Powered-JMeter'>" +
            "github.com/Sam-Richard-007/Smart-GenAI-Powered-JMeter</a></p>" +
            "</body></html>";
    }
}
