package dev.atikdd.marksman;

import dev.atikdd.marksman.net.*;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.StrokeType;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class MarksmanController {

    @FXML AnchorPane mainPanel;
    @FXML AnchorPane loginPanel;
    @FXML TextField   tfUsername;
    @FXML Label       lblLoginStatus;

    private Circle   target1;
    private Circle   target2;
    private Polyline[] playerShapes;
    @FXML Line   arrowLine0, arrowLine1, arrowLine2, arrowLine3;

    @FXML Label lblState;
    @FXML Label p0name, p0score, p0shots;
    @FXML Label p1name, p1score, p1shots;
    @FXML Label p2name, p2score, p2shots;
    @FXML Label p3name, p3score, p3shots;

    @FXML Button btnReady;
    @FXML Button btnShoot;
    @FXML Button btnPause;

    private static final Color[] COLORS = {
        Color.web("#0086ff"), Color.web("#ff4444"),
        Color.web("#44bb00"), Color.web("#ff8800")
    };

    private GameClient  client;
    private String      myName;
    private volatile GameSnapshot currentSnapshot;

    private Line[]    arrows;
    private Label[][] playerLabels;

    @FXML
    public void initialize() {
        target1 = createTarget(27, "#ff1f40");
        target1.setLayoutX(380);
        target1.setLayoutY(100);

        target2 = createTarget(16, "#ff1f1f");
        target2.setLayoutX(500);
        target2.setLayoutY(200);

        playerShapes = new Polyline[4];
        for (int i = 0; i < 4; i++) {
            Polyline shape = new Polyline(-108, 19, -108, -42, -83, -10, -108, 19);
            shape.setFill(COLORS[i]);
            shape.setLayoutX(122);
            shape.setLayoutY(187);
            shape.setStrokeType(StrokeType.INSIDE);
            shape.setVisible(false);
            playerShapes[i] = shape;
        }

        int loginIdx = mainPanel.getChildren().indexOf(loginPanel);
        for (int i = 0; i < 4; i++) {
            mainPanel.getChildren().add(loginIdx + i, playerShapes[i]);
        }
        mainPanel.getChildren().add(loginIdx + 4, target1);
        mainPanel.getChildren().add(loginIdx + 5, target2);

        arrows = new Line[]{arrowLine0, arrowLine1, arrowLine2, arrowLine3};
        playerLabels = new Label[][]{
            {p0name, p0score, p0shots},
            {p1name, p1score, p1shots},
            {p2name, p2score, p2shots},
            {p3name, p3score, p3shots}
        };

        AnimationTimer render = new AnimationTimer() {
            @Override
            public void handle(long now) {
                GameSnapshot snap = currentSnapshot;
                if (snap == null) return;
                renderSnapshot(snap);
            }
        };
        render.start();
    }

    private void renderSnapshot(GameSnapshot snap) {
        target1.setLayoutY(snap.t1y);
        target2.setLayoutY(snap.t2y);

        for (Polyline shape : playerShapes) shape.setVisible(false);
        if (snap.players != null) {
            for (int i = 0; i < snap.players.size() && i < 4; i++) {
                PlayerInfo p = snap.players.get(i);
                playerShapes[i].setLayoutY(p.y + 10);
                playerShapes[i].setVisible(true);
            }
        }

        for (Line line : arrows) line.setVisible(false);
        if (snap.arrows != null) {
            for (ArrowInfo a : snap.arrows) {
                int idx = playerIndex(snap, a.playerName);
                if (idx < 0 || idx >= arrows.length) continue;
                Line line = arrows[idx];
                line.setStartX(a.x);
                line.setEndX(a.x + 30);
                line.setStartY(a.y);
                line.setEndY(a.y);
                line.setVisible(true);
            }
        }

        lblState.setText(switch (snap.state) {
            case "WAITING"  -> "Ожидание";
            case "PLAYING"  -> "Игра идёт";
            case "PAUSED"   -> "Пауза";
            case "FINISHED" -> snap.winner != null ? "Победитель:\n" + snap.winner : "Завершено";
            default         -> snap.state;
        });

        for (Label[] row : playerLabels) {
            row[0].setText("—"); row[0].setTextFill(Color.LIGHTGRAY);
            row[1].setText("—");
            row[2].setText("—");
        }
        if (snap.players != null) {
            for (int i = 0; i < snap.players.size() && i < 4; i++) {
                PlayerInfo p = snap.players.get(i);
                playerLabels[i][0].setText(p.ready ? p.name + " ✓" : p.name);
                playerLabels[i][0].setTextFill(COLORS[i]);
                playerLabels[i][1].setText(String.valueOf(p.score));
                playerLabels[i][2].setText(String.valueOf(p.shots));
            }
        }

        boolean playing      = "PLAYING".equals(snap.state);
        boolean myArrowFlying = snap.arrows != null &&
            snap.arrows.stream().anyMatch(a -> myName != null && myName.equals(a.playerName));

        btnReady.setDisable(playing);
        btnShoot.setDisable(!playing || myArrowFlying);
        btnPause.setDisable(!playing);
    }

    private int playerIndex(GameSnapshot snap, String name) {
        if (snap.players == null || name == null) return -1;
        for (int i = 0; i < snap.players.size(); i++) {
            if (name.equals(snap.players.get(i).name)) return i;
        }
        return -1;
    }

    @FXML
    void onConnect() {
        String username = tfUsername.getText().trim();
        InetAddress host     = null;
        try {
            host = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        if (username.isEmpty()) { lblLoginStatus.setText("Введите имя"); return; }

        try {
            client = new GameClient(host, 3124);
            client.onSnapshot   = snap  -> currentSnapshot = snap;
            client.onError      = error -> Platform.runLater(() -> {
                lblLoginStatus.setText(error);
                client = null;
                loginPanel.setVisible(true);
            });
            client.onDisconnect = ()    -> Platform.runLater(() -> {
                lblLoginStatus.setText("Соединение потеряно");
                loginPanel.setVisible(true);
                currentSnapshot = null;
            });

            myName = username;
            client.send(Msg.join(username));
            loginPanel.setVisible(false);
        } catch (IOException e) {
            lblLoginStatus.setText("Ошибка: " + e.getMessage());
        }
    }

    private Circle createTarget(double radius, String color) {
        Circle c = new Circle(radius);
        c.setStroke(Color.BLACK);
        c.setStrokeType(StrokeType.INSIDE);
        c.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web(color)),
            new Stop(1, Color.WHITE)));
        return c;
    }

    @FXML void onReady() { if (client != null) client.send(Msg.ready()); }
    @FXML void onShoot() { if (client != null) client.send(Msg.shoot()); }
    @FXML void onPause() { if (client != null) client.send(Msg.pause()); }
}
