package com.winlator.core;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;

import androidx.preference.PreferenceManager;

/** Plain-text device summary placed at the top of exported logs. */
public final class DeviceReport {
    private DeviceReport() {}

    /** Never blocks: GPU values come from the preferences cache and read "unknown" until it is filled. */
    public static String build(Context context) {
        java.util.LinkedHashMap<String, String> info = new java.util.LinkedHashMap<>();
        info.put("Device", Build.MANUFACTURER+" "+Build.MODEL+" ("+Build.DEVICE+")");
        info.put("Board", Build.BOARD+" / "+Build.HARDWARE);
        if (Build.VERSION.SDK_INT >= 31) info.put("SoC", Build.SOC_MANUFACTURER+" "+Build.SOC_MODEL);
        info.put("Android", Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")");
        info.put("Security patch", Build.VERSION.SDK_INT >= 23 ? Build.VERSION.SECURITY_PATCH : "unknown");
        info.put("Build", Build.DISPLAY);
        info.put("ABIs", join(Build.SUPPORTED_ABIS));

        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            info.put("App", packageInfo.versionName+" (code "+packageInfo.versionCode+")");
        }
        catch (Exception e) {
            info.put("App", "unknown");
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        info.put("GL renderer", preferences.getString("gpu_renderer", "unknown"));
        info.put("GL vendor", preferences.getString("gpu_vendor", "unknown"));
        info.put("GL version", preferences.getString("gpu_version", "unknown"));

        ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            info.put("RAM total / available (MB)", (memoryInfo.totalMem >> 20)+" / "+(memoryInfo.availMem >> 20));
        }
        info.put("Page size (bytes)", String.valueOf(pageSize()));
        return ReportFormatter.format(info);
    }

    private static String join(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) sb.append(',');
            sb.append(value);
        }
        return sb.toString();
    }

    private static long pageSize() {
        try {
            return android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE);
        }
        catch (Throwable e) {
            return -1;
        }
    }
}
