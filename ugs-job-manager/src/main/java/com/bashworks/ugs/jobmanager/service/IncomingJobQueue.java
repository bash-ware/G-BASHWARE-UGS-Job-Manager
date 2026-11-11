package com.bashworks.ugs.jobmanager.service;

import com.bashworks.ugs.jobmanager.model.JobEntry;
import com.bashworks.ugs.jobmanager.model.JobOrigin;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Persistent manual run order and lifecycle. Removing entries preserves G-code files. */
public final class IncomingJobQueue {
    public enum State { WAITING, RUNNING, FINISHED, INTERRUPTED, DISMISSED }
    private final File directory;
    private final Path stateFile;
    private final LinkedHashMap<String, State> records = new LinkedHashMap<>();

    public IncomingJobQueue(File directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory.toPath());
        stateFile = directory.toPath().resolve(".queue-state");
        if (Files.exists(stateFile)) {
            for (String line : Files.readAllLines(stateFile, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t", 2);
                if (parts.length != 2) throw new IOException("Invalid queue state");
                State state;
                try { state = State.valueOf(parts[0]); }
                catch (IllegalArgumentException ex) { throw new IOException("Invalid queue state", ex); }
                records.put(parts[1], state == State.RUNNING ? State.INTERRUPTED : state);
            }
        } else {
            Path legacy = directory.toPath().resolve(".queue-dismissed");
            if (Files.exists(legacy)) for (String key : Files.readAllLines(legacy, StandardCharsets.UTF_8)) records.put(key, State.DISMISSED);
        }
    }

    public List<JobEntry> jobs() throws IOException {
        Map<String, JobEntry> files = new LinkedHashMap<>();
        File[] scanned = directory.listFiles();
        if (scanned == null) throw new IOException("Cannot read incoming folder");
        // Stable oldest-first order for new files, independent of directory enumeration.
        Arrays.sort(scanned, Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        LinkedHashMap<String, State> next = new LinkedHashMap<>(records);
        for (File file : scanned) if (JobLibraryService.isSupportedGcodeFile(file)) {
            String id = key(file);
            files.put(id, JobEntry.fromFile(file, JobOrigin.LOCAL_DISK));
            next.putIfAbsent(id, State.WAITING);
        }
        if (!new ArrayList<>(next.entrySet()).equals(new ArrayList<>(records.entrySet()))) save(next);
        List<JobEntry> jobs = new ArrayList<>();
        // Running job is pinned above pending jobs; completed history stays below them.
        for (State group : new State[]{State.RUNNING, State.WAITING, State.FINISHED}) {
            for (Map.Entry<String, State> record : records.entrySet()) {
                State state = record.getValue();
                boolean inGroup = group == State.WAITING ? state == State.WAITING || state == State.INTERRUPTED : state == group;
                if (inGroup && files.containsKey(record.getKey())) jobs.add(files.get(record.getKey()));
            }
        }
        return jobs;
    }

    public State state(File file) { return records.getOrDefault(key(file), State.WAITING); }
    public boolean isPending(File file) { State s = state(file); return s == State.WAITING || s == State.INTERRUPTED; }

    public void received(JobEntry job) throws IOException {
        LinkedHashMap<String, State> next = new LinkedHashMap<>(records);
        // An explicit resend is a fresh queue entry, at the end of pending work.
        if (next.get(key(job.getFile())) == State.RUNNING) return;
        next.remove(key(job.getFile()));
        next.put(key(job.getFile()), State.WAITING);
        save(next);
    }

    public void remove(Collection<JobEntry> jobs) throws IOException {
        LinkedHashMap<String, State> next = new LinkedHashMap<>(records);
        for (JobEntry job : jobs) next.put(key(job.getFile()), State.DISMISSED);
        save(next);
    }

    public void started(File file) throws IOException { transition(file, State.RUNNING); }
    public void finished(File file) throws IOException {
        if (file != null && state(file) == State.RUNNING) transition(file, State.FINISHED);
    }
    public void interrupted(File file) throws IOException {
        if (file != null && state(file) == State.RUNNING) transition(file, State.INTERRUPTED);
    }
    private void transition(File file, State state) throws IOException {
        if (file == null || !records.containsKey(key(file)) || records.get(key(file)) == State.DISMISSED) return;
        LinkedHashMap<String, State> next = new LinkedHashMap<>(records);
        if (state == State.RUNNING) next.replaceAll((k, v) -> v == State.RUNNING ? State.INTERRUPTED : v);
        next.put(key(file), state);
        save(next);
    }

    public void reorderPending(List<File> ordered) throws IOException {
        List<String> pending = new ArrayList<>();
        for (JobEntry job : jobs()) if (isPending(job.getFile())) pending.add(key(job.getFile()));
        List<String> proposed = new ArrayList<>();
        for (File file : ordered) proposed.add(key(file));
        if (new HashSet<>(proposed).size() != proposed.size() || proposed.size() != pending.size() || !new HashSet<>(proposed).equals(new HashSet<>(pending)))
            throw new IllegalArgumentException("Reorder must include each pending job exactly once");
        LinkedHashMap<String, State> next = new LinkedHashMap<>();
        for (String id : proposed) next.put(id, records.get(id));
        for (Map.Entry<String, State> entry : records.entrySet()) next.putIfAbsent(entry.getKey(), entry.getValue());
        save(next);
    }

    private String key(File file) {
        if (file == null) return "";
        String path = Base64.getEncoder().encodeToString(file.getAbsolutePath().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        return path + ":" + file.length() + ":" + file.lastModified();
    }
    private void save(LinkedHashMap<String, State> next) throws IOException {
        Path temp = Files.createTempFile(directory.toPath(), ".queue-", ".tmp");
        try {
            List<String> lines = new ArrayList<>();
            next.forEach((key, value) -> lines.add(value.name() + "\t" + key));
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try { Files.move(temp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING); }
            records.clear();
            records.putAll(next);
        } finally { Files.deleteIfExists(temp); }
    }
}
