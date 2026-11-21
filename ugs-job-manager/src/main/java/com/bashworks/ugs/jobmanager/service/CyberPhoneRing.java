package com.bashworks.ugs.jobmanager.service;

import javax.sound.sampled.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A full-level electronic double ring, synthesized locally without audio assets. */
public final class CyberPhoneRing implements AutoCloseable {
    public static final int SAMPLE_RATE = 44100;
    public static final int DURATION_SECONDS = 6;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private static final Logger LOG = Logger.getLogger(CyberPhoneRing.class.getName());
    private final ScheduledThreadPoolExecutor audio;
    private final AtomicBoolean closed = new AtomicBoolean();
    // Only accessed on the audio executor, including cleanup.
    private Clip current;

    public CyberPhoneRing() {
        audio = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "ugs-job-completion-ring");
            thread.setDaemon(true);
            return thread;
        });
        audio.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        audio.setRemoveOnCancelPolicy(true);
    }

    private static final class Sample {
        private static final byte[] PCM = synthesize();
    }

    /** Restarts the ring for a new completion; never waits on the Swing thread. */
    public void play() {
        if (closed.get()) return;
        try {
            audio.execute(() -> {
                if (closed.get()) return;
                stopCurrent();
                Clip clip = null;
                try {
                    byte[] pcm = Sample.PCM;
                    clip = AudioSystem.getClip();
                    clip.open(FORMAT, pcm, 0, pcm.length);
                    if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), 0f)));
                    }
                    current = clip;
                    clip.start();
                    Clip playing = clip;
                    audio.schedule(() -> {
                        if (current == playing) stopCurrent();
                    }, DURATION_SECONDS * 1000L + 250, TimeUnit.MILLISECONDS);
                } catch (Exception ex) {
                    if (clip != null) clip.close();
                    current = null;
                    LOG.log(Level.WARNING, "Could not play the job-completion ring", ex);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // The panel closed while the completion event was being dispatched.
        }
    }

    private void stopCurrent() {
        if (current != null) {
            current.stop();
            current.close();
            current = null;
        }
    }

    /** 16-bit little-endian mono PCM: three pairs of pulsing, detuned phone tones. */
    public static byte[] synthesize() {
        int frames = SAMPLE_RATE * DURATION_SECONDS;
        double[] wave = new double[frames];
        double peak = 0;
        for (int i = 0; i < frames; i++) {
            double t = i / (double) SAMPLE_RATE;
            double cycle = t % 2.0;
            double pulse = cycle < 0.55 ? cycle : cycle >= 0.71 && cycle < 1.26 ? cycle - 0.71 : -1;
            if (pulse < 0) continue;
            double envelope = Math.min(1, pulse / 0.015) * Math.min(1, (0.55 - pulse) / 0.045);
            double vibrato = 0.85 * Math.sin(2 * Math.PI * 7 * pulse);
            double tremolo = 0.38 + 0.62 * (0.5 + 0.5 * Math.sin(2 * Math.PI * 13 * pulse));
            double tone = 0.40 * Math.sin(2 * Math.PI * 880 * pulse + vibrato)
                    + 0.34 * Math.sin(2 * Math.PI * 1174.66 * pulse - vibrato * 0.7)
                    + 0.14 * Math.sin(2 * Math.PI * 440 * pulse)
                    + 0.08 * Math.sin(2 * Math.PI * 1760 * pulse + vibrato * 2)
                    + 0.04 * Math.sin(2 * Math.PI * 2349.32 * pulse);
            wave[i] = envelope * tremolo * tone;
            peak = Math.max(peak, Math.abs(wave[i]));
        }
        byte[] pcm = new byte[frames * 2];
        double scale = peak > 0 ? 0.95 * Short.MAX_VALUE / peak : 0;
        for (int i = 0; i < frames; i++) {
            int sample = (int) Math.round(wave[i] * scale);
            pcm[i * 2] = (byte) sample;
            pcm[i * 2 + 1] = (byte) (sample >> 8);
        }
        return pcm;
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            audio.execute(this::stopCurrent);
            audio.shutdown();
        }
    }
}
