package com.bashworks.ugs.jobmanager.service;

import com.bashworks.ugs.jobmanager.model.JobEntry;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecoveryStoreTest {
    @Test public void freshSessionRestoresQueueAndImmutableRecoveryFiles() throws Exception {
        var root = Files.createTempDirectory("ugs-recovery-test");
        var incoming = Files.createDirectory(root.resolve("incoming"));
        var first = Files.writeString(incoming.resolve("first.nc"), "G1 Z1").toFile();
        var second = Files.writeString(incoming.resolve("second.nc"), "G1 Z2").toFile();
        var third = Files.writeString(incoming.resolve("third.nc"), "G1 Z3").toFile();
        var mesh = Files.writeString(root.resolve("height-map.xyz"), "0 0 0.025\n").toFile();
        var queue = new IncomingJobQueue(incoming.toFile());
        queue.jobs();
        queue.reorderPending(List.of(second, first, third));
        queue.started(second);
        queue.finished(second);
        queue.started(first);

        var store = new RecoveryStore(root.resolve("recovery"));
        var data = new Properties();
        data.setProperty("queue", queue.checkpoint());
        data.setProperty("unfinished.backup", store.backup(first, "unfinished"));
        data.setProperty("map.backup", store.backup(mesh, "map"));
        data.setProperty("sent.command", "G1 Z1");
        store.save(data);

        Files.writeString(mesh.toPath(), "changed");
        var freshStore = new RecoveryStore(root.resolve("recovery"));
        Properties restored = freshStore.read();
        var freshQueue = new IncomingJobQueue(incoming.toFile());
        freshQueue.remove(freshQueue.jobs());
        freshQueue.restore(restored.getProperty("queue"));
        assertEquals(List.of(first, third, second), freshQueue.jobs().stream().map(JobEntry::getFile).toList());
        assertEquals(IncomingJobQueue.State.INTERRUPTED, freshQueue.state(first));
        assertEquals(IncomingJobQueue.State.FINISHED, freshQueue.state(second));
        assertEquals("G1 Z1", Files.readString(freshStore.backedUp(restored.getProperty("unfinished.backup")).toPath()));
        assertEquals("0 0 0.025\n", Files.readString(freshStore.backedUp(restored.getProperty("map.backup")).toPath()));
        assertEquals("G1 Z1", restored.getProperty("sent.command"));
        freshStore.discard();
        assertTrue(freshStore.read().isEmpty());
    }
}
