package com.bashworks.ugs.jobmanager.service;

import com.bashworks.ugs.jobmanager.model.JobAnalysis;
import com.bashworks.ugs.jobmanager.model.JobBounds;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast, memory-efficient streaming analyzer for CNC G-code files.
 * Extracts motion bounds, tool changes, feed rates, spindle speeds, and line previews.
 */
public final class GcodeAnalyzer {

    private static final Pattern WORD_PATTERN = Pattern.compile("([A-Za-z])\\s*([-+]?[0-9]*\\.?[0-9]+)");
    private static final int MAX_PREVIEW_LINES = 50;

    public JobAnalysis analyze(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return JobAnalysis.empty();
        }

        long totalLines = 0;
        long motionLines = 0;

        double curX = 0.0;
        double curY = 0.0;
        double curZ = 0.0;

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        boolean hasCoords = false;

        boolean absoluteMode = true; // G90 default
        String units = "mm";         // G21 default
        int motionMode = -1;         // 0=G0, 1=G1, 2=G2, 3=G3

        double maxFeed = 0.0;
        double rapidDist = 0.0;
        double feedDist = 0.0;

        Set<Integer> tools = new HashSet<>();
        Set<Integer> spindleSpeeds = new HashSet<>();
        boolean hasSpindle = false;
        boolean hasCoolant = false;

        List<String> previewLines = new ArrayList<>(MAX_PREVIEW_LINES);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalLines++;
                if (previewLines.size() < MAX_PREVIEW_LINES) {
                    previewLines.add(line);
                }

                String clean = stripComments(line).trim();
                if (clean.isEmpty()) {
                    continue;
                }

                Matcher matcher = WORD_PATTERN.matcher(clean);
                Double newX = null;
                Double newY = null;
                Double newZ = null;
                Integer gCode = null;

                while (matcher.find()) {
                    char letter = Character.toUpperCase(matcher.group(1).charAt(0));
                    double value = Double.parseDouble(matcher.group(2));

                    switch (letter) {
                        case 'G':
                            int gInt = (int) Math.round(value * 10);
                            if (gInt == 0 || gInt == 10 || gInt == 20 || gInt == 30) {
                                motionMode = gInt / 10;
                            } else if (gInt == 900) {
                                absoluteMode = true;
                            } else if (gInt == 910) {
                                absoluteMode = false;
                            } else if (gInt == 200) {
                                units = "inch";
                            } else if (gInt == 210) {
                                units = "mm";
                            }
                            break;
                        case 'M':
                            int mVal = (int) Math.round(value);
                            if (mVal == 3 || mVal == 4 || mVal == 5) {
                                hasSpindle = true;
                            } else if (mVal == 7 || mVal == 8 || mVal == 9) {
                                hasCoolant = true;
                            }
                            break;
                        case 'T':
                            tools.add((int) Math.round(value));
                            break;
                        case 'S':
                            int sVal = (int) Math.round(value);
                            if (sVal > 0) {
                                spindleSpeeds.add(sVal);
                                hasSpindle = true;
                            }
                            break;
                        case 'F':
                            if (value > maxFeed) {
                                maxFeed = value;
                            }
                            break;
                        case 'X':
                            newX = value;
                            break;
                        case 'Y':
                            newY = value;
                            break;
                        case 'Z':
                            newZ = value;
                            break;
                        default:
                            break;
                    }
                }

                if (newX != null || newY != null || newZ != null) {
                    motionLines++;

                    double targetX = (newX != null) ? (absoluteMode ? newX : curX + newX) : curX;
                    double targetY = (newY != null) ? (absoluteMode ? newY : curY + newY) : curY;
                    double targetZ = (newZ != null) ? (absoluteMode ? newZ : curZ + newZ) : curZ;

                    if (newX != null) {
                        if (targetX < minX) minX = targetX;
                        if (targetX > maxX) maxX = targetX;
                    }
                    if (newY != null) {
                        if (targetY < minY) minY = targetY;
                        if (targetY > maxY) maxY = targetY;
                    }
                    if (newZ != null) {
                        if (targetZ < minZ) minZ = targetZ;
                        if (targetZ > maxZ) maxZ = targetZ;
                    }
                    hasCoords = true;

                    double dx = targetX - curX;
                    double dy = targetY - curY;
                    double dz = targetZ - curZ;
                    double segmentLength = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (motionMode == 0) {
                        rapidDist += segmentLength;
                    } else {
                        feedDist += segmentLength;
                    }

                    curX = targetX;
                    curY = targetY;
                    curZ = targetZ;
                }
            }
        } catch (Exception ignored) {
            // Best effort; partial data returned if file read interrupted
        }

        JobBounds bounds = hasCoords
                ? new JobBounds(minX, maxX, minY, maxY, minZ, maxZ, true)
                : new JobBounds();

        List<Integer> sortedTools = new ArrayList<>(tools);
        sortedTools.sort(Integer::compareTo);

        List<Integer> sortedSpindle = new ArrayList<>(spindleSpeeds);
        sortedSpindle.sort(Integer::compareTo);

        return new JobAnalysis(
                totalLines,
                motionLines,
                bounds,
                sortedTools,
                maxFeed,
                sortedSpindle,
                units,
                hasSpindle,
                hasCoolant,
                rapidDist,
                feedDist,
                previewLines
        );
    }

    private static String stripComments(String line) {
        if (line == null) return "";
        int semiIdx = line.indexOf(';');
        if (semiIdx >= 0) {
            line = line.substring(0, semiIdx);
        }
        int parenStart = line.indexOf('(');
        while (parenStart >= 0) {
            int parenEnd = line.indexOf(')', parenStart);
            if (parenEnd > parenStart) {
                line = line.substring(0, parenStart) + line.substring(parenEnd + 1);
            } else {
                line = line.substring(0, parenStart);
                break;
            }
            parenStart = line.indexOf('(');
        }
        return line;
    }
}
