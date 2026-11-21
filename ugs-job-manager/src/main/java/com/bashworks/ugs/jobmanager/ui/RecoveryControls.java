package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.machine.JobManagerGateway;
import com.bashworks.ugs.jobmanager.machine.RecoveryTelemetry;
import com.bashworks.ugs.jobmanager.machine.UgsHeightMapRecovery;
import com.bashworks.ugs.jobmanager.service.RecoveryStore;
import com.willwinder.universalgcodesender.model.UnitUtils.Units;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
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
    private final DarkButton map = new DarkButton("Height map");
    private final DarkButton resume = new DarkButton("Continue…");
    private final DarkButton viewCheckpoint = new DarkButton("View checkpoint");
    private final DarkButton recoverNow = new DarkButton("Recover autosave…");
    private final JLabel status = UITheme.createMutedLabel("");
    private final javax.swing.Timer timer;
    private File attachedMap;
    private Units attachedMapUnits;
    private File restoredFromCopy;
    private Properties saved = new Properties();
    private Properties recovered;
    private boolean ready, writing, closed;
    private boolean preserveNativeMap;
    private Supplier<String> nativeMap = () -> null;
    private java.util.function.Function<String, Boolean> restoreNativeMap = data -> false;
    private java.util.function.BiFunction<File, Units, Boolean> restoreXyzMap = (file, units) -> false;
    private String retainedNativeMap = "";

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
        map.addActionListener(e -> chooseMap());
        resume.setEnabled(false);
        resume.addActionListener(e -> review());
        add(enabled);
        add(map);
        add(resume);
        viewCheckpoint.addActionListener(e -> review());
        viewCheckpoint.setVisible(false);
        add(viewCheckpoint);
        recoverNow.addActionListener(e -> open());
        recoverNow.setVisible(false);
        add(recoverNow);
        add(status);
        timer = new javax.swing.Timer(500, e -> tick());
    }

    void setNativeMap(Supplier<String> read, java.util.function.Function<String, Boolean> restore,
                      java.util.function.BiFunction<File, Units, Boolean> restoreXyz) {
        nativeMap = read;
        restoreNativeMap = restore;
        restoreXyzMap = restoreXyz;
    }

    void open() {
        if (closed || ready) return;
        try {
            saved = store.read();
            if (!saved.isEmpty() && !offerRecovery()) {
                status.setText("Recovery deferred");
                recoverNow.setVisible(true);
                return;
            }
            recoverNow.setVisible(false);
            ready = true;
            timer.start();
            tick();
        } catch (IOException ex) {
            status.setText("Recovery needs attention");
            recoverNow.setVisible(!saved.isEmpty());
            showError(ex);
        }
    }

    private boolean offerRecovery() throws IOException {
        String file = saved.getProperty("unfinished.name", saved.getProperty("loaded.name", "No loaded job"));
        Object[] actions = {"Recover", "Discard checkpoint", "Later"};
        int answer = JOptionPane.showOptionDialog(this, "Restore the saved queue, job and height map?\n" + file,
                "Recover autosave", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, actions, actions[0]);
        if (answer == 0 && gateway.isSendingFile()) {
            throw new IOException("An active stream prevents restoring the saved job.");
        }
        if (answer == 0) {
            recover();
        } else if (answer == 1) {
            store.discard();
            saved = new Properties();
        } else {
            return false;
        }
        return true;
    }

    private void recover() throws IOException {
        Properties checkpoint = saved;
        File loaded = null;
        String loadIssue = "";
        try {
            loaded = restoreFile(checkpoint, checkpoint.containsKey("unfinished.name") ? "unfinished" : "loaded");
        } catch (IOException ex) {
            loadIssue = ex.getMessage();
        }
        boolean hasUnfinished = checkpoint.containsKey("unfinished.name");
        Object[] movementChoices = {"Tool stayed in place", "Tool moved", "Unsure"};
        int movement = JOptionPane.CLOSED_OPTION;
        if (hasUnfinished) {
            movement = JOptionPane.showOptionDialog(this, "Did the tool or machine axes move after the job stopped?",
                    "Verify tool position", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, movementChoices, movementChoices[2]);
        }
        if (gateway.isSendingFile()) throw new IOException("An active stream prevents restoring the saved job.");
        jobs.restoreQueue(checkpoint.getProperty("queue", ""));
        try {
            attachedMap = store.backedUp(checkpoint.getProperty("map.backup"));
            attachedMapUnits = readUnits(checkpoint.getProperty("map.units"));
        } catch (IOException ex) {
            loadIssue = loadIssue.isBlank() ? ex.getMessage() : loadIssue + "\n" + ex.getMessage();
        }
        retainedNativeMap = checkpoint.getProperty("map.native", "");
        recovered = checkpoint;
        if (loaded != null) {
            try {
                gateway.loadJobIntoUgs(loaded);
                jobs.selectFile(loaded);
            } catch (Exception ex) {
                loadIssue = ex.getMessage();
                loaded = null;
            }
        }
        boolean mapRestored = loadSavedMap();
        preserveNativeMap = !mapRestored && !retainedNativeMap.isEmpty();
        if (!retainedNativeMap.isEmpty() || attachedMap != null) {
            map.setText(mapRestored ? "Height map restored" : "Saved height map");
        }
        resume.setEnabled(hasUnfinished && movement == 0 && loaded != null);
        viewCheckpoint.setVisible(!resume.isEnabled());
        resume.setToolTipText(hasUnfinished && movement != 0
                ? "Tool position must be re-established before any continuation" : "Review the saved continuation point");
        status.setText(hasUnfinished && movement != 0 ? "Recovered · tool position unverified" : "Autosave recovered");
        if (!loadIssue.isBlank()) {
            status.setText("Recovered with missing data");
            JOptionPane.showMessageDialog(this, loadIssue, "Recovery details", JOptionPane.WARNING_MESSAGE);
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

    private boolean isXyz(File file) {
        return file != null && file.getName().toLowerCase(Locale.ROOT).endsWith(".xyz");
    }

    private static Units readUnits(String value) {
        if ("MM".equals(value)) return Units.MM;
        if ("INCH".equals(value)) return Units.INCH;
        return null;
    }

    private Units chooseMapUnits() {
        Object[] units = {"Millimetres", "Inches"};
        int choice = JOptionPane.showOptionDialog(this, "Choose the units used when this XYZ map was exported.",
                "Height map units", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, units, null);
        if (choice < 0) return null;
        return choice == 0 ? Units.MM : Units.INCH;
    }

    private boolean loadSavedMap() {
        if (!retainedNativeMap.isEmpty()) return restoreNativeMap.apply(retainedNativeMap);
        return isXyz(attachedMap) && attachedMapUnits != null
                && restoreXyzMap.apply(attachedMap, attachedMapUnits);
    }

    private void chooseMap() {
        if (attachedMap != null || !retainedNativeMap.isEmpty()) {
            Object[] options = {"Load in AutoLeveler", "Export saved map", "Choose map file", "Detach map", "Close"};
            int choice = JOptionPane.showOptionDialog(this, "A height map is attached to recovery.", "Height map",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (choice == 0) {
                if (isXyz(attachedMap) && attachedMapUnits == null) attachedMapUnits = chooseMapUnits();
                if (loadSavedMap()) {
                    preserveNativeMap = false;
                    map.setText("Height map restored");
                    tick();
                } else {
                    JOptionPane.showMessageDialog(this,
                            "AutoLeveler needs an idle controller, an available scanner, matching units, and an empty scan grid.",
                            "Height map saved for import", JOptionPane.INFORMATION_MESSAGE);
                }
                return;
            }
            if (choice == 1) { exportMap(); return; }
            if (choice == 3) {
                attachedMap = null;
                attachedMapUnits = null;
                retainedNativeMap = "";
                preserveNativeMap = false;
                map.setText("Height map");
                tick();
                return;
            }
            if (choice != 2) return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Attach an exported height map");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            Units units = isXyz(file) ? chooseMapUnits() : null;
            if (isXyz(file) && units == null) return;
            attachedMap = file;
            attachedMapUnits = units;
            retainedNativeMap = "";
            map.setText("Height map attached");
            map.setToolTipText(attachedMap.getName());
            tick();
        }
    }

    private void exportMap() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(attachedMap != null ? "Export recovered height map"
                : "Export recovered height map (" + UgsHeightMapRecovery.units(retainedNativeMap) + ")");
        chooser.setSelectedFile(new File(attachedMap != null ? attachedMap.getName() : "recovered-height-map.xyz"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        try {
            if (attachedMap != null) Files.copy(attachedMap.toPath(), target);
            else Files.writeString(target, UgsHeightMapRecovery.exportXyz(retainedNativeMap), StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) { showError(ex); }
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
        JOptionPane.showMessageDialog(this, new Object[]{
                "Review the checkpoint before deciding where to restart. Machine continuation is not enabled.",
                new JScrollPane(details)}, "Continue from stop position", JOptionPane.INFORMATION_MESSAGE);
    }

    void newRun() {
        recovered = null;
        restoredFromCopy = null;
        resume.setEnabled(false);
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
        String liveMap = mapFileFromScanner();
        if (liveMap != null && (!liveMap.isEmpty() || !preserveNativeMap)) {
            retainedNativeMap = liveMap;
            preserveNativeMap = false;
            if (!liveMap.isEmpty() && attachedMap == null && !map.getText().equals("Height map restored"))
                map.setText("Saved height map");
            if (liveMap.isEmpty() && attachedMap == null) map.setText("Height map");
        }
        if (!retainedNativeMap.isEmpty()) data.setProperty("map.native", retainedNativeMap);
        File mapFile = attachedMap;
        if (mapFile != null && attachedMapUnits != null) data.setProperty("map.units", attachedMapUnits.name());
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
                saveFile(data, preceding, "map", mapFile);
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
                    status.setToolTipText(saved.isEmpty() ? null : "Saved " + saved.getProperty("saved.at"));
                } catch (Exception ex) {
                    status.setText("Autosave failed");
                    status.setToolTipText(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                }
            }
        }.execute();
    }

    private String mapFileFromScanner() { return attachedMap == null ? nativeMap.get() : null; }

    private void saveFile(Properties data, Properties preceding, String prefix, File file) throws IOException {
        if (file == null) return;
        data.setProperty(prefix + ".name", file.getName());
        data.setProperty(prefix + ".path", file.getAbsolutePath());
        String backup = store.backup(file, prefix);
        if (backup.isBlank() && file.getAbsolutePath().equals(preceding.getProperty(prefix + ".path")))
            backup = preceding.getProperty(prefix + ".backup", "");
        if (!backup.isBlank()) data.setProperty(prefix + ".backup", backup);
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Job recovery", JOptionPane.WARNING_MESSAGE);
    }

    @Override public void close() { closed = true; timer.stop(); }
}
