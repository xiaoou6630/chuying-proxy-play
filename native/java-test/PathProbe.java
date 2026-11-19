public class PathProbe {
  static boolean isAscii(String s){ for(int i=0;i<s.length();i++) if(s.charAt(i)>0x7F) return false; return true; }
  public static void main(String[] a) {
    String gameDir = a[0];
    System.out.println("gameDir = " + gameDir);
    System.out.println("contains non-ASCII = " + !isAscii(gameDir));
    String tmp = System.getProperty("java.io.tmpdir", ".");
    String safe = isAscii(tmp) ? tmp : "C:\\";
    System.out.println("java.io.tmpdir = " + tmp + "  (ascii=" + isAscii(tmp) + ")");
    System.out.println("=> engines would go to: " + new java.io.File(new java.io.File(safe, "chuying-engines"), "windows-x86_64"));
  }
}
