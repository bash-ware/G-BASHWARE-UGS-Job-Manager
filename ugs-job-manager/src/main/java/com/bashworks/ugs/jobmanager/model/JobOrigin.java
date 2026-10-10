package com.bashworks.ugs.jobmanager.model;

/**
 * Origin source of a CNC job.
 */
public enum JobOrigin {
    LOCAL_DISK("Disk", "Local file system"),
    WORKSPACE("Workspace", "UGS default workspace folder"),
    WEB_URL("Web URL", "Downloaded from remote HTTP/HTTPS address"),
    REMOTE_SERVER("Remote Server", "Fetched from remote CNC/OctoPrint server");

    private final String label;
    private final String description;

    JobOrigin(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
