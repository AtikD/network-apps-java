package dev.atikdd.marksman;

import dev.atikdd.marksman.net.ArrowInfo;
import dev.atikdd.marksman.net.GameSnapshot;
import dev.atikdd.marksman.net.GameState;
import dev.atikdd.marksman.net.PlayerInfo;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.StrokeType;

import static dev.atikdd.marksman.net.GameConstants.*;

class GameRenderer {

    static final Color[] COLORS = {
        Color.web("#0086ff"), Color.web("#ff4444"),
        Color.web("#44bb00"), Color.web("#ff8800")
    };

    private static final double PLAYER_LAYOUT_X = 122;
    private static final double PLAYER_TIP_Y_OFFSET = 10;

    private final AnchorPane mainPanel;
    private final AnchorPane loginPanel;
    private final Line[] arrows;
    private final Label lblState;
    private final Label[][] playerLabels;
    private final Button btnReady, btnShoot, btnPause;

    private Circle target1, target2;
    private Polyline[] playerShapes;
    private String myName;

    GameRenderer(AnchorPane mainPanel, AnchorPane loginPanel,
                 Line[] arrows, Label lblState, Label[][] playerLabels,
                 Button btnReady, Button btnShoot, Button btnPause) {
        this.mainPanel = mainPanel;
        this.loginPanel = loginPanel;
        this.arrows = arrows;
        this.lblState = lblState;
        this.playerLabels = playerLabels;
        this.btnReady = btnReady;
        this.btnShoot = btnShoot;
        this.btnPause = btnPause;
    }

    void setup() {
        target1 = createTarget(TARGET1_RADIUS, "#ff1f40");
        target1.setLayoutX(TARGET1_X);
        target1.setLayoutY(TARGET1_START_Y);

        target2 = createTarget(TARGET2_RADIUS, "#ff1f1f");
        target2.setLayoutX(TARGET2_X);
        target2.setLayoutY(TARGET2_START_Y);

        playerShapes = new Polyline[MAX_PLAYERS];
        for (int i = 0; i < MAX_PLAYERS; i++) {
            Polyline shape = new Polyline(-108, 19, -108, -42, -83, -10, -108, 19);
            shape.setFill(COLORS[i]);
            shape.setLayoutX(PLAYER_LAYOUT_X);
            shape.setLayoutY(187);
            shape.setStrokeType(StrokeType.INSIDE);
            shape.setVisible(false);
            playerShapes[i] = shape;
        }

        int loginIdx = mainPanel.getChildren().indexOf(loginPanel);
        for (int i = 0; i < MAX_PLAYERS; i++) {
            mainPanel.getChildren().add(loginIdx + i, playerShapes[i]);
        }
        mainPanel.getChildren().add(loginIdx + MAX_PLAYERS,     target1);
        mainPanel.getChildren().add(loginIdx + MAX_PLAYERS + 1, target2);
    }

    void setMyName(String name) {
        this.myName = name;
    }

    void render(GameSnapshot snap) {
        target1.setLayoutY(snap.t1y);
        target2.setLayoutY(snap.t2y);

        for (Polyline shape : playerShapes) shape.setVisible(false);
        if (snap.players != null) {
            for (PlayerInfo p : snap.players) {
                if (p.index < 0 || p.index >= MAX_PLAYERS) continue;
                playerShapes[p.index].setLayoutY(p.y + PLAYER_TIP_Y_OFFSET);
                playerShapes[p.index].setVisible(true);
            }
        }

        for (Line line : arrows) line.setVisible(false);
        if (snap.arrows != null) {
            for (ArrowInfo a : snap.arrows) {
                if (a.playerIndex < 0 || a.playerIndex >= arrows.length) continue;
                Line line = arrows[a.playerIndex];
                line.setStartX(a.x);
                line.setEndX(a.x + ARROW_LENGTH);
                line.setStartY(a.y);
                line.setEndY(a.y);
                line.setVisible(true);
            }
        }

        lblState.setText(switch (snap.state) {
            case WAITING  -> "Ожидание";
            case PLAYING  -> "Игра идёт";
            case PAUSED   -> "Пауза";
            case FINISHED -> snap.winner != null ? "Победитель:\n" + snap.winner : "Завершено";
        });

        for (Label[] row : playerLabels) {
            row[0].setText("—"); row[0].setTextFill(Color.LIGHTGRAY);
            row[1].setText("—");
            row[2].setText("—");
        }
        if (snap.players != null) {
            for (PlayerInfo p : snap.players) {
                if (p.index < 0 || p.index >= MAX_PLAYERS) continue;
                playerLabels[p.index][0].setText(p.ready ? p.name + " ✓" : p.name);
                playerLabels[p.index][0].setTextFill(COLORS[p.index]);
                playerLabels[p.index][1].setText(String.valueOf(p.score));
                playerLabels[p.index][2].setText(String.valueOf(p.shots));
            }
        }

        boolean playing = snap.state == GameState.PLAYING;
        int myIndex = myIndex(snap);
        boolean myArrowFlying = myIndex >= 0 && snap.arrows != null &&
            snap.arrows.stream().anyMatch(a -> a.playerIndex == myIndex);

        btnReady.setDisable(playing);
        btnShoot.setDisable(!playing || myArrowFlying);
        btnPause.setDisable(!playing);

        if (myIndex >= 0) {
            btnShoot.setStyle("-fx-background-color: " + toHex(COLORS[myIndex]) + "; -fx-text-fill: white;");
        }
    }

    private int myIndex(GameSnapshot snap) {
        if (snap.players == null || myName == null) return -1;
        for (PlayerInfo p : snap.players) {
            if (myName.equals(p.name)) return p.index;
        }
        return -1;
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
            (int) Math.round(c.getRed()   * 255),
            (int) Math.round(c.getGreen() * 255),
            (int) Math.round(c.getBlue()  * 255));
    }

    private static Circle createTarget(double radius, String color) {
        Circle c = new Circle(radius);
        c.setStroke(Color.BLACK);
        c.setStrokeType(StrokeType.INSIDE);
        c.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web(color)),
            new Stop(1, Color.WHITE)));
        return c;
    }
}
