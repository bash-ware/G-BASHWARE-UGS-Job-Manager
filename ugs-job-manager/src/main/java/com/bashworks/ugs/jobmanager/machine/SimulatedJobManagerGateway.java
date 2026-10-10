package com.bashworks.ugs.jobmanager.machine;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory simulated UGS Gateway for fast standalone preview and testing without running UGS.
 */
public final class SimulatedJobManagerGateway implements JobManagerGateway {

    private final List<JobManagerListener> listeners = new CopyOnWriteArrayList<>();
    private File activeGcodeFile;
    private boolean connected = true;
    private boolean idle = true;
    private boolean sending = false;
    private boolean paused = false;
    private String stateName = "IDLE";

    public SimulatedJobManagerGateway() {
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isIdle() {
        return idle && !sending;
    }

    @Override
    public boolean isSendingFile() {
        return sending;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public String getControllerStateName() {
        return stateName;
    }

    @Override
    public File getActiveGcodeFile() {
        return activeGcodeFile;
    }

    @Override
    public void loadJobIntoUgs(File file) throws Exception {
        if (sending) {
            throw new IllegalStateException("Simulated machine is currently running a job!");
        }
        this.activeGcodeFile = file;
        this.stateName = "IDLE";
        this.idle = true;
        this.sending = false;

        dispatchActiveJobChanged(file);
    }

    @Override
    public void reloadActiveJob() throws Exception {
        dispatchActiveJobChanged(activeGcodeFile);
    }

    @Override
    public void unsetActiveJob() throws Exception {
        this.activeGcodeFile = null;
        dispatchActiveJobChanged(null);
    }

    @Override
    public void startJobInUgs() throws Exception {
        if (!connected) {
            throw new IllegalStateException("Simulated machine is disconnected!");
        }
        if (activeGcodeFile == null) {
            throw new IllegalStateException("No G-code file loaded to start!");
        }
        this.sending = true;
        this.idle = false;
        this.stateName = "RUN";

        SwingUtilities.invokeLater(() -> {
            for (JobManagerListener l : listeners) {
                l.onMachineStateChanged("RUN", false, true);
                l.onStreamStateChanged(true, false);
            }
        });
    }

    public void setSimulatedConnected(boolean connected) {
        this.connected = connected;
        this.stateName = connected ? "IDLE" : "DISCONNECTED";
        this.idle = connected;
        this.sending = false;

        SwingUtilities.invokeLater(() -> {
            for (JobManagerListener l : listeners) {
                l.onMachineConnectionChanged(connected);
                l.onMachineStateChanged(stateName, idle, false);
            }
        });
    }

    @Override
    public void addListener(JobManagerListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(JobManagerListener listener) {
        listeners.remove(listener);
    }

    private void dispatchActiveJobChanged(File file) {
        SwingUtilities.invokeLater(() -> {
            for (JobManagerListener l : listeners) {
                l.onActiveJobChanged(file);
            }
        });
    }
}
