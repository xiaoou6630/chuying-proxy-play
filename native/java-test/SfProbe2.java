import com.chuying.engine.WChessNativeBridge;
public class SfProbe2 {
  static WChessNativeBridge b;
  static void drain(String tag) throws Exception {
    long t0=System.currentTimeMillis();
    while (System.currentTimeMillis()-t0 < 8000) {
      String l = b.read(500);
      if (l != null) System.out.println("[" + tag + "] < " + l);
      if (l != null && (l.startsWith("readyok") || l.startsWith("bestmove"))) return;
    }
    System.out.println("[" + tag + "] TIMEOUT after 8s");
  }
  public static void main(String[] a) throws Exception {
    b = new WChessNativeBridge();
    b.load(a[0]);
    b.start(new String[0]);
    b.send("uci");
    drain("uci");
    System.out.println("--- setoption EvalFile (big) ---");
    b.send("setoption name EvalFile value " + a[1]);
    b.send("isready");
    drain("big");
    System.out.println("--- setoption EvalFileSmall ---");
    b.send("setoption name EvalFileSmall value " + a[2]);
    b.send("isready");
    drain("small");
    b.send("position startpos moves e2e4");
    b.send("go depth 4");
    drain("go");
    System.exit(0);
  }
}
