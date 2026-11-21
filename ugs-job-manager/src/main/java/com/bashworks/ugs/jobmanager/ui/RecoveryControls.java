package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.machine.JobManagerGateway;
import com.bashworks.ugs.jobmanager.machine.RecoveryTelemetry;
import com.bashworks.ugs.jobmanager.service.RecoveryStore;
import com.willwinder.universalgcodesender.model.UnitUtils.Units;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import javax.swing.*;

/** Restores operator context without issuing controller commands. */
final class RecoveryControls extends JPanel implements AutoCloseable {
    private final LocalJobsPanel jobs;
    private final JobManagerGateway gateway;
    private final RecoveryStore store;
    private final Preferences preferences;
    private final JCheckBox enabled = new JCheckBox("Autosave");
    private final DarkButton viewCheckpoint = new DarkButton("View checkpoint");
    private final JLabel status = UITheme.createMutedLabel("");
    private final javax.swing.Timer timer;
    private File restoredFromCopy;
    private Properties saved = new Properties();
    private Properties recovered;
    private boolean ready, writing, closed;
    private Supplier<String> nativeMap = () -> null;
    private Function<String, Boolean> restoreNativeMap = data -> false;
    private BiFunction<File, Units, Boolean> restoreXyzMap = (file, units) -> false;
    private String retainedNativeMap = "";
    // Existing 1.6.0 checkpoints may reference an attached XYZ export.
    private final Properties legacyMap = new Properties();
    private String mapSeenAfterFailedRestore;

    RecoveryControls(LocalJobsPanel jobs, JobManagerGateway gateway, File incoming) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 0));
        this.jobs = jobs;
        this.gateway = gateway;
        store = new RecoveryStore(incoming.toPath().resolveSibling("recovery"));
        String profile = UUID.nameUUIDFromBytes(incoming.getAbsolutePath().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        preferences = Preferences.userNodeForPackage(RecoveryControls.class).node(profile);
        setOpaque(false);
        enabled.setOpaque(false);
        enabled.setForeground(UITheme.TEXT_SECONDARY);
        enabled.setSelected(preferences.getBoolean("autosave", true));
        enabled.setToolTipText("Save the queue and machine recovery checkpoint");
        enabled.addActionListener(e -> {
            preferences.putBoolean("autosave", enabled.isSelected());
            if (enabled.isSelected()) tick();
            else status.setText("Autosave off");
        });
        add(enabled);
        viewCheckpoint.addActionListener(e -> review());
        viewCheckpoint.setVisible(false);
        add(viewCheckpoint);
        add(status);
        timer = new javax.swing.Timer(500, e -> tick());
    }

    void setNativeMap(Supplier<String> read, Function<String, Boolean> restore,
                      BiFunction<File, Units, Boolean> restoreXyz) {
        nativeMap = read;
        restoreNativeMap = restore;
        restoreXyzMap = restoreXyz;
    }

    void open() {
        if (closed || ready) return;
        try {
            saved = store.read();
        } catch (IOException | IllegalArgumentException ex) {
            showStatus("Could not read autosave", ex.getMessage());
            return;
        }

        if (!saved.isEmpty()) {
            String file = saved.getProperty("unfinished.name", saved.getProperty("loaded.name", ""));
            String message = "Restore the saved queue, job and AutoLeveler map?";
            if (!file.isBlank()) message += "\n" + file;
            Object[] options = {"Restore", "Skip"};
            int answer = JOptionPane.showOptionDialog(this, message, "Restore autosave?",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (answer == 0) {
                if (gateway.isSendingFile() || (gateway.isConnected() && !gateway.isIdle())) {
                    showStatus("Autosave retained", "Restore requires an idle controller. Reopen Job Manager when idle.");
                    return;
                }
                recover(saved);
            }
        }

        ready = true;
        timer.start();
        tick();
    }

    private void recover(Properties checkpoint) {
        List<String> issues = new ArrayList<>();
        try {
            jobs.restoreQueue(checkpoint.getProperty("queue", ""));
        } catch (IOException ex) {
            issues.add("Queue: " + ex.getMessage());
        }
        try {
            File file = restoreFile(checkpoint, checkpoint.containsKey("unfinished.name") ? "unfinished" : "loaded");
            if (file != null) {
                gateway.loadJobIntoUgs(file);
                jobs.selectFile(file);
            }
        } catch (Exception ex) {
            issues.add("Job: " + ex.getMessage());
        }

        retainedNativeMap = checkpoint.getProperty("map.native", "");
        checkpoint.stringPropertyNames().stream()
                .filter(key -> key.startsWith("map.") && !key.equals("map.native"))
                .forEach(key -> legacyMap.setProperty(key, checkpoint.getProperty(key)));
        boolean expectsMap = !retainedNativeMap.isEmpty() || !legacyMap.isEmpty();
        boolean mapRestored = false;
        if (expectsMap) {
            try {
                if (!retainedNativeMap.isEmpty()) {
                    mapRestored = restoreNativeMap.apply(retainedNativeMap);
                } else {
                    File xyz = store.backedUp(legacyMap.getProperty("map.backup"));
                    String savedUnits = legacyMap.getProperty("map.units", "");
                    if (xyz != null && ("MM".equals(savedUnits) || "INCH".equals(savedUnits))) {
                        mapRestored = restoreXyzMap.apply(xyz, Units.valueOf(savedUnits));
                    }
                }
            } catch (IOException | RuntimeException ex) {
                issues.add("AutoLeveler: " + ex.getMessage());
            }
            if (!mapRestored) {
                mapSeenAfterFailedRestore = Objects.toString(nativeMap.get(), "");
                issues.add("The height map remains saved in the checkpoint.");
            }
        }

        recovered = checkpoint;
        viewCheckpoint.setVisible(checkpoint.containsKey("unfinished.name"));
        if (!issues.isEmpty()) {
            showStatus("Autosave partially restored", String.join(" ", issues));
        } else {
            showStatus(mapRestored ? "AutoLeveler map restored" : "Autosave restored", null);
        }
        jobs.refreshActiveFile(gateway.getActiveGcodeFile());
    }

    private File restoreFile(Properties checkpoint, String prefix) throws IOException {
        String original = checkpoint.getProperty(prefix + ".path");
        File copy = store.backedUp(checkpoint.getProperty(prefix + ".backup"));
        if (original == null) return copy;
        File file = new File(original);
        if (copy != null && file.isFile() && Files.mismatch(file.toPath(), copy.toPath()) == -1) return file;
        if (copy == null) throw new IOException("Saved job is missing: " + file.getName());
        restoredFromCopy = copy;
        return copy;
    }

    private void review() {
        if (recovered == null) return;
        StringBuilder text = new StringBuilder();
        text.append(recovered.getProperty("unfinished.name", "Saved job")).append('\n');
        text.append("Saved at ").append(recovered.getProperty("saved.at", "unknown")).append("\n\n");
        for (String[] field : new String[][] {
                {"Status", "status"}, {"Machine position", "machine"}, {"Work position", "work"}, {"Work offset", "offset"},
                {"Last sent command", "sent.command"}, {"UGS command number", "sent.line"},
                {"Last acknowledged command", "ack.command"}, {"Acknowledged UGS command number", "ack.line"},
                {"Rows sent", "rows.sent"}, {"Rows acknowledged", "rows.acknowledged"},
                {"Modal state", "modal"}, {"Accessories", "accessories"}, {"Programmed feed", "feed"},
                {"Reported spindle RPM", "spindle.actual"}, {"Reported feed", "feed.actual"}
        }) text.append(field[0]).append(": ").append(recovered.getProperty(field[1], "Unavailable")).append('\n');
        text.append("\nSaved position is a controller report. Some acknowledged moves may still have been buffered.");
        if (recovered.containsKey("processed.backup")) text.append("\nThe original processed UGS stream is preserved with the checkpoint.");
        if (restoredFromCopy != null) text.append("\nLoaded the saved copy: ").append(restoredFromCopy.getName());
        JTextArea details = new JTextArea(text.toString(), 17, 62);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(details), "Saved job checkpoint", JOptionPane.INFORMATION_MESSAGE);
    }

    void newRun() {
        recovered = null;
        restoredFromCopy = null;
        viewCheckpoint.setVisible(false);
        tick();
    }

    void tick() {
        if (!ready || closed || writing || !enabled.isSelected()) return;
        RecoveryTelemetry telemetry = gateway.recoveryTelemetry();
        RecoveryTelemetry.Snapshot snapshot = telemetry == null
                ? new RecoveryTelemetry.Snapshot(gateway.getActiveGcodeFile(), null, null, Map.of()) : telemetry.snapshot();
        Properties data = new Properties();
        data.setProperty("queue", jobs.queueCheckpoint());
        data.putAll(snapshot.state());
        String liveMap = nativeMap.get();
        if (liveMap != null && !liveMap.equals(mapSeenAfterFailedRestore)) {
            retainedNativeMap = liveMap;
            legacyMap.clear();
            mapSeenAfterFailedRestore = null;
        }
        data.putAll(legacyMap);
        if (!retainedNativeMap.isEmpty()) data.setProperty("map.native", retainedNativeMap);
        Properties preceding = saved;
        Properties recoveredSnapshot = recovered;
        boolean keepUnfinished = snapshot.unfinished() == null && recoveredSnapshot != null
                && recoveredSnapshot.containsKey("unfinished.name") && !"FINISHED".equals(snapshot.state().get("status"));
        writing = true;
        new SwingWorker<Properties, Void>() {
            @Override protected Properties doInBackground() throws IOException {
                saveFile(data, preceding, "loaded", snapshot.loaded());
                if (keepUnfinished) {
                    for (String key : recoveredSnapshot.stringPropertyNames())
                        if (!key.startsWith("loaded.") && !key.startsWith("map.") && !key.equals("queue")
                                && !key.equals("format") && !key.equals("saved.at")) data.setProperty(key, recoveredSnapshot.getProperty(key));
                } else {
                    saveFile(data, preceding, "unfinished", snapshot.unfinished());
                    saveFile(data, preceding, "processed", snapshot.processed());
                }
                if (!data.containsKey("loaded.name") && !data.containsKey("unfinished.name")
                        && !data.containsKey("map.name") && !data.containsKey("map.native")
                        && data.getProperty("queue", "").isEmpty() && preceding.isEmpty()) return preceding;
                Properties previous = new Properties();
                previous.putAll(preceding);
                previous.remove("saved.at");
                previous.remove("format");
                if (data.equals(previous)) return preceding;
                data.setProperty("saved.at", Instant.now().toString());
                store.save(data);
                return data;
            }
            @Override protected void done() {
                writing = false;
                if (closed) return;
                try {
                    saved = get();
                    if (status.getText().isBlank() && !saved.isEmpty())
                        status.setToolTipText("Saved " + saved.getProperty("saved.at"));
                } catch (Exception ex) {
                    status.setText("Autosave failed");
                    status.setToolTipText(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                }
            }
        }.execute();
    }

    private void saveFile(Properties data, Properties preceding, String prefix, File file) throws IOException {
        if (file == null) return;
        data.setProperty(prefix + ".name", file.getName());
        data.setProperty(prefix + ".path", file.getAbsolutePath());
        String backup = store.backup(file, prefix);
        if (backup.isBlank() && file.getAbsolutePath().equals(preceding.getProperty(prefix + ".path")))
            backup = preceding.getProperty(prefix + ".backup", "");
        if (!backup.isBlank()) data.setProperty(prefix + ".backup", backup);
    }

    private void showStatus(String message, String details) {
        status.setText(message);
        status.setToolTipText(details);
    }

    @Override public void close() { closed = true; timer.stop(); }
}
