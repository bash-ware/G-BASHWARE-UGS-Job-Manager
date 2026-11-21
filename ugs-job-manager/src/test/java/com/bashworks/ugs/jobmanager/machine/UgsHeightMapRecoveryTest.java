package com.bashworks.ugs.jobmanager.machine;

import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.model.UnitUtils.Units;
import com.willwinder.universalgcodesender.utils.Settings;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class UgsHeightMapRecoveryTest {
    @Test public void restoresTheSavedGridInProbeOrderWithoutMachineCommands() {
        Settings settings = new Settings();
        settings.setPreferredUnits(Units.MM);
        settings.getAutoLevelSettings().setStepResolution(10);
        settings.getAutoLevelSettings().setAutoLevelProbeOffset(new Position(0, 0, 0.25, Units.MM));
        settings.getAutoLevelSettings().setApplyToGcode(true);
        BackendAPI backend = backend(settings);
        var source = new Window();
        source.scanner.grid[0][0].z = 0.1;
        source.scanner.grid[0][1].z = 0.2;
        source.scanner.grid[1][1].z = 0.3;
        source.scanner.grid[1][0].z = 0.4;
        String encoded = new UgsHeightMapRecovery(backend, () -> source).capture();
        assertNotNull(encoded);
        assertTrue(UgsHeightMapRecovery.exportXyz(encoded).contains("10.0 10.0 0.3"));
        var recovered = new Window();
        var adapter = new UgsHeightMapRecovery(backend, () -> recovered);
        assertTrue(adapter.restore(encoded));
        assertEquals(List.of(0.1, 0.2, 0.3, 0.4), recovered.scanner.restoredHeights);
        assertFalse(settings.getAutoLevelSettings().getApplyToGcode());
    }

    @Test public void importsExportedXyzWithSavedUnitsAndPreservesHeights() throws Exception {
        Settings settings = new Settings();
        settings.setPreferredUnits(Units.MM);
        var scannerSettings = settings.getAutoLevelSettings();
        scannerSettings.setMin(new Position(0, 0, -2, Units.MM));
        scannerSettings.setMax(new Position(10, 10, 2, Units.MM));
        scannerSettings.setStepResolution(1);
        scannerSettings.setAutoLevelProbeOffset(new Position(0, 0, 0.25, Units.MM));
        scannerSettings.setApplyToGcode(true);
        var recovered = new Window();
        var adapter = new UgsHeightMapRecovery(backend(settings), () -> recovered);
        var xyz = java.nio.file.Files.createTempFile("ugs-recovered-height-map-", ".xyz");
        try {
            java.nio.file.Files.writeString(xyz, "10 10 0.3\n0 0 0.1\n10 0 0.4\n0 10 0.2\n");
            assertFalse("A unit mismatch must leave the scanner untouched", adapter.restoreXyz(xyz.toFile(), Units.INCH));
            assertTrue(recovered.scanner.restoredHeights.isEmpty());
            assertTrue(scannerSettings.getApplyToGcode());
            assertTrue(adapter.restoreXyz(xyz.toFile(), Units.MM));
            assertEquals(List.of(0.1, 0.2, 0.3, 0.4), recovered.scanner.restoredHeights);
            assertEquals(10, scannerSettings.getStepResolution(), 0.00001);
            assertFalse(scannerSettings.getApplyToGcode());
        } finally {
            java.nio.file.Files.deleteIfExists(xyz);
        }
    }

    private static BackendAPI backend(Settings settings) {
        return (BackendAPI) Proxy.newProxyInstance(BackendAPI.class.getClassLoader(),
                new Class<?>[]{BackendAPI.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getSettings": return settings;
                        case "isConnected", "isSendingFile": return false;
                        default: throw new AssertionError("Unexpected backend call: " + method.getName());
                    }
                });
    }

    public static final class Window {
        private final Scanner scanner = new Scanner();
    }

    public static final class Scanner {
        private final Position minXYZ = new Position(0, 0, -2, Units.MM);
        private final Position maxXYZ = new Position(10, 10, 2, Units.MM);
        private final Position[][] grid = {
                {new Position(0, 0, Double.NaN, Units.MM), new Position(0, 10, Double.NaN, Units.MM)},
                {new Position(10, 0, Double.NaN, Units.MM), new Position(10, 10, Double.NaN, Units.MM)}
        };
        private final List<Double> restoredHeights = new ArrayList<>();

        public Position[][] getProbePositionGrid() { return grid; }
        public void update(Position min, Position max) {
            assertEquals(minXYZ, min);
            assertEquals(maxXYZ, max);
        }
        public void probeEvent(Position raw) {
            restoredHeights.add(raw.z + 0.25);
        }
    }
}
