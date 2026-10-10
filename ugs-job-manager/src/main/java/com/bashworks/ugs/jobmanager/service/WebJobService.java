package com.bashworks.ugs.jobmanager.service;

import com.bashworks.ugs.jobmanager.model.JobEntry;
import com.bashworks.ugs.jobmanager.model.JobOrigin;
import com.bashworks.ugs.jobmanager.model.JobSource;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Service for fetching G-code jobs from remote Web URLs or endpoints, caching them locally,
 * and preparing them for UGS activation.
 */
public final class WebJobService implements JobSource {

    private static final String PREF_WEB_HISTORY = "web_url_history";
    private static final int MAX_HISTORY = 20;

    private final Preferences preferences;
    private final GcodeAnalyzer analyzer;
    private final File cacheDirectory;
    private final LinkedHashSet<String> urlHistory = new LinkedHashSet<>();

    public WebJobService(Preferences preferences, GcodeAnalyzer analyzer) {
        this.preferences = preferences != null ? preferences : initPreferences();
        this.analyzer = analyzer != null ? analyzer : new GcodeAnalyzer();

        File home = new File(System.getProperty("user.home"));
        this.cacheDirectory = new File(home, ".ugs-job-manager/web-cache");
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdirs();
        }

        loadHistory();
    }

    private static Preferences initPreferences() {
        try {
            Class<?> nbPrefClass = Class.forName("org.openide.util.NbPreferences");
            return (Preferences) nbPrefClass.getMethod("forModule", Class.class).invoke(null, WebJobService.class);
        } catch (Throwable fallback) {
            return Preferences.userNodeForPackage(WebJobService.class);
        }
    }

    private void loadHistory() {
        String hist = preferences.get(PREF_WEB_HISTORY, "");
        if (!hist.isEmpty()) {
            String[] parts = hist.split(";");
            for (String p : parts) {
                if (!p.trim().isEmpty()) {
                    urlHistory.add(p.trim());
                }
            }
        }
    }

    private void saveHistory() {
        StringBuilder sb = new StringBuilder();
        for (String u : urlHistory) {
            if (sb.length() > 0) sb.append(";");
            sb.append(u);
        }
        preferences.put(PREF_WEB_HISTORY, sb.toString());
    }

    public void addUrlToHistory(String url) {
        if (url == null || url.trim().isEmpty()) return;
        url = url.trim();
        urlHistory.remove(url);

        LinkedHashSet<String> updated = new LinkedHashSet<>();
        updated.add(url);
        updated.addAll(urlHistory);
        urlHistory.clear();

        int count = 0;
        for (String u : updated) {
            if (count++ < MAX_HISTORY) {
                urlHistory.add(u);
            }
        }
        saveHistory();
    }

    public List<String> getUrlHistory() {
        return new ArrayList<>(urlHistory);
    }

    @Override
    public String getSourceName() {
        return "Web / Remote URL";
    }

    @Override
    public JobOrigin getOrigin() {
        return JobOrigin.WEB_URL;
    }

    @Override
    public List<JobEntry> listJobs() {
        List<JobEntry> cached = new ArrayList<>();
        File[] files = cacheDirectory.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && JobLibraryService.isSupportedGcodeFile(f)) {
                    JobEntry entry = JobEntry.fromFile(f, JobOrigin.WEB_URL);
                    cached.add(entry);
                }
            }
        }
        cached.sort((a, b) -> Long.compare(b.getLastModifiedMs(), a.getLastModifiedMs()));
        return cached;
    }

    @Override
    public JobEntry fetchJob(String urlString, ProgressCallback callback) throws Exception {
        if (urlString == null || urlString.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }
        String cleanUrl = urlString.trim();
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }

        if (callback != null) callback.onProgress("Connecting to " + cleanUrl + "...", 10);

        URI uri = new URI(cleanUrl);
        URL url = uri.toURL();
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "UGS-Job-Manager/1.0.0");

        if (conn instanceof HttpURLConnection) {
            HttpURLConnection http = (HttpURLConnection) conn;
            int code = http.getResponseCode();
            if (code >= 400) {
                throw new IOException("HTTP error " + code + ": " + http.getResponseMessage());
            }
        }

        int contentLength = conn.getContentLength();
        String filename = extractFilename(cleanUrl, conn);
        File targetFile = new File(cacheDirectory, filename);

        if (callback != null) callback.onProgress("Downloading " + filename + "...", 25);

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream out = new FileOutputStream(targetFile)) {

            byte[] buffer = new byte[8192];
            int read;
            long totalRead = 0;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalRead += read;
                if (contentLength > 0 && callback != null) {
                    int pct = 25 + (int) ((totalRead * 60) / contentLength);
                    callback.onProgress(String.format(Locale.US, "Downloading... (%.1f KB / %.1f KB)",
                            totalRead / 1024.0, contentLength / 1024.0), Math.min(85, pct));
                }
            }
        }

        if (!targetFile.exists() || targetFile.length() == 0) {
            throw new IOException("Downloaded file is empty or missing: " + targetFile.getName());
        }

        if (callback != null) callback.onProgress("Analyzing G-code metrics...", 90);

        JobEntry entry = new JobEntry(
                cleanUrl,
                filename,
                targetFile,
                cleanUrl,
                JobOrigin.WEB_URL,
                targetFile.length(),
                System.currentTimeMillis()
        );
        entry.setAnalysis(analyzer.analyze(targetFile));

        addUrlToHistory(cleanUrl);

        if (callback != null) callback.onProgress("Ready for activation", 100);

        return entry;
    }

    private String extractFilename(String urlString, URLConnection conn) {
        String path = urlString;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int lastSlash = path.lastIndexOf('/');
        String rawName = (lastSlash >= 0 && lastSlash < path.length() - 1)
                ? path.substring(lastSlash + 1)
                : "job_" + System.currentTimeMillis() + ".gcode";

        // Sanitize
        String clean = rawName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!JobLibraryService.isSupportedGcodeFile(new File(clean))) {
            clean += ".gcode";
        }
        return clean;
    }

    public File getCacheDirectory() {
        return cacheDirectory;
    }

    public void clearCache() {
        File[] files = cacheDirectory.listFiles();
        if (files != null) {
            for (File f : files) {
                try {
                    Files.deleteIfExists(f.toPath());
                } catch (Exception ignored) {}
            }
        }
    }
}
