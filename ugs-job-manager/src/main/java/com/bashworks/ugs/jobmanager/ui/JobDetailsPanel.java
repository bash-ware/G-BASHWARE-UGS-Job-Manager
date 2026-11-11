package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.machine.JobManagerGateway;
import com.bashworks.ugs.jobmanager.model.JobAnalysis;
import com.bashworks.ugs.jobmanager.model.JobBounds;
import com.bashworks.ugs.jobmanager.model.JobEntry;
import com.bashworks.ugs.jobmanager.service.GcodeAnalyzer;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;

/**
 * Inspector and Activation control panel for the currently selected CNC job.
 */
public class JobDetailsPanel extends JPanel {

    private final JobManagerGateway gateway;
    private final GcodeAnalyzer analyzer;
    private JobEntry currentJob;
    private String queueStatus;

    private final JLabel titleLabel = new JLabel("Select a job");
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
    private final ToolpathPreviewPanel toolpathPreview = new ToolpathPreviewPanel();

    // Action buttons
    private final DarkButton btnActivate = new DarkButton("Load into UGS", DarkButton.ButtonStyle.PRIMARY);
    private final DarkButton btnRun = new DarkButton("Start job", DarkButton.ButtonStyle.ACCENT);
    private final DarkButton btnUnload = new DarkButton("Unload", DarkButton.ButtonStyle.SECONDARY);
    private final DarkButton btnExplorer = new DarkButton("Show folder", DarkButton.ButtonStyle.SECONDARY);
    private final DarkButton btnCopyPath = new DarkButton("Copy path", DarkButton.ButtonStyle.SECONDARY);

    public JobDetailsPanel(JobManagerGateway gateway, GcodeAnalyzer analyzer) {
        this.gateway = gateway;
        this.analyzer = analyzer != null ? analyzer : new GcodeAnalyzer();

        setMinimumSize(new Dimension(340, 300));
        setLayout(new BorderLayout(8, 16));
        setBackground(UITheme.BG_PANEL);
        setBorder(BorderFactory.createEmptyBorder(22, 22, 18, 22));

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        headerPanel.setOpaque(false);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleLabel.setFont(UITheme.FONT_HEADER);
        titleLabel.setForeground(UITheme.TEXT_PRIMARY);
        titleRow.add(titleLabel);

        headerPanel.add(activeBadge, BorderLayout.EAST);

        headerPanel.add(titleRow, BorderLayout.NORTH);

        feedbackLabel.setFont(UITheme.FONT_SMALL);
        feedbackLabel.setForeground(UITheme.ACCENT_TEAL);
        headerPanel.add(feedbackLabel, BorderLayout.SOUTH);

        // Center: Stats grid + Preview
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setOpaque(false);

        JPanel statsCard = createStatsGrid();
        statsCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        centerPanel.add(statsCard, BorderLayout.NORTH);

        centerPanel.add(toolpathPreview, BorderLayout.CENTER);

        // Buttons Panel
        JPanel actionsPanel = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
        JPanel primaryActions = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        primaryActions.setOpaque(false);
        JPanel secondaryActions = new JPanel(new java.awt.GridLayout(1, 3, 8, 0));
        secondaryActions.setOpaque(false);
        actionsPanel.setOpaque(false);
        primaryActions.add(btnActivate);
        primaryActions.add(btnRun);
        secondaryActions.add(btnUnload);
        secondaryActions.add(btnExplorer);
        secondaryActions.add(btnCopyPath);
        actionsPanel.add(primaryActions);
        actionsPanel.add(secondaryActions);

        add(headerPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(actionsPanel, BorderLayout.SOUTH);

        setupListeners();
        updateUIState();
    }

    private JPanel createStatsGrid() {
        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setBackground(UITheme.BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(new UITheme.RoundedBorder(UITheme.BORDER_COLOR, 12),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        JPanel grid = new JPanel(new java.awt.GridLayout(0, 2, 20, 14));
        grid.setOpaque(false);
        addMetric(grid, "DIMENSIONS", valDimensions);
        addMetric(grid, "LINES / MOTION", valLines);
        addMetric(grid, "TOOLS", valTools);
        addMetric(grid, "SPINDLE", valSpindle);
        addMetric(grid, "MAX FEED", valFeed);
        addMetric(grid, "FILE SIZE", valSize);
        card.add(grid, BorderLayout.CENTER);
        valRange.setFont(UITheme.FONT_SMALL);
        valRange.setForeground(UITheme.TEXT_SECONDARY);
        card.add(valRange, BorderLayout.SOUTH);
        return card;
    }

    private void addMetric(JPanel grid, String caption, JLabel value) {
        JPanel metric = new JPanel(new BorderLayout(0, 5));
        metric.setOpaque(false);
        metric.add(UITheme.createMutedLabel(caption), BorderLayout.NORTH);
        value.setFont(UITheme.FONT_BODY_BOLD);
        value.setForeground(UITheme.TEXT_PRIMARY);
        metric.add(value, BorderLayout.CENTER);
        grid.add(metric);
    }

    private static String rangeLine(String axis, double min, double max, String unit) {
        return Double.isFinite(min) && Double.isFinite(max)
                ? String.format(java.util.Locale.US, "%s  %.2f to %.2f %s", axis, min, max, unit)
                : axis + "  No motion recorded";
    }

    private void setupListeners() {
        btnActivate.addActionListener(e -> activateCurrentJob());
        btnRun.addActionListener(e -> runCurrentJob());
        btnUnload.addActionListener(e -> unloadJob());
        btnExplorer.addActionListener(e -> showInExplorer());
        btnCopyPath.addActionListener(e -> copyPathToClipboard());
    }

    public void setJob(JobEntry job) {
        queueStatus = null;
        this.currentJob = job;
        toolpathPreview.setJob(job == null ? null : job.getFile());
        if (job != null && job.getAnalysis() == null && job.getFile().exists()) {
            job.setAnalysis(analyzer.analyze(job.getFile()));
        }
        updateUIState();
    }

    public void setQueueStatus(String status) {
        queueStatus = status;
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
            feedbackLabel.setText("Choose a queued job to inspect its toolpath.");
            titleLabel.setText("Select a job");
            sourceBadge.setText("NONE");
            activeBadge.setText("INACTIVE");
            valDimensions.setText("—");
            valRange.setText("—");
            valLines.setText("—");
            valTools.setText("—");
            valSpindle.setText("—");
            valFeed.setText("—");
            valSize.setText("—");

            btnActivate.setEnabled(false);
            btnRun.setEnabled(false);
            btnUnload.setEnabled(false);
            btnExplorer.setEnabled(false);
            btnCopyPath.setEnabled(false);
            return;
        }

        feedbackLabel.setText(" ");
        titleLabel.setText(currentJob.getName());
        titleLabel.setToolTipText(currentJob.getName());
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

        if (queueStatus != null) {
            activeBadge.setText(queueStatus);
            activeBadge.setForeground(UITheme.statusColor(queueStatus));
        }
        JobAnalysis a = currentJob.getAnalysis();
        if (a != null) {
            valDimensions.setText(a.getBounds().toDimensionsString(a.getUnits()));
            JobBounds bounds = a.getBounds();
            valRange.setText("<html>" + rangeLine("X", bounds.getMinX(), bounds.getMaxX(), a.getUnits()) + "<br>"
                    + rangeLine("Y", bounds.getMinY(), bounds.getMaxY(), a.getUnits()) + "<br>"
                    + rangeLine("Z", bounds.getMinZ(), bounds.getMaxZ(), a.getUnits()) + "</html>");
            valLines.setText(String.format("%,d / %,d", a.getTotalLines(), a.getMotionLines()));
            valTools.setText(a.getToolsSummary());
            valSpindle.setText(a.getSpindleSummary());
            valFeed.setText(a.getFeedRateSummary());

        } else {
            valDimensions.setText("—");
            valRange.setText("—");
            valLines.setText("—");
            valTools.setText("—");
            valSpindle.setText("—");
            valFeed.setText("—");
        }

        valSize.setText(currentJob.getFormattedSize());

        btnActivate.setEnabled(!gateway.isSendingFile());
        btnExplorer.setEnabled(currentJob.getFile().exists());
        btnCopyPath.setEnabled(true);
        btnUnload.setEnabled(isActive && !gateway.isSendingFile());

        boolean canRun = isActive && gateway.isConnected() && gateway.isIdle() && !gateway.isSendingFile();
        btnRun.setEnabled(canRun);
    }

    public void close() { toolpathPreview.close(); }

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
