package com.bashworks.ugs.jobmanager.service;

import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;
import javax.sound.sampled.AudioSystem;
import javax.swing.JPanel;
import java.awt.event.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class CompletionAlarmTest {
    static class FakePlayback implements CompletionAlarm.Playback {
        final CountDownLatch started = new CountDownLatch(1), stopped = new CountDownLatch(1);
        final AtomicInteger starts = new AtomicInteger();
        long duration = 60_000;
        public long durationMillis() { return duration; }
        public void start() { starts.incrementAndGet(); started.countDown(); }
        public void close() { stopped.countDown(); }
    }
    private void await(CountDownLatch latch) throws Exception { assertTrue("Alarm action timed out", latch.await(2, TimeUnit.SECONDS)); }

    @Test public void bundlesTheSuppliedRecordingAsDecodedPcm() throws Exception {
        var resource = CompletionAlarm.class.getResource(CompletionAlarm.RESOURCE);
        assertNotNull(resource);
        try (var stream = resource.openStream()) {
            assertEquals("8c6f783ee7d99d6d14fcedaf9af76240b179b092f092db9211e90619d5460d56",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
        }
        try (var audio = AudioSystem.getAudioInputStream(resource)) {
            assertEquals(44100, audio.getFormat().getSampleRate(), 0);
            assertEquals(2, audio.getFormat().getChannels());
            assertEquals(3.8922449, audio.getFrameLength() / audio.getFormat().getFrameRate(), 0.001);
        }
    }

    @Test public void keyboardAndMouseMovementDismissPlayback() throws Exception {
        for (boolean keyboard : new boolean[]{true, false}) {
            FakePlayback output = new FakePlayback();
            try (CompletionAlarm alarm = new CompletionAlarm(() -> output, () -> 1, false)) {
                alarm.play(); await(output.started);
                JPanel source = new JPanel();
                if (keyboard) alarm.handleInput(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, 'a'));
                else alarm.handleInput(new MouseEvent(source, MouseEvent.MOUSE_MOVED, 0, 0, 1, 2, 0, false));
                await(output.stopped);
            }
        }
    }

    @Test public void globalInputTimestampChangeDismissesPlayback() throws Exception {
        FakePlayback output = new FakePlayback();
        AtomicLong activity = new AtomicLong(10);
        try (CompletionAlarm alarm = new CompletionAlarm(() -> output, activity::get, false)) {
            alarm.play(); await(output.started);
            activity.set(11);
            await(output.stopped);
        }
    }

    @Test public void acknowledgementDuringLoadingPreventsDelayedPlayback() throws Exception {
        FakePlayback output = new FakePlayback();
        CountDownLatch opening = new CountDownLatch(1), release = new CountDownLatch(1);
        try (CompletionAlarm alarm = new CompletionAlarm(() -> {
            opening.countDown();
            if (!release.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
            return output;
        }, () -> 1, false)) {
            alarm.play(); await(opening);
            alarm.stop(); release.countDown(); await(output.stopped);
            assertEquals(0, output.starts.get());
        } finally { release.countDown(); }
    }

    @Test public void replayReplacesPreviousAudioAndCloseReleasesIt() throws Exception {
        FakePlayback first = new FakePlayback(), second = new FakePlayback();
        AtomicInteger opens = new AtomicInteger();
        CompletionAlarm alarm = new CompletionAlarm(() -> opens.getAndIncrement() == 0 ? first : second, () -> 1, false);
        try {
            alarm.play(); await(first.started);
            alarm.play(); await(second.started); await(first.stopped);
            alarm.close(); await(second.stopped);
            alarm.play();
            assertEquals(2, opens.get());
        } finally { alarm.close(); }
    }

    @Test public void recordingEndsWithoutRequiringInput() throws Exception {
        FakePlayback output = new FakePlayback(); output.duration = 10;
        try (CompletionAlarm alarm = new CompletionAlarm(() -> output, () -> 1, false)) {
            alarm.play(); await(output.started); await(output.stopped);
        }
    }

    @Test public void windowsCanReadTheGlobalLastInputTimestamp() {
        Assume.assumeTrue(System.getProperty("os.name", "").startsWith("Windows"));
        var info = new SystemInputActivity.LastInputInfo();
        assertEquals(8, info.cbSize);
        assertTrue(SystemInputActivity.User32.INSTANCE.GetLastInputInfo(info));
        assertTrue(new SystemInputActivity().getAsLong() >= 0);
    }
}
