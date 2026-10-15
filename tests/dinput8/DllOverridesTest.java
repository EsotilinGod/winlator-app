import com.winlator.core.DllOverrides;
public class DllOverridesTest {
    private static void check(String input, String expected) {
        String actual = DllOverrides.preferNativeDinput8(input);
        if (!expected.equals(actual)) throw new AssertionError(input + " -> " + actual);
        if (!actual.equals(DllOverrides.preferNativeDinput8(actual))) throw new AssertionError("Not idempotent");
    }
    public static void main(String[] args) {
        check(null, "dinput8=n,b");
        check("", "dinput8=n,b");
        check("dinput8=b", "dinput8=n,b");
        check("dinput8=", "dinput8=n,b");
        check("dxgi=n;dinput8,dinput=b;winhttp=n,b", "dxgi=n;dinput=b;winhttp=n,b;dinput8=n,b");
        check("*DINPUT8.DLL=b;dinput8=n;d3d9=n", "d3d9=n;dinput8=n,b");
        check("dinput=b;foo_dinput8=n", "dinput=b;foo_dinput8=n;dinput8=n,b");
        check("; dinput8 = b ;;", "dinput8=n,b");
        System.out.println("8 override cases and idempotence checks passed");
    }
}
