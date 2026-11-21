package com.bashworks.ugs.jobmanager.machine;

import com.willwinder.universalgcodesender.listeners.ControllerState;
import com.willwinder.universalgcodesender.listeners.ControllerStatus;
import com.willwinder.universalgcodesender.model.events.CommandEvent;
import com.willwinder.universalgcodesender.model.events.ControllerStatusEvent;
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
    private volatile File streamingFile;
    private final RecoveryTelemetry recovery = new RecoveryTelemetry();
    private final List<JobManagerListener> listeners = new CopyOnWriteArrayList<>();

    public UgsJobManagerGateway(BackendAPI backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        recovery.loaded(backend.getGcodeFile(), backend.getProcessedGcodeFile());
        if (backend.isSendingFile()) {
            streamingFile = getActiveGcodeFile();
            recovery.started(streamingFile, backend.getProcessedGcodeFile());
        }
    }

    @Override public RecoveryTelemetry recoveryTelemetry() { return recovery; }

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
        File source = backend.getGcodeFile();
        return source != null ? source : backend.getProcessedGcodeFile();
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
        if (event instanceof CommandEvent command) {
            var code = command.getCommand();
            switch (command.getCommandEventType()) {
                case COMMAND_SENT -> recovery.sent(code.getCommandNumber(), code.getCommandString());
                case COMMAND_COMPLETE -> {
                    if (code.isOk()) recovery.acknowledged(code.getCommandNumber(), code.getCommandString());
                }
                default -> { }
            }
        } else if (event instanceof ControllerStatusEvent status) {
            if (backend.isConnected()) captureRecoveryStatus(status.getStatus());
        } else if (event instanceof FileStateEvent) {
            FileStateEvent fse = (FileStateEvent) event;
            if (fse.getFileState() == FileState.FILE_LOADED) {
                recovery.loaded(backend.getGcodeFile(), backend.getProcessedGcodeFile());
                dispatchActiveJobChanged(getActiveGcodeFile());
            } else if (fse.getFileState() == FileState.FILE_UNLOADED) {
                recovery.loaded(null, null);
                dispatchActiveJobChanged(null);
            }
        } else if (event instanceof ControllerStateEvent) {
            ControllerStateEvent cse = (ControllerStateEvent) event;
            ControllerState state = cse.getState();
            boolean connected = isConnected();
            boolean idle = isIdle();
            boolean sending = isSendingFile();

            if (!connected || state == ControllerState.ALARM) interruptStream();
            else recovery.report(java.util.Map.of("status", state == null ? "UNKNOWN" : state.name(),
                    "paused", Boolean.toString(backend.isPaused())));
            SwingUtilities.invokeLater(() -> {
                for (JobManagerListener l : listeners) {
                    l.onMachineConnectionChanged(connected);
                    l.onMachineStateChanged(state != null ? state.name() : "UNKNOWN", idle, sending);
                }
            });
        } else if (event instanceof StreamEvent) {
            StreamEvent se = (StreamEvent) event;
            switch (se.getType()) {
                case STREAM_STARTED:
                    streamingFile = getActiveGcodeFile();
                    recovery.started(streamingFile, backend.getProcessedGcodeFile());
                    if (backend.isConnected()) captureRecoveryStatus(null);
                    File started = streamingFile;
                    SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.onJobStarted(started)));
                    break;
                case STREAM_COMPLETE:
                    File finished = streamingFile;
                    recovery.finished(finished);
                    streamingFile = null;
                    if (finished != null) SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.onJobFinished(finished)));
                    break;
                case STREAM_CANCELED:
                    interruptStream();
                    break;
                default: break;
            }
            boolean streaming = isSendingFile();
            boolean paused = isPaused();

            SwingUtilities.invokeLater(() -> {
                for (JobManagerListener l : listeners) {
                    l.onStreamStateChanged(streaming, paused);
                }
            });
        }
    }

    private void interruptStream() {
        File interrupted = streamingFile;
        recovery.interrupted();
        streamingFile = null;
        if (interrupted != null) SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.onJobInterrupted(interrupted)));
    }

    private void captureRecoveryStatus(ControllerStatus status) {
        var fields = new java.util.LinkedHashMap<String, String>();
        fields.put("status", getControllerStateName());
        fields.put("paused", Boolean.toString(backend.isPaused()));
        fields.put("streaming", Boolean.toString(backend.isSendingFile()));
        fields.put("rows.sent", Long.toString(backend.getNumSentRows()));
        fields.put("rows.acknowledged", Long.toString(backend.getNumCompletedRows()));
        if (status == null) {
            savePosition(fields, "machine", backend.getMachinePosition());
            savePosition(fields, "work", backend.getWorkPosition());
        } else {
            savePosition(fields, "machine", status.getMachineCoord());
            savePosition(fields, "work", status.getWorkCoord());
            savePosition(fields, "offset", status.getWorkCoordinateOffset());
            if (status.getSpindleSpeed() != null) fields.put("spindle.actual", status.getSpindleSpeed().toString());
            if (status.getFeedSpeed() != null) fields.put("feed.actual", status.getFeedSpeed() + " " + status.getFeedSpeedUnits());
        }
        var modal = backend.getGcodeState();
        if (modal != null) {
            fields.put("modal", modal.machineStateCode());
            fields.put("accessories", modal.toAccessoriesCode());
            fields.put("feed", Double.toString(modal.feedRate));
        }
        recovery.report(fields);
    }

    private static void savePosition(java.util.Map<String, String> fields, String key,
                                     com.willwinder.universalgcodesender.model.Position position) {
        if (position == null) return;
        fields.put(key, String.format(java.util.Locale.ROOT,
                "X %.6f  Y %.6f  Z %.6f  A %.6f  B %.6f  C %.6f  (%s)",
                position.x, position.y, position.z, position.a, position.b, position.c, position.getUnits()));
    }

    private void dispatchActiveJobChanged(File file) {
        SwingUtilities.invokeLater(() -> {
            for (JobManagerListener l : listeners) {
                l.onActiveJobChanged(file);
            }
        });
    }
}
