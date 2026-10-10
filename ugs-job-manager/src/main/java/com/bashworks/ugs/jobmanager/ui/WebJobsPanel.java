package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.machine.JobManagerGateway;
import com.bashworks.ugs.jobmanager.model.JobEntry;
import com.bashworks.ugs.jobmanager.service.WebJobService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Web and remote G-code job fetcher panel. Downloads jobs from remote URLs,
 * parses dimensional metrics, caches them locally, and activates them in UGS.
 */
public class WebJobsPanel extends JPanel {

    private final WebJobService webService;
    private final JobManagerGateway gateway;
    private final Consumer<JobEntry> onJobSelected;
    private final Consumer<JobEntry> onJobActivateRequested;

    private final JTextField urlField = new JTextField();
    private final JComboBox<String> presetCombo = new JComboBox<>();
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Enter a direct G-code URL or select a preset to download");

    private final DarkButton btnDownloadAndActivate = new DarkButton("⚡ Download & Activate in UGS", DarkButton.ButtonStyle.PRIMARY);
    private final DarkButton btnDownloadOnly = new DarkButton("📥 Download to Cache", DarkButton.ButtonStyle.SECONDARY);
    private final DarkButton btnClearCache = new DarkButton("🗑 Clear Cache", DarkButton.ButtonStyle.SECONDARY);

    private final DefaultTableModel tableModel;
    private final JTable historyTable;
    private final List<JobEntry> cachedJobs = new ArrayList<>();

    public WebJobsPanel(WebJobService webService,
                        JobManagerGateway gateway,
                        Consumer<JobEntry> onJobSelected,
                        Consumer<JobEntry> onJobActivateRequested) {
        this.webService = webService;
        this.gateway = gateway;
        this.onJobSelected = onJobSelected;
        this.onJobActivateRequested = onJobActivateRequested;

        setLayout(new BorderLayout(8, 8));
        setBackground(UITheme.BG_PANEL);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Top Fetcher Form Card
        JPanel topCard = new JPanel();
        topCard.setLayout(new BoxLayout(topCard, BoxLayout.Y_AXIS));
        topCard.setBackground(UITheme.BG_CARD);
        topCard.setBorder(BorderFactory.createCompoundBorder(
                new UITheme.RoundedBorder(UITheme.BORDER_COLOR, 8),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        JLabel title = new JLabel("Download & Load G-Code from Web URL");
        title.setFont(UITheme.FONT_HEADER);
        title.setForeground(UITheme.TEXT_PRIMARY);
        topCard.add(title);
        topCard.add(Box.createVerticalStrut(8));

        // Preset row
        JPanel presetRow = new JPanel(new BorderLayout(6, 0));
        presetRow.setOpaque(false);

        JLabel lblPreset = new JLabel("Presets & History: ");
        lblPreset.setFont(UITheme.FONT_SMALL_BOLD);
        lblPreset.setForeground(UITheme.TEXT_SECONDARY);
        presetRow.add(lblPreset, BorderLayout.WEST);

        presetCombo.setFont(UITheme.FONT_SMALL);
        presetCombo.setBackground(UITheme.BG_INPUT);
        presetCombo.setForeground(UITheme.TEXT_PRIMARY);
        presetCombo.addItem("— Custom URL —");
        presetCombo.addItem("https://raw.githubusercontent.com/winder/Universal-G-Code-Sender/master/ugs-core/test/resources/test.gcode");
        presetCombo.addItem("https://raw.githubusercontent.com/grbl/grbl/master/build/test.gcode");

        for (String h : webService.getUrlHistory()) {
            presetCombo.addItem(h);
        }

        presetCombo.addActionListener(e -> {
            int idx = presetCombo.getSelectedIndex();
            if (idx > 0) {
                urlField.setText((String) presetCombo.getSelectedItem());
            }
        });
        presetRow.add(presetCombo, BorderLayout.CENTER);
        topCard.add(presetRow);
        topCard.add(Box.createVerticalStrut(6));

        // URL row
        JPanel urlRow = new JPanel(new BorderLayout(6, 0));
        urlRow.setOpaque(false);

        JLabel lblUrl = new JLabel("G-Code URL: ");
        lblUrl.setFont(UITheme.FONT_SMALL_BOLD);
        lblUrl.setForeground(UITheme.TEXT_SECONDARY);
        urlRow.add(lblUrl, BorderLayout.WEST);

        urlField.setFont(UITheme.FONT_BODY);
        urlField.setBackground(UITheme.BG_INPUT);
        urlField.setForeground(UITheme.TEXT_PRIMARY);
        urlField.setCaretColor(UITheme.ACCENT_CYAN);
        urlField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        urlRow.add(urlField, BorderLayout.CENTER);
        topCard.add(urlRow);
        topCard.add(Box.createVerticalStrut(8));

        // Progress bar
        progressBar.setStringPainted(true);
        progressBar.setFont(UITheme.FONT_SMALL);
        progressBar.setForeground(UITheme.ACCENT_CYAN);
        progressBar.setBackground(UITheme.BG_INPUT);
        progressBar.setValue(0);
        progressBar.setVisible(false);
        topCard.add(progressBar);

        // Status row
        statusLabel.setFont(UITheme.FONT_SMALL);
        statusLabel.setForeground(UITheme.TEXT_MUTED);
        topCard.add(statusLabel);
        topCard.add(Box.createVerticalStrut(8));

        // Action Buttons Row
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false);

        btnDownloadAndActivate.setFont(UITheme.FONT_BODY_BOLD);
        btnDownloadAndActivate.addActionListener(e -> startDownload(true));
        btnRow.add(btnDownloadAndActivate);

        btnDownloadOnly.setFont(UITheme.FONT_BODY);
        btnDownloadOnly.addActionListener(e -> startDownload(false));
        btnRow.add(btnDownloadOnly);

        topCard.add(btnRow);
        add(topCard, BorderLayout.NORTH);

        // Center History / Cached Files Table
        JPanel centerPanel = new JPanel(new BorderLayout(4, 4));
        centerPanel.setOpaque(false);

        JPanel historyHeader = new JPanel(new BorderLayout());
        historyHeader.setOpaque(false);
        JLabel lblHistory = new JLabel("Cached Web Jobs:");
        lblHistory.setFont(UITheme.FONT_HEADER);
        lblHistory.setForeground(UITheme.TEXT_PRIMARY);
        historyHeader.add(lblHistory, BorderLayout.WEST);

        btnClearCache.setFont(UITheme.FONT_SMALL);
        btnClearCache.addActionListener(e -> clearCache());
        historyHeader.add(btnClearCache, BorderLayout.EAST);
        centerPanel.add(historyHeader, BorderLayout.NORTH);

        String[] columns = {"Status", "Job Name", "Dimensions (X×Y×Z)", "Size", "Downloaded"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        historyTable = new JTable(tableModel);
        historyTable.setBackground(UITheme.BG_PANEL);
        historyTable.setForeground(UITheme.TEXT_PRIMARY);
        historyTable.setSelectionBackground(new java.awt.Color(0x35, 0x48, 0x5E));
        historyTable.setSelectionForeground(java.awt.Color.WHITE);
        historyTable.setRowHeight(26);
        historyTable.setFont(UITheme.FONT_BODY);
        historyTable.setShowGrid(false);
        historyTable.setIntercellSpacing(new Dimension(0, 1));
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.getTableHeader().setBackground(UITheme.BG_CARD);
        historyTable.getTableHeader().setForeground(UITheme.TEXT_SECONDARY);
        historyTable.getTableHeader().setFont(UITheme.FONT_SMALL_BOLD);

        // Column widths
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(75);
        historyTable.getColumnModel().getColumn(0).setMaxWidth(90);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(120);

        JobTableRenderer renderer = new JobTableRenderer();
        for (int i = 0; i < historyTable.getColumnCount(); i++) {
            historyTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = historyTable.getSelectedRow();
                if (row >= 0 && row < cachedJobs.size()) {
                    JobEntry selected = cachedJobs.get(row);
                    if (onJobSelected != null) {
                        onJobSelected.accept(selected);
                    }
                }
            }
        });

        historyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int row = historyTable.getSelectedRow();
                    if (row >= 0 && row < cachedJobs.size()) {
                        JobEntry selected = cachedJobs.get(row);
                        if (onJobActivateRequested != null) {
                            onJobActivateRequested.accept(selected);
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setBackground(UITheme.BG_PANEL);
        scrollPane.getViewport().setBackground(UITheme.BG_PANEL);
        scrollPane.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_COLOR));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        refreshHistory();
    }

    private void startDownload(boolean activateAfterDownload) {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a valid HTTP/HTTPS G-code URL.",
                    "Invalid URL", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnDownloadAndActivate.setEnabled(false);
        btnDownloadOnly.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setVisible(true);
        statusLabel.setText("Connecting...");
        statusLabel.setForeground(UITheme.TEXT_PRIMARY);

        // Download on worker thread to keep Swing EDT responsive
        new Thread(() -> {
            try {
                JobEntry job = webService.fetchJob(url, (msg, pct) -> SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(msg);
                    progressBar.setValue(pct);
                }));

                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    btnDownloadAndActivate.setEnabled(true);
                    btnDownloadOnly.setEnabled(true);
                    statusLabel.setText("Downloaded: " + job.getName() + " (" + job.getFormattedSize() + ")");
                    statusLabel.setForeground(UITheme.ACCENT_TEAL);

                    refreshHistory();

                    if (activateAfterDownload) {
                        if (onJobActivateRequested != null) {
                            onJobActivateRequested.accept(job);
                        }
                    } else if (onJobSelected != null) {
                        onJobSelected.accept(job);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    btnDownloadAndActivate.setEnabled(true);
                    btnDownloadOnly.setEnabled(true);
                    statusLabel.setText("Failed: " + ex.getMessage());
                    statusLabel.setForeground(UITheme.ACCENT_RED);
                    JOptionPane.showMessageDialog(WebJobsPanel.this,
                            "Failed to download G-code from URL:\n" + ex.getMessage(),
                            "Download Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "ugs-job-web-fetcher").start();
    }

    public void refreshHistory() {
        cachedJobs.clear();
        cachedJobs.addAll(webService.listJobs());
        tableModel.setRowCount(0);

        File activeFile = gateway.getActiveGcodeFile();

        for (JobEntry job : cachedJobs) {
            boolean isActive = activeFile != null && job.getFile().getAbsolutePath().equalsIgnoreCase(activeFile.getAbsolutePath());
            job.setActiveInUgs(isActive);

            String status = isActive ? "ACTIVE" : "READY";
            tableModel.addRow(new Object[]{
                    status,
                    job.getName(),
                    job.getDimensionsSummary(),
                    job.getFormattedSize(),
                    job.getFormattedDate()
            });
        }
    }

    public void refreshActiveFile(File activeFile) {
        for (JobEntry j : cachedJobs) {
            boolean active = activeFile != null && j.getFile().getAbsolutePath().equalsIgnoreCase(activeFile.getAbsolutePath());
            j.setActiveInUgs(active);
        }
        refreshHistory();
    }

    private void clearCache() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete all locally cached web jobs?",
                "Confirm Cache Clearance", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            webService.clearCache();
            refreshHistory();
            statusLabel.setText("Cache cleared");
        }
    }
}
