package com.bashworks.ugs.jobmanager.machine;

import com.willwinder.universalgcodesender.listeners.ControllerState;
import com.willwinder.universalgcodesender.listeners.UGSEventListener;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.utils.Settings;
import com.willwinder.universalgcodesender.model.UGSEvent;
import com.willwinder.universalgcodesender.model.events.ControllerStateEvent;
import com.willwinder.universalgcodesender.model.events.FileState;
import com.willwinder.universalgcodesender.model.events.FileStateEvent;
import com.willwinder.universalgcodesender.model.events.StreamEvent;
import com.willwinder.universalgcodesender.utils.SettingsFactory;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Concrete gateway integrating with UGS BackendAPI and listening for UGS events.
 */
public final class UgsJobManagerGateway implements JobManagerGateway, UGSEventListener {

    private final BackendAPI backend;
    private final List<JobManagerListener> listeners = new CopyOnWriteArrayList<>();

    public UgsJobManagerGateway(BackendAPI backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override
    public boolean isConnected() {
        return backend.isConnected();
    }

    @Override
    public boolean isIdle() {
        return backend.isIdle() && backend.getControllerState() == ControllerState.IDLE;
    }

    @Override
    public boolean isSendingFile() {
        return backend.isSendingFile();
    }

    @Override
    public boolean isPaused() {
        return backend.isPaused();
    }

    @Override
    public String getControllerStateName() {
        ControllerState state = backend.getControllerState();
        return state != null ? state.name() : "DISCONNECTED";
    }

    @Override
    public File getActiveGcodeFile() {
        File proc = backend.getProcessedGcodeFile();
        return (proc != null && proc.exists()) ? proc : backend.getGcodeFile();
    }

    @Override
    public void loadJobIntoUgs(File file) throws Exception {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("File does not exist: " + (file != null ? file.getAbsolutePath() : "null"));
        }
        if (isSendingFile()) {
            throw new IllegalStateException("Cannot change job while machine is actively sending/streaming!");
        }

        backend.setGcodeFile(file);

        try {
            Settings settings = backend.getSettings();
            if (settings != null) {
                settings.setLastOpenedFilename(file.getAbsolutePath());
                SettingsFactory.saveSettings(settings);
            }
        } catch (Throwable ignored) {
            // Non-critical settings saving error
        }

        dispatchActiveJobChanged(file);
    }

    @Override
    public void reloadActiveJob() throws Exception {
        if (isSendingFile()) {
            throw new IllegalStateException("Cannot reload job while machine is actively streaming!");
        }
        backend.reloadGcodeFile();
        dispatchActiveJobChanged(getActiveGcodeFile());
    }

    @Override
    public void unsetActiveJob() throws Exception {
        if (isSendingFile()) {
            throw new IllegalStateException("Cannot unload job while machine is actively streaming!");
        }
        backend.unsetGcodeFile();
        dispatchActiveJobChanged(null);
    }

    @Override
    public void startJobInUgs() throws Exception {
        if (!isConnected()) {
            throw new IllegalStateException("Machine is not connected.");
        }
        if (!isIdle()) {
            throw new IllegalStateException("Machine is busy or in Alarm state: " + getControllerStateName());
        }
        if (getActiveGcodeFile() == null) {
            throw new IllegalStateException("No G-code job is currently loaded in UGS.");
        }
        backend.send();
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

    @Override
    public void UGSEvent(UGSEvent event) {
        if (event instanceof FileStateEvent) {
            FileStateEvent fse = (FileStateEvent) event;
            if (fse.getFileState() == FileState.FILE_LOADED) {
                dispatchActiveJobChanged(getActiveGcodeFile());
            } else if (fse.getFileState() == FileState.FILE_UNLOADED) {
                dispatchActiveJobChanged(null);
            }
        } else if (event instanceof ControllerStateEvent) {
            ControllerStateEvent cse = (ControllerStateEvent) event;
            ControllerState state = cse.getState();
            boolean connected = isConnected();
            boolean idle = isIdle();
            boolean sending = isSendingFile();

            SwingUtilities.invokeLater(() -> {
                for (JobManagerListener l : listeners) {
                    l.onMachineConnectionChanged(connected);
                    l.onMachineStateChanged(state != null ? state.name() : "UNKNOWN", idle, sending);
                }
            });
        } else if (event instanceof StreamEvent) {
            StreamEvent se = (StreamEvent) event;
            boolean streaming = isSendingFile();
            boolean paused = isPaused();

            SwingUtilities.invokeLater(() -> {
                for (JobManagerListener l : listeners) {
                    l.onStreamStateChanged(streaming, paused);
                }
            });
        }
    }

    private void dispatchActiveJobChanged(File file) {
        SwingUtilities.invokeLater(() -> {
            for (JobManagerListener l : listeners) {
                l.onActiveJobChanged(file);
            }
        });
    }
}
