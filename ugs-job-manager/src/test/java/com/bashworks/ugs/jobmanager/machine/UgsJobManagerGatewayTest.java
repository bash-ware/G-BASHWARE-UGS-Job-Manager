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
import java.util.concurrent.atomic.AtomicInteger;

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
                case "getNumSentRows": return 12L;
                case "getNumCompletedRows": return 10L;
                case "getMachinePosition": return new com.willwinder.universalgcodesender.model.Position(
                        12, 23, 34, com.willwinder.universalgcodesender.model.UnitUtils.Units.MM);
                default: return null;
            }
        });
        UgsJobManagerGateway gateway = new UgsJobManagerGateway(backend);
        AtomicInteger rings = new AtomicInteger();
        JobCompletionAlert completionAlert = new JobCompletionAlert(gateway, rings::incrementAndGet);
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
        assertEquals("Start, pause and resume must stay silent", 0, rings.get());
        var command = new com.willwinder.universalgcodesender.types.GcodeCommand("G1 X12", 42);
        var next = new com.willwinder.universalgcodesender.types.GcodeCommand("G1 Y23", 43);
        gateway.UGSEvent(new CommandEvent(CommandEventType.COMMAND_SENT, command));
        command.setOk(true);
        gateway.UGSEvent(new CommandEvent(CommandEventType.COMMAND_COMPLETE, command));
        gateway.UGSEvent(new CommandEvent(CommandEventType.COMMAND_SENT, next));
        var running = gateway.recoveryTelemetry().snapshot();
        assertEquals(source, running.unfinished());
        assertEquals(processed, running.processed());
        assertEquals("G1 Y23", running.state().get("sent.command"));
        assertEquals("G1 X12", running.state().get("ack.command"));
        assertEquals("12", running.state().get("rows.sent"));
        assertTrue(running.state().get("machine").contains("Z 34.000000"));
        sending.set(false);
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_COMPLETE));
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_COMPLETE));
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(Arrays.asList("started", "finished"), events);
        assertEquals("A completed run must ring once despite duplicate completion", 1, rings.get());
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
        assertEquals("Cancellation and disconnect must stay silent", 1, rings.get());
        assertEquals(source, gateway.recoveryTelemetry().snapshot().unfinished());
        assertEquals("INTERRUPTED", gateway.recoveryTelemetry().snapshot().state().get("status"));
        connected.set(true);
        sending.set(true);
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_STARTED));
        sending.set(false);
        gateway.UGSEvent(new StreamEvent(StreamEventType.STREAM_COMPLETE));
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals("Running the same file again must ring again", 2, rings.get());
        assertNull(gateway.recoveryTelemetry().snapshot().unfinished());
        completionAlert.close();
        completionAlert.onJobStarted(source);
        completionAlert.onJobFinished(source);
        assertEquals("Closed panels must stay silent", 2, rings.get());
    }
}
