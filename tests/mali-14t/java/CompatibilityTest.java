import com.winlator.core.GraphicsCompatibility;
import com.winlator.core.KeyValueSet;
import com.winlator.container.GraphicsDrivers;
import java.util.Arrays;

public class CompatibilityTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(GraphicsCompatibility.isMaliRenderer("Mali-G615 MC6"), "Xiaomi 14T Mali");
        check(GraphicsCompatibility.isMaliRenderer("mAlI-G715"), "Case insensitive Mali");
        check(GraphicsCompatibility.isMaliRenderer("Immortalis-G720"), "ARM Immortalis");
        check(!GraphicsCompatibility.isMaliRenderer("Adreno (TM) 730"), "Adreno unchanged");
        check(!GraphicsCompatibility.isMaliRenderer("ANGLE (Qualcomm Adreno 750)"), "ANGLE Adreno unchanged");
        check(!GraphicsCompatibility.isMaliRenderer(""), "Unknown is not Mali");
        check(!GraphicsCompatibility.isMaliRenderer(null), "Null is not Mali");

        String first = "VK_KHR_swapchain";
        String second = "VK_EXT_memory_budget";
        check(Arrays.equals(GraphicsCompatibility.parseExposedExtensions(first), new String[]{first}), "One extension stays one");
        check(Arrays.equals(GraphicsCompatibility.parseExposedExtensions(first + ":" + second), new String[]{first, second}), "Colon-separated extensions reach native individually");
        for (String defaults : new String[]{null, "", "all", first + "|" + second}) {
            check(GraphicsCompatibility.parseExposedExtensions(defaults) == null, "Default / ambiguous legacy handling");
        }

        for (String vk : new String[]{"", "imageCacheSize=256", "exposedDeviceExtensions=" + first, "exposedDeviceExtensions=" + first + ":" + second}) {
            for (String gl : new String[]{"", "glVersion=3.1"}) {
                String serialized = GraphicsDrivers.serializeConfigs(new KeyValueSet[]{new KeyValueSet(vk), new KeyValueSet(gl)});
                check(serialized.equals(vk + "|" + gl), "Both config slots are preserved");
                KeyValueSet[] decoded = GraphicsDrivers.parseConfigs("vortek,virgl", serialized);
                check(decoded[0].toString().equals(vk), "Vulkan round trip");
                check(decoded[1].toString().equals(gl), "OpenGL round trip");
                if (vk.startsWith("exposedDeviceExtensions=")) {
                    check(Arrays.equals(GraphicsCompatibility.parseExposedExtensions(decoded[0].get("exposedDeviceExtensions")), GraphicsCompatibility.parseExposedExtensions(vk.substring(vk.indexOf('=') + 1))), "Extension selection survives combined config round trip");
                }
            }
        }
        check(GraphicsDrivers.parseConfigs("vortek", "imageCacheSize=512")[0].getInt("imageCacheSize") == 512, "Legacy standalone Vulkan config");
        check(GraphicsDrivers.parseConfigs("virgl", "glVersion=3.1")[1].get("glVersion").equals("3.1"), "Legacy standalone OpenGL config");
        System.out.println("PASS: " + checks + " checks (JVM logic only; Android/JNI not executed)");
    }
}
