package com.genai.jmeter.plugin.ui.panels;

import com.genai.jmeter.plugin.ai.AIProvider;
import com.genai.jmeter.plugin.ai.AIProviderFactory;
import com.genai.jmeter.plugin.ai.providers.MetaProvider;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * AI provider configuration panel — embedded in the main toolbar.
 */
public class AISettingsPanel extends JDialog {

    private final AIProviderFactory factory = AIProviderFactory.getInstance();
    private final Map<String, JPasswordField> apiKeyFields = new HashMap<>();
    private final Map<String, JComboBox<String>> modelCombos = new HashMap<>();

    public AISettingsPanel(Frame parent) {
        super(parent, "AI Provider Settings", true);
        setSize(600, 500);
        setLocationRelativeTo(parent);
        build();
    }

    private void build() {
        setLayout(new BorderLayout(10, 10));

        JLabel title = new JLabel("Configure AI Providers", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
        add(title, BorderLayout.NORTH);

        JPanel providersPanel = new JPanel();
        providersPanel.setLayout(new BoxLayout(providersPanel, BoxLayout.Y_AXIS));
        providersPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        for (AIProvider provider : factory.getAllProviders()) {
            providersPanel.add(buildProviderCard(provider));
            providersPanel.add(Box.createVerticalStrut(10));
        }

        JScrollPane scroll = new JScrollPane(providersPanel);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        // Active provider selector
        JPanel activePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        activePanel.setBorder(BorderFactory.createTitledBorder("Active Provider (used for Correlate + Assess)"));
        JComboBox<String> activeCombo = new JComboBox<>(factory.getProviderNames().toArray(new String[0]));
        activeCombo.setSelectedItem(factory.getActiveProvider().getName());
        activePanel.add(new JLabel("Active:"));
        activePanel.add(activeCombo);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton testBtn = new JButton("Test Active Provider");
        testBtn.addActionListener(e -> testProvider());
        JButton saveBtn = new JButton("Save");
        saveBtn.setBackground(new Color(0, 120, 215));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setOpaque(true);
        saveBtn.addActionListener(e -> {
            saveSettings(activeCombo);
            dispose();
        });
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(testBtn);
        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.add(activePanel, BorderLayout.NORTH);
        south.add(btnPanel, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
    }

    private JPanel buildProviderCard(AIProvider provider) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), provider.getName()));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        card.add(new JLabel("API Key:"), gbc);
        JPasswordField keyField = new JPasswordField(provider.getApiKey(), 30);
        apiKeyFields.put(provider.getId(), keyField);
        gbc.gridx = 1; gbc.weightx = 1;
        card.add(keyField, gbc);

        JButton showHide = new JButton("Show");
        showHide.addActionListener(e -> {
            if ("Show".equals(showHide.getText())) {
                keyField.setEchoChar((char) 0);
                showHide.setText("Hide");
            } else {
                keyField.setEchoChar('•');
                showHide.setText("Show");
            }
        });
        gbc.gridx = 2; gbc.weightx = 0;
        card.add(showHide, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        card.add(new JLabel("Model:"), gbc);
        JComboBox<String> modelCombo = new JComboBox<>(provider.getAvailableModels().toArray(new String[0]));
        modelCombo.setSelectedItem(provider.getSelectedModel());
        modelCombos.put(provider.getId(), modelCombo);
        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        card.add(modelCombo, gbc);

        // Meta-specific backend selector
        if (provider instanceof MetaProvider metaProvider) {
            gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0;
            card.add(new JLabel("Backend:"), gbc);
            JComboBox<String> backendCombo = new JComboBox<>(new String[]{"Meta Official API", "Together AI (Llama)"});
            backendCombo.setSelectedIndex(metaProvider.getBackend() == MetaProvider.Backend.TOGETHER_AI ? 1 : 0);
            backendCombo.addActionListener(e -> {
                MetaProvider.Backend b = backendCombo.getSelectedIndex() == 0
                        ? MetaProvider.Backend.META_OFFICIAL : MetaProvider.Backend.TOGETHER_AI;
                metaProvider.setBackend(b);
                modelCombo.removeAllItems();
                metaProvider.getAvailableModels().forEach(m -> modelCombo.addItem(m));
                modelCombo.setSelectedIndex(0);
            });
            gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1;
            card.add(backendCombo, gbc);
        }

        // Info label
        String info = switch (provider.getId()) {
            case "groq" -> "Free tier available at console.groq.com — fastest inference";
            case "gemini" -> "Get API key at aistudio.google.com — generous free quota";
            case "meta" -> "Meta Llama API at llama.meta.com | Together AI at api.together.xyz";
            default -> "";
        };
        if (!info.isEmpty()) {
            JLabel infoLabel = new JLabel("<html><i>" + info + "</i></html>");
            infoLabel.setForeground(Color.GRAY);
            infoLabel.setFont(infoLabel.getFont().deriveFont(10f));
            gbc.gridx = 0; gbc.gridy = card.getComponentCount(); gbc.gridwidth = 3; gbc.weightx = 1;
            card.add(infoLabel, gbc);
        }

        return card;
    }

    private void saveSettings(JComboBox<String> activeCombo) {
        for (AIProvider provider : factory.getAllProviders()) {
            JPasswordField keyField = apiKeyFields.get(provider.getId());
            JComboBox<String> modelCombo = modelCombos.get(provider.getId());
            if (keyField != null) provider.setApiKey(new String(keyField.getPassword()));
            if (modelCombo != null) provider.setSelectedModel((String) modelCombo.getSelectedItem());
        }
        String activeName = (String) activeCombo.getSelectedItem();
        AIProvider active = factory.getProviderByName(activeName);
        if (active != null) factory.setActiveProvider(active.getId());
    }

    private void testProvider() {
        AIProvider active = factory.getActiveProvider();
        if (!active.isConfigured()) {
            JOptionPane.showMessageDialog(this, "No API key set for " + active.getName(),
                    "Not Configured", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override protected Boolean doInBackground() {
                try {
                    return active.chat("You are a test assistant.", "Reply with exactly: OK").isSuccess();
                } catch (Exception e) { return false; }
            }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    boolean ok = get();
                    JOptionPane.showMessageDialog(AISettingsPanel.this,
                            ok ? "✓ " + active.getName() + " is working correctly!"
                               : "✗ Connection failed. Check your API key.",
                            "Connection Test", ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(AISettingsPanel.this, "Test failed: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
