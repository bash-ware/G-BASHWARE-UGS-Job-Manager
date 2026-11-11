package com.bashworks.ugs.jobmanager.service;

import com.bashworks.ugs.jobmanager.model.JobEntry;
import com.bashworks.ugs.jobmanager.model.JobOrigin;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Persistent queue dismissals. Removing an entry never deletes its G-code file. */
public final class IncomingJobQueue {
    private final File directory;
    private final Path stateFile;
    private final Set<String> dismissed = new HashSet<>();

    public IncomingJobQueue(File directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory.toPath());
        stateFile = directory.toPath().resolve(".queue-dismissed");
        if (Files.exists(stateFile)) dismissed.addAll(Files.readAllLines(stateFile, StandardCharsets.UTF_8));
    }

    public List<JobEntry> jobs() {
        List<JobEntry> jobs = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) {
            if (JobLibraryService.isSupportedGcodeFile(file) && !dismissed.contains(key(file)))
                jobs.add(JobEntry.fromFile(file, JobOrigin.LOCAL_DISK));
        }
        jobs.sort(Comparator.comparingLong(JobEntry::getLastModifiedMs).reversed());
        return jobs;
    }

    public void received(JobEntry job) throws IOException {
        Set<String> next = new HashSet<>(dismissed);
        next.remove(key(job.getFile()));
        save(next);
    }

    public void remove(Collection<JobEntry> jobs) throws IOException {
        Set<String> next = new HashSet<>(dismissed);
        for (JobEntry job : jobs) next.add(key(job.getFile()));
        save(next);
    }

    private String key(File file) {
        String path = Base64.getEncoder().encodeToString(file.getAbsolutePath().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        return path + ":" + file.length() + ":" + file.lastModified();
    }

    private void save(Set<String> next) throws IOException {
        Path temp = Files.createTempFile(directory.toPath(), ".queue-", ".tmp");
        try {
            Files.write(temp, next, StandardCharsets.UTF_8);
            try { Files.move(temp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING); }
            dismissed.clear();
            dismissed.addAll(next);
        } finally { Files.deleteIfExists(temp); }
    }
}
