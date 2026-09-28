import com.chuying.engine.NativeEngineBridge;

/** Local Windows check: does the DLL really embed NNUE and evaluate? */
public class LocalCheck {
    public static void main(String[] args) throws Exception {
        NativeEngineBridge e = new NativeEngineBridge();
        e.load(args[0]);
        int rc = e.start(new String[0]);
        System.out.println("start rc=" + rc);

        e.send("uci");
        String s;
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < 30000) {
            s = e.read(500);
            if (s == null) continue;
            System.out.println("< " + s);
            if (s.equals("uciok")) break;
        }
        e.send("position startpos moves e2e4 e7e5");
        e.send("go depth 1");
        boolean gotBestmove = false;
        t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < 30000) {
            s = e.read(500);
            if (s == null) continue;
            System.out.println("< " + s);
            if (s.startsWith("bestmove")) { gotBestmove = true; break; }
        }
        System.out.println(gotBestmove ? "EVAL_OK (NNUE embedded and working)" : "EVAL_FAIL (no bestmove - likely missing NNUE)");
        e.send("quit");
        Thread.sleep(1000);
        e.stop();
        System.exit(gotBestmove ? 0 : 1);
    }
}
