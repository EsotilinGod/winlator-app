import com.winlator.core.ReportFormatter;

import java.util.LinkedHashMap;

public class ReportFormatterTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        LinkedHashMap<String, String> info = new LinkedHashMap<>();
        info.put("Device", "Xiaomi 14T");
        info.put("Multi", "a\nb");
        info.put("Missing", null);
        String text = ReportFormatter.format(info);
        check(text.startsWith("=== Winlator device report ===\n"), "header");
        check(text.contains("Device: Xiaomi 14T\n"), "plain value");
        check(text.contains("Multi: a b\n"), "newline in value is flattened");
        check(text.contains("Missing: unknown\n"), "null value");
        check(text.endsWith("=== End of report ===\n"), "footer");
        check(ReportFormatter.format(new LinkedHashMap<String, String>()).split("\n").length == 2, "empty report");
        System.out.println("PASS: device report formatting (5 checks)");
    }
}
