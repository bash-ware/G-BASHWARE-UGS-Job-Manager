package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.machine.SimulatedJobManagerGateway;
import com.bashworks.ugs.jobmanager.machine.JobManagerListener;
import java.util.*;
import com.bashworks.ugs.jobmanager.model.*;
import com.bashworks.ugs.jobmanager.service.IncomingJobQueue;
import java.nio.file.*;
import java.io.File;
import javax.swing.*;
import java.awt.*;

/** Exercises the actual queue buttons, filtering, selection and persistence. */
public class IncomingQueueTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return type.cast(f.get(target));
    }
    public static void main(String[] args) throws Exception {
        testOrderAndLifecycle();
        Path directory = Files.createTempDirectory("ugs-queue-test-");
        File first = Files.writeString(directory.resolve("first.nc"), "G21\nG1 X10\n").toFile();
        Files.writeString(directory.resolve("second.nc"), "G1 X20\n");
        Files.writeString(directory.resolve("third.nc"), "G1 X30\n");
        SimulatedJobManagerGateway gateway = new SimulatedJobManagerGateway();
        gateway.loadJobIntoUgs(first);
        gateway.startJobInUgs();
        SwingUtilities.invokeAndWait(() -> {
            try {
                LocalJobsPanel panel = new LocalJobsPanel(directory.toFile(), gateway, job -> {}, job -> {});
                JTable table = field(panel, "table", JTable.class);
                DarkButton remove = field(panel, "remove", DarkButton.class);
                DarkButton clear = field(panel, "clear", DarkButton.class);
                check(table.getRowCount() == 3, "Startup must restore incoming jobs");
                check(!remove.isEnabled(), "Remove disabled without a selection");
                table.addRowSelectionInterval(0, 0);
                table.addRowSelectionInterval(2, 2);
                panel.refreshActiveFile(first);
                check(table.getSelectedRowCount() == 2, "Machine updates must preserve multi-selection");
                remove.doClick();
                check(table.getRowCount() == 1, "Remove nonadjacent selected rows only");
                panel.refreshJobs();
                check(table.getRowCount() == 1, "Refresh must not restore removed jobs");
                check(new IncomingJobQueue(directory.toFile()).jobs().size() == 1, "Restart must retain removals");
                check(clear.isEnabled(), "Clear enabled with remaining jobs");
                clear.doClick();
                check(new IncomingJobQueue(directory.toFile()).jobs().isEmpty(), "Clear must persist empty queue");
                check(first.exists() && directory.resolve("second.nc").toFile().exists(), "Source files must survive removal");
                check(gateway.isSendingFile() && gateway.getActiveGcodeFile().equals(first), "Queue removal must not interrupt machine");
                panel.selectOrAddJob(JobEntry.fromFile(first, JobOrigin.LOCAL_DISK));
                check(table.getRowCount() == 1, "Receiving again must requeue a dismissed file");
                check(new IncomingJobQueue(directory.toFile()).jobs().size() == 1, "Requeue must persist");
                System.out.println("ALL IncomingQueue TESTS PASSED!");
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
    private static void testOrderAndLifecycle() throws Exception {
        Path dir = Files.createTempDirectory("ugs-order-test-");
        File a = Files.writeString(dir.resolve("a.nc"), "G1 X1").toFile();
        File b = Files.writeString(dir.resolve("b.nc"), "G1 X2").toFile();
        File c = Files.writeString(dir.resolve("c.nc"), "G1 X3").toFile();
        for (File file : new File[]{a,b,c}) file.setLastModified(1000);
        IncomingJobQueue queue = new IncomingJobQueue(dir.toFile());
        check(queue.jobs().get(0).getFile().equals(a), "Stable oldest-first arrival order");
        queue.reorderPending(java.util.List.of(c,a,b));
        check(new IncomingJobQueue(dir.toFile()).jobs().get(0).getFile().equals(c), "Order survives restart");
        queue.started(c);
        check(queue.jobs().get(0).getFile().equals(c), "Running job pinned first");
        check(new IncomingJobQueue(dir.toFile()).state(c) == IncomingJobQueue.State.INTERRUPTED, "Restart must never imply a completed run");
        queue.interrupted(c);
        check(queue.state(c) == IncomingJobQueue.State.INTERRUPTED, "Cancellation is not completion");
        queue.finished(c);
        check(queue.state(c) != IncomingJobQueue.State.FINISHED, "Stray completion after cancellation is ignored");
        queue.started(c);
        queue.finished(c);
        check(queue.state(c) == IncomingJobQueue.State.FINISHED, "Explicit completion marks finished");
        check(queue.jobs().get(2).getFile().equals(c), "Completed history is below pending jobs");
        check(new IncomingJobQueue(dir.toFile()).state(c) == IncomingJobQueue.State.FINISHED, "Finished state persists");
        File d = Files.writeString(dir.resolve("d.nc"), "G1 X4").toFile();
        queue.received(JobEntry.fromFile(d, JobOrigin.LOCAL_DISK));
        check(queue.jobs().get(2).getFile().equals(d), "New incoming job appends after waiting work");
        queue.received(JobEntry.fromFile(c, JobOrigin.LOCAL_DISK));
        check(queue.jobs().get(3).getFile().equals(c), "Resent finished job is queued at the end");
        boolean rejected = false;
        try { queue.reorderPending(java.util.List.of(a,a,b,d)); } catch (IllegalArgumentException ex) { rejected = true; }
        check(rejected, "Invalid reorder cannot duplicate or lose jobs");

        SimulatedJobManagerGateway gateway = new SimulatedJobManagerGateway();
        LocalJobsPanel[] ref = new LocalJobsPanel[1];
        SwingUtilities.invokeAndWait(() -> ref[0] = new LocalJobsPanel(dir.toFile(), gateway, j -> {}, j -> {}));
        LocalJobsPanel panel = ref[0];
        gateway.addListener(new JobManagerListener() {
            public void onJobStarted(File file) { panel.jobStarted(file); }
            public void onJobFinished(File file) { panel.jobFinished(file); }
            public void onJobInterrupted(File file) { panel.jobInterrupted(file); }
            public void onActiveJobChanged(File file) { panel.refreshActiveFile(file); }
            public void onMachineConnectionChanged(boolean connected) {}
            public void onMachineStateChanged(String state, boolean idle, boolean sending) { panel.refreshActiveFile(gateway.getActiveGcodeFile()); }
            public void onStreamStateChanged(boolean streaming, boolean paused) { panel.refreshActiveFile(gateway.getActiveGcodeFile()); }
        });
        SwingUtilities.invokeAndWait(() -> {
            try {
                JTable table = field(panel, "table", JTable.class);
                table.setRowSelectionInterval(1, 1); // b
                field(panel, "up", DarkButton.class).doClick();
                check("b.nc".equals(table.getValueAt(0, 2)), "Up arrow changes actual order");
                check(table.getSelectedRow() == 0, "Reordering preserves selection by identity");
                field(panel, "down", DarkButton.class).doClick();
                check("b.nc".equals(table.getValueAt(1, 2)), "Down arrow changes actual order");
                var visible = field(panel, "visibleJobs", java.util.List.class);
                var method = LocalJobsPanel.class.getDeclaredMethod("dropSelected", java.util.List.class, int.class);
                method.setAccessible(true);
                check((boolean)method.invoke(panel, java.util.List.of(visible.get(3)), 0), "Drag insertion succeeds");
                check("c.nc".equals(table.getValueAt(0, 2)), "Drag uses the persisted run order");
                check("NEXT".equals(table.getValueAt(0, 1)), "First pending row is Next");
                table.setRowSelectionInterval(0, 0);
                field(panel, "down", DarkButton.class).doClick(); // a,c,b,d
                check("a.nc".equals(table.getValueAt(0, 2)), "Arrow updates Next immediately");
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
        gateway.loadJobIntoUgs(a);
        gateway.startJobInUgs();
        SwingUtilities.invokeAndWait(() -> {
            try {
                JTable table = field(panel, "table", JTable.class);
                check("RUNNING".equals(table.getValueAt(0, 1)), "Started event shows Running");
                check("NEXT".equals(table.getValueAt(1, 1)), "Following job shows Next during a run");
                table.setRowSelectionInterval(0, 0);
                check(!field(panel, "down", DarkButton.class).isEnabled(), "Running job cannot be reordered");
                check(!field(panel, "loadNext", DarkButton.class).isEnabled(), "Cannot load over running work");
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
        gateway.finishSimulatedJob();
        SwingUtilities.invokeAndWait(() -> {
            try {
                JTable table = field(panel, "table", JTable.class);
                check("FINISHED".equals(table.getValueAt(3, 1)), "Completion moves row to finished history");
                check("NEXT".equals(table.getValueAt(0, 1)), "Next advances after completion");
                check(!gateway.isSendingFile(), "Next job never starts automatically");
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
        gateway.loadJobIntoUgs(c);
        gateway.startJobInUgs();
        SwingUtilities.invokeAndWait(() -> {});
        gateway.cancelSimulatedJob();
        SwingUtilities.invokeAndWait(() -> check(new IncomingJobQueueUnchecked(dir.toFile()).state(c) != IncomingJobQueue.State.FINISHED, "Stopped job is never Finished"));
        System.out.println("ALL queue order and lifecycle TESTS PASSED!");
    }
    private static class IncomingJobQueueUnchecked {
        final IncomingJobQueue queue;
        IncomingJobQueueUnchecked(File dir) {
            try { queue = new IncomingJobQueue(dir); } catch (Exception ex) { throw new RuntimeException(ex); }
        }
        IncomingJobQueue.State state(File file) { return queue.state(file); }
    }

}
