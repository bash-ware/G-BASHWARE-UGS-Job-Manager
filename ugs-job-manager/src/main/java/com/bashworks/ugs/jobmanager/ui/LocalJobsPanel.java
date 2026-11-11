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
import java.awt.datatransfer.*;
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
    private final JLabel empty = UITheme.createMutedLabel("Waiting for jobs from your other computer");
    private final DarkButton remove = new DarkButton("Remove selected");
    private final DarkButton up = new DarkButton("↑ Up");
    private final DarkButton down = new DarkButton("↓ Down");
    private final DarkButton loadNext = new DarkButton("Load next", DarkButton.ButtonStyle.ACCENT);
    private final JLabel runningLabel = UITheme.createHeaderLabel("No job running");
    private final JLabel nextLabel = UITheme.createHeaderLabel("Next: —");
    private final DarkButton clear = new DarkButton("Clear queue", DarkButton.ButtonStyle.DANGER);
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Order", "State", "Job", "Size", "Received"}, 0) {
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
        JPanel queueStatus = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
        queueStatus.setOpaque(false);
        runningLabel.setForeground(UITheme.ACCENT_TEAL);
        nextLabel.setForeground(UITheme.ACCENT_CYAN);
        queueStatus.add(runningLabel);
        queueStatus.add(nextLabel);
        add(queueStatus, BorderLayout.NORTH);

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
        int[] widths = {48, 116, 310, 65, 135};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            table.getColumnModel().getColumn(i).setCellRenderer(new JobTableRenderer());
        }
        table.getColumnModel().getColumn(0).setMaxWidth(55);
        table.getColumnModel().getColumn(1).setMinWidth(105);
        setupReordering();
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
        JPanel orderActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        orderActions.setOpaque(false);
        up.setToolTipText("Move selected pending jobs up (Alt + Up)");
        down.setToolTipText("Move selected pending jobs down (Alt + Down)");
        up.addActionListener(e -> moveSelected(-1));
        down.addActionListener(e -> moveSelected(1));
        loadNext.addActionListener(e -> { JobEntry job = nextJob(); if (job != null) onActivate.accept(job); });
        orderActions.add(up);
        orderActions.add(down);
        orderActions.add(loadNext);
        footer.add(orderActions, BorderLayout.NORTH);
        footer.add(actions, BorderLayout.CENTER);
        footer.add(UITheme.createMutedLabel("Drag pending jobs or use arrows. Ctrl / Shift selects several."), BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        refreshJobs();
    }

    public void selectOrAddJob(JobEntry entry) {
        try {
            queue.received(entry);
            refreshJobs();
            for (int i = 0; i < visibleJobs.size(); i++) if (visibleJobs.get(i).getFile().equals(entry.getFile())) {
                allJobs.set(allJobs.indexOf(visibleJobs.get(i)), entry);
                visibleJobs.set(i, entry);
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                onSelected.accept(entry);
                break;
            }
        } catch (java.io.IOException ex) { showError(ex); }
    }

    public void refreshJobs() {
        try {
            List<JobEntry> jobs = queue.jobs();
            allJobs.clear();
            allJobs.addAll(jobs);
            rebuild();
        } catch (java.io.IOException ex) { showError(ex); }
    }

    public void refreshActiveFile(File file) { rebuild(); }

    public void jobStarted(File file) { changeState(file, "start"); }
    public void jobFinished(File file) { changeState(file, "finish"); }
    public void jobInterrupted(File file) { changeState(file, "interrupt"); }
    private void changeState(File file, String event) {
        try {
            if ("start".equals(event)) queue.started(file);
            else if ("finish".equals(event)) queue.finished(file);
            else queue.interrupted(file);
            refreshJobs();
        } catch (java.io.IOException ex) { showError(ex); }
    }

    public String statusFor(JobEntry job) {
        File file = job.getFile();
        if (file.equals(gateway.getActiveGcodeFile()) && gateway.isSendingFile())
            return gateway.isPaused() ? "PAUSED" : "RUNNING";
        IncomingJobQueue.State state = queue.state(file);
        if (state == IncomingJobQueue.State.FINISHED) return "FINISHED";
        if (state == IncomingJobQueue.State.RUNNING) return "RUNNING";
        JobEntry next = nextJob();
        if (next != null && next.getFile().equals(file))
            return state == IncomingJobQueue.State.INTERRUPTED ? "NEXT / RETRY" : "NEXT";
        if (state == IncomingJobQueue.State.INTERRUPTED) return "STOPPED";
        return file.equals(gateway.getActiveGcodeFile()) ? "LOADED" : "WAITING";
    }

    private JobEntry nextJob() {
        for (JobEntry job : allJobs) if (queue.isPending(job.getFile()) &&
                !(gateway.isSendingFile() && job.getFile().equals(gateway.getActiveGcodeFile()))) return job;
        return null;
    }

    private List<JobEntry> pendingJobs() {
        List<JobEntry> pending = new ArrayList<>();
        for (JobEntry job : allJobs) if (queue.isPending(job.getFile()) &&
                !(gateway.isSendingFile() && job.getFile().equals(gateway.getActiveGcodeFile()))) pending.add(job);
        return pending;
    }

    private List<JobEntry> selectedJobs() {
        List<JobEntry> selected = new ArrayList<>();
        for (int row : table.getSelectedRows()) selected.add(visibleJobs.get(row));
        return selected;
    }

    private boolean canReorder() {
        List<JobEntry> selected = selectedJobs();
        return !selected.isEmpty() && pendingJobs().containsAll(selected);
    }

    private void moveSelected(int direction) {
        if (!canReorder()) return;
        List<JobEntry> pending = pendingJobs();
        Set<JobEntry> selected = new HashSet<>(selectedJobs());
        if (direction < 0) {
            for (int i = 1; i < pending.size(); i++) if (selected.contains(pending.get(i)) && !selected.contains(pending.get(i-1)))
                Collections.swap(pending, i, i-1);
        } else {
            for (int i = pending.size()-2; i >= 0; i--) if (selected.contains(pending.get(i)) && !selected.contains(pending.get(i+1)))
                Collections.swap(pending, i, i+1);
        }
        persistOrder(pending);
    }

    private boolean persistOrder(List<JobEntry> pending) {
        try {
            List<File> files = new ArrayList<>();
            for (JobEntry job : pending) files.add(job.getFile());
            queue.reorderPending(files);
            refreshJobs();
            return true;
        } catch (java.io.IOException | IllegalArgumentException ex) { showError(ex); return false; }
    }

    private void setupReordering() {
        if (!GraphicsEnvironment.isHeadless()) table.setDragEnabled(true);
        table.setDropMode(DropMode.INSERT_ROWS);
        table.setTransferHandler(new TransferHandler() {
            private final DataFlavor flavor = new DataFlavor(LocalJobsPanel.class, "Job queue rows");
            private List<JobEntry> dragged = Collections.emptyList();
            @Override public int getSourceActions(JComponent c) { return MOVE; }
            @Override protected Transferable createTransferable(JComponent c) {
                if (!canReorder()) return null;
                dragged = new ArrayList<>(selectedJobs());
                return new Transferable() {
                    public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{flavor}; }
                    public boolean isDataFlavorSupported(DataFlavor f) { return flavor.equals(f); }
                    public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                        if (!isDataFlavorSupported(f)) throw new UnsupportedFlavorException(f);
                        return LocalJobsPanel.this;
                    }
                };
            }
            @Override public boolean canImport(TransferSupport support) {
                if (!support.isDrop() || !support.isDataFlavorSupported(flavor) || !canReorder()) return false;
                try {
                    if (support.getTransferable().getTransferData(flavor) != LocalJobsPanel.this) return false;
                } catch (Exception ex) { return false; }
                int row = ((JTable.DropLocation)support.getDropLocation()).getRow();
                // The displayed list equals the full list when reordering is enabled.
                if (row > 0 && "FINISHED".equals(statusFor(allJobs.get(row-1)))) return false;
                if (row < allJobs.size() && ("RUNNING".equals(statusFor(allJobs.get(row))) || "PAUSED".equals(statusFor(allJobs.get(row))))) return false;
                return pendingJobs().containsAll(dragged);
            }
            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                int row = ((JTable.DropLocation)support.getDropLocation()).getRow();
                return dropSelected(dragged, row);
            }
        });
        table.getInputMap().put(KeyStroke.getKeyStroke("alt UP"), "queue-up");
        table.getInputMap().put(KeyStroke.getKeyStroke("alt DOWN"), "queue-down");
        table.getActionMap().put("queue-up", new AbstractAction() { public void actionPerformed(ActionEvent e) { moveSelected(-1); } });
        table.getActionMap().put("queue-down", new AbstractAction() { public void actionPerformed(ActionEvent e) { moveSelected(1); } });
    }

    private boolean dropSelected(List<JobEntry> dragged, int row) {
        List<JobEntry> pending = pendingJobs();
        if (!pending.containsAll(dragged) || row < 0 || row > allJobs.size()) return false;
        int insertion = 0;
        for (int i = 0; i < row; i++) if (pending.contains(allJobs.get(i)) && !dragged.contains(allJobs.get(i))) insertion++;
        pending.removeAll(dragged);
        pending.addAll(insertion, dragged);
        return persistOrder(pending);
    }

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
        File active = gateway.getActiveGcodeFile();
        for (JobEntry job : allJobs) {
            visibleJobs.add(job);
            boolean loaded = job.getFile().equals(active);
            job.setActiveInUgs(loaded);
            String status = statusFor(job);
            int position = allJobs.indexOf(job) + 1;
            model.addRow(new Object[]{"FINISHED".equals(status) ? "—" : position, status, job.getName(), job.getFormattedSize(), job.getFormattedDate()});
            if (selected.contains(job.getFile())) table.addRowSelectionInterval(model.getRowCount()-1, model.getRowCount()-1);
        }
        table.getSelectionModel().setValueIsAdjusting(false);

        runningLabel.setText(gateway.isSendingFile() && active != null ? (gateway.isPaused() ? "Paused: " : "Running: ") + active.getName() : "No job running");
        runningLabel.setToolTipText(runningLabel.getText());
        JobEntry next = nextJob();
        nextLabel.setText(next == null ? "Next: queue complete" : "Next: " + next.getName());
        if (allJobs.isEmpty()) nextLabel.setText("Next: waiting for incoming jobs");
        nextLabel.setToolTipText(nextLabel.getText());
        empty.setText(allJobs.isEmpty() ? "Waiting for jobs from your other computer" : " ");
        if (table.getSelectedRow() < 0) onSelected.accept(null);
        updateActions();
    }

    private void updateActions() {
        int n = table.getSelectedRowCount();
        remove.setText(n > 0 ? "Remove selected (" + n + ")" : "Remove selected");
        remove.setEnabled(n > 0);
        clear.setEnabled(!allJobs.isEmpty());
        List<JobEntry> pending = pendingJobs();
        boolean reorder = canReorder();
        Set<JobEntry> selected = new HashSet<>(selectedJobs());
        boolean canUp = false, canDown = false;
        for (int i = 0; i < pending.size(); i++) if (selected.contains(pending.get(i))) {
            if (i > 0 && !selected.contains(pending.get(i-1))) canUp = true;
            if (i + 1 < pending.size() && !selected.contains(pending.get(i+1))) canDown = true;
        }
        up.setEnabled(reorder && canUp);
        down.setEnabled(reorder && canDown);
        loadNext.setEnabled(nextJob() != null && !gateway.isSendingFile());
    }
}
