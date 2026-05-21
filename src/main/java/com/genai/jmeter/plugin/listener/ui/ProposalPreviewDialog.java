package com.genai.jmeter.plugin.listener.ui;

import com.genai.jmeter.plugin.listener.ChangeProposal;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shows a table of pending changes with checkboxes, editable variable names,
 * and a marker for duplicates that already exist in the tree.
 * User reviews, edits, deselects what they don't want, then clicks Apply Selected.
 */
public class ProposalPreviewDialog extends JDialog {

    private final List<ChangeProposal> proposals;
    private final ProposalTableModel model;
    private final JTable table;
    private Consumer<List<ChangeProposal>> applyCallback;
    private final JLabel summaryLabel;
    private final JTextArea detailArea;

    public ProposalPreviewDialog(Frame parent, String title, List<ChangeProposal> proposals) {
        super(parent, title, true);
        this.proposals = new ArrayList<>(proposals);
        this.model = new ProposalTableModel(this.proposals);
        this.table = new JTable(model);
        this.summaryLabel = new JLabel();
        this.detailArea = new JTextArea(4, 0);

        setSize(900, 600);
        setLocationRelativeTo(parent);
        build();
        updateSummary();
    }

    private void build() {
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── Header ─────────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Review proposed changes before applying to JMX");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        header.add(title, BorderLayout.NORTH);

        summaryLabel.setFont(summaryLabel.getFont().deriveFont(11f));
        summaryLabel.setForeground(new Color(80, 80, 100));
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        header.add(summaryLabel, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);

        // ── Table ──────────────────────────────────────────────────────────────
        table.setRowHeight(26);
        table.setShowGrid(true);
        table.setGridColor(new Color(220, 220, 220));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setFillsViewportHeight(true);

        TableColumn applyCol = table.getColumnModel().getColumn(0);
        applyCol.setMaxWidth(50);
        applyCol.setMinWidth(50);

        TableColumn dupCol = table.getColumnModel().getColumn(1);
        dupCol.setMaxWidth(30);
        dupCol.setMinWidth(30);
        dupCol.setCellRenderer(new DupRenderer());

        table.getColumnModel().getColumn(2).setPreferredWidth(130);  // Type
        table.getColumnModel().getColumn(3).setPreferredWidth(200);  // Sampler
        table.getColumnModel().getColumn(4).setPreferredWidth(140);  // VarName
        table.getColumnModel().getColumn(5).setPreferredWidth(300);  // Detail

        table.setDefaultRenderer(Object.class, new RowColorRenderer());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelectionDetail();
        });

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createTitledBorder("Proposed changes (uncheck the ones you don't want)"));
        add(sp, BorderLayout.CENTER);

        // ── Detail + action buttons ────────────────────────────────────────────
        JPanel bottom = new JPanel(new BorderLayout(0, 6));

        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        detailArea.setBackground(new Color(248, 248, 252));
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createTitledBorder("Selected change details"));
        detailScroll.setPreferredSize(new Dimension(0, 100));
        bottom.add(detailScroll, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        JButton selectAllBtn = new JButton("Select All (non-duplicate)");
        selectAllBtn.addActionListener(e -> {
            for (ChangeProposal p : proposals) p.selected = !p.isDuplicate;
            model.fireTableDataChanged();
            updateSummary();
        });

        JButton deselectAllBtn = new JButton("Deselect All");
        deselectAllBtn.addActionListener(e -> {
            for (ChangeProposal p : proposals) p.selected = false;
            model.fireTableDataChanged();
            updateSummary();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        JButton applyBtn = Theme.coloredButton("✓ Apply Selected to JMX", new Color(0, 120, 60));
        applyBtn.setFont(applyBtn.getFont().deriveFont(Font.BOLD));
        applyBtn.addActionListener(e -> doApply());

        btnPanel.add(selectAllBtn);
        btnPanel.add(deselectAllBtn);
        btnPanel.add(Box.createHorizontalStrut(20));
        btnPanel.add(cancelBtn);
        btnPanel.add(applyBtn);

        bottom.add(btnPanel, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        if (!proposals.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
            showSelectionDetail();
        }
    }

    private void showSelectionDetail() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= proposals.size()) { detailArea.setText(""); return; }
        ChangeProposal p = proposals.get(row);
        StringBuilder sb = new StringBuilder();
        sb.append("Action:   ").append(p.displayAction()).append("\n");
        sb.append("Sampler:  ").append(p.targetSampler == null ? "(global)" : p.targetSampler).append("\n");
        if (p.variableName != null) sb.append("Variable: ${").append(p.variableName).append("}\n");
        if (p.expression != null && !p.expression.isEmpty()) sb.append("Expr:     ").append(p.expression).append("\n");
        if (p.leftBoundary != null && !p.leftBoundary.isEmpty()) sb.append("Left:     ").append(p.leftBoundary).append("\n");
        if (p.rightBoundary != null && !p.rightBoundary.isEmpty()) sb.append("Right:    ").append(p.rightBoundary).append("\n");
        if (p.script != null && !p.script.isEmpty()) sb.append("Script (").append(p.language).append("):\n").append(p.script).append("\n");
        if (p.assertionContains != null) sb.append("Contains: ").append(p.assertionContains).append("\n");
        if (p.delayMs > 0 && p.action == ChangeProposal.Action.ADD_TIMER) sb.append("Delay:    ").append(p.delayMs).append(" ms\n");
        if (p.isDuplicate) sb.append("\n⚠ DUPLICATE — already exists as: ").append(p.duplicateOf).append("\n");
        if (p.reasoning != null && !p.reasoning.isEmpty()) sb.append("\nReasoning: ").append(p.reasoning).append("\n");
        if (p.explanation != null && !p.explanation.isEmpty()) sb.append("\n").append(p.explanation);
        detailArea.setText(sb.toString());
        detailArea.setCaretPosition(0);
    }

    private void updateSummary() {
        long sel = proposals.stream().filter(p -> p.selected).count();
        long dup = proposals.stream().filter(p -> p.isDuplicate).count();
        summaryLabel.setText(String.format(" %d total proposed | %d selected | %d duplicate(s) skipped",
                proposals.size(), sel, dup));
    }

    private void doApply() {
        List<ChangeProposal> selected = new ArrayList<>();
        for (ChangeProposal p : proposals) if (p.selected) selected.add(p);
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Nothing selected. Tick at least one row, or click Cancel.",
                    "Nothing to apply", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (applyCallback != null) applyCallback.accept(selected);
        dispose();
    }

    public void setApplyCallback(Consumer<List<ChangeProposal>> cb) { this.applyCallback = cb; }

    // ── Table model ────────────────────────────────────────────────────────────

    private class ProposalTableModel extends AbstractTableModel {
        private final String[] COLS = {"Apply", "!", "Type", "Sampler", "Variable", "Detail"};
        private final List<ChangeProposal> data;

        ProposalTableModel(List<ChangeProposal> data) { this.data = data; }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 0 || col == 4;  // Apply checkbox and Variable name
        }

        @Override
        public Object getValueAt(int row, int col) {
            ChangeProposal p = data.get(row);
            return switch (col) {
                case 0 -> p.selected;
                case 1 -> p.isDuplicate ? "⚠" : "";
                case 2 -> p.displayAction();
                case 3 -> p.targetSampler == null ? "(global)" : p.targetSampler;
                case 4 -> p.variableName == null ? "" : p.variableName;
                case 5 -> p.displayDetail();
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            ChangeProposal p = data.get(row);
            if (col == 0) {
                p.selected = Boolean.TRUE.equals(value);
            } else if (col == 4) {
                p.variableName = value != null ? value.toString() : "";
                // Re-check duplicate with new name
                p.isDuplicate = false;
                p.duplicateOf = null;
                p.checkDuplicate();
            }
            fireTableRowsUpdated(row, row);
            updateSummary();
        }
    }

    private static class RowColorRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc,
                                                       int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            if (sel) return c;
            ProposalTableModel m = (ProposalTableModel) t.getModel();
            ChangeProposal p = m.data.get(row);
            if (p.isDuplicate) {
                c.setBackground(new Color(255, 240, 200));
                c.setForeground(new Color(120, 80, 0));
            } else if (!p.selected) {
                c.setBackground(new Color(245, 245, 245));
                c.setForeground(Color.GRAY);
            } else {
                c.setBackground(Color.WHITE);
                c.setForeground(Color.BLACK);
            }
            return c;
        }
    }

    private static class DupRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc,
                                                       int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            setHorizontalAlignment(CENTER);
            if (!sel) {
                ProposalTableModel m = (ProposalTableModel) t.getModel();
                ChangeProposal p = m.data.get(row);
                c.setForeground(p.isDuplicate ? new Color(200, 100, 0) : Color.BLACK);
                c.setBackground(p.isDuplicate ? new Color(255, 240, 200) : Color.WHITE);
            }
            return c;
        }
    }
}
