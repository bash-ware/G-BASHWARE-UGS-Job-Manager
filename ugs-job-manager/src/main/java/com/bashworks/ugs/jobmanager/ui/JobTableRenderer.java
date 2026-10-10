package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.model.JobEntry;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;

/**
 * Custom table cell renderer providing dark theme, badges, and clean alignment.
 */
public class JobTableRenderer extends DefaultTableCellRenderer {

    private static final Color BG_EVEN = UITheme.BG_PANEL;
    private static final Color BG_ODD = new Color(0x28, 0x2A, 0x33);
    private static final Color BG_SELECTED = new Color(0x35, 0x48, 0x5E);

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (c instanceof JLabel) {
            JLabel label = (JLabel) c;
            label.setFont(UITheme.FONT_BODY);
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

            if (isSelected) {
                label.setBackground(BG_SELECTED);
                label.setForeground(Color.WHITE);
            } else {
                label.setBackground((row % 2 == 0) ? BG_EVEN : BG_ODD);
                label.setForeground(UITheme.TEXT_PRIMARY);
            }

            if (column == 0 && value instanceof String) {
                // Status column
                String status = (String) value;
                if ("ACTIVE".equalsIgnoreCase(status)) {
                    label.setForeground(UITheme.ACCENT_TEAL);
                    label.setFont(UITheme.FONT_BODY_BOLD);
                } else {
                    label.setForeground(UITheme.TEXT_MUTED);
                }
            } else if (column == 3 || column == 4) {
                // Size or Date
                label.setHorizontalAlignment(RIGHT);
                if (!isSelected) {
                    label.setForeground(UITheme.TEXT_SECONDARY);
                }
            } else {
                label.setHorizontalAlignment(LEFT);
            }
        }

        return c;
    }
}
