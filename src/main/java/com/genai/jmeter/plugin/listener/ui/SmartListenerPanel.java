package com.genai.jmeter.plugin.listener.ui;

import com.genai.jmeter.plugin.listener.ChangeProposal;
import com.genai.jmeter.plugin.listener.LiveCorrelationEngine;
import com.genai.jmeter.plugin.listener.LiveJMXModifier;
import com.genai.jmeter.plugin.listener.RecordedReference;
import com.genai.jmeter.plugin.listener.ResultEntry;
import com.genai.jmeter.plugin.listener.ScriptFixer;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main panel for the SmartCorrelationListener tree element.
 * Layout:
 *   Toolbar (top)
 *   ┌── Left sidebar: AI Chat (collapsible) ──┬── Main content ──┐
 *   │   width ~320px when expanded,           │  SamplerList     │
 *   │   ~28px when collapsed                  │  | ResponseView  │
 *   └──────────────────────────────────────────┴──────────────────┘
 *   Footer with credit
 */
public class SmartListenerPanel extends JPanel {

    private static final String GITHUB_URL = "https://github.com/Sam-Richard-007";
    private static final int SIDEBAR_EXPANDED_WIDTH = 340;
    private static final int SIDEBAR_COLLAPSED_WIDTH = 32;

    private final SamplerListPanel samplerList;
    private final ResponseViewerPanel responseViewer;
    private final AIChatPanel aiChat;

    private final List<ResultEntry> results = new CopyOnWriteArrayList<>();
    private final AtomicInteger counter = new AtomicInteger(0);
    private final LiveCorrelationEngine correlationEngine = new LiveCorrelationEngine();

    private JLabel statusBar;
    private JButton autoCorrelateBtn;
    private JSplitPane mainSplit;       // sidebar | content
    private JPanel sidebar;             // wraps aiChat
    private JButton sidebarToggleBtn;
    private JButton recordedRefBtn;
    private boolean sidebarExpanded = true;
    private File lastRefDir = null;

    private final RecordedReference recordedRef = new RecordedReference();

    public SmartListenerPanel() {
        super(new BorderLayout(4, 4));

        samplerList = new SamplerListPanel();
        responseViewer = new ResponseViewerPanel();
        aiChat = new AIChatPanel();

        buildUI();
        wireCallbacks();
    }

    private void buildUI() {
        // ── Toolbar ──────────────────────────────────────────────────────────
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(50, 50, 55));

        sidebarToggleBtn = toolbarBtn("◀ AI Chat", new Color(80, 60, 130), e -> toggleSidebar());
        sidebarToggleBtn.setToolTipText("Show/hide the AI Chat sidebar");

        autoCorrelateBtn = toolbarBtn("⚡ Auto-Correlate (AI)", new Color(100, 50, 150), e -> runAutoCorrelate());
        JButton scriptFixerBtn = toolbarBtn("🔧 Fix Errors (AI)", new Color(180, 90, 30), e -> runScriptFixer());
        scriptFixerBtn.setToolTipText("Scan captured failures and ask AI to propose fixes (extractors, headers, assertions, ...)");
        JButton clearBtn = toolbarBtn("🗑 Clear", new Color(160, 60, 60), e -> clearResults());

        recordedRefBtn = toolbarBtn("⋮ Recorded Ref", new Color(70, 90, 120), e -> openRecordedRefMenu());
        recordedRefBtn.setToolTipText("Load a recorded .jmx or .har as a baseline so AI can compare against original values");

        toolbar.add(sidebarToggleBtn);
        toolbar.addSeparator();
        toolbar.add(autoCorrelateBtn);
        toolbar.add(scriptFixerBtn);
        toolbar.add(clearBtn);
        toolbar.addSeparator();
        toolbar.add(recordedRefBtn);

        statusBar = new JLabel("  Ready — run your test plan, results will appear here");
        statusBar.setForeground(new Color(200, 200, 200));
        statusBar.setFont(statusBar.getFont().deriveFont(11f));
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(statusBar);
        toolbar.add(Box.createHorizontalStrut(8));

        add(toolbar, BorderLayout.NORTH);

        // ── Main content: sampler list | response viewer ─────────────────────
        // Force preferred size to (0, 0) so the split itself takes whatever space
        // the parent gives it — the inner JScrollPanes handle overflow internally.
        samplerList.setPreferredSize(new Dimension(0, 0));
        samplerList.setMinimumSize(new Dimension(0, 0));
        responseViewer.setPreferredSize(new Dimension(0, 0));
        responseViewer.setMinimumSize(new Dimension(0, 0));

        JSplitPane contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, samplerList, responseViewer);
        contentSplit.setDividerLocation(300);
        contentSplit.setResizeWeight(0.30);
        contentSplit.setPreferredSize(new Dimension(0, 0));
        contentSplit.setMinimumSize(new Dimension(0, 0));

        // ── Sidebar wrapper ──────────────────────────────────────────────────
        aiChat.setPreferredSize(new Dimension(SIDEBAR_EXPANDED_WIDTH, 0));
        aiChat.setMinimumSize(new Dimension(0, 0));
        sidebar = new JPanel(new BorderLayout());
        sidebar.add(aiChat, BorderLayout.CENTER);
        sidebar.setPreferredSize(new Dimension(SIDEBAR_EXPANDED_WIDTH, 0));
        sidebar.setMinimumSize(new Dimension(SIDEBAR_COLLAPSED_WIDTH, 0));

        // ── Main split: sidebar | content ────────────────────────────────────
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, contentSplit);
        mainSplit.setDividerLocation(SIDEBAR_EXPANDED_WIDTH);
        mainSplit.setOneTouchExpandable(true);
        mainSplit.setContinuousLayout(true);
        mainSplit.setDividerSize(8);
        mainSplit.setPreferredSize(new Dimension(0, 0));
        mainSplit.setMinimumSize(new Dimension(0, 0));

        add(mainSplit, BorderLayout.CENTER);

        // ── Footer with GitHub credit ────────────────────────────────────────
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private void openRecordedRefMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem loadItem = new JMenuItem(recordedRef.isLoaded() ? "Replace Recorded Reference..." : "Load Recorded JMX or HAR...");
        loadItem.addActionListener(e -> loadRecordedReference());
        menu.add(loadItem);

        if (recordedRef.isLoaded()) {
            JMenuItem statusItem = new JMenuItem("Loaded: " + recordedRef.size() + " samples ("
                    + new File(recordedRef.getSourcePath()).getName() + ")");
            statusItem.setEnabled(false);
            menu.add(statusItem);

            JMenuItem clearItem = new JMenuItem("Clear Recorded Reference");
            clearItem.addActionListener(e -> {
                recordedRef.clear();
                aiChat.setRecordedReference(null);
                responseViewer.setRecordedReference(null);
                recordedRefBtn.setText("⋮ Recorded Ref");
                setStatus("Recorded reference cleared");
            });
            menu.add(clearItem);
        } else {
            JMenuItem helpItem = new JMenuItem("(no baseline loaded)");
            helpItem.setEnabled(false);
            menu.add(helpItem);
        }
        menu.show(recordedRefBtn, 0, recordedRefBtn.getHeight());
    }

    private void loadRecordedReference() {
        JFileChooser fc = new JFileChooser(lastRefDir);
        fc.setDialogTitle("Load Recorded Baseline (.jmx or .har)");
        fc.setFileFilter(new FileNameExtensionFilter("JMeter / HAR (*.jmx, *.har, *.xml)", "jmx", "har", "xml"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        lastRefDir = f.getParentFile();
        try {
            recordedRef.loadFrom(f);
            aiChat.setRecordedReference(recordedRef);
            responseViewer.setRecordedReference(recordedRef);
            recordedRefBtn.setText("⋮ Ref: " + recordedRef.size() + " samples");
            setStatus("Loaded " + recordedRef.size() + " baseline samples from " + f.getName());
            JOptionPane.showMessageDialog(this,
                    "Loaded " + recordedRef.size() + " baseline samples.\n" +
                            "AI Chat and Regex AI will now see both your live response and the recorded values.",
                    "Recorded Reference Loaded", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load: " + ex.getMessage(),
                    "Load failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(45, 45, 48));
        footer.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        JLabel info = new JLabel("GenAI Smart Correlation Listener — live AI-assisted correlation");
        info.setForeground(new Color(170, 170, 170));
        info.setFont(info.getFont().deriveFont(10f));
        footer.add(info, BorderLayout.WEST);

        JLabel credit = new JLabel("<html><a style='color:#9eccff'>Created by Sam Richard — github.com/Sam-Richard-007</a></html>");
        credit.setForeground(new Color(158, 204, 255));
        credit.setFont(credit.getFont().deriveFont(10f));
        credit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        credit.setToolTipText(GITHUB_URL);
        credit.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                try { Desktop.getDesktop().browse(new URI(GITHUB_URL)); } catch (Exception ignored) {}
            }
        });
        footer.add(credit, BorderLayout.EAST);
        return footer;
    }

    private void wireCallbacks() {
        // Sampler list selection → show in response viewer + push context to AI chat
        samplerList.setSelectionCallback(entry -> {
            responseViewer.showEntry(entry);
            aiChat.setContextEntry(entry);
        });

        // Response extract callback → update sampler list hints
        responseViewer.setExtractCallback((selectedText, body) -> {
            ResultEntry current = responseViewer.getCurrentEntry();
            if (current != null) {
                samplerList.updateRow(current.getIndex());
                setStatus("Manual extractor added for: " + selectedText);
            }
        });

        // Send Response → AI (attaches response, focuses sidebar)
        responseViewer.setSendToAiCallback(entry -> {
            if (!sidebarExpanded) toggleSidebar();
            aiChat.sendResponseToAI(entry, null);
            setStatus("Response attached to AI chat: " + entry.getSamplerName());
        });

        // Multi-group AI proposals → preview dialog
        responseViewer.setProposalCallback(proposals -> showProposalDialog(
                "Multi-Group Extractor — Review", proposals,
                applied -> applyProposalList(applied, "Multi-Group AI")));

        // AI chat: open proposal preview before any tree change
        aiChat.setProposalCallback(proposals -> showProposalDialog(
                "AI Chat — Review proposed changes", proposals, applied -> applyProposalList(applied, "AI chat")));
        aiChat.setActionCallback(action -> setStatus("AI: " + action.action + " → " + action.targetSampler));
        aiChat.setAllResultsSupplier(() -> new ArrayList<>(results));

        // Correlation engine status
        correlationEngine.setStatusCallback(msg -> SwingUtilities.invokeLater(() -> setStatus(msg)));
    }

    private void toggleSidebar() {
        if (sidebarExpanded) {
            mainSplit.setDividerLocation(SIDEBAR_COLLAPSED_WIDTH);
            sidebarToggleBtn.setText("▶ AI Chat");
            sidebarExpanded = false;
        } else {
            mainSplit.setDividerLocation(SIDEBAR_EXPANDED_WIDTH);
            sidebarToggleBtn.setText("◀ AI Chat");
            sidebarExpanded = true;
            aiChat.focusInput();
        }
    }

    /**
     * Called by SmartCorrelationListener.add() for each JMeter sample result.
     */
    public void addResult(org.apache.jmeter.samplers.SampleResult sampleResult) {
        int idx = counter.getAndIncrement();
        ResultEntry entry = new ResultEntry(idx, sampleResult);
        results.add(entry);

        correlationEngine.analyseEntry(entry);

        SwingUtilities.invokeLater(() -> {
            samplerList.addEntry(entry);
            setStatus(String.format("Captured %d results — %s [%d ms]",
                    results.size(), sampleResult.getSampleLabel(), sampleResult.getTime()));
        });
    }

    private void runAutoCorrelate() {
        if (results.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No results captured yet. Run your test first.",
                    "No Data", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        autoCorrelateBtn.setEnabled(false);
        setStatus("Running AI auto-correlation...");

        List<ResultEntry> snapshot = new ArrayList<>(results);

        SwingWorker<List<ChangeProposal>, Void> worker = new SwingWorker<>() {
            @Override protected List<ChangeProposal> doInBackground() {
                correlationEngine.analyseAll(snapshot);
                return buildProposals(snapshot);
            }
            @Override protected void done() {
                autoCorrelateBtn.setEnabled(true);
                samplerList.setEntries(snapshot);
                try {
                    List<ChangeProposal> proposals = get();
                    if (proposals.isEmpty()) {
                        setStatus("Auto-correlation: no new candidates found");
                        JOptionPane.showMessageDialog(SmartListenerPanel.this,
                                "AI found no new correlation candidates.",
                                "Auto-Correlate", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    long dups = proposals.stream().filter(p -> p.isDuplicate).count();
                    setStatus(String.format("Auto-correlation: %d proposed (%d already in tree)",
                            proposals.size(), dups));

                    showProposalDialog("Auto-Correlation — Review proposed extractors", proposals,
                            applied -> finishAutoCorrelate(snapshot, applied));
                } catch (Exception e) {
                    setStatus("Auto-correlation failed: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void runScriptFixer() {
        if (results.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No results captured yet. Run your test first.",
                    "No Data", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<ResultEntry> snapshot = new ArrayList<>(results);
        long failures = snapshot.stream().filter(r -> !r.isSuccess() || r.getStatusCode() >= 400).count();
        if (failures == 0) {
            JOptionPane.showMessageDialog(this,
                    "No failed samples in this run. Script Fixer is for analysing failures.",
                    "Nothing to fix", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        setStatus("Script Fixer: analysing " + failures + " failed sample(s)...");

        SwingWorker<List<ChangeProposal>, Void> worker = new SwingWorker<>() {
            @Override protected List<ChangeProposal> doInBackground() {
                List<String> samplerNames = LiveJMXModifier.getAllSamplerNames();
                return ScriptFixer.analyseAndPropose(snapshot, samplerNames, "");
            }
            @Override protected void done() {
                try {
                    List<ChangeProposal> proposals = get();
                    if (proposals.isEmpty()) {
                        setStatus("Script Fixer: no actionable fixes proposed");
                        JOptionPane.showMessageDialog(SmartListenerPanel.this,
                                "AI did not propose any concrete fixes. The failures may be server-side or non-correlation issues.",
                                "Script Fixer", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    setStatus(String.format("Script Fixer proposed %d fix(es)", proposals.size()));
                    showProposalDialog("Script Fixer — Review proposed fixes", proposals,
                            applied -> applyProposalList(applied, "Script Fixer"));
                } catch (Exception ex) {
                    setStatus("Script Fixer failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(SmartListenerPanel.this,
                            "Script Fixer failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /** Build proposals from each entry's extractor hints, marking duplicates. */
    private List<ChangeProposal> buildProposals(List<ResultEntry> snapshot) {
        List<ChangeProposal> out = new ArrayList<>();
        for (ResultEntry entry : snapshot) {
            for (ResultEntry.ExtractorHint hint : entry.getExtractorHints()) {
                if (hint.isApplied() || hint.getExpression() == null || hint.getExpression().isEmpty()) continue;
                ChangeProposal p = new ChangeProposal();
                p.targetSampler = entry.getSamplerName();
                p.variableName = hint.getVariableName();
                p.reasoning = hint.getReasoning();
                switch (hint.getType()) {
                    case JSON_PATH -> {
                        p.action = ChangeProposal.Action.ADD_JSONPATH;
                        p.expression = hint.getExpression();
                    }
                    case BOUNDARY -> {
                        p.action = ChangeProposal.Action.ADD_BOUNDARY;
                        String[] parts = hint.getExpression().split("\\s*\\|\\|\\s*", 2);
                        p.leftBoundary = parts.length > 0 ? parts[0] : "";
                        p.rightBoundary = parts.length > 1 ? parts[1] : "";
                        p.expression = hint.getExpression();
                    }
                    case GROOVY -> {
                        p.action = ChangeProposal.Action.ADD_JSR223_POST;
                        p.script = hint.getExpression();
                    }
                    default -> {
                        p.action = ChangeProposal.Action.ADD_REGEX;
                        p.expression = hint.getExpression();
                    }
                }
                p.checkDuplicate();
                out.add(p);
            }
        }
        return out;
    }

    private void finishAutoCorrelate(List<ResultEntry> snapshot, List<ChangeProposal> applied) {
        int ok = 0, fail = 0;
        for (ChangeProposal p : applied) {
            if (p.apply()) {
                ok++;
                // mark matching hint as applied
                for (ResultEntry entry : snapshot) {
                    if (!entry.getSamplerName().equals(p.targetSampler)) continue;
                    for (ResultEntry.ExtractorHint hint : entry.getExtractorHints()) {
                        if (hint.getVariableName().equals(p.variableName)) hint.setApplied(true);
                    }
                }
            } else fail++;
        }
        samplerList.setEntries(snapshot);
        setStatus(String.format("Applied %d extractor(s) to JMX tree (%d failed)", ok, fail));
        JOptionPane.showMessageDialog(this,
                String.format("Applied %d extractor(s) to your JMX tree.%s\n\nPress Ctrl+S in JMeter to save.",
                        ok, fail > 0 ? " " + fail + " failed." : ""),
                "Applied", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Apply a list of selected proposals and surface a summary. */
    private void applyProposalList(List<ChangeProposal> applied, String source) {
        int ok = 0, fail = 0;
        StringBuilder detail = new StringBuilder();
        for (ChangeProposal p : applied) {
            boolean success = p.apply();
            if (success) {
                ok++;
                detail.append("✓ ").append(p.displayAction())
                        .append(" → ").append(p.targetSampler == null ? "(global)" : p.targetSampler);
                if (p.variableName != null) detail.append(" [${").append(p.variableName).append("}]");
                detail.append("\n");
            } else {
                fail++;
                detail.append("✗ ").append(p.displayAction())
                        .append(" → failed on ").append(p.targetSampler).append("\n");
            }
        }
        setStatus(String.format("%s: applied %d, failed %d", source, ok, fail));
        JOptionPane.showMessageDialog(this,
                String.format("Applied %d change(s) to JMX (%d failed)\n\n%s\nPress Ctrl+S in JMeter to save.",
                        ok, fail, detail.toString()),
                "Applied", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Show the proposal preview dialog with a callback for applied items. */
    public void showProposalDialog(String title, List<ChangeProposal> proposals,
                                    java.util.function.Consumer<List<ChangeProposal>> onApply) {
        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
        java.awt.Frame parent = (w instanceof java.awt.Frame) ? (java.awt.Frame) w : null;
        ProposalPreviewDialog dlg = new ProposalPreviewDialog(parent, title, proposals);
        dlg.setApplyCallback(onApply);
        dlg.setVisible(true);
    }

    public void clearResults() {
        results.clear();
        counter.set(0);
        samplerList.clearAll();
        responseViewer.clear();
        setStatus("Results cleared");
    }

    private JButton toolbarBtn(String text, Color bg, java.awt.event.ActionListener al) {
        JButton btn = Theme.coloredButton(text, bg);
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.addActionListener(al);
        return btn;
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusBar.setText("  " + msg));
    }

    public List<ResultEntry> getResults() { return Collections.unmodifiableList(results); }
    public AIChatPanel getAIChatPanel() { return aiChat; }
}
