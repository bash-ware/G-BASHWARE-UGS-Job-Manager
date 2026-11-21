package com.bashworks.ugs.jobmanager.service;

import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Plays the supplied alarm once; input acknowledges it without blocking Swing. */
public final class CompletionAlarm implements AutoCloseable {
    public static final String RESOURCE = "/com/bashworks/ugs/jobmanager/audio/job-complete.wav";
    private static final Logger LOG = Logger.getLogger(CompletionAlarm.class.getName());
    interface Playback extends AutoCloseable {
        long durationMillis();
        void start();
        void close();
    }
    private final ScheduledThreadPoolExecutor audio;
    private final Callable<Playback> openAudio;
    private final LongSupplier activity;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final AWTEventListener inputListener = this::handleInput;
    private volatile boolean armed;
    private boolean listening;
    // Accessed only on the audio executor.
    private Playback current;
    private ScheduledFuture<?> poll, finish;

    public CompletionAlarm() { this(CompletionAlarm::openClip, new SystemInputActivity(), true); }

    CompletionAlarm(Callable<Playback> openAudio, LongSupplier activity, boolean listenForInput) {
        this.openAudio = openAudio;
        this.activity = activity;
        audio = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "ugs-job-completion-alarm");
            thread.setDaemon(true);
            return thread;
        });
        audio.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        audio.setRemoveOnCancelPolicy(true);
        if (listenForInput) {
            try {
                Toolkit.getDefaultToolkit().addAWTEventListener(inputListener,
                        AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
                listening = true;
            } catch (SecurityException ex) { LOG.log(Level.WARNING, "Application input listener unavailable", ex); }
        }
    }

    public void play() {
        if (closed.get()) return;
        long request = generation.incrementAndGet();
        armed = true;
        submit(() -> {
            if (!valid(request)) return;
            stopCurrent();
            long baseline = activity.getAsLong();
            Playback opened = null;
            try {
                opened = openAudio.call();
                if (!valid(request) || baseline != activity.getAsLong()) {
                    opened.close();
                    if (generation.get() == request) armed = false;
                    return;
                }
                current = opened;
                opened.start();
                poll = audio.scheduleAtFixedRate(() -> {
                    if (valid(request) && baseline != activity.getAsLong()) stop();
                }, 25, 25, TimeUnit.MILLISECONDS);
                finish = audio.schedule(() -> {
                    if (generation.get() == request) { armed = false; stopCurrent(); }
                }, Math.max(1, opened.durationMillis()) + 100, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                if (opened != null) opened.close();
                current = null;
                if (generation.get() == request) armed = false;
                if (!closed.get()) LOG.log(Level.WARNING, "Could not play the job-completion alarm", ex);
            }
        });
    }

    private boolean valid(long request) { return !closed.get() && armed && generation.get() == request; }

    void handleInput(AWTEvent event) {
        int id = event.getID();
        if (id == KeyEvent.KEY_PRESSED || id == MouseEvent.MOUSE_MOVED || id == MouseEvent.MOUSE_DRAGGED || id == MouseEvent.MOUSE_PRESSED) stop();
    }

    public void stop() {
        if (!armed) return;
        armed = false;
        generation.incrementAndGet();
        submit(this::stopCurrent);
    }

    private void submit(Runnable work) {
        try { audio.execute(work); }
        catch (RejectedExecutionException ignored) { /* Panel closed during dispatch. */ }
    }

    private void stopCurrent() {
        if (poll != null) { poll.cancel(false); poll = null; }
        if (finish != null) { finish.cancel(false); finish = null; }
        if (current != null) { current.close(); current = null; }
    }

    private static Playback openClip() throws Exception {
        var resource = CompletionAlarm.class.getResource(RESOURCE);
        if (resource == null) throw new IllegalStateException("Missing bundled completion alarm");
        Clip clip = AudioSystem.getClip();
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(resource)) {
            clip.open(stream);
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), 0f)));
            }
            return new Playback() {
                public long durationMillis() { return clip.getMicrosecondLength() / 1000; }
                public void start() { clip.start(); }
                public void close() { clip.stop(); clip.close(); }
            };
        } catch (Exception ex) { clip.close(); throw ex; }
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            armed = false;
            generation.incrementAndGet();
            if (listening) Toolkit.getDefaultToolkit().removeAWTEventListener(inputListener);
            submit(this::stopCurrent);
            audio.shutdown();
        }
    }
}
