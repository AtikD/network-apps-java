package dev.atikdd.marksman.server;

import dev.atikdd.marksman.net.*;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static dev.atikdd.marksman.net.GameConstants.*;

public class GameServer {

    static final long TICK_MS = 10;

    final List<PlayerSession> sessions = new CopyOnWriteArrayList<>();
    final List<PlayerSession> pendingSessions = new CopyOnWriteArrayList<>();

    volatile GameState state = GameState.WAITING;
    volatile double t1y = TARGET1_START_Y, t2y = TARGET2_START_Y;
    volatile boolean t1up = true, t2up = true;
    volatile String winner = null;

    public void start() {
        HibernateUtil.getSessionFactory();

        Thread tickThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                tick();
                try {
                    Thread.sleep(TICK_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "game-tick");
        tickThread.setDaemon(true);
        tickThread.start();

        try (ServerSocket ss = new ServerSocket(PORT, 0, InetAddress.getLocalHost())) {
            System.out.println("Сервер запущен на порту " + PORT);
            while (true) {
                Socket cs = ss.accept();
                System.out.println("Подключение: " + cs.getPort());
                try {
                    PlayerSession session = new PlayerSession(cs, this);
                    synchronized (this) { pendingSessions.add(session); }
                } catch (IOException e) {
                    System.err.println("Ошибка сессии: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Сервер упал: " + e.getMessage());
        } finally {
            HibernateUtil.shutdown();
        }
    }

    private void tick() {
        Msg snap;
        synchronized (this) {
            if (state != GameState.PLAYING) return;

            t1y += t1up ? 1 : -1;
            if (t1y <= TARGET_MIN_Y || t1y >= TARGET_MAX_Y) t1up = !t1up;
            t2y += t2up ? 1 : -1;
            if (t2y <= TARGET_MIN_Y || t2y >= TARGET_MAX_Y) t2up = !t2up;

            for (PlayerSession s : sessions) {
                if (s.arrowActive) advanceArrow(s);
            }

            snap = buildSnapshot();
        }
        broadcast(snap);
    }

    private void advanceArrow(PlayerSession s) {
        s.arrowX += ARROW_SPEED;
        if (s.arrowX + ARROW_LENGTH > ARROW_FIELD_END) {
            s.arrowActive = false;
            return;
        }
        double tipX = s.arrowX + ARROW_LENGTH;

        double dx1 = tipX - TARGET1_X, dy1 = s.arrowY - t1y;
        if (Math.sqrt(dx1 * dx1 + dy1 * dy1) < TARGET1_RADIUS) {
            s.arrowActive = false;
            s.score++;
            checkWin(s);
            return;
        }

        double dx2 = tipX - TARGET2_X, dy2 = s.arrowY - t2y;
        if (Math.sqrt(dx2 * dx2 + dy2 * dy2) < TARGET2_RADIUS) {
            s.arrowActive = false;
            s.score += 2;
            checkWin(s);
        }
    }

    synchronized void onMessage(PlayerSession session, Msg msg) {
        if (msg.type != TypeMsg.JOIN && session.name == null) return;
        switch (msg.type) {
            case JOIN              -> handleJoin(session, msg.username);
            case READY             -> handleReady(session);
            case SHOOT             -> handleShoot(session);
            case PAUSE             -> handlePause(session);
            case LEADERBOARD_REQUEST -> handleLeaderboard(session);
            case SNAPSHOT, ERROR, LEADERBOARD_RESPONSE -> {}
        }
    }

    private void handleJoin(PlayerSession session, String username) {
        if (username == null || username.isEmpty()) {
            session.send(Msg.error("Введите имя"));
            return;
        }
        if (sessions.size() >= MAX_PLAYERS) {
            session.send(Msg.error("Сервер заполнен"));
            pendingSessions.remove(session);
            session.close();
            return;
        }
        for (PlayerSession s : sessions) {
            if (username.equals(s.name)) {
                session.send(Msg.error("Имя «" + username + "» уже занято"));
                pendingSessions.remove(session);
                session.close();
                return;
            }
        }
        session.index = freeIndex();
        session.name = username;
        pendingSessions.remove(session);
        sessions.add(session);
        recalcSpawnPositions();
        ensurePlayerInDb(username);
        System.out.println("Игрок: " + username);
        broadcast(buildSnapshot());
    }

    private void handleReady(PlayerSession session) {
        session.ready = true;

        if (state == GameState.WAITING || state == GameState.FINISHED) {
            if (!sessions.isEmpty() && sessions.stream().allMatch(s -> s.ready)) {
                startGame();
                return;
            }
        } else if (state == GameState.PAUSED) {
            if (sessions.stream().allMatch(s -> s.ready)) {
                state = GameState.PLAYING;
            }
        }
        broadcast(buildSnapshot());
    }

    private void handleShoot(PlayerSession session) {
        if (state != GameState.PLAYING || session.arrowActive) return;
        session.shots++;
        session.arrowX = ARROW_START_X;
        session.arrowY = session.playerY;
        session.arrowActive = true;
    }

    private void handlePause(PlayerSession session) {
        if (state != GameState.PLAYING) return;
        state = GameState.PAUSED;
        for (PlayerSession s : sessions) s.ready = false;
        broadcast(buildSnapshot());
    }

    private void handleLeaderboard(PlayerSession session) {
        if (state == GameState.PLAYING) {
            state = GameState.PAUSED;
            for (PlayerSession s : sessions) s.ready = false;
            broadcast(buildSnapshot());
        }
        session.send(Msg.leaderboardResponse(getLeaderboard()));
    }

    private void checkWin(PlayerSession session) {
        if (session.score >= WIN_SCORE) {
            winner = session.name;
            state = GameState.FINISHED;
            for (PlayerSession s : sessions) {
                s.ready = false;
                s.arrowActive = false;
            }
            incrementWins(session.name);
        }
    }

    private void startGame() {
        state = GameState.PLAYING;
        winner = null;
        recalcSpawnPositions();
        for (PlayerSession s : sessions) {
            s.score = 0;
            s.shots = 0;
            s.ready = false;
            s.arrowActive = false;
        }
        t1y = TARGET1_START_Y; t1up = true;
        t2y = TARGET2_START_Y; t2up = true;
        broadcast(buildSnapshot());
    }

    synchronized void onDisconnect(PlayerSession session) {
        boolean wasRegistered = sessions.remove(session);
        boolean wasPending = pendingSessions.remove(session);
        if (!wasRegistered && !wasPending) return;
        session.close();
        if (wasRegistered) {
            System.out.println("Отключился: " + session.name);
        }
        if (sessions.isEmpty() && state != GameState.WAITING) {
            state = GameState.WAITING;
        }
        if (state == GameState.WAITING || state == GameState.FINISHED) {
            recalcSpawnPositions();
        }
        broadcast(buildSnapshot());
    }

    void broadcast(Msg msg) {
        for (PlayerSession s : sessions) {
            s.send(msg);
        }
    }

    Msg buildSnapshot() {
        GameSnapshot snap = new GameSnapshot();
        snap.state = state;
        snap.t1y = t1y;
        snap.t2y = t2y;
        snap.winner = winner;

        List<PlayerInfo> players = new ArrayList<>();
        for (PlayerSession s : sessions) {
            players.add(new PlayerInfo(s.index, s.name, s.score, s.shots, s.ready, s.playerY));
        }
        snap.players = players;

        List<ArrowInfo> arrows = new ArrayList<>();
        for (PlayerSession s : sessions) {
            if (s.arrowActive) arrows.add(new ArrowInfo(s.index, s.arrowX, s.arrowY));
        }
        snap.arrows = arrows;

        return Msg.snapshot(snap);
    }

    private void recalcSpawnPositions() {
        int n = sessions.size();
        if (n == 0) return;
        for (int r = 0; r < n; r++) {
            sessions.get(r).playerY = TARGET_MAX_Y * (r + 0.5) / n;
        }
    }

    private int freeIndex() {
        boolean[] used = new boolean[MAX_PLAYERS];
        for (PlayerSession s : sessions) if (s.index >= 0) used[s.index] = true;
        for (PlayerSession s : pendingSessions) if (s.index >= 0) used[s.index] = true;
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!used[i]) return i;
        }
        throw new IllegalStateException("Нет свободных слотов");
    }

    private void ensurePlayerInDb(String username) {
        try (Session hibSession = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = hibSession.beginTransaction();
            if (hibSession.find(PlayerEntity.class, username) == null) {
                hibSession.persist(new PlayerEntity(username));
            }
            tx.commit();
        } catch (Exception e) {
            System.err.println("DB error: " + e.getMessage());
        }
    }

    private void incrementWins(String username) {
        try (Session hibSession = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = hibSession.beginTransaction();
            PlayerEntity player = hibSession.find(PlayerEntity.class, username);
            player.setWins(player.getWins() + 1);
            tx.commit();
        } catch (Exception e) {
            System.err.println("DB error: " + e.getMessage());
        }
    }

    private List<LeaderboardEntry> getLeaderboard() {
        try (Session hibSession = HibernateUtil.getSessionFactory().openSession()) {
            return hibSession
                .createQuery("FROM PlayerEntity ORDER BY wins DESC", PlayerEntity.class)
                .getResultList()
                .stream()
                .map(p -> new LeaderboardEntry(p.getUsername(), p.getWins()))
                .toList();
        } catch (Exception e) {
            System.err.println("DB error: " + e.getMessage());
            return List.of();
        }
    }
}
