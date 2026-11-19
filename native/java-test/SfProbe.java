import com.chuying.engine.WChessNativeBridge;
public class SfProbe {
  public static void main(String[] a) throws Exception {
    WChessNativeBridge b = new WChessNativeBridge();
    b.load(a[0]);
    System.out.println("start rc=" + b.start(new String[0]));
    b.send("uci");
    while (true) { String l = b.read(2000); if (l == null) break; System.out.println("< " + l); if (l.startsWith("uciok")) break; }
    b.send("isready");
    long t0=System.currentTimeMillis();
    while (System.currentTimeMillis()-t0 < 60000) { String l = b.read(1000); if (l!=null) System.out.println("< " + l); if (l!=null && l.startsWith("readyok")) break; }
    System.out.println("ready in " + (System.currentTimeMillis()-t0) + " ms");
    b.send("position startpos moves e2e4");
    b.send("go depth 4");
    t0=System.currentTimeMillis();
    while (System.currentTimeMillis()-t0 < 60000) { String l = b.read(1000); if (l!=null) System.out.println("< " + l); if (l!=null && l.startsWith("bestmove")) { System.out.println("BESTMOVE OK in " + (System.currentTimeMillis()-t0) + " ms"); break; } }
    b.send("quit");
    Thread.sleep(500);
    b.stop();
    System.exit(0);
  }
}
