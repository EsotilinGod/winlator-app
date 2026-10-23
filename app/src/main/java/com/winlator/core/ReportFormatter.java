package com.winlator.core;

import java.util.Map;

/** Pure text formatting for {@link DeviceReport}; kept free of Android types so it can be tested on the JVM. */
public final class ReportFormatter {
    private ReportFormatter() {}

    public static String format(Map<String, String> info) {
        StringBuilder sb = new StringBuilder("=== Winlator device report ===\n");
        for (Map.Entry<String, String> entry : info.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().replace('\n', ' ') : "unknown";
            sb.append(entry.getKey()).append(": ").append(value).append('\n');
        }
        return sb.append("=== End of report ===\n").toString();
    }
}
