package com.bashworks.ugs.jobmanager.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * Modern dark-mode styling tokens and helper components for G-BASHWARE plugins.
 */
public final class UITheme {

    public static final Color BG_DARK = new Color(0x10, 0x13, 0x19);
    public static final Color BG_PANEL = new Color(0x18, 0x1D, 0x25);
    public static final Color BG_CARD = new Color(0x20, 0x26, 0x30);
    public static final Color BG_CARD_ALT = new Color(0x2A, 0x32, 0x3F);
    public static final Color BG_INPUT = new Color(0x16, 0x17, 0x1D);

    public static final Color TEXT_PRIMARY = new Color(0xF2, 0xF4, 0xF8);
    public static final Color TEXT_SECONDARY = new Color(0xAE, 0xB3, 0xC2);
    public static final Color TEXT_MUTED = new Color(0x94, 0xA0, 0xB2);

    public static final Color ACCENT_CYAN = new Color(0x00, 0xB4, 0xD8);
    public static final Color ACCENT_TEAL = new Color(0x06, 0xD6, 0xA0);
    public static final Color ACCENT_AMBER = new Color(0xFF, 0xD1, 0x66);
    public static final Color ACCENT_RED = new Color(0xEF, 0x47, 0x6F);
    public static final Color ACCENT_BLUE = new Color(0x11, 0x8A, 0xB2);

    public static final Color BORDER_COLOR = new Color(0x30, 0x39, 0x47);
    public static final Color BORDER_ACTIVE = new Color(0x00, 0xB4, 0xD8);

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_SMALL_BOLD = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_CODE = new Font("Consolas", Font.PLAIN, 13);

    public static Color statusColor(String status) {
        if (status.startsWith("NEXT")) return ACCENT_CYAN;
        if ("RUNNING".equals(status)) return ACCENT_TEAL;
        if ("PAUSED".equals(status) || "STOPPED".equals(status)) return ACCENT_AMBER;
        if ("FINISHED".equals(status)) return new Color(0x9B, 0xB1, 0xC7);
        return TEXT_MUTED;
    }

    private UITheme() {
    }

    public static JPanel createCardPanel() {
        JPanel p = new JPanel();
        p.setBackground(BG_CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(BORDER_COLOR, 8),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        return p;
    }

    public static JLabel createHeaderLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_HEADER);
        l.setForeground(TEXT_PRIMARY);
        return l;
    }

    public static JLabel createMutedLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_SMALL);
        l.setForeground(TEXT_MUTED);
        return l;
    }

    public static JLabel createBadge(String text, Color bgColor, Color textColor) {
        JLabel badge = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setOpaque(false);
        badge.setForeground(textColor);
        badge.setFont(FONT_SMALL_BOLD);
        badge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return badge;
    }

    public static class RoundedBorder implements Border {
        private final Color color;
        private final int radius;

        public RoundedBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }
}
