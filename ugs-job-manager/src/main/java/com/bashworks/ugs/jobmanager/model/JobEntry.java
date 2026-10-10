package com.bashworks.ugs.jobmanager.model;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * Encapsulates a G-code job entry from disk, workspace, or remote web source.
 */
public final class JobEntry {

    private final String id;
    private final String name;
    private final File file;
    private final String sourceLocation;
    private final JobOrigin origin;
    private final long fileSizeBytes;
    private final long lastModifiedMs;
    private JobAnalysis analysis;
    private boolean activeInUgs;

    public JobEntry(String id,
                    String name,
                    File file,
                    String sourceLocation,
                    JobOrigin origin,
                    long fileSizeBytes,
                    long lastModifiedMs) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.file = Objects.requireNonNull(file, "file");
        this.sourceLocation = sourceLocation != null ? sourceLocation : file.getAbsolutePath();
        this.origin = origin != null ? origin : JobOrigin.LOCAL_DISK;
        this.fileSizeBytes = fileSizeBytes;
        this.lastModifiedMs = lastModifiedMs;
        this.analysis = null;
        this.activeInUgs = false;
    }

    public static JobEntry fromFile(File file, JobOrigin origin) {
        Objects.requireNonNull(file, "file");
        return new JobEntry(
                file.getAbsolutePath(),
                file.getName(),
                file,
                file.getAbsolutePath(),
                origin != null ? origin : JobOrigin.LOCAL_DISK,
                file.length(),
                file.lastModified()
        );
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public File getFile() { return file; }
    public String getSourceLocation() { return sourceLocation; }
    public JobOrigin getOrigin() { return origin; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public long getLastModifiedMs() { return lastModifiedMs; }

    public JobAnalysis getAnalysis() { return analysis; }
    public void setAnalysis(JobAnalysis analysis) { this.analysis = analysis; }

    public boolean isActiveInUgs() { return activeInUgs; }
    public void setActiveInUgs(boolean activeInUgs) { this.activeInUgs = activeInUgs; }

    public String getFormattedSize() {
        if (fileSizeBytes < 1024) {
            return fileSizeBytes + " B";
        } else if (fileSizeBytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", fileSizeBytes / 1024.0);
        } else {
            return String.format(Locale.US, "%.2f MB", fileSizeBytes / (1024.0 * 1024.0));
        }
    }

    public String getFormattedDate() {
        if (lastModifiedMs <= 0) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        return sdf.format(new Date(lastModifiedMs));
    }

    public String getDimensionsSummary() {
        if (analysis == null || !analysis.getBounds().isValid()) {
            return "—";
        }
        return analysis.getBounds().toDimensionsString(analysis.getUnits());
    }

    public String getLinesSummary() {
        if (analysis == null || analysis.getTotalLines() <= 0) {
            return "—";
        }
        return String.format(Locale.US, "%,d", analysis.getTotalLines());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobEntry jobEntry = (JobEntry) o;
        return Objects.equals(id, jobEntry.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name + " (" + getFormattedSize() + ")";
    }
}
