import gg.vape.fabric.InProcessBytecode;
public class BridgeSmoke {
    public static int value() { return 123; }
    public static void main(String[] args) throws Exception {
        InProcessBytecode.load();
        byte[] bytes = InProcessBytecode.capture(BridgeSmoke.class);
        if (bytes == null || bytes.length < 10 || bytes[0] != (byte)0xca) throw new AssertionError("capture failed");
        int result = InProcessBytecode.redefine(BridgeSmoke.class, bytes);
        if (result != 0 || value() != 123) throw new AssertionError("redefine failed: " + result);
        System.out.println("PASS: packaged bridge loads, captures and redefines a test class on Java " + Runtime.version());
    }
}
