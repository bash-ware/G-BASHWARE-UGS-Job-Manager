package com.bashworks.ugs.jobmanager.model;

import java.util.List;

/**
 * Abstraction for loading or discovering CNC jobs from disk, workspace, web, or remote server.
 */
public interface JobSource {

    @FunctionalInterface
    interface ProgressCallback {
        void onProgress(String statusMessage, int percent);
    }

    String getSourceName();

    JobOrigin getOrigin();

    List<JobEntry> listJobs();

    JobEntry fetchJob(String identifier, ProgressCallback callback) throws Exception;
}
