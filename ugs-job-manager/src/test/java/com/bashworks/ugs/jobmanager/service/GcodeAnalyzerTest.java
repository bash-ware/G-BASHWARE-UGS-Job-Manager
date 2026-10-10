package com.bashworks.ugs.jobmanager.service;

import com.bashworks.ugs.jobmanager.model.JobAnalysis;
import com.bashworks.ugs.jobmanager.model.JobBounds;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Self-contained unit tests for GcodeAnalyzer without requiring external runner.
 */
public class GcodeAnalyzerTest {

    public static void main(String[] args) throws Exception {
        testBasicParsing();
        testCommentsAndTools();
        System.out.println("ALL GcodeAnalyzer TESTS PASSED!");
    }

    public static void testBasicParsing() throws IOException {
        File temp = File.createTempFile("test_sample", ".gcode");
        temp.deleteOnExit();

        try (FileWriter fw = new FileWriter(temp)) {
            fw.write("(Header Comment)\n");
            fw.write("G21 ; Millimeters\n");
            fw.write("G90 ; Absolute\n");
            fw.write("G0 Z5.000\n");
            fw.write("G0 X10.000 Y20.000\n");
            fw.write("G1 Z-2.000 F600\n");
            fw.write("G1 X110.000 Y70.000 F1800\n");
            fw.write("G0 Z10.000\n");
            fw.write("M30\n");
        }

        GcodeAnalyzer analyzer = new GcodeAnalyzer();
        JobAnalysis analysis = analyzer.analyze(temp);

        JobBounds b = analysis.getBounds();
        assertTrue(b.isValid(), "Bounds should be valid");
        assertEquals(10.0, b.getMinX(), 0.001, "Min X");
        assertEquals(110.0, b.getMaxX(), 0.001, "Max X");
        assertEquals(100.0, b.getSpanX(), 0.001, "Span X");

        assertEquals(20.0, b.getMinY(), 0.001, "Min Y");
        assertEquals(70.0, b.getMaxY(), 0.001, "Max Y");
        assertEquals(50.0, b.getSpanY(), 0.001, "Span Y");

        assertEquals(-2.0, b.getMinZ(), 0.001, "Min Z");
        assertEquals(10.0, b.getMaxZ(), 0.001, "Max Z");
        assertEquals(12.0, b.getSpanZ(), 0.001, "Span Z");

        assertEquals(1800.0, analysis.getMaxFeedRate(), 0.001, "Max feed rate");
        assertTrue("mm".equals(analysis.getUnits()), "Units should be mm");
        assertTrue(analysis.getTotalLines() == 9, "Total lines count");
    }

    public static void testCommentsAndTools() throws IOException {
        File temp = File.createTempFile("test_tools", ".nc");
        temp.deleteOnExit();

        try (FileWriter fw = new FileWriter(temp)) {
            fw.write("T1 M6 (Tool 1 Endmill)\n");
            fw.write("S12000 M3\n");
            fw.write("G0 X0 Y0 Z5\n");
            fw.write("G1 Z-1 F500\n");
            fw.write("T2 M6\n");
            fw.write("S8000 M3\n");
            fw.write("G1 X50 Y50\n");
            fw.write("M5\n");
        }

        GcodeAnalyzer analyzer = new GcodeAnalyzer();
        JobAnalysis analysis = analyzer.analyze(temp);

        assertTrue(analysis.getToolsUsed().contains(1), "Contains tool 1");
        assertTrue(analysis.getToolsUsed().contains(2), "Contains tool 2");
        assertTrue(analysis.hasSpindleControl(), "Has spindle control");
        assertTrue(analysis.getSpindleSpeeds().contains(12000), "Contains 12000 RPM");
        assertTrue(analysis.getSpindleSpeeds().contains(8000), "Contains 8000 RPM");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Test assertion failed: " + message);
        }
    }

    private static void assertEquals(double expected, double actual, double delta, String message) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError(String.format("Expected %f but got %f: %s", expected, actual, message));
        }
    }
}
