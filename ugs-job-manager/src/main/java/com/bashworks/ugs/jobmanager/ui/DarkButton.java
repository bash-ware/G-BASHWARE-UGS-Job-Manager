package com.bashworks.ugs.jobmanager.ui;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Modern styled dark button with custom colors, hover, and press states.
 */
public class DarkButton extends JButton {

    public enum ButtonStyle {
        PRIMARY(UITheme.ACCENT_TEAL, new Color(0x05, 0xB8, 0x8A), new Color(0x1B, 0x1C, 0x22)),
        ACCENT(UITheme.ACCENT_CYAN, new Color(0x00, 0x96, 0xB4), new Color(0x1B, 0x1C, 0x22)),
        SECONDARY(UITheme.BG_CARD_ALT, new Color(0x40, 0x43, 0x54), UITheme.TEXT_PRIMARY),
        DANGER(UITheme.ACCENT_RED, new Color(0xD6, 0x36, 0x5B), Color.WHITE),
        WARNING(UITheme.ACCENT_AMBER, new Color(0xE5, 0xBA, 0x50), new Color(0x1B, 0x1C, 0x22));

        final Color normalBg;
        final Color hoverBg;
        final Color textColor;

        ButtonStyle(Color normalBg, Color hoverBg, Color textColor) {
            this.normalBg = normalBg;
            this.hoverBg = hoverBg;
            this.textColor = textColor;
        }
    }

    private final ButtonStyle style;
    private boolean isHovered = false;
    private boolean isPressed = false;
    private int cornerRadius = 6;

    public DarkButton(String text, ButtonStyle style) {
        super(text);
        this.style = style != null ? style : ButtonStyle.SECONDARY;

        setFont(UITheme.FONT_BODY_BOLD);
        setForeground(this.style.textColor);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    isHovered = true;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    isPressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });
    }

    public DarkButton(String text) {
        this(text, ButtonStyle.SECONDARY);
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bg;
        if (!isEnabled()) {
            bg = new Color(0x2A, 0x2B, 0x33);
            setForeground(UITheme.TEXT_MUTED);
        } else if (isPressed) {
            bg = style.hoverBg.darker();
            setForeground(style.textColor);
        } else if (isHovered) {
            bg = style.hoverBg;
            setForeground(style.textColor);
        } else {
            bg = style.normalBg;
            setForeground(style.textColor);
        }

        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);

        // Subtle border
        if (isEnabled() && style == ButtonStyle.SECONDARY) {
            g2.setColor(UITheme.BORDER_COLOR);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);
        }

        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width + 16, 70), Math.max(d.height + 6, 28));
    }
}
