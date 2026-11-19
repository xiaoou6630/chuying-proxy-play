import com.chuying.engine.CChessNativeBridge;
public class PkfProbe2 {
  public static void main(String[] a) throws Exception {
    CChessNativeBridge b = new CChessNativeBridge();
    b.load(a[0]);
    b.start(new String[0]);
    b.send("uci");
    long t0=System.currentTimeMillis();
    while (System.currentTimeMillis()-t0<15000){ String l=b.read(500); if(l!=null&&l.startsWith("uciok"))break; }
    System.out.println("--- setoption EvalFile = " + a[1]);
    b.send("setoption name EvalFile value " + a[1]);
    b.send("isready");
    t0=System.currentTimeMillis();
    while (System.currentTimeMillis()-t0<60000){
      String l=b.read(1000);
      if(l!=null){ System.out.println("< "+l); if(l.startsWith("readyok"))break; }
    }
    System.out.println("--- isready done, now go");
    b.send("position startpos moves h2e2");
    b.send("go depth 4");
    t0=System.currentTimeMillis();
    while (System.currentTimeMillis()-t0<60000){
      String l=b.read(1000);
      if(l!=null){ System.out.println("< "+l); if(l.startsWith("bestmove")){System.out.println("BESTMOVE OK");break;} }
    }
    System.out.println("--- done, alive=" + b.isAlive());
    System.exit(0);
  }
}
