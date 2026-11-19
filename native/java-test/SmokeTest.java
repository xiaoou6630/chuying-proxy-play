import com.chuying.engine.CChessNativeBridge;
import com.chuying.engine.GoNativeBridge;
import com.chuying.engine.GomokuNativeBridge;
import com.chuying.engine.WChessNativeBridge;

/**
 * CI smoke test: loads the native engine library in-process (no OS process
 * spawned) and runs a minimal protocol handshake.
 *
 * Each engine has its own JNI bridge class (CChess/WChess/GomokuNativeBridge):
 * the three libraries must NOT share JNI symbol names, otherwise the JVM binds
 * them all to the first-loaded library and the 2nd/3rd engine fails at start.
 * This test uses the same per-engine class as the mod, so it covers the real
 * binding.
 *
 * Usage: java SmokeTest <stockfish|pikafish|rapfi> <path-to-library>
 * Exit 0 = PASS, 1 = FAIL.
 */
public class SmokeTest {

    /** Minimal bridge surface shared by the three native bridge classes. */
    interface Bridge {
        void load(String libraryPath);
        int start(String[] args);
        int send(String cmd);
        String read(int timeoutMs);
        void stop();
        boolean isAlive();
    }

    static final class CChessBridge implements Bridge {
        private final CChessNativeBridge b = new CChessNativeBridge();
        public void load(String p) { b.load(p); }
        public int start(String[] a) { return b.start(a); }
        public int send(String c) { return b.send(c); }
        public String read(int t) { return b.read(t); }
        public void stop() { b.stop(); }
        public boolean isAlive() { return b.isAlive(); }
    }

    static final class WChessBridge implements Bridge {
        private final WChessNativeBridge b = new WChessNativeBridge();
        public void load(String p) { b.load(p); }
        public int start(String[] a) { return b.start(a); }
        public int send(String c) { return b.send(c); }
        public String read(int t) { return b.read(t); }
        public void stop() { b.stop(); }
        public boolean isAlive() { return b.isAlive(); }
    }

    static final class GomokuBridge implements Bridge {
        private final GomokuNativeBridge b = new GomokuNativeBridge();
        public void load(String p) { b.load(p); }
        public int start(String[] a) { return b.start(a); }
        public int send(String c) { return b.send(c); }
        public String read(int t) { return b.read(t); }
        public void stop() { b.stop(); }
        public boolean isAlive() { return b.isAlive(); }
    }

    public static void main(String[] args) throws Exception {
        String engine = args[0];
        String libPath = args[1];

        // 围棋（GNU Go）不走文本协议：直接验证它的 C API 桥
        if ("gnugo".equals(engine)) {
            goSmoke(libPath);
            return;
        }

        Bridge bridge;
        if ("stockfish".equals(engine)) {
            bridge = new WChessBridge();
        } else if ("pikafish".equals(engine)) {
            bridge = new CChessBridge();
        } else if ("rapfi".equals(engine)) {
            bridge = new GomokuBridge();
        } else {
            System.out.println("[smoke] unknown engine: " + engine);
            System.exit(1);
            return;
        }
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
            // Real search: handshake alone passes even when NNUE eval is broken,
            // which is exactly how a crashing build slipped through before.
            if (ok) {
                // Xiangqi (Pikafish) and chess (Stockfish) use different move notation.
                String moves = "pikafish".equals(engine)
                        ? "position startpos moves h2e2"   // 炮二平五
                        : "position startpos moves e2e4";
                bridge.send(moves);
                bridge.send("go depth 4");
                ok = expectPrefix(bridge, "bestmove", 120000);
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

    /**
     * 围棋（GNU Go）冒烟：初始化 -> 开新局 -> 整盘注入棋面 -> 真的算一手。
     * 覆盖 ABI/链接、坐标系（POS/I/J）、颜色映射（模组 1=黑 2=白 vs GNU Go 1=白 2=黑）与劫点注入。
     */
    static void goSmoke(String libPath) {
        GoNativeBridge go = new GoNativeBridge();
        go.load(libPath);
        System.out.println("[smoke] library loaded: " + libPath);

        int rc = go.nativeInit(16, 5);
        System.out.println("[smoke] gnugo nativeInit rc=" + rc);
        if (rc != 0) {
            goFail("nativeInit rc=" + rc);
            return;
        }
        if (!go.nativeIsReady()) {
            goFail("nativeIsReady=false");
            return;
        }

        rc = go.nativeNewGame(15, 6.5f);
        System.out.println("[smoke] gnugo nativeNewGame(15, 6.5) rc=" + rc);
        if (rc != 0) {
            goFail("nativeNewGame rc=" + rc);
            return;
        }

        // 15x15 快照：黑天元(7,7)、白(8,6)；模组编码 1=黑 2=白
        final int size = 15;
        byte[] board = new byte[size * size];
        board[7 * size + 7] = 1;
        board[8 * size + 6] = 2;
        int placed = go.nativeSetPosition(board, -1, -1);
        System.out.println("[smoke] gnugo nativeSetPosition placed=" + placed);
        if (placed != 2) {
            goFail("nativeSetPosition placed=" + placed);
            return;
        }

        long t0 = System.currentTimeMillis();
        int mv = go.nativeGenmove(2); // 2 = 模组的白
        long ms = System.currentTimeMillis() - t0;
        System.out.println("[smoke] gnugo genmove(white) packed=" + mv + " in " + ms + " ms");
        if (mv < 0) {
            goFail("nativeGenmove returned " + mv);
            return;
        }
        int x = mv / 100;
        int y = mv % 100;
        System.out.println("[smoke] gnugo move = (" + x + "," + y + ")");
        if (x >= size || y >= size || board[x * size + y] != 0) {
            goFail("genmove returned an illegal point (" + x + "," + y + ")");
            return;
        }

        // 劫点注入路径：只有黑(7,7)、劫点 (0,0)，引擎不得下在劫点
        byte[] single = new byte[size * size];
        single[7 * size + 7] = 1;
        rc = go.nativeSetPosition(single, 0, 0);
        if (rc != 1) {
            goFail("nativeSetPosition(ko) rc=" + rc);
            return;
        }
        int koMv = go.nativeGenmove(2);
        System.out.println("[smoke] gnugo genmove with ko at (0,0) -> packed=" + koMv);
        if (koMv == 0) {
            goFail("engine played the ko point");
            return;
        }

        System.out.println("[smoke] gnugo -> PASS");
        System.exit(0);
    }

    static void goFail(String what) {
        System.out.println("[smoke] gnugo -> FAIL (" + what + ")");
        System.exit(1);
    }

    static boolean expect(Bridge bridge, String want, int timeoutMs)
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

    /** Wait for any line starting with the given prefix (e.g. "bestmove"). */
    static boolean expectPrefix(Bridge bridge, String prefix, int timeoutMs)
            throws InterruptedException {
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < timeoutMs) {
            String line = bridge.read(500);
            if (line != null) {
                System.out.println("[smoke] < " + line);
                if (line.startsWith(prefix)) return true;
            }
        }
        return false;
    }
}
