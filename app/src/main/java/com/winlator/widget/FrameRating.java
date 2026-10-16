package com.winlator.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.box64.Box64Utils;
import com.winlator.core.CPUStatus;
import com.winlator.core.FrameTiming;
import com.winlator.core.StringUtils;

import java.util.Locale;

public class FrameRating extends FrameLayout implements Runnable {
    public enum Mode {DISABLED, SIMPLE, FULL, ADVANCED}
    private final FrameTiming timings = new FrameTiming();
    private LinearLayout timingPanel;
    private TextView timingText;
    private FrameTimeGraph graph;
    private boolean attached;
    private final Runnable advancedRefresh = new Runnable() {
        @Override public void run() {
            if (!attached || getWindowVisibility() != VISIBLE) return;
            if (mode == Mode.ADVANCED && getVisibility() == VISIBLE) FrameRating.this.run();
            postDelayed(this, 500);
        }
    };
    private long lastTime = 0;
    private short frameCount = 0;
    private float lastFPS = 0;
    private final LinearLayout fpsPanel;
    private final LinearLayout gpuPanel;
    private final LinearLayout ramPanel;
    private final LinearLayout cpuPanel;
    private Mode mode = Mode.SIMPLE;
    private ActivityManager activityManager;
    private ActivityManager.MemoryInfo memoryInfo;
    private String cpuInfo = null;
    private byte tick = 0;

    public FrameRating(Context context) {
        this(context, null);
    }

    public FrameRating(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FrameRating(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        View view = LayoutInflater.from(context).inflate(R.layout.frame_rating, this, false);
        fpsPanel = view.findViewById(R.id.LLFPSPanel);
        gpuPanel = view.findViewById(R.id.LLGPUPanel);
        ramPanel = view.findViewById(R.id.LLRAMPanel);
        cpuPanel = view.findViewById(R.id.LLCPUPanel);
        addView(view);
        timingPanel = new LinearLayout(context);
        timingPanel.setOrientation(LinearLayout.VERTICAL);
        timingText = new TextView(context);
        timingText.setTextColor(0xffffffff);
        timingText.setTextSize(11);
        timingText.setMaxLines(3);
        timingPanel.addView(timingText);
        graph = new FrameTimeGraph(context);
        float density = getResources().getDisplayMetrics().density;
        timingPanel.addView(graph, new LinearLayout.LayoutParams((int)(280 * density), (int)(64 * density)));
        TextView legend = new TextView(context);
        legend.setText(R.string.hud_timing_legend);
        legend.setTextSize(9);
        legend.setTextColor(0xffcccccc);
        timingPanel.addView(legend);
        ((LinearLayout)fpsPanel.getParent()).addView(timingPanel);
        setupPanels();
    }

    private void setupPanels() {
        timingPanel.setVisibility(mode == Mode.ADVANCED ? VISIBLE : GONE);
        ((TextView)ramPanel.getChildAt(0)).setText(mode == Mode.ADVANCED ? getContext().getString(R.string.hud_system_ram) : "RAM:");
        ((TextView)cpuPanel.getChildAt(0)).setText(mode == Mode.ADVANCED ? getContext().getString(R.string.hud_cpu_clock) : "CPU:");
        switch (mode) {
            case DISABLED:
                fpsPanel.setVisibility(GONE);
                gpuPanel.setVisibility(GONE);
                ramPanel.setVisibility(GONE);
                cpuPanel.setVisibility(GONE);
                
                activityManager = null;
                memoryInfo = null;
                break;
            case SIMPLE:
                fpsPanel.setVisibility(VISIBLE);
                gpuPanel.setVisibility(GONE);
                ramPanel.setVisibility(GONE);
                cpuPanel.setVisibility(GONE);

                activityManager = null;
                memoryInfo = null;
                break;
            case FULL:
            case ADVANCED:
                fpsPanel.setVisibility(VISIBLE);
                gpuPanel.setVisibility(VISIBLE);
                ramPanel.setVisibility(VISIBLE);
                cpuPanel.setVisibility(VISIBLE);

                Context context = getContext();
                activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                memoryInfo = new ActivityManager.MemoryInfo();
                break;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (mode == Mode.ADVANCED) postDelayed(advancedRefresh, 500);
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        removeCallbacks(advancedRefresh);
        if (attached && visibility == VISIBLE && mode == Mode.ADVANCED)
            postDelayed(advancedRefresh, 500);
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        removeCallbacks(advancedRefresh);
        removeCallbacks(this);
        super.onDetachedFromWindow();
    }

    public boolean hasDeviceInfo() {
        return mode == Mode.FULL || mode == Mode.ADVANCED;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        setupPanels();
        removeCallbacks(advancedRefresh);
        if (attached && mode == Mode.ADVANCED) postDelayed(advancedRefresh, 500);
    }

    public void setGPUInfo(String gpuInfo) {
        post(() -> ((TextView)gpuPanel.getChildAt(1)).setText(gpuInfo));
    }

    public void reset() {
        timings.reset();
        frameCount = 0;
        lastTime = SystemClock.elapsedRealtime();
        lastFPS = 0;
        tick = 2;
        if (mode == Mode.ADVANCED) post(() -> setVisibility(VISIBLE));
    }

    public void update() {
        if (mode == Mode.ADVANCED) {
            timings.record(SystemClock.elapsedRealtimeNanos());
            return;
        }
        long time = SystemClock.elapsedRealtime();
        if (time >= lastTime + 500) {
            lastFPS = ((float)(frameCount * 1000) / (time - lastTime));
            post(this);
            lastTime = time;
            frameCount = 0;
        }

        frameCount++;
    }

    @Override
    public void run() {
        if (getVisibility() == GONE) setVisibility(View.VISIBLE);
        if (mode == Mode.ADVANCED) {
            FrameTiming.Snapshot snapshot = timings.snapshot(SystemClock.elapsedRealtimeNanos());
            ((TextView)fpsPanel.getChildAt(1)).setText(String.format(Locale.ENGLISH, "%.1f", snapshot.fps));
            timingText.setText(snapshot.milliseconds.length == 0 ? getContext().getString(R.string.hud_waiting_frames) :
                    getContext().getString(R.string.hud_timing_values, snapshot.meanMs, snapshot.p99Ms, snapshot.peakMs));
            graph.setSamples(snapshot.milliseconds);
        }
        else ((TextView)fpsPanel.getChildAt(1)).setText(String.format(Locale.ENGLISH, "%.1f", lastFPS));

        if (hasDeviceInfo() && ++tick >= 2) {
            tick = 0;
            activityManager.getMemoryInfo(memoryInfo);
            long usedMem = memoryInfo.totalMem - memoryInfo.availMem;
            String ramText = StringUtils.formatBytes(usedMem, false)+"/"+StringUtils.formatBytes(memoryInfo.totalMem);
            ((TextView)ramPanel.getChildAt(1)).setText(ramText);

            if (cpuInfo == null) {
                cpuInfo = "Box64 v"+ Box64Utils.extractBinVersion(cpuPanel.getContext());
            }

            short[] clockSpeeds = CPUStatus.getCurrentClockSpeeds();
            int maxClockSpeed = 0;
            for (short clockSpeed : clockSpeeds) maxClockSpeed = Math.max(maxClockSpeed, clockSpeed);
            ((TextView)cpuPanel.getChildAt(1)).setText(CPUStatus.formatClockSpeed(maxClockSpeed)+" | "+cpuInfo);
        }
    }
}