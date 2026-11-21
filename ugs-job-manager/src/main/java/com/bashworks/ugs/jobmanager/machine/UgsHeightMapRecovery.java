package com.bashworks.ugs.jobmanager.machine;

import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.model.UnitUtils.Units;
import com.willwinder.universalgcodesender.model.UnitUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Optional adapter for UGS versions that have an AutoLeveler window. */
public final class UgsHeightMapRecovery {
    private static final Logger LOG = Logger.getLogger(UgsHeightMapRecovery.class.getName());
    private static final int MAX_POINTS = 100_000;
    private final BackendAPI backend;
    private final Supplier<Object> findWindow;
    private boolean warned;

    public UgsHeightMapRecovery(BackendAPI backend, Supplier<Object> findWindow) {
        this.backend = backend;
        this.findWindow = findWindow;
    }

    public String capture() {
        try {
            Object scanner = scanner();
            if (scanner == null) return null;
            Position[][] grid = grid(scanner);
            if (grid.length == 0 || grid[0].length == 0 || (long) grid.length * grid[0].length > MAX_POINTS) return null;
            Position min = (Position) field(scanner, "minXYZ");
            Position max = (Position) field(scanner, "maxXYZ");
            var settings = backend.getSettings().getAutoLevelSettings();
            Position offset = settings.getAutoLevelProbeOffset().getPositionIn(min.getUnits());
            StringBuilder result = new StringBuilder();
            result.append(grid.length).append(',').append(grid[0].length).append(',').append(min.getUnits().name())
                    .append(',').append(settings.getStepResolution()).append('\n');
            point(result, min);
            point(result, max);
            point(result, offset);
            boolean hasData = false;
            for (Position[] column : grid) {
                if (column.length != grid[0].length) return null;
                for (Position p : column) {
                    if (p == null || p.getUnits() != min.getUnits()) return null;
                    if (Double.isFinite(p.z)) hasData = true;
                    point(result, p);
                }
            }
            return hasData ? result.toString() : "";
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logUnavailable(ex);
            return null;
        }
    }

    public boolean restoreXyz(File file, Units units) {
        if (file == null || (units != Units.MM && units != Units.INCH)
                || !file.getName().toLowerCase(Locale.ROOT).endsWith(".xyz")
                || units != backend.getSettings().getPreferredUnits()) return false;
        try {
            var settings = backend.getSettings().getAutoLevelSettings();
            var offset = settings.getAutoLevelProbeOffset().getPositionIn(units);
            var lower = new Position(0, 0, settings.getMinZ(), Units.MM).getPositionIn(units);
            var upper = new Position(0, 0, settings.getMaxZ(), Units.MM).getPositionIn(units);
            return restore(encodeXyz(file, units, lower.z, upper.z, offset));
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.FINE, "Could not read the saved XYZ height map", ex);
            return false;
        }
    }

    private static String encodeXyz(File file, Units units, double minZ, double maxZ, Position offset) throws IOException {
        // UGS XYZ exports are space-separated points in the original UGS display units.
        if (Files.size(file.toPath()) > 16 * 1024 * 1024) throw new IOException("XYZ height map is too large");
        var columns = new TreeMap<Double, TreeMap<Double, Double>>();
        var ys = new TreeSet<Double>();
        try (var reader = Files.newBufferedReader(file.toPath())) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] values = line.strip().split("\\s+");
                if (values.length != 3 || ++count > MAX_POINTS) throw new IOException("Invalid XYZ height map");
                double x = Double.parseDouble(values[0]);
                double y = Double.parseDouble(values[1]);
                double z = Double.parseDouble(values[2]);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                    throw new IOException("XYZ height map contains non-finite values");
                if (x == 0) x = 0; // Canonicalize signed zero.
                if (y == 0) y = 0;
                if (columns.computeIfAbsent(x, key -> new TreeMap<>()).putIfAbsent(y, z) != null)
                    throw new IOException("XYZ height map contains duplicate points");
                ys.add(y);
            }
            if (columns.size() < 2 || ys.size() < 2 || (long) columns.size() * ys.size() != count)
                throw new IOException("XYZ height map must contain a complete rectangular grid");
        }

        double step = Math.max(largestGap(columns.navigableKeySet()), largestGap(ys));
        // Round slightly upward to avoid a phantom edge row from floating point division.
        double stepMm = Math.nextUp(step * UnitUtils.scaleUnits(units, Units.MM));
        double unitStep = stepMm * UnitUtils.scaleUnits(Units.MM, units);
        double minX = columns.firstKey() - offset.x, maxX = columns.lastKey() - offset.x;
        double minY = ys.first() - offset.y, maxY = ys.last() - offset.y;
        if (!Double.isFinite(unitStep) || unitStep <= 0) throw new IOException("Invalid XYZ grid step");

        StringBuilder data = new StringBuilder();
        data.append(columns.size()).append(',').append(ys.size()).append(',').append(units.name())
                .append(',').append(stepMm).append('\n');
        point(data, new Position(minX, minY, minZ, units));
        point(data, new Position(maxX, maxY, maxZ, units));
        point(data, offset);
        for (var column : columns.entrySet()) {
            for (double y : ys) {
                Double z = column.getValue().get(y);
                if (z == null) throw new IOException("XYZ height map has a missing grid point");
                point(data, new Position(column.getKey(), y, z, units));
            }
        }
        return data.toString();
    }

    private static double largestGap(NavigableSet<Double> axis) {
        double previous = axis.first(), step = 0;
        for (double current : axis) {
            step = Math.max(step, current - previous);
            previous = current;
        }
        return step;
    }

    public boolean restore(String encoded) {
        try {
            if (backend.isSendingFile() || (backend.isConnected() && !backend.isIdle())) return false;
            Object scanner = scanner();
            if (scanner == null) return false;
            Position[][] existing = grid(scanner);
            for (Position[] column : existing) for (Position position : column)
                if (position != null && Double.isFinite(position.z)) return false;
            String[] lines = encoded.split("\\R");
            String[] header = lines[0].split(",");
            int width = Integer.parseInt(header[0]), height = Integer.parseInt(header[1]);
            long count = (long) width * height;
            if (width < 2 || height < 2 || count > MAX_POINTS || lines.length != count + 4) return false;
            Units units = Units.valueOf(header[2]);
            if (units == Units.UNKNOWN || units != backend.getSettings().getPreferredUnits()) return false;
            var settings = backend.getSettings().getAutoLevelSettings();
            double step = Double.parseDouble(header[3]);
            double unitStep = step * com.willwinder.universalgcodesender.model.UnitUtils.scaleUnits(Units.MM, units);
            if (!Double.isFinite(unitStep) || unitStep <= 0) return false;
            Position min = parse(lines[1], units), max = parse(lines[2], units), offset = parse(lines[3], units);
            if (!samePoint(offset, settings.getAutoLevelProbeOffset().getPositionIn(units))
                    || !Double.isFinite(min.z) || !Double.isFinite(max.z) || min.z >= max.z
                    || Math.ceil((max.x - min.x) / unitStep) + 1 != width
                    || Math.ceil((max.y - min.y) / unitStep) + 1 != height) return false;

            // Validate the full grid before touching the scanner. Scan order alternates Y direction.
            List<Position> replay = new ArrayList<>();
            boolean unprobed = false;
            for (int x = 0; x < width; x++) {
                for (int index = 0; index < height; index++) {
                    int y = x % 2 == 0 ? index : height - 1 - index;
                    Position p = parse(lines[4 + x * height + y], units);
                    if (Double.isNaN(p.z)) { unprobed = true; continue; }
                    double gridX = Math.min(max.x, min.x + x * unitStep);
                    double gridY = Math.min(max.y, min.y + y * unitStep);
                    if (!Double.isFinite(p.z) || unprobed || !near(p.x, gridX + offset.x)
                            || !near(p.y, gridY + offset.y)) return false;
                    replay.add(new Position(gridX, gridY, p.z - offset.z, units));
                }
            }
            if (replay.isEmpty()) return false;
            var update = scanner.getClass().getMethod("update", Position.class, Position.class);
            var importPoint = scanner.getClass().getMethod("probeEvent", Position.class);
            // Keep the map available for inspection. The operator can re-enable compensation after checking the setup.
            settings.setApplyToGcode(false);
            settings.setStepResolution(step);
            settings.setMin(min);
            settings.setMax(max);
            update.invoke(scanner, min, max);
            for (Position p : replay) importPoint.invoke(scanner, p);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logUnavailable(ex);
            return false;
        }
    }

    public static String exportXyz(String encoded) {
        String[] lines = encoded.split("\\R");
        StringBuilder xyz = new StringBuilder();
        for (int i = 4; i < lines.length; i++) {
            if (!lines[i].endsWith(",NaN")) xyz.append(lines[i].replace(',', ' ')).append('\n');
        }
        return xyz.toString();
    }

    public static String units(String encoded) {
        return encoded.lines().findFirst().orElseThrow().split(",")[2];
    }

    private Object scanner() throws ReflectiveOperationException {
        Object window = findWindow.get();
        if (window == null) return null;
        return field(window, "scanner");
    }

    private static Position[][] grid(Object scanner) throws ReflectiveOperationException {
        return (Position[][]) scanner.getClass().getMethod("getProbePositionGrid").invoke(scanner);
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void point(StringBuilder data, Position position) {
        data.append(position.x).append(',').append(position.y).append(',').append(position.z).append('\n');
    }

    private static Position parse(String line, Units units) {
        String[] axes = line.split(",");
        return new Position(Double.parseDouble(axes[0]), Double.parseDouble(axes[1]), Double.parseDouble(axes[2]), units);
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) <= 0.00001; }
    private static boolean samePoint(Position a, Position b) {
        return near(a.x, b.x) && near(a.y, b.y) && near(a.z, b.z);
    }

    private void logUnavailable(Exception ex) {
        if (!warned) LOG.log(Level.FINE, "The UGS AutoLeveler recovery adapter is unavailable", ex);
        warned = true;
    }
}
