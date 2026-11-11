package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.machine.SimulatedJobManagerGateway;
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
                JTextField search = field(panel, "search", JTextField.class);
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
                search.setText("no-match");
                check(table.getRowCount() == 0 && clear.isEnabled(), "Clear includes filtered jobs");
                clear.doClick();
                check(new IncomingJobQueue(directory.toFile()).jobs().isEmpty(), "Clear must persist empty queue");
                check(first.exists() && directory.resolve("second.nc").toFile().exists(), "Source files must survive removal");
                check(gateway.isSendingFile() && gateway.getActiveGcodeFile().equals(first), "Queue removal must not interrupt machine");
                panel.selectOrAddJob(JobEntry.fromFile(first, JobOrigin.LOCAL_DISK));
                check(table.getRowCount() == 1, "Receiving again must requeue a dismissed file and clear search");
                check(new IncomingJobQueue(directory.toFile()).jobs().size() == 1, "Requeue must persist");
                System.out.println("ALL IncomingQueue TESTS PASSED!");
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
}
