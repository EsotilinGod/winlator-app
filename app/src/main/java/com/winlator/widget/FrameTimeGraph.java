package com.winlator.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import com.winlator.core.FrameTiming;

/** Repainted at the HUD sampling rate, never schedules its own animation. */
public final class FrameTimeGraph extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float[] samples = new float[0];
    private final float density;

    public FrameTimeGraph(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setSamples(float[] values) {
        samples = values;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0x88000000);
        float width = getWidth(), height = getHeight();
        // Fixed 0-100 ms scale keeps spikes comparable between refreshes.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(0xff667788);
        for (float ms : new float[]{16.67f, 33.33f}) {
            float y = height * (1 - ms / 100);
            canvas.drawLine(0, y, width, y, paint);
        }
        path.reset();
        for (int i = 0; i < samples.length; i++) {
            float x = (FrameTiming.CAPACITY - samples.length + i) * width / (FrameTiming.CAPACITY - 1);
            float y = Math.max(density, height * (1 - Math.min(samples[i], 100) / 100));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        paint.setColor(0xff66ffcc);
        paint.setStrokeWidth(1.5f * density);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.LTGRAY);
        paint.setTextSize(9 * density);
        canvas.drawText("100 ms", 3 * density, 10 * density, paint);
        canvas.drawText("33.3", 3 * density, height * (1 - 33.33f / 100) - density, paint);
        canvas.drawText("16.7", 3 * density, height * (1 - 16.67f / 100) - density, paint);
    }
}
