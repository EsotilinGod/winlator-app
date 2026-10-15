package com.winlator.core;

/** Per-launch overrides; never changes the container registry or its DLL files. */
public final class DllOverrides {
    private DllOverrides() {}

    public static String preferNativeDinput8(String overrides) {
        StringBuilder result = new StringBuilder();
        if (overrides != null) {
            for (String entry : overrides.split(";")) {
                int equals = entry.indexOf('=');
                if (equals < 0) {
                    if (!entry.trim().isEmpty()) result.append(entry).append(';');
                    continue;
                }
                StringBuilder names = new StringBuilder();
                for (String name : entry.substring(0, equals).split(",")) {
                    String normalized = name.trim();
                    if (normalized.startsWith("*")) normalized = normalized.substring(1);
                    if (normalized.equalsIgnoreCase("dinput8") || normalized.equalsIgnoreCase("dinput8.dll")) continue;
                    if (names.length() > 0) names.append(',');
                    names.append(name);
                }
                if (names.length() > 0) result.append(names).append(entry.substring(equals)).append(';');
            }
        }
        return result.append("dinput8=n,b").toString();
    }
}
