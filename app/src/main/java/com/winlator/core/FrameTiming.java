package com.winlator.core;

import java.util.Arrays;

/** Intervals between observed window updates, not GPU execution timestamps. */
public final class FrameTiming {
    public static final int CAPACITY = 120;
    private static final long IDLE_NS = 2000000000L;
    private final float[] times = new float[CAPACITY];
    private int count, next;
    private long previous = -1;

    public synchronized void reset() {
        count = next = 0;
        previous = -1;
    }

    public synchronized void record(long now) {
        if (previous < 0) { previous = now; return; }
        long delta = now - previous;
        if (delta <= 0) return;
        previous = now;
        if (delta > IDLE_NS) { count = next = 0; return; }
        times[next] = delta / 1000000.0f;
        next = (next + 1) % CAPACITY;
        if (count < CAPACITY) count++;
    }

    public synchronized Snapshot snapshot(long now) {
        if (previous < 0 || now - previous > IDLE_NS || count == 0)
            return new Snapshot(new float[0], 0, 0, 0, 0);
        float[] ordered = new float[count];
        float sum = 0, peak = 0;
        int start = (next - count + CAPACITY) % CAPACITY;
        for (int i = 0; i < count; i++) {
            float value = times[(start + i) % CAPACITY];
            ordered[i] = value;
            sum += value;
            peak = Math.max(peak, value);
        }
        float[] sorted = ordered.clone();
        Arrays.sort(sorted);
        float mean = sum / count;
        float p99 = sorted[(int)Math.ceil(count * 0.99) - 1];
        return new Snapshot(ordered, 1000.0f / mean, mean, p99, peak);
    }

    public static final class Snapshot {
        public final float[] milliseconds;
        public final float fps, meanMs, p99Ms, peakMs;
        private Snapshot(float[] values, float fps, float mean, float p99, float peak) {
            this.milliseconds = values;
            this.fps = fps;
            this.meanMs = mean;
            this.p99Ms = p99;
            this.peakMs = peak;
        }
    }
}
