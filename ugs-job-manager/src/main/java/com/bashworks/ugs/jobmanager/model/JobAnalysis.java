package com.bashworks.ugs.jobmanager.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Detailed analysis metrics extracted from a G-code program.
 */
public final class JobAnalysis {

    private final long totalLines;
    private final long motionLines;
    private final JobBounds bounds;
    private final List<Integer> toolsUsed;
    private final double maxFeedRate;
    private final List<Integer> spindleSpeeds;
    private final String units;
    private final boolean hasSpindleControl;
    private final boolean hasCoolant;
    private final double rapidDistance;
    private final double feedDistance;
    private final List<String> previewLines;

    public JobAnalysis(long totalLines,
                       long motionLines,
                       JobBounds bounds,
                       List<Integer> toolsUsed,
                       double maxFeedRate,
                       List<Integer> spindleSpeeds,
                       String units,
                       boolean hasSpindleControl,
                       boolean hasCoolant,
                       double rapidDistance,
                       double feedDistance,
                       List<String> previewLines) {
        this.totalLines = totalLines;
        this.motionLines = motionLines;
        this.bounds = bounds != null ? bounds : new JobBounds();
        this.toolsUsed = toolsUsed != null ? Collections.unmodifiableList(new ArrayList<>(toolsUsed)) : Collections.emptyList();
        this.maxFeedRate = maxFeedRate;
        this.spindleSpeeds = spindleSpeeds != null ? Collections.unmodifiableList(new ArrayList<>(spindleSpeeds)) : Collections.emptyList();
        this.units = units != null ? units : "mm";
        this.hasSpindleControl = hasSpindleControl;
        this.hasCoolant = hasCoolant;
        this.rapidDistance = rapidDistance;
        this.feedDistance = feedDistance;
        this.previewLines = previewLines != null ? Collections.unmodifiableList(new ArrayList<>(previewLines)) : Collections.emptyList();
    }

    public static JobAnalysis empty() {
        return new JobAnalysis(0, 0, new JobBounds(), Collections.emptyList(), 0.0,
                Collections.emptyList(), "mm", false, false, 0.0, 0.0, Collections.emptyList());
    }

    public long getTotalLines() { return totalLines; }
    public long getMotionLines() { return motionLines; }
    public JobBounds getBounds() { return bounds; }
    public List<Integer> getToolsUsed() { return toolsUsed; }
    public double getMaxFeedRate() { return maxFeedRate; }
    public List<Integer> getSpindleSpeeds() { return spindleSpeeds; }
    public String getUnits() { return units; }
    public boolean hasSpindleControl() { return hasSpindleControl; }
    public boolean hasCoolant() { return hasCoolant; }
    public double getRapidDistance() { return rapidDistance; }
    public double getFeedDistance() { return feedDistance; }
    public List<String> getPreviewLines() { return previewLines; }

    public String getToolsSummary() {
        if (toolsUsed.isEmpty()) {
            return "Default (None)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < toolsUsed.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("T").append(toolsUsed.get(i));
        }
        return sb.toString();
    }

    public String getSpindleSummary() {
        if (!hasSpindleControl || spindleSpeeds.isEmpty()) {
            return "None";
        }
        int min = Collections.min(spindleSpeeds);
        int max = Collections.max(spindleSpeeds);
        if (min == max) {
            return max + " RPM";
        }
        return min + " - " + max + " RPM";
    }

    public String getFeedRateSummary() {
        if (maxFeedRate <= 0.0) {
            return "N/A";
        }
        return String.format(Locale.US, "%.0f %s/min", maxFeedRate, units);
    }

    public String getTotalDistanceSummary() {
        double total = rapidDistance + feedDistance;
        if (total <= 0.0) {
            return "N/A";
        }
        return String.format(Locale.US, "%.1f %s (Cut: %.1f, Rapid: %.1f)", total, units, feedDistance, rapidDistance);
    }
}
