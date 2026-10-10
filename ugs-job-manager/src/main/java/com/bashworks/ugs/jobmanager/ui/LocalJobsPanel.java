package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.machine.JobManagerGateway;
import com.bashworks.ugs.jobmanager.model.JobEntry;
import com.bashworks.ugs.jobmanager.model.JobOrigin;
import com.bashworks.ugs.jobmanager.service.JobLibraryService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Local file system browser and job catalog panel with folder watching,
 * fast search, drag-and-drop, and recent file history.
 */
public class LocalJobsPanel extends JPanel {

    private final JobLibraryService libraryService;
    private final JobManagerGateway gateway;
    private final Consumer<JobEntry> onJobSelected;
    private final Consumer<JobEntry> onJobActivateRequested;

    private final JTextField dirField = new JTextField();
    private final JCheckBox chkRecursive = new JCheckBox("Include Subfolders");
    private final JTextField searchField = new JTextField(14);
    private final JLabel countLabel = new JLabel("0 jobs");

    private final DefaultTableModel tableModel;
    private final JTable jobsTable;
    private final List<JobEntry> allJobs = new ArrayList<>();
    private final List<JobEntry> filteredJobs = new ArrayList<>();

    private final JComboBox<JobEntry> recentCombo = new JComboBox<>();

    public LocalJobsPanel(JobLibraryService libraryService,
                          JobManagerGateway gateway,
                          Consumer<JobEntry> onJobSelected,
                          Consumer<JobEntry> onJobActivateRequested) {
        this.libraryService = libraryService;
        this.gateway = gateway;
        this.onJobSelected = onJobSelected;
        this.onJobActivateRequested = onJobActivateRequested;

        setLayout(new BorderLayout(6, 6));
        setBackground(UITheme.BG_PANEL);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Top Control Area (Folder Bar + Search Bar)
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setOpaque(false);

        // Folder bar
        JPanel folderBar = new JPanel(new BorderLayout(6, 0));
        folderBar.setOpaque(false);

        JLabel lblDir = new JLabel("Directory: ");
        lblDir.setFont(UITheme.FONT_SMALL_BOLD);
        lblDir.setForeground(UITheme.TEXT_SECONDARY);
        folderBar.add(lblDir, BorderLayout.WEST);

        dirField.setFont(UITheme.FONT_SMALL);
        dirField.setBackground(UITheme.BG_INPUT);
        dirField.setForeground(UITheme.TEXT_PRIMARY);
        dirField.setCaretColor(UITheme.ACCENT_CYAN);
        dirField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        dirField.setEditable(false);
        folderBar.add(dirField, BorderLayout.CENTER);

        JPanel folderActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        folderActions.setOpaque(false);

        chkRecursive.setFont(UITheme.FONT_SMALL);
        chkRecursive.setForeground(UITheme.TEXT_SECONDARY);
        chkRecursive.setOpaque(false);
        chkRecursive.setSelected(libraryService.isScanRecursive());
        chkRecursive.addActionListener(e -> {
            libraryService.setScanRecursive(chkRecursive.isSelected());
            refreshJobs();
        });
        folderActions.add(chkRecursive);

        DarkButton btnBrowse = new DarkButton("📁 Browse...", DarkButton.ButtonStyle.SECONDARY);
        btnBrowse.setFont(UITheme.FONT_SMALL_BOLD);
        btnBrowse.addActionListener(e -> browseFolder());
        folderActions.add(btnBrowse);

        DarkButton btnRefresh = new DarkButton("🔄 Refresh", DarkButton.ButtonStyle.SECONDARY);
        btnRefresh.setFont(UITheme.FONT_SMALL_BOLD);
        btnRefresh.addActionListener(e -> refreshJobs());
        folderActions.add(btnRefresh);

        folderBar.add(folderActions, BorderLayout.EAST);
        topContainer.add(folderBar);
        topContainer.add(Box.createVerticalStrut(6));

        // Filter Bar
        JPanel filterBar = new JPanel(new BorderLayout(6, 0));
        filterBar.setOpaque(false);

        JPanel filterLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filterLeft.setOpaque(false);

        JLabel lblSearch = new JLabel("Filter: ");
        lblSearch.setFont(UITheme.FONT_SMALL_BOLD);
        lblSearch.setForeground(UITheme.TEXT_SECONDARY);
        filterLeft.add(lblSearch);

        searchField.setFont(UITheme.FONT_BODY);
        searchField.setBackground(UITheme.BG_INPUT);
        searchField.setForeground(UITheme.TEXT_PRIMARY);
        searchField.setCaretColor(UITheme.ACCENT_CYAN);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)
        ));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        filterLeft.add(searchField);

        filterBar.add(filterLeft, BorderLayout.WEST);

        countLabel.setFont(UITheme.FONT_SMALL);
        countLabel.setForeground(UITheme.TEXT_MUTED);
        filterBar.add(countLabel, BorderLayout.EAST);

        topContainer.add(filterBar);
        add(topContainer, BorderLayout.NORTH);

        // Center Table
        String[] columns = {"Status", "Job Name", "Dimensions (X×Y×Z)", "Size", "Modified"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        jobsTable = new JTable(tableModel);
        jobsTable.setBackground(UITheme.BG_PANEL);
        jobsTable.setForeground(UITheme.TEXT_PRIMARY);
        jobsTable.setSelectionBackground(new java.awt.Color(0x35, 0x48, 0x5E));
        jobsTable.setSelectionForeground(java.awt.Color.WHITE);
        jobsTable.setRowHeight(26);
        jobsTable.setFont(UITheme.FONT_BODY);
        jobsTable.setShowGrid(false);
        jobsTable.setIntercellSpacing(new Dimension(0, 1));
        jobsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jobsTable.getTableHeader().setBackground(UITheme.BG_CARD);
        jobsTable.getTableHeader().setForeground(UITheme.TEXT_SECONDARY);
        jobsTable.getTableHeader().setFont(UITheme.FONT_SMALL_BOLD);
        jobsTable.getTableHeader().setBorder(BorderFactory.createLineBorder(UITheme.BORDER_COLOR));

        // Column widths
        jobsTable.getColumnModel().getColumn(0).setPreferredWidth(75);
        jobsTable.getColumnModel().getColumn(0).setMaxWidth(90);
        jobsTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        jobsTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        jobsTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        jobsTable.getColumnModel().getColumn(4).setPreferredWidth(120);

        JobTableRenderer renderer = new JobTableRenderer();
        for (int i = 0; i < jobsTable.getColumnCount(); i++) {
            jobsTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        // Table Selection & Double Click
        jobsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = jobsTable.getSelectedRow();
                if (row >= 0 && row < filteredJobs.size()) {
                    JobEntry selected = filteredJobs.get(row);
                    if (onJobSelected != null) {
                        onJobSelected.accept(selected);
                    }
                }
            }
        });

        jobsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int row = jobsTable.getSelectedRow();
                    if (row >= 0 && row < filteredJobs.size()) {
                        JobEntry selected = filteredJobs.get(row);
                        if (onJobActivateRequested != null) {
                            onJobActivateRequested.accept(selected);
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(jobsTable);
        scrollPane.setBackground(UITheme.BG_PANEL);
        scrollPane.getViewport().setBackground(UITheme.BG_PANEL);
        scrollPane.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_COLOR));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Actions Bar (Open Single File, Recent Files, Drag&Drop Hint)
        JPanel bottomBar = new JPanel(new BorderLayout(6, 4));
        bottomBar.setOpaque(false);

        JPanel leftBottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        leftBottom.setOpaque(false);

        DarkButton btnOpenFile = new DarkButton("📄 Open Single File...", DarkButton.ButtonStyle.SECONDARY);
        btnOpenFile.setFont(UITheme.FONT_SMALL_BOLD);
        btnOpenFile.addActionListener(e -> openSingleFile());
        leftBottom.add(btnOpenFile);

        JLabel lblRecent = new JLabel("Recent: ");
        lblRecent.setFont(UITheme.FONT_SMALL_BOLD);
        lblRecent.setForeground(UITheme.TEXT_MUTED);
        leftBottom.add(lblRecent);

        recentCombo.setFont(UITheme.FONT_SMALL);
        recentCombo.setPreferredSize(new Dimension(180, 26));
        recentCombo.setBackground(UITheme.BG_CARD);
        recentCombo.setForeground(UITheme.TEXT_PRIMARY);
        leftBottom.add(recentCombo);

        DarkButton btnLoadRecent = new DarkButton("Load", DarkButton.ButtonStyle.SECONDARY);
        btnLoadRecent.setFont(UITheme.FONT_SMALL_BOLD);
        btnLoadRecent.addActionListener(e -> {
            JobEntry selected = (JobEntry) recentCombo.getSelectedItem();
            if (selected != null) {
                selectOrAddJob(selected);
            }
        });
        leftBottom.add(btnLoadRecent);

        bottomBar.add(leftBottom, BorderLayout.WEST);

        JLabel lblDropHint = new JLabel("💡 Drag & drop G-code files here");
        lblDropHint.setFont(UITheme.FONT_SMALL);
        lblDropHint.setForeground(UITheme.TEXT_MUTED);
        bottomBar.add(lblDropHint, BorderLayout.EAST);

        add(bottomBar, BorderLayout.SOUTH);

        setupDragAndDrop();
        updateWatchedDirField();
        refreshJobs();
        refreshRecentCombo();
    }

    private void updateWatchedDirField() {
        File dir = libraryService.getWatchedDirectory();
        dirField.setText(dir != null ? dir.getAbsolutePath() : "");
    }

    private void browseFolder() {
        JFileChooser chooser = new JFileChooser(libraryService.getWatchedDirectory());
        chooser.setDialogTitle("Select CNC G-Code Jobs Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            libraryService.setWatchedDirectory(selected);
            updateWatchedDirField();
            refreshJobs();
        }
    }

    private void openSingleFile() {
        JFileChooser chooser = new JFileChooser(libraryService.getWatchedDirectory());
        chooser.setDialogTitle("Open G-Code File");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter("CNC G-Code Files (*.gcode, *.nc, *.tap, *.cnc)",
                "gcode", "nc", "ngc", "tap", "cnc", "txt", "g"));

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            JobEntry entry = libraryService.createEntryAndAnalyze(file, JobOrigin.LOCAL_DISK);
            selectOrAddJob(entry);
            refreshRecentCombo();
        }
    }

    public void selectOrAddJob(JobEntry entry) {
        if (entry == null) return;
        boolean found = false;
        for (int i = 0; i < allJobs.size(); i++) {
            if (allJobs.get(i).getFile().getAbsolutePath().equalsIgnoreCase(entry.getFile().getAbsolutePath())) {
                entry = allJobs.get(i);
                found = true;
                break;
            }
        }
        if (!found) {
            allJobs.add(0, entry);
        }
        applyFilter();
        // Select in table
        int rowInFiltered = filteredJobs.indexOf(entry);
        if (rowInFiltered >= 0) {
            jobsTable.setRowSelectionInterval(rowInFiltered, rowInFiltered);
            jobsTable.scrollRectToVisible(jobsTable.getCellRect(rowInFiltered, 0, true));
        }
        if (onJobSelected != null) {
            onJobSelected.accept(entry);
        }
    }

    public void refreshJobs() {
        allJobs.clear();
        allJobs.addAll(libraryService.scanJobs());
        applyFilter();
    }

    public void refreshRecentCombo() {
        recentCombo.removeAllItems();
        List<JobEntry> recents = libraryService.getRecentJobs();
        for (JobEntry r : recents) {
            recentCombo.addItem(r);
        }
    }

    public void refreshActiveFile(File activeFile) {
        for (JobEntry j : allJobs) {
            boolean active = activeFile != null && j.getFile().getAbsolutePath().equalsIgnoreCase(activeFile.getAbsolutePath());
            j.setActiveInUgs(active);
        }
        applyFilter();
    }

    private void applyFilter() {
        String filter = searchField.getText().trim().toLowerCase(Locale.ROOT);
        filteredJobs.clear();
        tableModel.setRowCount(0);

        File activeFile = gateway.getActiveGcodeFile();

        for (JobEntry job : allJobs) {
            if (filter.isEmpty() || job.getName().toLowerCase(Locale.ROOT).contains(filter)) {
                filteredJobs.add(job);
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

        countLabel.setText(String.format("Showing %d of %d jobs", filteredJobs.size(), allJobs.size()));
    }

    private void setupDragAndDrop() {
        new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Object data = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (data instanceof List<?>) {
                        List<?> list = (List<?>) data;
                        JobEntry firstValid = null;
                        for (Object item : list) {
                            if (item instanceof File) {
                                File f = (File) item;
                                if (f.isFile() && JobLibraryService.isSupportedGcodeFile(f)) {
                                    JobEntry entry = libraryService.createEntryAndAnalyze(f, JobOrigin.LOCAL_DISK);
                                    if (firstValid == null) {
                                        firstValid = entry;
                                    }
                                }
                            }
                        }
                        if (firstValid != null) {
                            selectOrAddJob(firstValid);
                            refreshRecentCombo();
                        }
                    }
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                }
            }
        });
    }
}
