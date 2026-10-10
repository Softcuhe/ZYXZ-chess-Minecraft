package com.kodari.chess.client;

import com.kodari.chess.ChessGame;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Easing;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class ChessLessonsScreen extends BaseOwoScreen<FlowLayout> {
    private record Lesson(String category, String title, String summary, String takeaway, List<String> steps) {
    }

    private static final int ACCENT = 0x63B78B;
    private static final List<String> CATEGORIES = List.of("Basics", "Openings", "Gambits", "Checkmates");
    private static final List<Lesson> LESSONS = List.of(
            new Lesson("Basics", "How a chess turn works", "Move one piece on your turn; capture an enemy piece by moving onto its square.",
                    "Check the whole board before every move.", List.of("White moves first, then players alternate.", "You cannot make a move that leaves your own king in check.")),
            new Lesson("Basics", "Piece movement", "Every piece has its own movement pattern. Pawns move forward but capture diagonally.",
                    "Learn how each piece attacks before planning a combination.", List.of("Rooks move in straight lines; bishops move diagonally.", "Queens combine rook and bishop movement; knights jump in an L shape.", "Kings move one square; pawns move forward and capture diagonally.")),
            new Lesson("Basics", "Check and escape", "A king is in check when an enemy piece attacks its square.",
                    "Answer every check immediately.", List.of("Move the king out of attack, capture the checking piece, or block its line.", "If none of those moves is legal, the position is checkmate.")),
            new Lesson("Basics", "Checkmate versus stalemate", "Checkmate ends the game with a winner. Stalemate is a draw when the player to move has no legal move but is not in check.",
                    "Before pushing a lone king, make sure it still has a legal square.", List.of("Check whether the king is attacked.", "If it is attacked and has no legal defense, it is checkmate.", "If it is not attacked and has no legal move, it is stalemate.")),
            new Lesson("Basics", "Castling", "Castling moves the king two squares toward a rook and places that rook beside the king.",
                    "Castle early when it makes your king safer and connects your rooks.", List.of("Neither the king nor the chosen rook may have moved.", "Squares between them must be empty.", "The king cannot start in check, cross an attacked square, or finish in check.")),
            new Lesson("Basics", "Pawn promotion", "A pawn reaching the farthest rank promotes to a queen, rook, bishop, or knight.",
                    "A queen is usually strongest, but a knight promotion can give a forced check or mate.", List.of("Advance a pawn to the last rank.", "Choose the piece that best fits the position; promotion is not limited to captured pieces.")),
            new Lesson("Basics", "Build a safe opening", "Develop minor pieces, contest the center, and protect your king before launching an attack.",
                    "Avoid moving the same piece repeatedly while undeveloped pieces wait.", List.of("Control central squares with pawns or pieces.", "Develop knights and bishops toward active squares.", "Castle and connect your rooks.")),
            new Lesson("Basics", "Spot a hanging piece", "A hanging piece is undefended or can be captured without adequate compensation.",
                    "Before moving, ask what your opponent can capture next.", List.of("Count attackers and defenders on the destination square.", "Look for checks and captures after each candidate move.", "Do not leave a valuable piece en prise without a reason.")),
            new Lesson("Openings", "Italian Game", "A classical open game that develops quickly and aims at the center and the f7 square.",
                    "Develop with tempo, then choose a plan: d3 for a quieter game or d4 for a central break.", List.of("1. e4 e5", "2. Nf3 Nc6", "3. Bc4 Bc5")),
            new Lesson("Openings", "Ruy Lopez", "White pressures the knight that supports Black's e5 pawn and builds long-term central pressure.",
                    "The bishop is not expected to win the knight immediately; use the pressure to improve your position.", List.of("1. e4 e5", "2. Nf3 Nc6", "3. Bb5")),
            new Lesson("Openings", "Sicilian Defense", "Black answers 1. e4 with ...c5, fighting for the center asymmetrically.",
                    "Black often accepts less space on the queenside in exchange for active counterplay.", List.of("1. e4 c5", "2. Nf3 d6", "3. d4 cxd4", "4. Nxd4")),
            new Lesson("Openings", "Queen's Gambit", "White offers the c-pawn to draw Black away from the center and build central control.",
                    "The c-pawn is usually recoverable; the opening is a positional offer, not a forced pawn sacrifice.", List.of("1. d4 d5", "2. c4 e6", "3. Nc3 Nf6")),
            new Lesson("Openings", "King's Indian Defense", "Black allows White a broad center, then prepares a kingside counterattack.",
                    "Black's ...e5 or ...c5 break is key; do not wait passively while White expands.", List.of("1. d4 Nf6", "2. c4 g6", "3. Nc3 Bg7", "4. e4 d6")),
            new Lesson("Openings", "London System", "White develops a solid setup with d4, Nf3, Bf4, and e3.",
                    "A familiar setup still needs adaptation to the opponent's center and piece placement.", List.of("1. d4 d5", "2. Nf3 Nf6", "3. Bf4 e6", "4. e3")),
            new Lesson("Openings", "Najdorf Sicilian", "A sharp Sicilian variation reached after ...a6, preparing ...e5 or queenside expansion.",
                    "Both sides should calculate carefully: the position is dynamic and tactical.", List.of("1. e4 c5", "2. Nf3 d6", "3. d4 cxd4", "4. Nxd4 Nf6", "5. Nc3 a6")),
            new Lesson("Gambits", "King's Gambit", "White offers the f-pawn after 1. e4 e5 2. f4 to open lines and seize initiative.",
                    "A gambit buys activity, not a free attack; develop quickly and keep the king safe.", List.of("1. e4 e5", "2. f4 exf4", "3. Nf3")),
            new Lesson("Gambits", "Evans Gambit", "White gives up a b-pawn to gain tempi and open lines against Black's center.",
                    "Use the lead in development before the opponent consolidates the extra pawn.", List.of("1. e4 e5", "2. Nf3 Nc6", "3. Bc4 Bc5", "4. b4 Bxb4", "5. c3")),
            new Lesson("Gambits", "Danish Gambit", "White offers central pawns for rapid bishop development and open diagonals.",
                    "If the opponent declines, continue developing rather than forcing the sacrifice.", List.of("1. e4 e5", "2. d4 exd4", "3. c3 dxc3", "4. Bc4")),
            new Lesson("Gambits", "Smith-Morra Gambit", "Against the Sicilian, White offers a pawn for fast development and open files.",
                    "The initiative matters: place rooks on open files and avoid trading every active piece.", List.of("1. e4 c5", "2. d4 cxd4", "3. c3 dxc3", "4. Nxc3")),
            new Lesson("Checkmates", "Scholar's Mate pattern", "A fast attack on f7 can punish a king left undefended, but the pattern is easy to meet with accurate play.",
                    "Do not rely on a trap; develop pieces and recognize the same f7 weakness defensively.", List.of("1. e4 e5", "2. Qh5 Nc6", "3. Bc4 Nf6?", "4. Qxf7#")),
            new Lesson("Checkmates", "Back-rank mate", "A rook or queen mates along the back rank when the enemy king is trapped behind its own pawns.",
                    "Create a luft square before the back rank becomes a weakness.", List.of("Look for a king boxed in by unmoved pawns.", "Remove or deflect the defender of the back rank.", "Deliver the rook or queen check on the last rank.")),
            new Lesson("Checkmates", "Ladder mate", "Two major pieces can drive a lone king to the edge by taking turns controlling ranks or files.",
                    "Keep the pieces coordinated and avoid stalemate when the king is near the edge.", List.of("Place one rook on a rank to restrict the king.", "Use the other rook to check from the next rank.", "Repeat until the king reaches the board edge.")),
            new Lesson("Checkmates", "Smothered mate", "A knight can checkmate a king surrounded by its own pieces because those pieces block every escape square.",
                    "Look for a trapped king and a knight check that cannot be captured.", List.of("Identify a king whose neighboring squares are occupied or controlled.", "Force the king into the crowded area with checks or a sacrifice.", "Deliver the knight check when no legal escape or capture remains.")),
            new Lesson("Checkmates", "Queen and king mate", "A queen and king can force a lone king to the edge, then deliver mate with the king's support.",
                    "Bring your king closer; the queen alone can accidentally stalemate.", List.of("Use the queen to shrink the enemy king's box.", "Bring your king up to support the final squares.", "Checkmate at the edge while keeping every escape square covered."))
    );

    private final boolean inWorld;
    private FlowLayout lessonList;
    private LabelComponent selectedTitle;
    private LabelComponent selectedSummary;
    private LabelComponent selectedTakeaway;
    private LabelComponent selectedSteps;
    private LabelComponent stepCount;
    private ButtonComponent nextStepButton;
    private Lesson selectedLesson = LESSONS.get(0);
    private String selectedCategory = "Basics";
    private int revealedSteps;

    public ChessLessonsScreen(boolean inWorld) {
        super(Text.literal("Chess Lessons"));
        this.inWorld = inWorld;
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.blur(3, 5).and(Surface.flat(0xC0080C14)));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(600), Sizing.fixed(380));
        panel.padding(Insets.of(12));
        panel.gap(8);
        panel.surface(Surface.flat(0xEE151D25).and(Surface.outline(0xFF3A5A4A)));

        FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        header.verticalAlignment(VerticalAlignment.CENTER);
        LabelComponent title = UIComponents.label(Text.literal("LEARN CHESS"));
        title.color(Color.ofRgb(ACCENT));
        title.shadow(true);
        header.child(title);
        header.child(UIComponents.spacer().verticalSizing(Sizing.fixed(0)));
        header.child(UIComponents.button(Text.literal("Back"), button -> returnHome()));
        panel.child(header);
        panel.child(UIComponents.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofArgb(0xFF3A5A4A)).fill(true));

        FlowLayout categoryRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        categoryRow.gap(5);
        for (String category : CATEGORIES) {
            ButtonComponent button = UIComponents.button(Text.literal(category), component -> selectCategory(category));
            button.renderer(ButtonComponent.Renderer.flat(0xFF22332D, 0xFF345344, 0xFF1B2522));
            button.textShadow(false);
            categoryRow.child(button);
        }
        panel.child(categoryRow);

        FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(250));
        body.gap(8);
        FlowLayout listPanel = UIContainers.verticalFlow(Sizing.fixed(236), Sizing.fill(100));
        listPanel.padding(Insets.of(8));
        listPanel.gap(6);
        listPanel.surface(Surface.flat(0xB51D2A30));
        listPanel.child(UIComponents.label(Text.literal("LESSONS")));
        this.lessonList = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.lessonList.gap(4);
        ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), this.lessonList);
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        scroll.scrollbarThiccness(3);
        listPanel.child(scroll);
        body.child(listPanel);

        FlowLayout detailPanel = UIContainers.verticalFlow(Sizing.fixed(332), Sizing.fill(100));
        detailPanel.padding(Insets.of(10));
        detailPanel.gap(8);
        detailPanel.surface(Surface.flat(0xB51D2A30));
        this.selectedTitle = UIComponents.label(Text.literal(""));
        this.selectedTitle.color(Color.ofRgb(ACCENT));
        this.selectedTitle.shadow(true);
        this.selectedTitle.maxWidth(306);
        this.selectedSummary = UIComponents.label(Text.literal(""));
        this.selectedSummary.maxWidth(306);
        this.selectedTakeaway = UIComponents.label(Text.literal(""));
        this.selectedTakeaway.maxWidth(306);
        this.selectedSteps = UIComponents.label(Text.literal(""));
        this.selectedSteps.maxWidth(306);
        this.stepCount = UIComponents.label(Text.literal(""));
        this.stepCount.color(Color.ofRgb(0x9EC8B2));
        detailPanel.child(this.selectedTitle);
        detailPanel.child(this.selectedSummary);
        detailPanel.child(UIComponents.label(Text.literal("KEY IDEA")).color(Color.ofRgb(0x9EC8B2)));
        detailPanel.child(this.selectedTakeaway);
        detailPanel.child(UIComponents.label(Text.literal("MOVE-BY-MOVE")).color(Color.ofRgb(0x9EC8B2)));
        detailPanel.child(this.selectedSteps);
        detailPanel.child(this.stepCount);
        this.nextStepButton = UIComponents.button(Text.literal("Reveal next step"), button -> revealNextStep());
        this.nextStepButton.renderer(ButtonComponent.Renderer.flat(0xFF2E6A4D, 0xFF39835E, 0xFF26362E));
        FlowLayout controls = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        controls.gap(6);
        controls.verticalAlignment(VerticalAlignment.CENTER);
        controls.child(this.nextStepButton);
        controls.child(UIComponents.button(Text.literal("Reset line"), button -> resetSteps()));
        detailPanel.child(controls);
        body.child(detailPanel);
        panel.child(body);

        FlowLayout footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        footer.verticalAlignment(VerticalAlignment.CENTER);
        LabelComponent hint = UIComponents.label(Text.literal("Study a concept, then reveal its line one step at a time."));
        hint.color(Color.ofRgb(0x9AA9BD));
        footer.child(hint);
        panel.child(footer);
        root.child(panel);

        refreshLessons();
        updateLessonDetails();
    }

    private void selectCategory(String category) {
        selectedCategory = category;
        List<Lesson> filtered = lessonsInCategory(category);
        selectedLesson = filtered.get(0);
        revealedSteps = 0;
        refreshLessons();
        updateLessonDetails();
    }

    private void selectLesson(Lesson lesson) {
        selectedLesson = lesson;
        revealedSteps = 0;
        updateLessonDetails();
        selectedTitle.color(Color.ofRgb(0xE6EDF3));
        selectedTitle.color().animate(240, Easing.CUBIC, Color.ofRgb(ACCENT)).forwards();
    }

    private void refreshLessons() {
        lessonList.clearChildren();
        for (Lesson lesson : lessonsInCategory(selectedCategory)) {
            ButtonComponent button = UIComponents.button(Text.literal(lesson.title()), component -> selectLesson(lesson));
            button.horizontalSizing(Sizing.fill(100));
            button.textShadow(false);
            button.renderer(ButtonComponent.Renderer.flat(0xFF25332E, 0xFF385244, 0xFF1B2522));
            lessonList.child(button);
        }
    }

    private static List<Lesson> lessonsInCategory(String category) {
        ArrayList<Lesson> filtered = new ArrayList<>();
        for (Lesson lesson : LESSONS) {
            if (lesson.category().equals(category)) filtered.add(lesson);
        }
        return filtered;
    }

    private void updateLessonDetails() {
        selectedTitle.text(Text.literal(selectedLesson.title()));
        selectedSummary.text(Text.literal(selectedLesson.summary()));
        selectedTakeaway.text(Text.literal(selectedLesson.takeaway()));
        String revealed = String.join("\n", selectedLesson.steps().subList(0,
                Math.min(revealedSteps, selectedLesson.steps().size())));
        selectedSteps.text(Text.literal(revealed.isEmpty() ? "Select Reveal next step to begin." : revealed));
        stepCount.text(Text.literal("Step " + revealedSteps + " / " + selectedLesson.steps().size()));
        nextStepButton.active(revealedSteps < selectedLesson.steps().size());
        nextStepButton.setMessage(Text.literal(revealedSteps < selectedLesson.steps().size()
                ? "Reveal next step" : "Line complete"));
    }

    private void revealNextStep() {
        if (revealedSteps >= selectedLesson.steps().size()) return;
        revealedSteps++;
        updateLessonDetails();
    }

    private void resetSteps() {
        revealedSteps = 0;
        updateLessonDetails();
    }

    private void returnHome() {
        MinecraftClient.getInstance().setScreen(new ChessHomeScreen(null, inWorld));
    }
}