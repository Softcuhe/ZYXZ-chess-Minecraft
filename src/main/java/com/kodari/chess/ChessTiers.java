package com.kodari.chess;

public final class ChessTiers {
    private static final String[] NAMES = {"LT5", "HT5", "LT4", "HT4", "LT3", "HT3", "LT2", "HT2", "LT1", "HT1"};
    private static final int[] COLORS = {
            0xFF9AA0A6, 0xFF90A4AE, 0xFF4DB6AC, 0xFF26A69A, 0xFF42A5F5,
            0xFF5C6BC0, 0xFF7E57C2, 0xFFAB47BC, 0xFFFF7043, 0xFFFFCA28
    };

    private ChessTiers() {
    }

    public static String name(int index) {
        return NAMES[Math.max(0, Math.min(NAMES.length - 1, index))];
    }

    public static int index(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) return i;
        }
        return 0;
    }

    public static int color(int index) {
        return COLORS[Math.max(0, Math.min(COLORS.length - 1, index))];
    }

    public static int color(String name) {
        return color(index(name));
    }
}