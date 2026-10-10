package com.kodari.chess.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

final class ChessPreferences {
    private static final int MAX_IMAGE_BYTES = 1024 * 1024;
    private static final String PIECE_ASSET_BASE = "https://raw.githubusercontent.com/GiorgioMegrelli/chess.com-boards-and-pieces/v2.x/pieces/";
    private static final String[] PIECE_PRESETS = {"bubblegum", "book", "bases", "alpha", "8qetl"};
    private static final Path FILE = Path.of(System.getProperty("user.dir"), "config", "kodarichess.properties");
    private static final Properties VALUES = new Properties();

    static {
        try (InputStream input = Files.newInputStream(FILE)) {
            VALUES.load(input);
        } catch (IOException ignored) {
        }
    }

    private ChessPreferences() {
    }

    static String get(String key, String fallback) {
        return VALUES.getProperty(key, fallback);
    }

    static int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static boolean getBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, Boolean.toString(fallback)));
    }

    static int getColor(String key, int fallback) {
        String value = get(key, "").trim().replace("#", "");
        if (value.length() != 6) return fallback;
        try {
            return 0xFF000000 | Integer.parseInt(value, 16);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static String getPiecePreset() {
        String preset = get("piece.set", "8qetl");
        return isPiecePreset(preset) ? preset : "8qetl";
    }

    static void setPiecePreset(String preset) {
        if (isPiecePreset(preset)) VALUES.setProperty("piece.set", preset);
    }

    static boolean isPiecePreset(String preset) {
        for (String available : PIECE_PRESETS) {
            if (available.equals(preset)) return true;
        }
        return false;
    }

    static String[] piecePresets() {
        return PIECE_PRESETS.clone();
    }

    static String getPieceImageUrl(String preset, boolean white, char piece) {
        String selectedPreset = isPiecePreset(preset) ? preset : "8qetl";
        return PIECE_ASSET_BASE + selectedPreset + "/" + (white ? "w" : "b")
                + Character.toLowerCase(piece) + ".png";
    }

    static void set(String key, String value) {
        VALUES.setProperty(key, value);
    }

    static void setInt(String key, int value) {
        set(key, Integer.toString(value));
        save();
    }

    static void setBoolean(String key, boolean value) {
        set(key, Boolean.toString(value));
        save();
    }

    static boolean isWebUrl(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) return false;
        try {
            URI uri = URI.create(value.trim());
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static boolean isLocalImage(String value) {
        if (value == null || !value.startsWith("local:")) return false;
        try {
            UUID.fromString(value.substring("local:".length()));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static boolean isImageSource(String value) {
        return isWebUrl(value) || isLocalImage(value);
    }

    static String storeImage(Path source) {
        try {
            if (!Files.isRegularFile(source) || Files.size(source) > MAX_IMAGE_BYTES) return "";
            return storeImage(Files.readAllBytes(source));
        } catch (IOException exception) {
            return "";
        }
    }

    static String storeImage(byte[] image) {
        if (image.length < 8 || image.length > MAX_IMAGE_BYTES
                || image[0] != (byte) 0x89 || image[1] != 'P' || image[2] != 'N' || image[3] != 'G'
                || image[4] != 0x0D || image[5] != 0x0A || image[6] != 0x1A || image[7] != 0x0A) {
            return "";
        }
        String id = UUID.randomUUID().toString();
        try {
            Path directory = imageDirectory();
            Files.createDirectories(directory);
            Files.write(directory.resolve(id + ".png"), image);
            return "local:" + id;
        } catch (IOException exception) {
            return "";
        }
    }

    static byte[] loadImage(String source) throws IOException {
        if (!isLocalImage(source)) throw new IOException("Invalid local image reference");
        return Files.readAllBytes(imageDirectory().resolve(source.substring("local:".length()) + ".png"));
    }

    private static Path imageDirectory() {
        return FILE.getParent().resolve("kodarichess").resolve("images");
    }

    static boolean isHexColor(String value) {
        String hex = value.trim().replace("#", "");
        if (hex.length() != 6) return false;
        try {
            Integer.parseInt(hex, 16);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                VALUES.store(output, "Kodari Chess settings");
            }
        } catch (IOException ignored) {
        }
    }
}