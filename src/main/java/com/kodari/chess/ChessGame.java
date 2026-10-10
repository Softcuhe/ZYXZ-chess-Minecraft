package com.kodari.chess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ChessGame {
    public record Move(int from, int to, char promotion) {
    }

    public enum MoveQuality { BRILLIANT, GREAT, GOOD, MISTAKE, BLUNDER }

    public record MoveAnalysis(Move bestMove, MoveQuality quality) {
    }

    private static final int[] KNIGHT_X = {1, 2, 2, 1, -1, -2, -2, -1};
    private static final int[] KNIGHT_Y = {2, 1, -1, -2, -2, -1, 1, 2};
    private static final int[] KING_X = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] KING_Y = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[][] ROOK_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP_DIRECTIONS = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    private char[] board = new char[64];
    private boolean whiteToMove = true;
    private boolean whiteKingSide = true;
    private boolean whiteQueenSide = true;
    private boolean blackKingSide = true;
    private boolean blackQueenSide = true;
    private int enPassant = -1;
    private int halfmoveClock;
    private int fullmoveNumber = 1;
    private Move lastMove;
    private String result = "";
    private final Map<String, Integer> repetitions = new HashMap<>();
    private final List<String> sanMoves = new ArrayList<>();

    public ChessGame() {
        String start = "RNBQKBNRPPPPPPPP................................pppppppprnbqkbnr";
        board = start.toCharArray();
        repetitions.put(positionKey(), 1);
    }

    private ChessGame(ChessGame other) {
        this(other, true);
    }

    private ChessGame(ChessGame other, boolean copySanMoves) {
        board = other.board.clone();
        whiteToMove = other.whiteToMove;
        whiteKingSide = other.whiteKingSide;
        whiteQueenSide = other.whiteQueenSide;
        blackKingSide = other.blackKingSide;
        blackQueenSide = other.blackQueenSide;
        enPassant = other.enPassant;
        halfmoveClock = other.halfmoveClock;
        fullmoveNumber = other.fullmoveNumber;
        lastMove = other.lastMove;
        result = other.result;
        repetitions.putAll(other.repetitions);
        if (copySanMoves) sanMoves.addAll(other.sanMoves);
    }

    public ChessGame copy() {
        return new ChessGame(this);
    }

    public static ChessGame fromSnapshot(String snapshot) {
        String[] fields = snapshot.split(";", -1);
        if (fields.length < 10 || fields[0].length() != 64) {
            return new ChessGame();
        }
        ChessGame game = new ChessGame();
        game.board = fields[0].toCharArray();
        game.whiteToMove = fields[1].equals("w");
        int rights = Integer.parseInt(fields[2]);
        game.whiteKingSide = (rights & 1) != 0;
        game.whiteQueenSide = (rights & 2) != 0;
        game.blackKingSide = (rights & 4) != 0;
        game.blackQueenSide = (rights & 8) != 0;
        game.enPassant = Integer.parseInt(fields[3]);
        game.halfmoveClock = Integer.parseInt(fields[4]);
        game.fullmoveNumber = Integer.parseInt(fields[5]);
        int from = Integer.parseInt(fields[6]);
        int to = Integer.parseInt(fields[7]);
        char promotion = fields[8].equals("-") ? 0 : fields[8].charAt(0);
        if (from >= 0 && from < 64 && to >= 0 && to < 64) {
            game.lastMove = new Move(from, to, promotion);
        }
        game.result = fields[9];
        if (fields.length > 10 && !fields[10].isEmpty()) {
            try {
                String history = new String(Base64.getUrlDecoder().decode(fields[10]), StandardCharsets.UTF_8);
                if (!history.isEmpty()) game.sanMoves.addAll(List.of(history.split("\n", -1)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        game.repetitions.clear();
        game.repetitions.put(game.positionKey(), 1);
        return game;
    }

    public String snapshot() {
        int rights = (whiteKingSide ? 1 : 0) | (whiteQueenSide ? 2 : 0)
                | (blackKingSide ? 4 : 0) | (blackQueenSide ? 8 : 0);
        int from = lastMove == null ? -1 : lastMove.from();
        int to = lastMove == null ? -1 : lastMove.to();
        char promotion = lastMove == null || lastMove.promotion() == 0 ? '-' : lastMove.promotion();
        String history = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.join("\n", sanMoves).getBytes(StandardCharsets.UTF_8));
        return new String(board) + ";" + (whiteToMove ? "w" : "b") + ";" + rights + ";" + enPassant
                + ";" + halfmoveClock + ";" + fullmoveNumber + ";" + from + ";" + to + ";" + promotion + ";" + result
                + ";" + history;
    }

    public char pieceAt(int square) {
        return square < 0 || square >= 64 ? 0 : board[square];
    }

    public boolean isWhiteToMove() {
        return whiteToMove;
    }

    public Move lastMove() {
        return lastMove;
    }

    public boolean isGameOver() {
        return !result.isEmpty();
    }

    public String result() {
        return result;
    }

    public int moveCount() {
        return sanMoves.size();
    }

    public List<String> sanMoves() {
        return List.copyOf(sanMoves);
    }

    public String pgn(String whiteName, String blackName) {
        String pgnResult = switch (result) {
            case "WHITE" -> "1-0";
            case "BLACK" -> "0-1";
            case "DRAW" -> "1/2-1/2";
            default -> "*";
        };
        StringBuilder pgn = new StringBuilder()
                .append("[Event \"ZYXZ chess\"]\n")
                .append("[Site \"Minecraft\"]\n")
                .append("[Date \"").append(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))).append("\"]\n")
                .append("[Round \"-\"]\n")
                .append("[White \"").append(pgnHeader(whiteName)).append("\"]\n")
                .append("[Black \"").append(pgnHeader(blackName)).append("\"]\n")
                .append("[Result \"").append(pgnResult).append("\"]\n\n");
        for (int i = 0; i < sanMoves.size(); i++) {
            if ((i & 1) == 0) pgn.append(i / 2 + 1).append(". ");
            pgn.append(sanMoves.get(i)).append(' ');
        }
        return pgn.append(pgnResult).toString();
    }

    private String pgnHeader(String name) {
        return name.replace("\\", "\\\\").replace("\"", "\\\"").replace('\n', ' ').replace('\r', ' ');
    }

    public boolean inCheck(boolean white) {
        int king = -1;
        char kingPiece = white ? 'K' : 'k';
        for (int i = 0; i < 64; i++) {
            if (board[i] == kingPiece) {
                king = i;
                break;
            }
        }
        return king < 0 || isSquareAttacked(king, !white);
    }

    public List<Move> legalMoves() {
        return legalMoves(whiteToMove);
    }

    public List<Move> legalMovesFor(int from) {
        List<Move> moves = new ArrayList<>();
        for (Move move : legalMoves()) {
            if (move.from() == from) {
                moves.add(move);
            }
        }
        return moves;
    }

    public List<Move> legalMoves(boolean white) {
        if (isGameOver() && white == whiteToMove) {
            return List.of();
        }
        List<Move> legal = new ArrayList<>();
        for (Move move : pseudoMoves(white)) {
            ChessGame copy = new ChessGame(this, false);
            copy.applyUnchecked(move, white);
            if (!copy.inCheck(white)) {
                legal.add(move);
            }
        }
        return legal;
    }

    public boolean move(int from, int to, char promotion) {
        if (isGameOver()) {
            return false;
        }
        Move chosen = null;
        for (Move legal : legalMoves()) {
            if (legal.from() == from && legal.to() == to
                    && (legal.promotion() == promotion || legal.promotion() == 0
                    || (promotion == 0 && legal.promotion() == 'Q'))) {
                chosen = legal;
                break;
            }
        }
        if (chosen == null) {
            return false;
        }
        String notation = san(chosen);
        applyAndUpdate(chosen, whiteToMove);
        if (inCheck(whiteToMove)) notation += legalMoves().isEmpty() ? "#" : "+";
        sanMoves.add(notation);
        return true;
    }

    private String san(Move move) {
        char piece = board[move.from()];
        char kind = Character.toUpperCase(piece);
        int fromFile = move.from() % 8;
        int fromRank = move.from() / 8;
        int toFile = move.to() % 8;
        int toRank = move.to() / 8;
        boolean capture = board[move.to()] != '.' || (kind == 'P' && fromFile != toFile);
        if (kind == 'K' && Math.abs(toFile - fromFile) == 2) return toFile == 6 ? "O-O" : "O-O-O";
        StringBuilder notation = new StringBuilder();
        if (kind != 'P') {
            notation.append(kind);
            boolean sameFile = false;
            boolean sameRank = false;
            for (Move alternative : legalMoves()) {
                if (alternative.from() == move.from() || alternative.to() != move.to()
                        || board[alternative.from()] != piece) continue;
                sameFile |= alternative.from() % 8 == fromFile;
                sameRank |= alternative.from() / 8 == fromRank;
            }
            if (sameFile && sameRank) notation.append((char) ('a' + fromFile)).append(fromRank + 1);
            else if (sameFile) notation.append(fromRank + 1);
            else if (sameRank || hasAlternative(move)) notation.append((char) ('a' + fromFile));
        } else if (capture) {
            notation.append((char) ('a' + fromFile));
        }
        if (capture) notation.append('x');
        notation.append((char) ('a' + toFile)).append(toRank + 1);
        if (move.promotion() != 0) notation.append('=').append(move.promotion());
        return notation.toString();
    }

    private boolean hasAlternative(Move move) {
        char piece = board[move.from()];
        for (Move alternative : legalMoves()) {
            if (alternative.from() != move.from() && alternative.to() == move.to()
                    && board[alternative.from()] == piece) return true;
        }
        return false;
    }

    public void forceResult(String forcedResult) {
        if (result.isEmpty()) {
            result = forcedResult;
        }
    }

    public MoveAnalysis analyzeMove(int from, int to, char promotion, int depth) {
        List<Move> moves = orderedMoves();
        Move played = null;
        for (Move move : moves) {
            if (move.from() == from && move.to() == to
                    && (move.promotion() == promotion || move.promotion() == 0
                    || (promotion == 0 && move.promotion() == 'Q'))) {
                played = move;
                break;
            }
        }
        if (played == null) return null;
        boolean movingWhite = whiteToMove;
        int perspective = movingWhite ? 1 : -1;
        int searchDepth = Math.max(1, Math.min(2, depth));
        int bestScore = Integer.MIN_VALUE;
        int playedScore = Integer.MIN_VALUE;
        Move recommended = null;
        for (Move move : moves) {
            ChessGame next = new ChessGame(this, false);
            next.applySearchMove(move);
            int score = minimax(next, searchDepth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE, 1) * perspective;
            if (score > bestScore) {
                bestScore = score;
                recommended = move;
            }
            if (move.from() == played.from() && move.to() == played.to() && move.promotion() == played.promotion()) {
                playedScore = score;
            }
        }
        int loss = Math.max(0, bestScore - playedScore);
        ChessGame resultingPosition = new ChessGame(this, false);
        resultingPosition.applySearchMove(played);
        char movingPiece = board[played.from()];
        char capturedPiece = board[played.to()];
        boolean capture = capturedPiece != '.' || (Character.toUpperCase(movingPiece) == 'P'
                && played.from() % 8 != played.to() % 8);
        boolean givesCheck = resultingPosition.inCheck(resultingPosition.whiteToMove);
        boolean brilliantSacrifice = loss <= 15 && !capture && pieceValue(movingPiece) >= 320
                && resultingPosition.isSquareAttacked(played.to(), !movingWhite);
        MoveQuality quality;
        if (loss >= 300) quality = MoveQuality.BLUNDER;
        else if (loss >= 100) quality = MoveQuality.MISTAKE;
        else if (brilliantSacrifice) quality = MoveQuality.BRILLIANT;
        else if (loss <= 10 && (capture || givesCheck || played.promotion() != 0)) quality = MoveQuality.GREAT;
        else quality = MoveQuality.GOOD;
        return new MoveAnalysis(playedScore == bestScore ? played : recommended, quality);
    }

    public Move bestMove(int difficulty) {
        List<Move> moves = orderedMoves();
        if (moves.isEmpty()) {
            return null;
        }
        int depth = Math.max(1, Math.min(5, difficulty));
        boolean maximize = whiteToMove;
        int bestScore = maximize ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int alpha = Integer.MIN_VALUE + 1;
        int beta = Integer.MAX_VALUE;
        List<Move> best = new ArrayList<>();
        Random random = new Random();
        for (Move move : moves) {
            ChessGame next = new ChessGame(this, false);
            next.applySearchMove(move);
            int score = minimax(next, depth - 1, alpha, beta, 1);
            if ((maximize && score > bestScore) || (!maximize && score < bestScore)) {
                bestScore = score;
                best.clear();
                best.add(move);
            } else if (score == bestScore) {
                best.add(move);
            }
            if (maximize) alpha = Math.max(alpha, bestScore);
            else beta = Math.min(beta, bestScore);
        }
        return best.get(random.nextInt(best.size()));
    }

    private static int minimax(ChessGame game, int depth, int alpha, int beta, int ply) {
        if (game.result.equals("WHITE")) return 100000 - ply;
        if (game.result.equals("BLACK")) return -100000 + ply;
        if (game.result.equals("DRAW")) return 0;
        List<Move> moves = game.orderedMoves();
        if (moves.isEmpty()) {
            if (!game.inCheck(game.whiteToMove)) return 0;
            return game.whiteToMove ? -100000 + ply : 100000 - ply;
        }
        if (depth == 0) return game.evaluate();
        if (game.whiteToMove) {
            int value = Integer.MIN_VALUE + 1;
            for (Move move : moves) {
                ChessGame next = new ChessGame(game, false);
                next.applySearchMove(move);
                value = Math.max(value, minimax(next, depth - 1, alpha, beta, ply + 1));
                alpha = Math.max(alpha, value);
                if (beta <= alpha) break;
            }
            return value;
        }
        int value = Integer.MAX_VALUE;
        for (Move move : moves) {
            ChessGame next = new ChessGame(game, false);
            next.applySearchMove(move);
            value = Math.min(value, minimax(next, depth - 1, alpha, beta, ply + 1));
            beta = Math.min(beta, value);
            if (beta <= alpha) break;
        }
        return value;
    }

    private List<Move> orderedMoves() {
        List<Move> moves = legalMoves();
        moves.sort((first, second) -> Integer.compare(moveOrderScore(second), moveOrderScore(first)));
        return moves;
    }

    private int moveOrderScore(Move move) {
        char movingPiece = board[move.from()];
        char capturedPiece = board[move.to()];
        if (capturedPiece == '.' && Character.toUpperCase(movingPiece) == 'P' && move.to() == enPassant) {
            capturedPiece = whiteToMove ? 'p' : 'P';
        }
        int score = capturedPiece == '.' ? 0 : 10 * pieceValue(capturedPiece) - pieceValue(movingPiece);
        if (move.promotion() != 0) score += 8000 + pieceValue(move.promotion());
        return score;
    }

    private static int pieceValue(char piece) {
        return switch (Character.toUpperCase(piece)) {
            case 'P' -> 100;
            case 'N' -> 320;
            case 'B' -> 330;
            case 'R' -> 500;
            case 'Q' -> 900;
            default -> 0;
        };
    }

    private void applySearchMove(Move move) {
        boolean movingWhite = whiteToMove;
        applyUnchecked(move, movingWhite);
        int repetitionsOfPosition = repetitions.merge(positionKey(), 1, Integer::sum);
        if (halfmoveClock >= 100 || repetitionsOfPosition >= 3 || insufficientMaterial()) result = "DRAW";
    }

    private int evaluate() {
        int score = 0;
        for (int square = 0; square < 64; square++) {
            char piece = board[square];
            if (piece == '.') continue;
            boolean white = Character.isUpperCase(piece);
            int file = square % 8;
            int rank = square / 8;
            int center = 7 - (Math.abs(3 - file) + Math.abs(3 - rank));
            int material = switch (Character.toUpperCase(piece)) {
                case 'P' -> 100 + (white ? rank : 7 - rank) * 5;
                case 'N' -> 320 + center * 4;
                case 'B' -> 330 + center * 2;
                case 'R' -> 500;
                case 'Q' -> 900;
                default -> 0;
            };
            score += white ? material : -material;
        }
        return score;
    }

    private List<Move> pseudoMoves(boolean white) {
        List<Move> moves = new ArrayList<>();
        for (int from = 0; from < 64; from++) {
            char piece = board[from];
            if (piece == '.' || Character.isUpperCase(piece) != white) continue;
            int x = from % 8;
            int y = from / 8;
            switch (Character.toUpperCase(piece)) {
                case 'P' -> pawnMoves(moves, from, x, y, white);
                case 'N' -> {
                    for (int i = 0; i < 8; i++) addStep(moves, from, x + KNIGHT_X[i], y + KNIGHT_Y[i], white);
                }
                case 'B' -> addRays(moves, from, x, y, white, BISHOP_DIRECTIONS);
                case 'R' -> addRays(moves, from, x, y, white, ROOK_DIRECTIONS);
                case 'Q' -> {
                    addRays(moves, from, x, y, white, BISHOP_DIRECTIONS);
                    addRays(moves, from, x, y, white, ROOK_DIRECTIONS);
                }
                case 'K' -> {
                    for (int i = 0; i < 8; i++) addStep(moves, from, x + KING_X[i], y + KING_Y[i], white);
                    addCastleMoves(moves, white);
                }
                default -> {
                }
            }
        }
        return moves;
    }

    private void pawnMoves(List<Move> moves, int from, int x, int y, boolean white) {
        int direction = white ? 1 : -1;
        int nextY = y + direction;
        if (inside(x, nextY) && board[index(x, nextY)] == '.') {
            addPawnMove(moves, from, index(x, nextY), nextY, white);
            int startRank = white ? 1 : 6;
            int twoY = y + direction * 2;
            if (y == startRank && board[index(x, twoY)] == '.') {
                moves.add(new Move(from, index(x, twoY), (char) 0));
            }
        }
        for (int dx : new int[]{-1, 1}) {
            int targetX = x + dx;
            if (!inside(targetX, nextY)) continue;
            int to = index(targetX, nextY);
            char target = board[to];
            if (target != '.' && Character.isUpperCase(target) != white && Character.toUpperCase(target) != 'K') {
                addPawnMove(moves, from, to, nextY, white);
            } else if (to == enPassant) {
                moves.add(new Move(from, to, (char) 0));
            }
        }
    }

    private static void addPawnMove(List<Move> moves, int from, int to, int rank, boolean white) {
        if ((white && rank == 7) || (!white && rank == 0)) {
            char[] promotions = {'Q', 'R', 'B', 'N'};
            for (char promotion : promotions) moves.add(new Move(from, to, promotion));
        } else {
            moves.add(new Move(from, to, (char) 0));
        }
    }

    private void addCastleMoves(List<Move> moves, boolean white) {
        int rank = white ? 0 : 7;
        int kingSquare = index(4, rank);
        if (inCheck(white)) return;
        boolean kingSide = white ? whiteKingSide : blackKingSide;
        boolean queenSide = white ? whiteQueenSide : blackQueenSide;
        if (kingSide && board[index(5, rank)] == '.' && board[index(6, rank)] == '.'
                && board[index(7, rank)] == (white ? 'R' : 'r')
                && !isSquareAttacked(index(5, rank), !white) && !isSquareAttacked(index(6, rank), !white)) {
            moves.add(new Move(kingSquare, index(6, rank), (char) 0));
        }
        if (queenSide && board[index(1, rank)] == '.' && board[index(2, rank)] == '.' && board[index(3, rank)] == '.'
                && board[index(0, rank)] == (white ? 'R' : 'r')
                && !isSquareAttacked(index(3, rank), !white) && !isSquareAttacked(index(2, rank), !white)) {
            moves.add(new Move(kingSquare, index(2, rank), (char) 0));
        }
    }

    private void addStep(List<Move> moves, int from, int x, int y, boolean white) {
        if (!inside(x, y)) return;
        char target = board[index(x, y)];
        if (target == '.' || (Character.isUpperCase(target) != white && Character.toUpperCase(target) != 'K')) {
            moves.add(new Move(from, index(x, y), (char) 0));
        }
    }

    private void addRays(List<Move> moves, int from, int x, int y, boolean white, int[][] directions) {
        for (int[] direction : directions) {
            int targetX = x + direction[0];
            int targetY = y + direction[1];
            while (inside(targetX, targetY)) {
                char target = board[index(targetX, targetY)];
                if (target == '.') {
                    moves.add(new Move(from, index(targetX, targetY), (char) 0));
                } else {
                    if (Character.isUpperCase(target) != white && Character.toUpperCase(target) != 'K') {
                        moves.add(new Move(from, index(targetX, targetY), (char) 0));
                    }
                    break;
                }
                targetX += direction[0];
                targetY += direction[1];
            }
        }
    }

    private boolean isSquareAttacked(int square, boolean byWhite) {
        int x = square % 8;
        int y = square / 8;
        int pawnSourceY = y - (byWhite ? 1 : -1);
        for (int dx : new int[]{-1, 1}) {
            if (inside(x + dx, pawnSourceY) && board[index(x + dx, pawnSourceY)] == (byWhite ? 'P' : 'p')) return true;
        }
        for (int i = 0; i < 8; i++) {
            int nx = x + KNIGHT_X[i];
            int ny = y + KNIGHT_Y[i];
            if (inside(nx, ny) && board[index(nx, ny)] == (byWhite ? 'N' : 'n')) return true;
        }
        for (int i = 0; i < 8; i++) {
            int kx = x + KING_X[i];
            int ky = y + KING_Y[i];
            if (inside(kx, ky) && board[index(kx, ky)] == (byWhite ? 'K' : 'k')) return true;
        }
        if (attackedOnRays(x, y, byWhite, ROOK_DIRECTIONS, 'R')) return true;
        return attackedOnRays(x, y, byWhite, BISHOP_DIRECTIONS, 'B');
    }

    private boolean attackedOnRays(int x, int y, boolean byWhite, int[][] directions, char slider) {
        for (int[] direction : directions) {
            int targetX = x + direction[0];
            int targetY = y + direction[1];
            int distance = 1;
            while (inside(targetX, targetY)) {
                char piece = board[index(targetX, targetY)];
                if (piece != '.') {
                    if (Character.isUpperCase(piece) == byWhite) {
                        char kind = Character.toUpperCase(piece);
                        if (kind == 'Q' || kind == slider || (distance == 1 && kind == 'K')) return true;
                    }
                    break;
                }
                distance++;
                targetX += direction[0];
                targetY += direction[1];
            }
        }
        return false;
    }

    private void applyAndUpdate(Move move, boolean movingWhite) {
        applyUnchecked(move, movingWhite);
        repetitions.merge(positionKey(), 1, Integer::sum);
        updateResult(movingWhite);
    }

    private void applyUnchecked(Move move, boolean movingWhite) {
        char piece = board[move.from()];
        char captured = board[move.to()];
        int fromX = move.from() % 8;
        int toX = move.to() % 8;
        if (Character.toUpperCase(piece) == 'P' && move.to() == enPassant && captured == '.') {
            int capturedSquare = move.to() + (movingWhite ? -8 : 8);
            captured = board[capturedSquare];
            board[capturedSquare] = '.';
        }
        if (Character.toUpperCase(piece) == 'K' && Math.abs(toX - fromX) == 2) {
            int rank = move.from() / 8;
            if (toX == 6) {
                board[index(5, rank)] = board[index(7, rank)];
                board[index(7, rank)] = '.';
            } else {
                board[index(3, rank)] = board[index(0, rank)];
                board[index(0, rank)] = '.';
            }
        }
        board[move.from()] = '.';
        board[move.to()] = move.promotion() == 0 ? piece
                : movingWhite ? move.promotion() : Character.toLowerCase(move.promotion());
        if (piece == 'K') { whiteKingSide = false; whiteQueenSide = false; }
        if (piece == 'k') { blackKingSide = false; blackQueenSide = false; }
        if (move.from() == 0 || move.to() == 0) whiteQueenSide = false;
        if (move.from() == 7 || move.to() == 7) whiteKingSide = false;
        if (move.from() == 56 || move.to() == 56) blackQueenSide = false;
        if (move.from() == 63 || move.to() == 63) blackKingSide = false;
        enPassant = Character.toUpperCase(piece) == 'P' && Math.abs(move.to() / 8 - move.from() / 8) == 2
                ? (move.from() + move.to()) / 2 : -1;
        halfmoveClock = Character.toUpperCase(piece) == 'P' || captured != '.' ? 0 : halfmoveClock + 1;
        if (!movingWhite) fullmoveNumber++;
        lastMove = move;
        whiteToMove = !movingWhite;
    }

    private void updateResult(boolean movedWhite) {
        List<Move> nextMoves = legalMoves(whiteToMove);
        if (nextMoves.isEmpty()) {
            if (inCheck(whiteToMove)) result = movedWhite ? "WHITE" : "BLACK";
            else result = "DRAW";
        } else if (halfmoveClock >= 100 || repetitions.getOrDefault(positionKey(), 0) >= 3 || insufficientMaterial()) {
            result = "DRAW";
        }
    }

    private boolean insufficientMaterial() {
        int minors = 0;
        int bishopColor = -1;
        boolean bishopsSameColor = true;
        for (int square = 0; square < 64; square++) {
            char piece = board[square];
            if (piece == '.' || Character.toUpperCase(piece) == 'K') continue;
            char kind = Character.toUpperCase(piece);
            if (kind == 'P' || kind == 'R' || kind == 'Q') return false;
            minors++;
            if (kind == 'B') {
                int color = ((square % 8) + (square / 8)) & 1;
                if (bishopColor < 0) bishopColor = color;
                else if (bishopColor != color) bishopsSameColor = false;
            }
        }
        return minors <= 1 || (minors > 0 && bishopsSameColor && onlyBishopsRemain());
    }

    private boolean onlyBishopsRemain() {
        for (char piece : board) {
            if (piece != '.' && Character.toUpperCase(piece) != 'K' && Character.toUpperCase(piece) != 'B') return false;
        }
        return true;
    }

    private String positionKey() {
        int rights = (whiteKingSide ? 1 : 0) | (whiteQueenSide ? 2 : 0)
                | (blackKingSide ? 4 : 0) | (blackQueenSide ? 8 : 0);
        return new String(board) + (whiteToMove ? 'w' : 'b') + rights + ':' + enPassant;
    }

    private static int index(int x, int y) {
        return y * 8 + x;
    }

    private static boolean inside(int x, int y) {
        return x >= 0 && x < 8 && y >= 0 && y < 8;
    }
}