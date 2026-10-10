package com.bashworks.ugs.jobmanager.service;

import com.bashworks.ugs.jobmanager.model.JobEntry;
import com.bashworks.ugs.jobmanager.model.JobOrigin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Manages the local file system job library, watched folder, file filtering,
 * and recent files history.
 */
public final class JobLibraryService {

    public static final Set<String> SUPPORTED_EXTENSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(".gcode", ".nc", ".ngc", ".tap", ".cnc", ".g", ".txt"))
    );

    private static final int MAX_RECENT = 25;
    private static final String PREF_WATCHED_DIR = "watched_directory";
    private static final String PREF_SCAN_RECURSIVE = "scan_recursive";
    private static final String PREF_RECENT_FILES = "recent_files";
    private static final String PREF_FAVORITES = "favorite_dirs";

    private final Preferences preferences;
    private final GcodeAnalyzer analyzer;
    private File watchedDirectory;
    private boolean scanRecursive;
    private final LinkedHashSet<String> recentFilePaths = new LinkedHashSet<>();
    private final LinkedHashSet<String> favoriteDirs = new LinkedHashSet<>();

    public JobLibraryService(Preferences preferences, GcodeAnalyzer analyzer) {
        this.preferences = preferences != null ? preferences : initPreferences();
        this.analyzer = analyzer != null ? analyzer : new GcodeAnalyzer();

        loadPreferences();
    }

    private static Preferences initPreferences() {
        try {
            Class<?> nbPrefClass = Class.forName("org.openide.util.NbPreferences");
            return (Preferences) nbPrefClass.getMethod("forModule", Class.class).invoke(null, JobLibraryService.class);
        } catch (Throwable fallback) {
            return Preferences.userNodeForPackage(JobLibraryService.class);
        }
    }

    private void loadPreferences() {
        String dirPath = preferences.get(PREF_WATCHED_DIR, "");
        if (!dirPath.isEmpty()) {
            File d = new File(dirPath);
            if (d.exists() && d.isDirectory()) {
                this.watchedDirectory = d;
            }
        }
        if (this.watchedDirectory == null) {
            // Default to user home or Desktop CNC folder
            File userHome = new File(System.getProperty("user.home"));
            File cncFolder = new File(userHome, "Documents/CNC");
            if (cncFolder.exists() && cncFolder.isDirectory()) {
                this.watchedDirectory = cncFolder;
            } else {
                this.watchedDirectory = userHome;
            }
        }

        this.scanRecursive = preferences.getBoolean(PREF_SCAN_RECURSIVE, false);

        String recent = preferences.get(PREF_RECENT_FILES, "");
        if (!recent.isEmpty()) {
            String[] parts = recent.split(";");
            for (String p : parts) {
                if (!p.trim().isEmpty()) {
                    recentFilePaths.add(p.trim());
                }
            }
        }

        String favs = preferences.get(PREF_FAVORITES, "");
        if (!favs.isEmpty()) {
            String[] parts = favs.split(";");
            for (String p : parts) {
                if (!p.trim().isEmpty()) {
                    favoriteDirs.add(p.trim());
                }
            }
        }
    }

    public File getWatchedDirectory() {
        return watchedDirectory;
    }

    public void setWatchedDirectory(File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            this.watchedDirectory = dir;
            preferences.put(PREF_WATCHED_DIR, dir.getAbsolutePath());
        }
    }

    public boolean isScanRecursive() {
        return scanRecursive;
    }

    public void setScanRecursive(boolean scanRecursive) {
        this.scanRecursive = scanRecursive;
        preferences.putBoolean(PREF_SCAN_RECURSIVE, scanRecursive);
    }

    public List<JobEntry> scanJobs() {
        List<JobEntry> entries = new ArrayList<>();
        if (watchedDirectory == null || !watchedDirectory.exists() || !watchedDirectory.isDirectory()) {
            return entries;
        }

        scanDirectory(watchedDirectory, entries, 0, scanRecursive ? 3 : 1);
        entries.sort((a, b) -> Long.compare(b.getLastModifiedMs(), a.getLastModifiedMs()));
        return entries;
    }

    private void scanDirectory(File dir, List<JobEntry> outList, int currentDepth, int maxDepth) {
        if (currentDepth >= maxDepth) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory() && !f.isHidden() && !f.getName().startsWith(".")) {
                if (scanRecursive) {
                    scanDirectory(f, outList, currentDepth + 1, maxDepth);
                }
            } else if (f.isFile() && isSupportedGcodeFile(f)) {
                JobEntry entry = JobEntry.fromFile(f, JobOrigin.LOCAL_DISK);
                outList.add(entry);
            }
        }
    }

    public static boolean isSupportedGcodeFile(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase(Locale.ROOT);
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public void addRecentFile(File file) {
        if (file == null || !file.exists()) return;
        String path = file.getAbsolutePath();
        recentFilePaths.remove(path);
        // Prepend by creating new set
        LinkedHashSet<String> updated = new LinkedHashSet<>();
        updated.add(path);
        updated.addAll(recentFilePaths);
        recentFilePaths.clear();

        int count = 0;
        for (String p : updated) {
            if (count++ < MAX_RECENT) {
                recentFilePaths.add(p);
            }
        }
        saveRecentFiles();
    }

    private void saveRecentFiles() {
        StringBuilder sb = new StringBuilder();
        for (String p : recentFilePaths) {
            if (sb.length() > 0) sb.append(";");
            sb.append(p);
        }
        preferences.put(PREF_RECENT_FILES, sb.toString());
    }

    public List<JobEntry> getRecentJobs() {
        List<JobEntry> list = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();
        for (String path : recentFilePaths) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                list.add(JobEntry.fromFile(f, JobOrigin.LOCAL_DISK));
            } else {
                toRemove.add(path);
            }
        }
        if (!toRemove.isEmpty()) {
            recentFilePaths.removeAll(toRemove);
            saveRecentFiles();
        }
        return list;
    }

    public void addFavoriteDirectory(File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            favoriteDirs.add(dir.getAbsolutePath());
            preferences.put(PREF_FAVORITES, String.join(";", favoriteDirs));
        }
    }

    public List<File> getFavoriteDirectories() {
        List<File> list = new ArrayList<>();
        for (String p : favoriteDirs) {
            File d = new File(p);
            if (d.exists() && d.isDirectory()) {
                list.add(d);
            }
        }
        return list;
    }

    public JobEntry createEntryAndAnalyze(File file, JobOrigin origin) {
        JobEntry entry = JobEntry.fromFile(file, origin != null ? origin : JobOrigin.LOCAL_DISK);
        entry.setAnalysis(analyzer.analyze(file));
        addRecentFile(file);
        return entry;
    }

    public GcodeAnalyzer getAnalyzer() {
        return analyzer;
    }
}
