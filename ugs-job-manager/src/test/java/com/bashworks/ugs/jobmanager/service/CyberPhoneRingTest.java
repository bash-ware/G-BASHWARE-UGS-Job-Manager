package com.bashworks.ugs.jobmanager.service;

import org.junit.Test;
import static org.junit.Assert.*;

public class CyberPhoneRingTest {
    @Test public void ringHasSixCleanPulsesAtFullLevelWithoutClipping() {
        byte[] pcm = CyberPhoneRing.synthesize();
        assertEquals(6 * 44100 * 2, pcm.length);
        double peak = 0;
        for (int i = 0; i < pcm.length / 2; i++) {
            double value = Math.abs(sample(pcm, i));
            peak = Math.max(peak, value);
            assertTrue("PCM must retain clipping headroom", value < 0.96);
        }
        assertTrue("The notification must be full-level", peak > 0.94);
        for (int cycle = 0; cycle < 3; cycle++) {
            assertTrue("First ring pulse must be audible", rms(pcm, cycle * 2 + 0.05, cycle * 2 + 0.50) > 0.2);
            assertTrue("Second ring pulse must be audible", rms(pcm, cycle * 2 + 0.76, cycle * 2 + 1.21) > 0.2);
            assertEquals("Double ring has a silent separator", 0, rms(pcm, cycle * 2 + 0.56, cycle * 2 + 0.70), 0);
            assertEquals("Ring pairs have a silent pause", 0, rms(pcm, cycle * 2 + 1.27, cycle * 2 + 1.99), 0);
        }
        assertEquals("Attack begins at zero to avoid clicking", 0, sample(pcm, 0), 0);
        assertEquals("Playback ends at zero", 0, sample(pcm, pcm.length / 2 - 1), 0);
    }

    private double sample(byte[] pcm, int frame) {
        return (short) ((pcm[frame * 2] & 255) | (pcm[frame * 2 + 1] << 8)) / 32768.0;
    }
    private double rms(byte[] pcm, double start, double end) {
        double energy = 0;
        int first = (int) (start * CyberPhoneRing.SAMPLE_RATE), last = (int) (end * CyberPhoneRing.SAMPLE_RATE);
        for (int frame = first; frame < last; frame++) { double s = sample(pcm, frame); energy += s * s; }
        return Math.sqrt(energy / (last - first));
    }
}
