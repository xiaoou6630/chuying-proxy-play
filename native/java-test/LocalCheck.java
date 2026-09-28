import com.chuying.engine.NativeEngineBridge;

/** Local Windows check: load DLL, optionally point EvalFile at a net, verify evaluation. */
public class LocalCheck {
    public static void main(String[] args) throws Exception {
        String lib = args[0];
        String net = args.length > 1 && !args[1].equals("-") ? args[1] : null;
        int depth = args.length > 2 ? Integer.parseInt(args[2]) : 6;

        NativeEngineBridge e = new NativeEngineBridge();
        e.load(lib);
        log("loaded " + lib);
        log("start rc=" + e.start(new String[0]));

        e.send("uci");
        log("sent uci, waiting uciok...");
        boolean uciok = false;
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < 30000) {
            String s = e.read(500);
            if (s == null) continue;
            log("< " + s);
            if (s.equals("uciok")) { uciok = true; break; }
        }
        log("uciok=" + uciok);
        if (!uciok) { log("FAIL: no uciok"); System.exit(1); }

        if (net != null) {
            e.send("setoption name EvalFile value " + net);
            log("set EvalFile=" + net);
        }
        e.send("isready");
        boolean ready = false;
        t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < 120000) {
            String s = e.read(500);
            if (s == null) continue;
            log("< " + s);
            if (s.equals("readyok")) { ready = true; break; }
        }
        log("readyok=" + ready);
        if (!ready) { log("FAIL: no readyok (net rejected?)"); System.exit(1); }

        e.send("position startpos moves e2e4 e7e5 g1f3");
        log("sending go depth " + depth);
        System.out.flush();
        e.send("go depth " + depth);
        log("go sent, reading...");
        System.out.flush();
        boolean best = false, scored = false;
        t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < 60000) {
            String s = e.read(500);
            if (s == null) continue;
            log("< " + s);
            if (s.startsWith("info") && s.contains("score")) scored = true;
            if (s.startsWith("bestmove")) { best = true; break; }
        }
        log("info-with-score=" + scored + " bestmove=" + best);
        log(best && scored ? "NET_OK" : "NET_PROBLEM");
        e.send("quit");
        Thread.sleep(500);
        e.stop();
        System.exit(best && scored ? 0 : 1);
    }

    static void log(String s) {
        System.out.println("[lc] " + s);
        System.out.flush();
    }
}
