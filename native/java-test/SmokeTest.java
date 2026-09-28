import com.chuying.engine.NativeEngineBridge;

/**
 * CI smoke test: loads the native engine library in-process (no OS process
 * spawned) and runs a minimal protocol handshake.
 *
 * Usage: java SmokeTest <stockfish|pikafish|rapfi> <path-to-library>
 * Exit 0 = PASS, 1 = FAIL.
 */
public class SmokeTest {

    public static void main(String[] args) throws Exception {
        String engine = args[0];
        String libPath = args[1];

        NativeEngineBridge bridge = new NativeEngineBridge();
        bridge.load(libPath);
        System.out.println("[smoke] library loaded: " + libPath);

        int rc = bridge.start(new String[0]);
        System.out.println("[smoke] start rc=" + rc);
        if (rc != 0) {
            System.out.println("[smoke] " + engine + " -> FAIL (start)");
            System.exit(1);
        }

        boolean ok;
        if ("rapfi".equals(engine)) {
            // pbrain protocol: BEGIN then expect some reply. Model file is not
            // shipped on CI, so this is lenient: only a hard JVM crash fails.
            bridge.send("BEGIN");
            String reply = bridge.read(15000);
            System.out.println("[smoke] rapfi reply: " + reply);
            bridge.stop();
            ok = true;
        } else {
            bridge.send("uci");
            ok = expect(bridge, "uciok", 60000);
            if (ok) {
                bridge.send("isready");
                ok = expect(bridge, "readyok", 60000);
            }
            bridge.send("quit");
            long t0 = System.currentTimeMillis();
            while (bridge.isAlive() && System.currentTimeMillis() - t0 < 10000) {
                Thread.sleep(100);
            }
            ok = ok && !bridge.isAlive();
            bridge.stop(); // join the worker thread before JVM exit
        }

        System.out.println("[smoke] " + engine + " -> " + (ok ? "PASS" : "FAIL"));
        System.exit(ok ? 0 : 1);
    }

    static boolean expect(NativeEngineBridge bridge, String want, int timeoutMs)
            throws InterruptedException {
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < timeoutMs) {
            String line = bridge.read(500);
            if (line != null) {
                System.out.println("[smoke] < " + line);
                if (want.equals(line)) return true;
            }
        }
        return false;
    }
}
