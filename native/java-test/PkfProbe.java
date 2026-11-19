import com.chuying.engine.CChessNativeBridge;
public class PkfProbe {
  public static void main(String[] a) throws Exception {
    CChessNativeBridge b = new CChessNativeBridge();
    b.load(a[0]);
    System.out.println("start rc=" + b.start(new String[0]));
    b.send("uci");
    long t0=System.currentTimeMillis();
    while (System.currentTimeMillis()-t0<15000){ String l=b.read(500); if(l!=null){System.out.println("< "+l); if(l.startsWith("uciok"))break;} }
    b.send("setoption name EvalFile value " + a[1]);
    b.send("isready");
    t0=System.currentTimeMillis();
    while (System.currentTimeMillis()-t0<30000){ String l=b.read(500); if(l!=null){System.out.println("< "+l); if(l.startsWith("readyok"))break;} }
    b.send("position startpos moves h2e2");
    b.send("go depth 4");
    t0=System.currentTimeMillis();
    while (System.currentTimeMillis()-t0<30000){ String l=b.read(500); if(l!=null){System.out.println("< "+l); if(l.startsWith("bestmove")){System.out.println("BESTMOVE OK");break;}} }
    System.exit(0);
  }
}
