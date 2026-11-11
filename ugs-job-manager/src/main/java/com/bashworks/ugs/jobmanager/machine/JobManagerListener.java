package com.bashworks.ugs.jobmanager.machine;

import java.io.File;

/**
 * Listener interface for UGS machine state changes and job activation events.
 */
public interface JobManagerListener {

    default void onJobStarted(File file) {}

    default void onJobFinished(File file) {}

    default void onJobInterrupted(File file) {}

    void onActiveJobChanged(File activeFile);

    void onMachineConnectionChanged(boolean connected);

    void onMachineStateChanged(String stateName, boolean isIdle, boolean isSending);

    void onStreamStateChanged(boolean isStreaming, boolean isPaused);
}
