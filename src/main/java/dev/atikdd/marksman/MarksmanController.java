package dev.atikdd.marksman;

import dev.atikdd.marksman.net.GameConstants;
import dev.atikdd.marksman.net.GameSnapshot;
import dev.atikdd.marksman.net.GameState;
import dev.atikdd.marksman.net.Msg;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Line;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class MarksmanController {

    @FXML AnchorPane mainPanel;
    @FXML AnchorPane loginPanel;
    @FXML TextField  tfUsername;
    @FXML Label      lblLoginStatus;

    @FXML Line arrowLine0, arrowLine1, arrowLine2, arrowLine3;

    @FXML Label lblState;
    @FXML Label p0name, p0score, p0shots;
    @FXML Label p1name, p1score, p1shots;
    @FXML Label p2name, p2score, p2shots;
    @FXML Label p3name, p3score, p3shots;

    @FXML Button btnReady;
    @FXML Button btnShoot;
    @FXML Button btnPause;

    private GameRenderer renderer;
    private GameClient   client;
    private volatile GameSnapshot currentSnapshot;
    private volatile GameState prevState;

    @FXML
    public void initialize() {
        renderer = new GameRenderer(
            mainPanel, loginPanel,
            new Line[]{arrowLine0, arrowLine1, arrowLine2, arrowLine3},
            lblState,
            new Label[][]{
                {p0name, p0score, p0shots},
                {p1name, p1score, p1shots},
                {p2name, p2score, p2shots},
                {p3name, p3score, p3shots}
            },
            btnReady, btnShoot, btnPause
        );
        renderer.setup();

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                GameSnapshot snap = currentSnapshot;
                if (snap != null) renderer.render(snap);
            }
        };
        timer.start();
    }

    @FXML
    void onConnect() {
        String username = tfUsername.getText().trim();
        if (username.isEmpty()) { lblLoginStatus.setText("Введите имя"); return; }

        InetAddress host;
        try {
            host = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            lblLoginStatus.setText("Не удалось определить адрес: " + e.getMessage());
            return;
        }

        try {
            client = new GameClient(host, GameConstants.PORT);
        } catch (IOException e) {
            lblLoginStatus.setText("Ошибка: " + e.getMessage());
            return;
        }

        client.onSnapshot   = this::onSnapshot;
        client.onError      = this::onServerError;
        client.onDisconnect = this::onDisconnect;

        renderer.setMyName(username);
        client.send(Msg.join(username));
        loginPanel.setVisible(false);
    }

    private void onSnapshot(GameSnapshot snap) {
        GameState prev = prevState;
        prevState = snap.state;
        currentSnapshot = snap;

        if (snap.state == GameState.FINISHED && snap.winner != null && prev != GameState.FINISHED) {
            Platform.runLater(() -> showWinnerDialog(snap.winner));
        }
    }

    private void showWinnerDialog(String winner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Игра окончена");
        alert.setHeaderText("Победитель: " + winner);
        alert.setContentText("Нажмите «Готов» для нового раунда.");
        alert.show();
    }

    private void onServerError(String error) {
        Platform.runLater(() -> {
            lblLoginStatus.setText(error);
            client = null;
            loginPanel.setVisible(true);
        });
    }

    private void onDisconnect() {
        Platform.runLater(() -> {
            if (client == null) return;
            lblLoginStatus.setText("Соединение потеряно");
            loginPanel.setVisible(true);
            currentSnapshot = null;
        });
    }

    @FXML void onReady() { if (client != null) client.send(Msg.ready()); }
    @FXML void onShoot() { if (client != null) client.send(Msg.shoot()); }
    @FXML void onPause() { if (client != null) client.send(Msg.pause()); }
}
