package com.bashworks.ugs.jobmanager.machine;

import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.model.events.*;
import com.willwinder.universalgcodesender.listeners.ControllerState;
import org.junit.Test;
import static org.junit.Assert.*;
import javax.swing.SwingUtilities;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class UgsJobManagerGatewayTest {
    @Test public void tracksSourceFileAndExplicitCompletion() throws Exception {
        var dir = Files.createTempDirectory("ugs-gateway-test-");
        File source = Files.writeString(dir.resolve("source.nc"), "G1 X10").toFile();
        File processed = Files.writeString(dir.resolve("processed.nc"), "G1 X10").toFile();
        AtomicBoolean connected = new AtomicBoolean(true);
        AtomicBoolean sending = new AtomicBoolean(false);
        BackendAPI backend = (BackendAPI)Proxy.newProxyInstance(BackendAPI.class.getClassLoader(), new Class[]{BackendAPI.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getGcodeFile": return source;
                case "getProcessedGcodeFile": return processed;
                case "isConnected": return connected.get();
                case "isSendingFile": return sending.get();
                case "isIdle": return !sending.get();
                case "getControllerState": return ControllerState.IDLE;
                case "isPaused": return false;
                default: return null;
            }
        });
        UgsJobManagerGateway gateway = new UgsJobManagerGateway(backend);
        assertEquals("Queue identity must use original file", source, gateway.getActiveGcodeFile());
        List<String> events = new ArrayList<>();
        gateway.addListener(new JobManagerListener() {
            public void onJobStarted(File file) { assertEquals(source, file); events.add("started"); }
            public void onJobFinished(File file) { assertEquals(source, file); events.add("finished"); }
            public void onJobInterrupted(File file) { assertEquals(source, file); events.add("stopped"); }
            public void onActiveJobChanged(File file) {}
            public void onMachineConnectionChanged(boolean value) {}
            public void onMachineStateChanged(String state, boolean idle, boolean streaming) {}
            public void onStreamStateChanged(boolean streaming, boolean paused) {}
        });
        sending.set(true);
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_STARTED));
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_PAUSED));
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_RESUMED));
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(Arrays.asList("started"), events);
        sending.set(false);
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_COMPLETE));
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_COMPLETE));
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(Arrays.asList("started", "finished"), events);
        sending.set(true);
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_STARTED));
        sending.set(false);
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_CANCELED));
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_COMPLETE));
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(Arrays.asList("started", "finished", "started", "stopped"), events);
        sending.set(true);
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_STARTED));
        connected.set(false);
        gateway.UGSEvent(new ControllerStateEvent(ControllerState.DISCONNECTED, ControllerState.IDLE));
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_COMPLETE));
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals("stopped", events.get(events.size()-1));
        assertEquals(6, events.size());
    }
}
