package com.kodari.chess.client;

import com.kodari.chess.ChessTiers;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class ChessProfileScreen extends Screen {
    private record PlayerSummary(String id, String name, int rating, int wins, int losses, int draws,
                                 String tier, int tierXp) {
    }

    private record PlayerProfile(String id, String name, int rating, int wins, int losses, int draws,
                                 String tier, int tierXp, List<String> history) {
    }

    private static final List<PlayerSummary> ROSTER = new ArrayList<>();
    private static final Map<String, PlayerProfile> PROFILES = new HashMap<>();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());
    private static ChessProfileScreen current;

    private final Screen parent;
    private String selectedPlayerId = "";
    private int rosterPage;

    public ChessProfileScreen(Screen parent) {
        super(Text.literal("Chess Profile"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        current = this;
        int centerX = width / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> refresh())
                .dimensions(centerX - 150, 20, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Previous"), button -> rosterPage = Math.max(0, rosterPage - 1))
                .dimensions(centerX - 75, height - 26, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next"), button -> rosterPage = Math.min(lastPage(), rosterPage + 1))
                .dimensions(centerX + 5, height - 26, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> returnToParent())
                .dimensions(centerX + 80, 20, 68, 20).build());
        refresh();
    }

    private void refresh() {
        ChessClient.send("PRESENCE");
        ChessClient.send("ROSTER");
        ChessClient.send("PROFILE");
    }

    static void updateRoster(String data) {
        ROSTER.clear();
        if (data.isEmpty()) return;
        for (String entry : data.split(";")) {
            String[] fields = entry.split("~", 8);
            if (fields.length < 6) continue;
            ROSTER.add(new PlayerSummary(fields[0], decode(fields[1]), number(fields[2]), number(fields[3]),
                    number(fields[4]), number(fields[5]), fields.length > 6 ? fields[6] : "LT5",
                    fields.length > 7 ? number(fields[7]) : 0));
        }
        if (current != null) current.rosterPage = Math.min(current.rosterPage, current.lastPage());
    }

    static void receiveProfile(String[] fields) {
        if (fields.length < 8) return;
        List<String> history = new ArrayList<>();
        boolean hasTier = fields.length >= 10;
        String rawHistory = decode(fields[hasTier ? 9 : 7]);
        if (!rawHistory.isEmpty()) {
            for (String entry : rawHistory.split("\n")) {
                String[] values = entry.split("~", 5);
                if (values.length != 5) continue;
                try {
                    String date = DATE_FORMAT.format(Instant.ofEpochMilli(Long.parseLong(values[0])));
                    String opponent = decode(values[1]);
                    int delta = Integer.parseInt(values[3]);
                    history.add(date + "  " + values[2] + " " + opponent + " (" + (delta > 0 ? "+" : "") + delta + ")");
                } catch (RuntimeException ignored) {
                }
            }
        }
        PlayerProfile profile = new PlayerProfile(fields[1], decode(fields[2]), number(fields[3]), number(fields[4]),
                number(fields[5]), number(fields[6]), hasTier ? fields[7] : "LT5",
                hasTier ? number(fields[8]) : 0, history);
        PROFILES.put(profile.id(), profile);
        if (current != null && current.selectedPlayerId.isEmpty()) current.selectedPlayerId = profile.id();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        ChessBoardScreen.drawCustomBackground(context, width, height, mouseX, mouseY);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("CHESS PROFILE"),
                12, 8, ChessPreferences.getColor("name.color", 0xFFF2F3EF));
        int divider = width / 2;
        int listTop = 54;
        int perPage = pageSize();
        context.fill(8, 48, divider - 5, height - 34, 0xAA1D2A30);
        context.fill(divider + 2, 48, width - 8, height - 34, 0xAA1D2A30);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("WORLD / SERVER TOP 100"),
                14, 39, 0xFFE9F2EC);
        if (ROSTER.isEmpty()) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("No rated players yet."),
                    14, listTop, 0xFF9BA8A4);
        }
        for (int row = 0; row < perPage; row++) {
            int index = rosterPage * perPage + row;
            if (index >= ROSTER.size()) break;
            PlayerSummary player = ROSTER.get(index);
            int y = listTop + row * 18;
            boolean selected = player.id().equals(selectedPlayerId);
            if (selected) context.fill(11, y - 2, divider - 8, y + 15, 0xAA47775F);
            ChessPlayerHeads.draw(context, player.id(), 14, y - 1, 14);
            String summary = (index + 1) + ". " + player.name() + "  " + player.rating()
                    + "  " + player.wins() + "/" + player.losses();
            int rowColor = switch (index) {
                case 0 -> 0xFFFFD166;
                case 1 -> 0xFFDCE6F1;
                case 2 -> 0xFFE8A879;
                default -> 0xFF9EC8B2;
            };
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(summary),
                    33, y, rowColor);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal(player.tier() + " · " + player.tierXp() + " XP").withColor(ChessTiers.color(player.tier())),
                    divider - 112, y, ChessTiers.color(player.tier()));
        }
        PlayerProfile profile = PROFILES.get(selectedPlayerId);
        int detailsX = divider + 10;
        if (profile == null) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("Select a player"),
                    detailsX, 58, 0xFF9BA8A4);
        } else {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(profile.name()),
                    detailsX, 58, ChessPreferences.getColor("name.color", 0xFFE2E9E5));
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("Elo: " + profile.rating()),
                    detailsX, 73, 0xFFE9F2EC);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal("Tier: " + profile.tier() + " — " + profile.tierXp() + "/100 XP")
                            .withColor(ChessTiers.color(profile.tier())), detailsX, 88,
                    ChessTiers.color(profile.tier()));
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal("W " + profile.wins() + "   L " + profile.losses() + "   D " + profile.draws()),
                    detailsX, 103, 0xFFE9F2EC);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("MATCH HISTORY"),
                    detailsX, 123, 0xFF9EC8B2);
            int y = 138;
            for (String line : profile.history()) {
                if (y > height - 42) break;
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(line),
                        detailsX, y, 0xFFC6D2CD);
                y += 13;
            }
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int divider = width / 2;
        int listTop = 54;
        int row = (int) ((click.y() - listTop) / 18);
        if (click.button() == 0 && click.x() >= 8 && click.x() < divider - 5
                && row >= 0 && row < pageSize()) {
            int index = rosterPage * pageSize() + row;
            if (index < ROSTER.size()) {
                selectedPlayerId = ROSTER.get(index).id();
                ChessClient.send("PROFILE|" + selectedPlayerId);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private int pageSize() {
        return Math.max(1, (height - 105) / 18);
    }

    private int lastPage() {
        return Math.max(0, (ROSTER.size() - 1) / pageSize());
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private void returnToParent() {
        if (current == this) current = null;
        MinecraftClient.getInstance().setScreen(parent);
    }
}