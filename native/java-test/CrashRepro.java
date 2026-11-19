import com.chuying.engine.GoNativeBridge;
public class CrashRepro {
  public static void main(String[] a) throws Exception {
    GoNativeBridge go = new GoNativeBridge();
    go.load(a[0]);
    int rc = go.nativeInit(16, 10);   // level 10 = 崩溃时的强度
    System.out.println("init rc=" + rc);
    rc = go.nativeNewGame(15, 6.5f);
    System.out.println("newgame rc=" + rc);

    // 崩溃日志里的黑棋（SGF AB）
    String[] black = {"ba","ca","ja","ka","la","ma","na","oa","ab","cb","db","gb","jb","kb","ob","ac","bc","cc","dc","ec","gc","hc","oc","ad","bd","cd","ed","fd","gd","hd","id","jd","kd","ld","md","od","ae","ce","de","fe","ge","oe","df","ef","ff","gf","of","cg","dg","fg","og","fh","gh","hh","mh","nh","oh","ci","ji","ki","li","mi","ni","oi","aj","bj","cj","jj","mj","oj","ck","jk","mk","nk","cl","il","jl","ml","ol","bm","cm","em","im","jm","lm","mm","nm","bn","en","fn","gn","on","bo","co","do","eo","go","ho","io","jo","ko","lo","mo","no"};
    // 白棋（SGF AW）
    String[] white = {"da","ea","fa","ga","ha","ia","eb","fb","hb","ib","lb","mb","nb","fc","ic","jc","kc","lc","mc","nc","nd","be","he","ie","je","ke","le","me","ne","af","bf","cf","hf","if","jf","kf","lf","mf","nf","bg","eg","gg","hg","ig","jg","kg","lg","mg","ng","bh","ch","dh","eh","ih","jh","kh","lh","ai","bi","di","ei","fi","gi","hi","ii","dj","ej","fj","gj","hj","ij","kj","lj","ak","bk","dk","ek","fk","gk","hk","ik","kk","lk","bl","dl","el","fl","gl","hl","kl","ll","am","dm","fm","gm","hm","km","an","cn","dn","hn","in","jn","kn","ln","mn","nn"};

    byte[] board = new byte[225];
    for (String s : black) { int x = s.charAt(0)-'a', y = s.charAt(1)-'a'; if (x<15&&y<15) board[x*15+y]=1; }
    for (String s : white) { int x = s.charAt(0)-'a', y = s.charAt(1)-'a'; if (x<15&&y<15) board[x*15+y]=2; }
    int b=0,w=0,e=0; for (byte v : board) { if(v==1)b++; else if(v==2)w++; else e++; }
    System.out.println("棋面: 黑="+b+" 白="+w+" 空="+e);

    int placed = go.nativeSetPosition(board, 11, 0);   // 崩溃时 ko=(11,0)
    System.out.println("placed=" + placed);

    long t0 = System.currentTimeMillis();
    int mv = go.nativeGenmove(1);   // 黑方（我方）
    long ms = System.currentTimeMillis()-t0;
    System.out.println("genmove packed=" + mv + " in " + ms + " ms");
    if (mv == -3) System.out.println(">>> 断言被 longjmp 兜住（返回 ERROR），进程存活 ✓");
    else if (mv == -1) System.out.println(">>> 引擎选择 pass");
    else System.out.println(">>> 引擎给出着法 (" + (mv/100) + "," + (mv%100) + ")");
    System.out.println("JVM 仍然存活 ✓");
    System.exit(0);
  }
}
