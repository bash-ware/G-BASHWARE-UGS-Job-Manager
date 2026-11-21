package com.bashworks.ugs.jobmanager.service;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import java.awt.MouseInfo;
import java.awt.Point;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads only the last-input timestamp, never keys or input contents. */
public final class SystemInputActivity implements LongSupplier {
    private static final Logger LOG = Logger.getLogger(SystemInputActivity.class.getName());
    private boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    public interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);
        boolean GetLastInputInfo(LastInputInfo info);
    }
    @Structure.FieldOrder({"cbSize", "dwTime"})
    public static class LastInputInfo extends Structure {
        public int cbSize = size();
        public int dwTime;
    }

    @Override public long getAsLong() {
        if (windows) {
            try {
                LastInputInfo info = new LastInputInfo();
                if (!User32.INSTANCE.GetLastInputInfo(info)) throw new IllegalStateException("GetLastInputInfo failed");
                return Integer.toUnsignedLong(info.dwTime);
            } catch (RuntimeException | LinkageError ex) {
                windows = false;
                LOG.log(Level.WARNING, "Global keyboard dismissal unavailable; using application input and mouse position", ex);
            }
        }
        try {
            var pointer = MouseInfo.getPointerInfo();
            if (pointer == null) return 0;
            Point p = pointer.getLocation();
            return ((long) p.x << 32) ^ (p.y & 0xffffffffL);
        } catch (RuntimeException ex) { return 0; }
    }
}
