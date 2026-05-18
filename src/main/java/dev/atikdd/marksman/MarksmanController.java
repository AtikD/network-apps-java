package dev.atikdd.marksman;

import dev.atikdd.marksman.net.GameConstants;
import dev.atikdd.marksman.net.GameSnapshot;
import dev.atikdd.marksman.net.GameState;
import dev.atikdd.marksman.net.LeaderboardEntry;
import dev.atikdd.marksman.net.Msg;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Line;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

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
    @FXML Button btnLeaderboard;

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

        client.onSnapshot    = this::onSnapshot;
        client.onError       = this::onServerError;
        client.onDisconnect  = this::onDisconnect;
        client.onLeaderboard = this::onLeaderboardResponse;

        renderer.setMyName(username);
        client.send(Msg.join(username));
        loginPanel.setVisible(false);
        btnLeaderboard.setDisable(false);
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
            btnLeaderboard.setDisable(true);
            loginPanel.setVisible(true);
        });
    }

    private void onDisconnect() {
        Platform.runLater(() -> {
            if (client == null) return;
            lblLoginStatus.setText("Соединение потеряно");
            btnLeaderboard.setDisable(true);
            loginPanel.setVisible(true);
            currentSnapshot = null;
        });
    }

    private void onLeaderboardResponse(List<LeaderboardEntry> entries) {
        Platform.runLater(() -> showLeaderboard(entries));
    }

    private void showLeaderboard(List<LeaderboardEntry> entries) {
        TableView<LeaderboardEntry> table = new TableView<>();

        TableColumn<LeaderboardEntry, String> nameCol = new TableColumn<>("Игрок");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        nameCol.setPrefWidth(180);

        TableColumn<LeaderboardEntry, Integer> winsCol = new TableColumn<>("Победы");
        winsCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().wins).asObject());
        winsCol.setPrefWidth(80);

        table.getColumns().add(nameCol);
        table.getColumns().add(winsCol);
        if (entries != null) table.getItems().addAll(entries);
        table.setPrefHeight(250);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Таблица лидеров");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(table);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.show();
    }

    @FXML void onReady()       { if (client != null) client.send(Msg.ready()); }
    @FXML void onShoot()       { if (client != null) client.send(Msg.shoot()); }
    @FXML void onPause()       { if (client != null) client.send(Msg.pause()); }
    @FXML void onLeaderboard() { if (client != null) client.send(Msg.leaderboardRequest()); }
}
