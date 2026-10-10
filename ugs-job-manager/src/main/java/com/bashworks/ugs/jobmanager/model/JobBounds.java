package com.bashworks.ugs.jobmanager.model;

import java.util.Locale;

/**
 * 3D bounding box for G-code motion.
 */
public final class JobBounds {

    private final double minX;
    private final double maxX;
    private final double minY;
    private final double maxY;
    private final double minZ;
    private final double maxZ;
    private final boolean valid;

    public JobBounds() {
        this(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
             Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
             Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
             false);
    }

    public JobBounds(double minX, double maxX,
                     double minY, double maxY,
                     double minZ, double maxZ,
                     boolean valid) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.valid = valid;
    }

    public double getMinX() { return minX; }
    public double getMaxX() { return maxX; }
    public double getMinY() { return minY; }
    public double getMaxY() { return maxY; }
    public double getMinZ() { return minZ; }
    public double getMaxZ() { return maxZ; }
    public boolean isValid() { return valid; }

    public double getSpanX() {
        return valid ? Math.max(0.0, maxX - minX) : 0.0;
    }

    public double getSpanY() {
        return valid ? Math.max(0.0, maxY - minY) : 0.0;
    }

    public double getSpanZ() {
        return valid ? Math.max(0.0, maxZ - minZ) : 0.0;
    }

    /**
     * Formats bounding dimensions as: "120.5 x 80.0 x 15.2 mm"
     */
    public String toDimensionsString(String unit) {
        if (!valid) {
            return "N/A";
        }
        return String.format(Locale.US, "%.1f × %.1f × %.1f %s",
                getSpanX(), getSpanY(), getSpanZ(), unit != null ? unit : "mm");
    }

    public String toDetailedRangeString(String unit) {
        if (!valid) {
            return "No coordinate motion detected";
        }
        String u = unit != null ? unit : "mm";
        return String.format(Locale.US,
                "X: [%.2f to %.2f] Δ%.2f %s | Y: [%.2f to %.2f] Δ%.2f %s | Z: [%.2f to %.2f] Δ%.2f %s",
                minX, maxX, getSpanX(), u,
                minY, maxY, getSpanY(), u,
                minZ, maxZ, getSpanZ(), u);
    }

    @Override
    public String toString() {
        return toDimensionsString("mm");
    }
}
