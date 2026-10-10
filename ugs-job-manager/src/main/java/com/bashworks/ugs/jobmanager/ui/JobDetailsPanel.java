package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.machine.JobManagerGateway;
import com.bashworks.ugs.jobmanager.model.JobAnalysis;
import com.bashworks.ugs.jobmanager.model.JobBounds;
import com.bashworks.ugs.jobmanager.model.JobEntry;
import com.bashworks.ugs.jobmanager.service.GcodeAnalyzer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.List;

/**
 * Inspector and Activation control panel for the currently selected CNC job.
 */
public class JobDetailsPanel extends JPanel {

    private final JobManagerGateway gateway;
    private final GcodeAnalyzer analyzer;
    private JobEntry currentJob;

    private final JLabel titleLabel = new JLabel("No Job Selected");
    private final JLabel sourceBadge = UITheme.createBadge("NONE", UITheme.BG_CARD_ALT, UITheme.TEXT_MUTED);
    private final JLabel activeBadge = UITheme.createBadge("INACTIVE", UITheme.BG_CARD_ALT, UITheme.TEXT_MUTED);
    private final JLabel feedbackLabel = new JLabel(" ");

    // Stat labels
    private final JLabel valDimensions = new JLabel("—");
    private final JLabel valRange = new JLabel("—");
    private final JLabel valLines = new JLabel("—");
    private final JLabel valTools = new JLabel("—");
    private final JLabel valSpindle = new JLabel("—");
    private final JLabel valFeed = new JLabel("—");
    private final JLabel valSize = new JLabel("—");
    private final JLabel valDate = new JLabel("—");

    // Preview
    private final JTextArea previewTextArea = new JTextArea();
    private final JScrollPane previewScrollPane;

    // Action buttons
    private final DarkButton btnActivate = new DarkButton("⚡ Load into UGS (Activate)", DarkButton.ButtonStyle.PRIMARY);
    private final DarkButton btnRun = new DarkButton("▶ Start in UGS", DarkButton.ButtonStyle.ACCENT);
    private final DarkButton btnUnload = new DarkButton("✕ Unload", DarkButton.ButtonStyle.SECONDARY);
    private final DarkButton btnExplorer = new DarkButton("📂 Show in Folder", DarkButton.ButtonStyle.SECONDARY);
    private final DarkButton btnCopyPath = new DarkButton("📋 Copy Path", DarkButton.ButtonStyle.SECONDARY);

    public JobDetailsPanel(JobManagerGateway gateway, GcodeAnalyzer analyzer) {
        this.gateway = gateway;
        this.analyzer = analyzer != null ? analyzer : new GcodeAnalyzer();

        setLayout(new BorderLayout(8, 8));
        setBackground(UITheme.BG_PANEL);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        headerPanel.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titleRow.setOpaque(false);
        titleLabel.setFont(UITheme.FONT_TITLE);
        titleLabel.setForeground(UITheme.TEXT_PRIMARY);
        titleRow.add(titleLabel);
        titleRow.add(sourceBadge);
        titleRow.add(activeBadge);

        headerPanel.add(titleRow, BorderLayout.NORTH);

        feedbackLabel.setFont(UITheme.FONT_SMALL);
        feedbackLabel.setForeground(UITheme.ACCENT_TEAL);
        headerPanel.add(feedbackLabel, BorderLayout.SOUTH);

        // Center: Stats grid + Preview
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        JPanel statsCard = createStatsGrid();
        centerPanel.add(statsCard);
        centerPanel.add(Box.createVerticalStrut(8));

        // Preview Card
        JPanel previewCard = new JPanel(new BorderLayout(4, 4));
        previewCard.setBackground(UITheme.BG_CARD);
        previewCard.setBorder(BorderFactory.createCompoundBorder(
                new UITheme.RoundedBorder(UITheme.BORDER_COLOR, 8),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        JLabel previewHeader = new JLabel("G-Code Header Preview (First 50 lines):");
        previewHeader.setFont(UITheme.FONT_SMALL_BOLD);
        previewHeader.setForeground(UITheme.TEXT_SECONDARY);
        previewCard.add(previewHeader, BorderLayout.NORTH);

        previewTextArea.setFont(UITheme.FONT_CODE);
        previewTextArea.setBackground(UITheme.BG_INPUT);
        previewTextArea.setForeground(new Color(0xD0, 0xD4, 0xE0));
        previewTextArea.setCaretColor(UITheme.ACCENT_CYAN);
        previewTextArea.setEditable(false);
        previewTextArea.setRows(8);

        previewScrollPane = new JScrollPane(previewTextArea);
        previewScrollPane.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_COLOR, 1));
        previewCard.add(previewScrollPane, BorderLayout.CENTER);

        centerPanel.add(previewCard);

        // Buttons Panel
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionsPanel.setOpaque(false);
        actionsPanel.add(btnActivate);
        actionsPanel.add(btnRun);
        actionsPanel.add(btnUnload);
        actionsPanel.add(btnExplorer);
        actionsPanel.add(btnCopyPath);

        add(headerPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(actionsPanel, BorderLayout.SOUTH);

        setupListeners();
        updateUIState();
    }

    private JPanel createStatsGrid() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(UITheme.BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new UITheme.RoundedBorder(UITheme.BORDER_COLOR, 8),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 12);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;

        // Row 0
        addStatCell(card, gbc, 0, 0, "Dimensions:", valDimensions);
        addStatCell(card, gbc, 2, 0, "Line Count:", valLines);
        addStatCell(card, gbc, 4, 0, "File Size:", valSize);

        // Row 1
        addStatCell(card, gbc, 0, 1, "Tools Used:", valTools);
        addStatCell(card, gbc, 2, 1, "Spindle Speed:", valSpindle);
        addStatCell(card, gbc, 4, 1, "Max Feed Rate:", valFeed);

        // Row 2 (Spanning)
        gbc.gridx = 0;
        gbc.gridy = 2;
        JLabel lblRange = new JLabel("Motion Range:");
        lblRange.setFont(UITheme.FONT_SMALL_BOLD);
        lblRange.setForeground(UITheme.TEXT_MUTED);
        card.add(lblRange, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 5;
        valRange.setFont(UITheme.FONT_SMALL);
        valRange.setForeground(UITheme.TEXT_SECONDARY);
        card.add(valRange, gbc);

        return card;
    }

    private void addStatCell(JPanel panel, GridBagConstraints gbc, int col, int row, String label, JLabel valLabel) {
        gbc.gridx = col;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        JLabel l = new JLabel(label);
        l.setFont(UITheme.FONT_SMALL_BOLD);
        l.setForeground(UITheme.TEXT_MUTED);
        panel.add(l, gbc);

        gbc.gridx = col + 1;
        valLabel.setFont(UITheme.FONT_BODY_BOLD);
        valLabel.setForeground(UITheme.TEXT_PRIMARY);
        panel.add(valLabel, gbc);
    }

    private void setupListeners() {
        btnActivate.addActionListener(e -> activateCurrentJob());
        btnRun.addActionListener(e -> runCurrentJob());
        btnUnload.addActionListener(e -> unloadJob());
        btnExplorer.addActionListener(e -> showInExplorer());
        btnCopyPath.addActionListener(e -> copyPathToClipboard());
    }

    public void setJob(JobEntry job) {
        this.currentJob = job;
        if (job != null && job.getAnalysis() == null && job.getFile().exists()) {
            job.setAnalysis(analyzer.analyze(job.getFile()));
        }
        updateUIState();
    }

    public void refreshActiveState(File activeFile) {
        if (currentJob != null && activeFile != null) {
            boolean active = currentJob.getFile().getAbsolutePath().equalsIgnoreCase(activeFile.getAbsolutePath());
            currentJob.setActiveInUgs(active);
        } else if (currentJob != null) {
            currentJob.setActiveInUgs(false);
        }
        updateUIState();
    }

    private void updateUIState() {
        if (currentJob == null) {
            titleLabel.setText("No Job Selected");
            sourceBadge.setText("NONE");
            activeBadge.setText("INACTIVE");
            valDimensions.setText("—");
            valRange.setText("—");
            valLines.setText("—");
            valTools.setText("—");
            valSpindle.setText("—");
            valFeed.setText("—");
            valSize.setText("—");
            previewTextArea.setText("");

            btnActivate.setEnabled(false);
            btnRun.setEnabled(false);
            btnUnload.setEnabled(false);
            btnExplorer.setEnabled(false);
            btnCopyPath.setEnabled(false);
            return;
        }

        titleLabel.setText(currentJob.getName());
        sourceBadge.setText(currentJob.getOrigin().getLabel().toUpperCase());
        sourceBadge.setForeground(UITheme.ACCENT_CYAN);

        boolean isActive = false;
        File activeUgsFile = gateway.getActiveGcodeFile();
        if (activeUgsFile != null && currentJob.getFile().exists()) {
            isActive = activeUgsFile.getAbsolutePath().equalsIgnoreCase(currentJob.getFile().getAbsolutePath());
        }
        currentJob.setActiveInUgs(isActive);

        if (isActive) {
            activeBadge.setText("● ACTIVE IN UGS");
            activeBadge.setForeground(UITheme.ACCENT_TEAL);
        } else {
            activeBadge.setText("READY");
            activeBadge.setForeground(UITheme.TEXT_MUTED);
        }

        JobAnalysis a = currentJob.getAnalysis();
        if (a != null) {
            valDimensions.setText(a.getBounds().toDimensionsString(a.getUnits()));
            valRange.setText(a.getBounds().toDetailedRangeString(a.getUnits()));
            valLines.setText(String.format("%,d (Motion: %,d)", a.getTotalLines(), a.getMotionLines()));
            valTools.setText(a.getToolsSummary());
            valSpindle.setText(a.getSpindleSummary());
            valFeed.setText(a.getFeedRateSummary());

            // Build preview
            StringBuilder sb = new StringBuilder();
            List<String> lines = a.getPreviewLines();
            for (int i = 0; i < lines.size(); i++) {
                sb.append(String.format("%3d | %s\n", i + 1, lines.get(i)));
            }
            previewTextArea.setText(sb.toString());
            previewTextArea.setCaretPosition(0);
        } else {
            valDimensions.setText("—");
            valRange.setText("—");
            valLines.setText("—");
            valTools.setText("—");
            valSpindle.setText("—");
            valFeed.setText("—");
            previewTextArea.setText("");
        }

        valSize.setText(currentJob.getFormattedSize());

        btnActivate.setEnabled(true);
        btnExplorer.setEnabled(currentJob.getFile().exists());
        btnCopyPath.setEnabled(true);
        btnUnload.setEnabled(isActive);

        boolean canRun = isActive && gateway.isConnected() && gateway.isIdle() && !gateway.isSendingFile();
        btnRun.setEnabled(canRun);
    }

    private void activateCurrentJob() {
        if (currentJob == null || !currentJob.getFile().exists()) {
            setFeedback("Error: Selected file does not exist on disk", true);
            return;
        }
        try {
            gateway.loadJobIntoUgs(currentJob.getFile());
            currentJob.setActiveInUgs(true);
            setFeedback("Job loaded and activated in UGS: " + currentJob.getName(), false);
            updateUIState();
        } catch (Exception ex) {
            setFeedback("Activation failed: " + ex.getMessage(), true);
            JOptionPane.showMessageDialog(this, "Failed to load job into UGS:\n" + ex.getMessage(),
                    "UGS Job Activation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void runCurrentJob() {
        try {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Start streaming '" + currentJob.getName() + "' to the machine?",
                    "Confirm Job Start", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                gateway.startJobInUgs();
                setFeedback("Started streaming job to machine", false);
                updateUIState();
            }
        } catch (Exception ex) {
            setFeedback("Run failed: " + ex.getMessage(), true);
            JOptionPane.showMessageDialog(this, "Failed to start job:\n" + ex.getMessage(),
                    "Start Job Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void unloadJob() {
        try {
            gateway.unsetActiveJob();
            setFeedback("Job unloaded from UGS", false);
            updateUIState();
        } catch (Exception ex) {
            setFeedback("Unload failed: " + ex.getMessage(), true);
        }
    }

    private void showInExplorer() {
        if (currentJob == null || !currentJob.getFile().exists()) return;
        try {
            Desktop.getDesktop().open(currentJob.getFile().getParentFile());
        } catch (Exception ex) {
            setFeedback("Could not open folder: " + ex.getMessage(), true);
        }
    }

    private void copyPathToClipboard() {
        if (currentJob == null) return;
        try {
            String path = currentJob.getFile().getAbsolutePath();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(path), null);
            setFeedback("Path copied to clipboard", false);
        } catch (Exception ex) {
            setFeedback("Failed to copy path: " + ex.getMessage(), true);
        }
    }

    private void setFeedback(String msg, boolean error) {
        feedbackLabel.setText(msg);
        feedbackLabel.setForeground(error ? UITheme.ACCENT_RED : UITheme.ACCENT_TEAL);
    }
}
