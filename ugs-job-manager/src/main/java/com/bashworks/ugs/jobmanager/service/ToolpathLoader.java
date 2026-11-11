package com.bashworks.ugs.jobmanager.service;

import com.willwinder.universalgcodesender.gcode.GcodeParser;
import com.willwinder.universalgcodesender.gcode.GcodePreprocessorUtils;
import com.willwinder.universalgcodesender.gcode.util.PlaneFormatter;
import com.willwinder.universalgcodesender.gcode.util.Code;
import com.willwinder.universalgcodesender.gcode.GcodeState;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.model.UnitUtils.Units;
import com.willwinder.universalgcodesender.types.PointSegment;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Reads geometry using UGS's own modal parser; never sends commands to a controller. */
public final class ToolpathLoader {
    private static final int MAX_SEGMENTS = 1_000_000;
    public static final class Segment {
        public final double x1,y1,z1,x2,y2,z2;
        public final boolean rapid;
        public Segment(Position a, Position b, boolean rapid) {
            x1=a.x; y1=a.y; z1=a.z; x2=b.x; y2=b.y; z2=b.z; this.rapid=rapid;
        }
    }
    public static final class Toolpath {
        public final List<Segment> segments;
        public final String note;
        public Toolpath(List<Segment> segments, String note) {
            this.segments=Collections.unmodifiableList(new ArrayList<>(segments)); this.note=note;
        }
    }

    public Toolpath load(File file) throws IOException {
        GcodeParser parser = new GcodeParser();
        parser.getCurrentState().currentPoint = new Position(0,0,0, Units.MM);
        Position previous = new Position(0,0,0, Units.MM);
        List<Segment> segments = new ArrayList<>();
        int lineNumber=0;
        try (BufferedReader reader=Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line=reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Preview cancelled");
                lineNumber++;
                try {
                    for (GcodeParser.GcodeMeta meta : parser.addCommand(explicitCircleEndpoint(line, parser.getCurrentState()), lineNumber)) {
                        PointSegment point=meta.point;
                        if (point == null) continue;
                        point.convertToMetric();
                        Position end=point.point();
                        if (!finite(end)) return new Toolpath(segments, "Preview stops at line " + lineNumber + ": unknown coordinates");
                        if (point.isArc()) {
                            double length=Math.max(0.25, 2 * Math.PI * Math.abs(point.getRadius()) / 4096.0);
                            List<Position> arc=GcodePreprocessorUtils.generatePointsAlongArcBDring(
                                    previous, end, point.center(), point.isClockwise(), point.getRadius(), 0, length,
                                    new PlaneFormatter(point.getPlaneState()));
                            if (arc.isEmpty()) throw new IllegalArgumentException("Arc could not be expanded");
                            for (Position next : arc) {
                                append(segments, previous, next, false);
                                previous=next;
                                if (segments.size() >= MAX_SEGMENTS) return new Toolpath(segments, "Partial preview: 1,000,000 segment limit");
                            }
                        } else {
                            append(segments, previous, end, point.isFastTraverse());
                        }
                        previous=new Position(end);
                        if (segments.size() >= MAX_SEGMENTS) return new Toolpath(segments, "Partial preview: 1,000,000 segment limit");
                    }
                } catch (Exception ex) {
                    return new Toolpath(segments, "Preview stops at line " + lineNumber + ": " + ex.getMessage());
                }
            }
        }
        return new Toolpath(segments, "");
    }
    // UGS 2.1.6 ignores IJK-only circles without XYZ words. Make the unchanged
    // endpoint explicit, then use UGS for all modal and arc geometry handling.
    private static String explicitCircleEndpoint(String line, GcodeState state) {
        List<String> words=GcodePreprocessorUtils.splitCommand(line);
        Set<Code> codes=GcodePreprocessorUtils.getGCodes(words);
        boolean arc=codes.contains(Code.G2) || codes.contains(Code.G3) ||
                (codes.stream().noneMatch(Code::consumesMotion) && (state.currentMotionMode==Code.G2 || state.currentMotionMode==Code.G3));
        boolean center=Double.isFinite(GcodePreprocessorUtils.parseCoord(words,'I')) || Double.isFinite(GcodePreprocessorUtils.parseCoord(words,'J')) || Double.isFinite(GcodePreprocessorUtils.parseCoord(words,'K'));
        boolean axes=Double.isFinite(GcodePreprocessorUtils.parseCoord(words,'X')) || Double.isFinite(GcodePreprocessorUtils.parseCoord(words,'Y')) || Double.isFinite(GcodePreprocessorUtils.parseCoord(words,'Z'));
        if(!arc || !center || axes) return line;
        boolean absolute=codes.contains(Code.G90) || (!codes.contains(Code.G91) && state.inAbsoluteMode);
        Units units=codes.contains(Code.G20)?Units.INCH:codes.contains(Code.G21)?Units.MM:state.getUnits();
        Position end=state.currentPoint.getPositionIn(units);
        return String.join(" ",words) + String.format(Locale.US," X%.9f Y%.9f Z%.9f",absolute?end.x:0,absolute?end.y:0,absolute?end.z:0);
    }
    private static boolean finite(Position p) { return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static void append(List<Segment> segments, Position a, Position b, boolean rapid) {
        if (!finite(a) || !finite(b)) throw new IllegalArgumentException("Invalid motion coordinates");
        if (a.x!=b.x || a.y!=b.y || a.z!=b.z) segments.add(new Segment(a,b,rapid));
    }
}
