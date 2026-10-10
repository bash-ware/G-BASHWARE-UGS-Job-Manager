package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.PluginInfo;
import com.bashworks.ugs.jobmanager.machine.JobManagerGateway;
import com.bashworks.ugs.jobmanager.machine.JobManagerListener;
import com.bashworks.ugs.jobmanager.model.JobEntry;
import com.bashworks.ugs.jobmanager.service.GcodeAnalyzer;
import com.bashworks.ugs.jobmanager.service.JobLibraryService;
import com.bashworks.ugs.jobmanager.service.LanReceiverService;
import com.bashworks.ugs.jobmanager.service.WebJobService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;

/**
 * Master UI panel for G-BASHWARE Job Manager. Contains navigation tabs for local
 * disk library, web downloads, zero-config LAN receiver, and persistent machine status.
 */
public class JobManagerPanel extends JPanel implements JobManagerListener, LanReceiverService.LanListener {

    private final JobManagerGateway gateway;
    private final GcodeAnalyzer analyzer;
    private final JobLibraryService libraryService;
    private final WebJobService webService;
    private final LanReceiverService lanService;

    // Header controls
    private final JLabel machineStatusBadge = UITheme.createBadge("DISCONNECTED", UITheme.BG_CARD_ALT, UITheme.TEXT_MUTED);
    private final JLabel lanLedBadge = UITheme.createBadge("● GCODE MERGER SEARCHING...", new Color(0x35, 0x1E, 0x22), UITheme.ACCENT_RED);
    private final JCheckBox chkAutoActivate = new JCheckBox("Auto-Load");
    private final JLabel activeJobLabel = new JLabel("No Job Loaded in UGS");
    private final DarkButton btnReloadActive = new DarkButton("🔄 Reload", DarkButton.ButtonStyle.SECONDARY);
    private final DarkButton btnUnloadActive = new DarkButton("✕ Unload", DarkButton.ButtonStyle.SECONDARY);

    // Sub-panels
    private final LocalJobsPanel localPanel;
    private final WebJobsPanel webPanel;
    private final JobDetailsPanel detailsPanel;
    private final JTabbedPane tabbedPane;

    public JobManagerPanel(JobManagerGateway gateway) {
        this.gateway = gateway;
        this.analyzer = new GcodeAnalyzer();
        this.libraryService = new JobLibraryService(null, analyzer);
        this.webService = new WebJobService(null, analyzer);
        this.lanService = new LanReceiverService(gateway, analyzer, null);

        setLayout(new BorderLayout(0, 4));
        setBackground(UITheme.BG_DARK);

        // Header Status Banner
        JPanel headerCard = createHeaderBanner();
        add(headerCard, BorderLayout.NORTH);

        // Sub-panels
        this.detailsPanel = new JobDetailsPanel(gateway, analyzer);

        this.localPanel = new LocalJobsPanel(
                libraryService,
                gateway,
                job -> detailsPanel.setJob(job),
                this::activateJobDirectly
        );

        this.webPanel = new WebJobsPanel(
                webService,
                gateway,
                job -> detailsPanel.setJob(job),
                this::activateJobDirectly
        );

        // Tabs
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_BODY_BOLD);
        tabbedPane.setBackground(UITheme.BG_CARD);
        tabbedPane.setForeground(UITheme.TEXT_PRIMARY);
        tabbedPane.addTab("📁 Local Library", localPanel);
        tabbedPane.addTab("🌐 Web Download", webPanel);

        // Split Pane (Left: Sources Tabs, Right: Job Details Inspector)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabbedPane, detailsPanel);
        splitPane.setResizeWeight(0.60);
        splitPane.setDividerSize(6);
        splitPane.setBackground(UITheme.BG_DARK);
        splitPane.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        add(splitPane, BorderLayout.CENTER);

        // Wire listener
        gateway.addListener(this);
        lanService.addListener(this);
        lanService.start();

        updateHeaderStatus();
        updateActiveJobDisplay(gateway.getActiveGcodeFile());
    }

    private JPanel createHeaderBanner() {
        JPanel banner = new JPanel(new BorderLayout(8, 0));
        banner.setBackground(UITheme.BG_CARD);
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        // Left title & machine status & LAN link LED
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel title = new JLabel(PluginInfo.TITLE);
        title.setFont(UITheme.FONT_HEADER);
        title.setForeground(UITheme.TEXT_PRIMARY);
        left.add(title);
        left.add(machineStatusBadge);
        left.add(lanLedBadge);

        chkAutoActivate.setFont(UITheme.FONT_SMALL);
        chkAutoActivate.setForeground(UITheme.TEXT_SECONDARY);
        chkAutoActivate.setOpaque(false);
        chkAutoActivate.setSelected(lanService.isAutoActivate());
        chkAutoActivate.setToolTipText("Automatically load received G-code jobs directly into UGS");
        chkAutoActivate.addActionListener(e -> lanService.setAutoActivate(chkAutoActivate.isSelected()));
        left.add(chkAutoActivate);

        banner.add(left, BorderLayout.WEST);

        // Right active job & quick actions
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);

        activeJobLabel.setFont(UITheme.FONT_BODY_BOLD);
        activeJobLabel.setForeground(UITheme.TEXT_SECONDARY);
        right.add(activeJobLabel);

        btnReloadActive.setFont(UITheme.FONT_SMALL);
        btnReloadActive.setPreferredSize(new Dimension(80, 26));
        btnReloadActive.addActionListener(e -> {
            try {
                gateway.reloadActiveJob();
            } catch (Exception ex) {
                showError("Reload Failed", ex.getMessage());
            }
        });
        right.add(btnReloadActive);

        btnUnloadActive.setFont(UITheme.FONT_SMALL);
        btnUnloadActive.setPreferredSize(new Dimension(75, 26));
        btnUnloadActive.addActionListener(e -> {
            try {
                gateway.unsetActiveJob();
            } catch (Exception ex) {
                showError("Unload Failed", ex.getMessage());
            }
        });
        right.add(btnUnloadActive);

        banner.add(right, BorderLayout.EAST);
        return banner;
    }

    private void activateJobDirectly(JobEntry job) {
        if (job == null || !job.getFile().exists()) return;
        try {
            gateway.loadJobIntoUgs(job.getFile());
            job.setActiveInUgs(true);
            detailsPanel.setJob(job);
            updateActiveJobDisplay(job.getFile());
        } catch (Exception ex) {
            showError("Job Activation Error", "Failed to activate '" + job.getName() + "':\n" + ex.getMessage());
        }
    }

    private void updateHeaderStatus() {
        boolean connected = gateway.isConnected();
        boolean idle = gateway.isIdle();
        boolean streaming = gateway.isSendingFile();

        if (!connected) {
            machineStatusBadge.setText("DISCONNECTED");
            machineStatusBadge.setForeground(UITheme.TEXT_MUTED);
        } else if (streaming) {
            machineStatusBadge.setText("● STREAMING");
            machineStatusBadge.setForeground(UITheme.ACCENT_CYAN);
        } else if (idle) {
            machineStatusBadge.setText("● IDLE (READY)");
            machineStatusBadge.setForeground(UITheme.ACCENT_TEAL);
        } else {
            machineStatusBadge.setText("● " + gateway.getControllerStateName());
            machineStatusBadge.setForeground(UITheme.ACCENT_AMBER);
        }
    }

    private void updateActiveJobDisplay(File file) {
        if (file != null && file.exists()) {
            activeJobLabel.setText("Active: " + file.getName());
            activeJobLabel.setForeground(UITheme.ACCENT_TEAL);
            btnReloadActive.setEnabled(true);
            btnUnloadActive.setEnabled(!gateway.isSendingFile());
        } else {
            activeJobLabel.setText("No Job in UGS");
            activeJobLabel.setForeground(UITheme.TEXT_MUTED);
            btnReloadActive.setEnabled(false);
            btnUnloadActive.setEnabled(false);
        }

        localPanel.refreshActiveFile(file);
        webPanel.refreshActiveFile(file);
        detailsPanel.refreshActiveState(file);
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    // === JobManagerListener implementation ===

    @Override
    public void onActiveJobChanged(File activeFile) {
        SwingUtilities.invokeLater(() -> updateActiveJobDisplay(activeFile));
    }

    @Override
    public void onMachineConnectionChanged(boolean connected) {
        SwingUtilities.invokeLater(this::updateHeaderStatus);
    }

    @Override
    public void onMachineStateChanged(String stateName, boolean isIdle, boolean isSending) {
        SwingUtilities.invokeLater(this::updateHeaderStatus);
    }

    @Override
    public void onStreamStateChanged(boolean isStreaming, boolean isPaused) {
        SwingUtilities.invokeLater(this::updateHeaderStatus);
    }

    @Override
    public void onClientLinkChanged(boolean linked, String clientIp) {
        SwingUtilities.invokeLater(() -> {
            if (linked) {
                lanLedBadge.setText("● GCODE MERGER: " + clientIp);
                lanLedBadge.setForeground(UITheme.ACCENT_TEAL);
                lanLedBadge.setToolTipText("Connected to GcodeMergerSplitter at " + clientIp);
            } else {
                lanLedBadge.setText("● GCODE MERGER SEARCHING...");
                lanLedBadge.setForeground(UITheme.ACCENT_RED);
                lanLedBadge.setToolTipText("Listening on UDP 9875 and HTTP 9876. Waiting for GcodeMergerSplitter...");
            }
        });
    }

    @Override
    public void onJobReceived(JobEntry job, String clientIp, boolean activated) {
        SwingUtilities.invokeLater(() -> {
            localPanel.selectOrAddJob(job);
            detailsPanel.setJob(job);
            if (activated) {
                updateActiveJobDisplay(job.getFile());
            }
        });
    }

    public LanReceiverService getLanService() {
        return lanService;
    }

    public void close() {
        gateway.removeListener(this);
        lanService.removeListener(this);
        lanService.stop();
    }
}
