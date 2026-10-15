package com.winlator.core;

import java.util.Locale;

/** Shared policy for the settings UI and the Vortek launcher. */
public final class GraphicsCompatibility {
    private GraphicsCompatibility() {}

    public static boolean isMaliRenderer(String renderer) {
        if (renderer == null) return false;
        String name = renderer.toLowerCase(Locale.ROOT);
        return name.contains("mali") || name.contains("immortalis");
    }

    /** null preserves Vortek's default extension handling. */
    public static String[] parseExposedExtensions(String value) {
        // A pipe separates Vulkan and OpenGL configurations, not extensions.
        // Preserve the dialog's existing fallback for ambiguous legacy values.
        if (value == null || value.isEmpty() || value.equals("all") || value.contains("|")) return null;
        return value.split(":");
    }
}
