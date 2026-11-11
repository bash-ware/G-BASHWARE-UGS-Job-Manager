package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.machine.JobManagerGateway;
import com.bashworks.ugs.jobmanager.model.JobEntry;
import com.bashworks.ugs.jobmanager.service.IncomingJobQueue;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Local inbox for jobs sent by the other computer. */
public class LocalJobsPanel extends JPanel {
    private final IncomingJobQueue queue;
    private final JobManagerGateway gateway;
    private final Consumer<JobEntry> onSelected;
    private final Consumer<JobEntry> onActivate;
    private final List<JobEntry> allJobs = new ArrayList<>();
    private final List<JobEntry> visibleJobs = new ArrayList<>();
    private final JTextField search = new JTextField();
    private final JLabel count = UITheme.createMutedLabel("0 jobs");
    private final JLabel empty = UITheme.createMutedLabel("Waiting for jobs from your other computer");
    private final DarkButton remove = new DarkButton("Remove selected");
    private final DarkButton clear = new DarkButton("Clear queue", DarkButton.ButtonStyle.DANGER);
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Status", "Job", "Dimensions", "Size", "Received"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable table = new JTable(model);

    public LocalJobsPanel(File incoming, JobManagerGateway gateway, Consumer<JobEntry> onSelected, Consumer<JobEntry> onActivate) {
        this.gateway = gateway;
        this.onSelected = onSelected;
        this.onActivate = onActivate;
        try { queue = new IncomingJobQueue(incoming); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Could not open incoming queue", ex); }
        setLayout(new BorderLayout(0, 18));
        setBackground(UITheme.BG_PANEL);
        setBorder(BorderFactory.createEmptyBorder(22, 22, 18, 22));
        setMinimumSize(new Dimension(420, 250));
        JPanel heading = new JPanel(new BorderLayout(8, 14));
        heading.setOpaque(false);
        JLabel title = UITheme.createHeaderLabel("Incoming jobs");
        title.setFont(UITheme.FONT_TITLE);
        heading.add(title, BorderLayout.WEST);
        heading.add(count, BorderLayout.EAST);
        search.setFont(UITheme.FONT_BODY);
        search.setBackground(UITheme.BG_INPUT);
        search.setForeground(UITheme.TEXT_PRIMARY);
        search.setCaretColor(UITheme.ACCENT_CYAN);
        search.setToolTipText("Search jobs by name");
        search.setBorder(BorderFactory.createCompoundBorder(new UITheme.RoundedBorder(UITheme.BORDER_COLOR, 12), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        JPanel searchRow = new JPanel(new BorderLayout(10, 0));
        searchRow.setOpaque(false);
        searchRow.add(UITheme.createMutedLabel("Search"), BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);
        DarkButton refresh = new DarkButton("Refresh");
        refresh.addActionListener(e -> refreshJobs());
        searchRow.add(refresh, BorderLayout.EAST);
        heading.add(searchRow, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);

        table.setFont(UITheme.FONT_BODY);
        table.setRowHeight(48);
        table.setBackground(UITheme.BG_PANEL);
        table.setForeground(UITheme.TEXT_PRIMARY);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(UITheme.BG_PANEL);
        table.getTableHeader().setForeground(UITheme.TEXT_MUTED);
        table.getTableHeader().setFont(UITheme.FONT_SMALL_BOLD);
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_COLOR));
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setBackground(UITheme.BG_PANEL);
        headerRenderer.setForeground(UITheme.TEXT_MUTED);
        headerRenderer.setFont(UITheme.FONT_SMALL_BOLD);
        headerRenderer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        table.getTableHeader().setDefaultRenderer(headerRenderer);
        int[] widths = {75, 240, 150, 65, 130};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            table.getColumnModel().getColumn(i).setCellRenderer(new JobTableRenderer());
        }
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                onSelected.accept(row >= 0 && row < visibleJobs.size() ? visibleJobs.get(row) : null);
                updateActions();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && row >= 0 && table.getSelectedRowCount() == 1)
                    onActivate.accept(visibleJobs.get(row));
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(UITheme.BG_PANEL);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        body.add(scroll, BorderLayout.CENTER);
        empty.setHorizontalAlignment(SwingConstants.CENTER);
        body.add(empty, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 12));
        footer.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        remove.addActionListener(e -> removeSelected());
        clear.addActionListener(e -> clearQueue());
        actions.add(remove);
        actions.add(clear);
        footer.add(actions, BorderLayout.NORTH);
        footer.add(UITheme.createMutedLabel("Ctrl / Shift to select multiple jobs. Files remain on disk."), BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { rebuild(); }
            public void removeUpdate(DocumentEvent e) { rebuild(); }
            public void changedUpdate(DocumentEvent e) { rebuild(); }
        });
        refreshJobs();
    }

    public void selectOrAddJob(JobEntry entry) {
        try { queue.received(entry); }
        catch (java.io.IOException ex) { showError(ex); }
        allJobs.removeIf(j -> j.getFile().equals(entry.getFile()));
        allJobs.add(0, entry);
        search.setText("");
        rebuild();
        table.setRowSelectionInterval(0, 0);
    }

    public void refreshJobs() {
        allJobs.clear();
        allJobs.addAll(queue.jobs());
        rebuild();
    }

    public void refreshActiveFile(File file) { rebuild(); }

    private void removeSelected() {
        List<JobEntry> selected = new ArrayList<>();
        for (int row : table.getSelectedRows()) selected.add(visibleJobs.get(row));
        dismiss(selected);
    }

    private void clearQueue() { dismiss(new ArrayList<>(allJobs)); }

    private void dismiss(List<JobEntry> jobs) {
        try {
            queue.remove(jobs);
            allJobs.removeAll(jobs);
            rebuild();
        } catch (java.io.IOException ex) { showError(ex); }
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, "Could not save queue: " + ex.getMessage(), "Queue error", JOptionPane.ERROR_MESSAGE);
    }

    private void rebuild() {
        Set<File> selected = new HashSet<>();
        for (int row : table.getSelectedRows()) if (row < visibleJobs.size()) selected.add(visibleJobs.get(row).getFile());
        visibleJobs.clear();
        table.getSelectionModel().setValueIsAdjusting(true);
        model.setRowCount(0);
        String filter = search.getText().trim().toLowerCase(Locale.ROOT);
        File active = gateway.getActiveGcodeFile();
        for (JobEntry job : allJobs) {
            if (!job.getName().toLowerCase(Locale.ROOT).contains(filter)) continue;
            visibleJobs.add(job);
            boolean loaded = job.getFile().equals(active);
            job.setActiveInUgs(loaded);
            model.addRow(new Object[]{loaded ? "ACTIVE" : "READY", job.getName(), job.getDimensionsSummary(), job.getFormattedSize(), job.getFormattedDate()});
            if (selected.contains(job.getFile())) table.addRowSelectionInterval(model.getRowCount()-1, model.getRowCount()-1);
        }
        table.getSelectionModel().setValueIsAdjusting(false);
        count.setText(visibleJobs.size() + " / " + allJobs.size() + " jobs");
        empty.setText(allJobs.isEmpty() ? "Waiting for jobs from your other computer" : visibleJobs.isEmpty() ? "No matching jobs" : " ");
        if (table.getSelectedRow() < 0) onSelected.accept(null);
        updateActions();
    }

    private void updateActions() {
        int n = table.getSelectedRowCount();
        remove.setText(n > 0 ? "Remove selected (" + n + ")" : "Remove selected");
        remove.setEnabled(n > 0);
        clear.setEnabled(!allJobs.isEmpty());
    }
}
