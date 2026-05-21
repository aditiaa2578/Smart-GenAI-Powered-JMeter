package com.genai.jmeter.plugin.listener.ui;

import com.genai.jmeter.plugin.listener.ResultEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Left panel showing captured sampler results in a table.
 */
public class SamplerListPanel extends JPanel {

    private final DefaultTableModel model;
    private final JTable table;
    private Consumer<ResultEntry> selectionCallback;
    private List<ResultEntry> entries;

    public SamplerListPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Captured Results"));

        String[] cols = {"#", "Sampler", "Status", "ms", "✓"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return c == 0 || c == 2 || c == 3 ? Integer.class : String.class;
            }
        };

        table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(30);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.getColumnModel().getColumn(2).setPreferredWidth(50);
        table.getColumnModel().getColumn(3).setPreferredWidth(55);
        table.getColumnModel().getColumn(4).setPreferredWidth(25);

        // Color rows by status
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                if (!sel) {
                    Object code = model.getValueAt(r, 2);
                    int status = code instanceof Integer ? (Integer) code : 0;
                    if (status >= 200 && status < 300) comp.setBackground(new Color(235, 255, 235));
                    else if (status >= 400) comp.setBackground(new Color(255, 235, 235));
                    else if (status >= 300) comp.setBackground(new Color(255, 252, 220));
                    else comp.setBackground(Color.WHITE);
                }
                return comp;
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && selectionCallback != null && entries != null) {
                int row = table.getSelectedRow();
                if (row >= 0 && row < entries.size()) {
                    selectionCallback.accept(entries.get(row));
                }
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        JLabel tip = new JLabel("  Click a row to inspect request/response");
        tip.setFont(tip.getFont().deriveFont(10f));
        tip.setForeground(Color.GRAY);
        add(tip, BorderLayout.SOUTH);
    }

    public void setEntries(List<ResultEntry> entries) {
        this.entries = entries;
        model.setRowCount(0);
        for (ResultEntry e : entries) {
            String hints = e.getExtractorHints().isEmpty() ? "" :
                    e.getExtractorHints().size() + " hint" + (e.getExtractorHints().size() > 1 ? "s" : "");
            model.addRow(new Object[]{
                    e.getIndex() + 1,
                    e.getSamplerName().length() > 35
                            ? e.getSamplerName().substring(0, 35) + "…" : e.getSamplerName(),
                    e.getStatusCode(),
                    e.getElapsedTime(),
                    hints
            });
        }
    }

    public void addEntry(ResultEntry entry) {
        String hints = entry.getExtractorHints().isEmpty() ? "" :
                entry.getExtractorHints().size() + " hints";
        model.addRow(new Object[]{
                entry.getIndex() + 1,
                entry.getSamplerName().length() > 35
                        ? entry.getSamplerName().substring(0, 35) + "…" : entry.getSamplerName(),
                entry.getStatusCode(),
                entry.getElapsedTime(),
                hints
        });
        int last = model.getRowCount() - 1;
        table.scrollRectToVisible(table.getCellRect(last, 0, true));
    }

    public void updateRow(int index) {
        if (entries == null || index >= entries.size()) return;
        ResultEntry e = entries.get(index);
        String hints = e.getExtractorHints().isEmpty() ? "" :
                e.getExtractorHints().size() + " hint" + (e.getExtractorHints().size() > 1 ? "s" : "");
        if (index < model.getRowCount()) model.setValueAt(hints, index, 4);
    }

    public void clearAll() {
        model.setRowCount(0);
        this.entries = null;
    }

    public void setSelectionCallback(Consumer<ResultEntry> cb) { this.selectionCallback = cb; }
    public JTable getTable() { return table; }
}
