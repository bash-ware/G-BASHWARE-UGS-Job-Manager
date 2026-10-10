package com.bashworks.ugs.jobmanager.machine;

import java.io.File;

/**
 * Interface abstracting UGS BackendAPI operations for job loading, activation, and state query.
 */
public interface JobManagerGateway {

    boolean isConnected();

    boolean isIdle();

    boolean isSendingFile();

    boolean isPaused();

    String getControllerStateName();

    File getActiveGcodeFile();

    /**
     * Loads the given G-code file into UGS, updating the visualizer, parsing commands,
     * and preparing the controller for execution.
     *
     * @param file the G-code file on disk
     * @throws Exception if loading fails or machine is currently streaming
     */
    void loadJobIntoUgs(File file) throws Exception;

    /**
     * Reloads the currently loaded G-code file in UGS.
     */
    void reloadActiveJob() throws Exception;

    /**
     * Unloads the current G-code file from UGS.
     */
    void unsetActiveJob() throws Exception;

    /**
     * Begins streaming the active file to the CNC controller.
     */
    void startJobInUgs() throws Exception;

    void addListener(JobManagerListener listener);

    void removeListener(JobManagerListener listener);
}
