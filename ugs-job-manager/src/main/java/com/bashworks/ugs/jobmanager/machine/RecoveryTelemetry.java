package com.bashworks.ugs.jobmanager.machine;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** Host observations only. Acknowledged commands can still be queued inside the controller. */
public final class RecoveryTelemetry {
    private File loaded;
    private File unfinished;
    private File processed;
    private boolean running;
    private final Map<String, String> state = new LinkedHashMap<>();

    public synchronized void loaded(File file, File stream) {
        loaded = file;
        if (running && unfinished != null && unfinished.equals(file) && processed == null) processed = stream;
    }

    public synchronized void started(File file, File stream) {
        if (file == null) return;
        unfinished = file;
        processed = stream;
        running = true;
        state.clear();
        state.put("status", "RUNNING");
    }

    public synchronized void finished(File file) {
        if (file != null && file.equals(unfinished)) {
            running = false;
            unfinished = null;
            processed = null;
            state.clear();
            state.put("status", "FINISHED");
        }
    }

    public synchronized void interrupted() {
        if (!running) return;
        running = false;
        state.put("status", "INTERRUPTED");
    }

    public synchronized void report(Map<String, String> values) {
        if (unfinished == null || running) state.putAll(values);
    }

    public synchronized void sent(int line, String text) {
        if (unfinished != null && !running) return;
        state.put("sent.line", Integer.toString(line));
        state.put("sent.command", text);
    }

    public synchronized void acknowledged(int line, String text) {
        if (!running) return;
        state.put("ack.line", Integer.toString(line));
        state.put("ack.command", text);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(loaded, unfinished, processed, Map.copyOf(state));
    }

    public record Snapshot(File loaded, File unfinished, File processed, Map<String, String> state) {}
}
