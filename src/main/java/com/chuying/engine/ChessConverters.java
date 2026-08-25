package com.chuying.engine;

import com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position;

/**
 * 引擎走法字符串 <-> TLM 内部 move 的换算。
 * <ul>
 *   <li>中国象棋（xqwlight）: pikafish UCI 坐标 —— file a-i，rank 0-9（0 为红方底线），内部 sq = file+3 + (rank+3)&lt;&lt;4</li>
 *   <li>国际象棋: TLM 自带的 PARSE_MOVE / MOVE_STR 就是 "e2e4" 坐标格式，直接兼容 Stockfish UCI</li>
 * </ul>
 */
public final class ChessConverters {
    private ChessConverters() {
    }

    /** 中象：内部 move -> pikafish UCI 字符串 */
    public static String cchessMoveToUci(int move) {
        int sqSrc = Position.SRC(move);
        int sqDst = Position.DST(move);
        int f1 = Position.FILE_X(sqSrc) - Position.FILE_LEFT;
        int r1 = Position.RANK_Y(sqSrc) - Position.RANK_TOP;
        int f2 = Position.FILE_X(sqDst) - Position.FILE_LEFT;
        int r2 = Position.RANK_Y(sqDst) - Position.RANK_TOP;
        return "" + (char) ('a' + f1) + (9 - r1) + (char) ('a' + f2) + (9 - r2);
    }

    /** 中象：pikafish UCI 字符串 -> 内部 move，解析失败返回 0 */
    public static int cchessUciToMove(String uci) {
        if (uci == null || uci.length() < 4) {
            return 0;
        }
        int f1 = uci.charAt(0) - 'a';
        int r1 = uci.charAt(1) - '0';
        int f2 = uci.charAt(2) - 'a';
        int r2 = uci.charAt(3) - '0';
        if (f1 < 0 || f1 > 8 || r1 < 0 || r1 > 9 || f2 < 0 || f2 > 8 || r2 < 0 || r2 > 9) {
            return 0;
        }
        int sqSrc = Position.COORD_XY(Position.FILE_LEFT + f1, Position.RANK_TOP + (9 - r1));
        int sqDst = Position.COORD_XY(Position.FILE_LEFT + f2, Position.RANK_TOP + (9 - r2));
        return Position.MOVE(sqSrc, sqDst);
    }

    /** 国象：Stockfish UCI 字符串 -> 内部 move（TLM 自带解析） */
    public static int wchessUciToMove(String uci) {
        return com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.PARSE_MOVE(uci == null ? "" : uci);
    }
}
