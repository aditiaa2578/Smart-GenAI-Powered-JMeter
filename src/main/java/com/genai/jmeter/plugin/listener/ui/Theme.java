package com.genai.jmeter.plugin.listener.ui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Theme-resilient button factory. JMeter's L&F may override foreground/background
 * colors on updateUI(), causing text to disappear on coloured buttons. This wraps
 * a JButton subclass that re-applies its colours after any L&F switch.
 */
public final class Theme {

    private Theme() {}

    /** Coloured button that keeps its colours through L&F switches (Darcula, etc.). */
    public static JButton coloredButton(String text, Color bg, Color fg) {
        return new ColoredButton(text, bg, fg);
    }

    public static JButton coloredButton(String text, Color bg) {
        return new ColoredButton(text, bg, Color.WHITE);
    }

    public static JLabel coloredLabel(String text, Color bg, Color fg) {
        JLabel lbl = new JLabel(text);
        lbl.setOpaque(true);
        lbl.setBackground(bg);
        lbl.setForeground(fg);
        return lbl;
    }

    private static class ColoredButton extends JButton {
        private final Color bg;
        private final Color fg;
        private final Border padding = BorderFactory.createEmptyBorder(4, 10, 4, 10);

        ColoredButton(String text, Color bg, Color fg) {
            super(text);
            this.bg = bg;
            this.fg = fg;
            applyColors();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            applyColors();
        }

        private void applyColors() {
            setBackground(bg);
            setForeground(fg);
            setOpaque(true);
            setContentAreaFilled(true);
            setBorderPainted(false);
            setFocusPainted(false);
            setBorder(padding);
            // Some L&Fs ignore background unless we set rolloverEnabled to false
            setRolloverEnabled(false);
        }
    }
}
