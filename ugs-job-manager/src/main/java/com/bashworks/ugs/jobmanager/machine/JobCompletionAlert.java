package com.bashworks.ugs.jobmanager.machine;

import java.io.File;
import java.util.Objects;

/** Rings once for a completed run, never for cancellation, disconnect, or file loading. */
public final class JobCompletionAlert implements JobManagerListener, AutoCloseable {
    private final JobManagerGateway gateway;
    private final Runnable ring;
    private File running;
    private boolean closed;

    public JobCompletionAlert(JobManagerGateway gateway, Runnable ring) {
        this.gateway = Objects.requireNonNull(gateway);
        this.ring = Objects.requireNonNull(ring);
        if (gateway.isSendingFile()) running = gateway.getActiveGcodeFile();
        gateway.addListener(this);
    }

    @Override public synchronized void onJobStarted(File file) { if (!closed) running = file; }
    @Override public void onJobFinished(File file) {
        synchronized (this) {
            if (closed || file == null || !file.equals(running)) return;
            running = null;
        }
        ring.run();
    }
    @Override public synchronized void onJobInterrupted(File file) {
        if (file != null && file.equals(running)) running = null;
    }
    @Override public synchronized void onMachineConnectionChanged(boolean connected) { if (!connected) running = null; }
    @Override public void onActiveJobChanged(File file) {}
    @Override public void onMachineStateChanged(String state, boolean idle, boolean sending) {}
    @Override public void onStreamStateChanged(boolean streaming, boolean paused) {}
    @Override public synchronized void close() {
        closed = true;
        running = null;
        gateway.removeListener(this);
    }
}
