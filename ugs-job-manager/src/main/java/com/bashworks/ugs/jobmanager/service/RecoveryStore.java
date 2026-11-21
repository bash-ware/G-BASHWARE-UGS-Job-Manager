package com.bashworks.ugs.jobmanager.service;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Objects;
import java.util.Properties;

/** The checkpoint is replaced only after its data has reached disk. */
public final class RecoveryStore {
    private final Path directory;
    private final Path checkpoint;

    public RecoveryStore(Path directory) {
        this.directory = directory;
        checkpoint = directory.resolve("checkpoint.properties");
    }

    public Properties read() throws IOException {
        Properties data = new Properties();
        if (Files.exists(checkpoint)) {
            try (var input = Files.newInputStream(checkpoint)) { data.load(input); }
            if (!"1".equals(data.getProperty("format"))) throw new IOException("Unsupported recovery checkpoint format");
        }
        return data;
    }

    public void save(Properties data) throws IOException {
        Properties copy = new Properties();
        copy.putAll(data);
        copy.setProperty("format", "1");
        writeAtomically(checkpoint, temp -> {
            try (var output = Files.newOutputStream(temp)) { copy.store(output, "UGS Job Manager recovery"); }
        });
    }

    public void discard() throws IOException { Files.deleteIfExists(checkpoint); }

    /** Store a separate copy so UGS temp-file cleanup and edits to the original cannot erase it. */
    public String backup(File file, String kind) throws IOException {
        if (file == null || !file.isFile()) return "";
        Path source = file.toPath().toAbsolutePath().normalize();
        long size = Files.size(source);
        var modified = Files.getLastModifiedTime(source);
        var key = java.util.UUID.nameUUIDFromBytes((kind + "\n" + source + "\n" + size + "\n" + modified)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path destination = directory.resolve("files").resolve(key.toString()).resolve(source.getFileName());
        if (!Files.exists(destination)) {
            writeAtomically(destination, temp -> {
                Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
                if (size != Files.size(source) || !modified.equals(Files.getLastModifiedTime(source)))
                    throw new IOException("File changed while saving: " + source.getFileName());
            });
        }
        return directory.relativize(destination).toString();
    }

    public File backedUp(String relative) throws IOException {
        if (relative == null || relative.isBlank()) return null;
        Path path = directory.resolve(relative).normalize();
        if (!path.startsWith(directory.resolve("files").normalize()) || !Files.isRegularFile(path))
            throw new IOException("Saved recovery file is unavailable: " + relative);
        return path.toFile();
    }

    private interface Writer { void write(Path temp) throws IOException; }

    private static void writeAtomically(Path path, Writer writer) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), ".recovery-", ".tmp");
        try {
            writer.write(temp);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                throw new IOException("Recovery folder does not support atomic saves", ex);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
