package dev.atikdd.marksman.server;

import dev.atikdd.marksman.net.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameServer {

    static final int PORT = 3124;
    static final int MAX_PLAYERS = 4;

    static final double T1_X = 380, T1_RADIUS = 27;
    static final double T2_X = 500, T2_RADIUS = 16;
    static final double ARROW_START_X = 39;
    static final double ARROW_LENGTH = 30;
    static final double ARROW_FIELD_END = 560;
    static final double T_MIN_Y = 0, T_MAX_Y = 334;
    static final int WIN_SCORE = 6;

    enum State { WAITING, PLAYING, PAUSED, FINISHED }

    final List<PlayerSession> sessions = new CopyOnWriteArrayList<>();

    volatile State state = State.WAITING;
    volatile double t1y = 100, t2y = 200;
    volatile boolean t1up = true, t2up = true;
    volatile String winner = null;

    Thread gameThread;

    public void start() {
        try (ServerSocket ss = new ServerSocket(PORT, 0, InetAddress.getLocalHost())) {
            System.out.println("Сервер запущен на порту " + PORT);
            while (true) {
                Socket cs = ss.accept();
                System.out.println("Подключение: " + cs.getPort());
                synchronized (this) {
                    if (sessions.size() >= MAX_PLAYERS) {
                        try { cs.close(); } catch (IOException ignored) {}
                        System.out.println("Отклонено: сервер заполнен");
                    } else {
                        try {
                            int idx = freeIndex();
                            PlayerSession session = new PlayerSession(cs, this, idx);
                            sessions.add(session);
                        } catch (IOException e) {
                            System.err.println("Ошибка сессии: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Сервер упал: " + e.getMessage());
        }
    }

    synchronized void onMessage(PlayerSession session, Msg msg) {
        switch (msg.type) {
            case JOIN     -> handleJoin(session, msg.username);
            case READY    -> handleReady(session);
            case SHOOT    -> handleShoot(session);
            case PAUSE    -> handlePause(session);
            case SNAPSHOT, ERROR -> {} // server-to-client only, ignore if received
        }
    }

    private void handleJoin(PlayerSession session, String username) {
        for (PlayerSession s : sessions) {
            if (username != null && username.equals(s.name) && s != session) {
                session.send(Msg.error("Имя «" + username + "» уже занято"));
                return;
            }
        }
        session.name = username;
        recalcSpawnPositions();
        System.out.println("Игрок: " + username);
        broadcast(buildSnapshot());
    }

    private void handleReady(PlayerSession session) {
        session.ready = true;
        session.paused = false;

        if (state == State.WAITING || state == State.FINISHED) {
            List<PlayerSession> named = namedSessions();
            if (!named.isEmpty() && named.stream().allMatch(s -> s.ready)) {
                startGame();
            } else {
                broadcast(buildSnapshot());
            }
        } else if (state == State.PAUSED) {
            if (sessions.stream().noneMatch(s -> s.paused)) {
                resumeGame();
            } else {
                broadcast(buildSnapshot());
            }
        }
    }

    private void handleShoot(PlayerSession session) {
        if (state != State.PLAYING || session.arrowActive || session.name == null) return;

        session.shots++;
        session.arrowX = ARROW_START_X;
        session.arrowActive = true;
        broadcast(buildSnapshot());

        session.arrowThread = new Thread(() -> {
            double arrowY = session.playerY;
            while (session.arrowActive) {
                session.arrowX += 3;

                if (session.arrowX + ARROW_LENGTH > ARROW_FIELD_END) {
                    session.arrowActive = false;
                    broadcast(buildSnapshot());
                    break;
                }

                double tipX = session.arrowX + ARROW_LENGTH;

                double dx1 = tipX - T1_X, dy1 = arrowY - t1y;
                if (Math.sqrt(dx1 * dx1 + dy1 * dy1) < T1_RADIUS) {
                    session.arrowActive = false;
                    session.score++;
                    checkWin(session);
                    break;
                }

                double dx2 = tipX - T2_X, dy2 = arrowY - t2y;
                if (Math.sqrt(dx2 * dx2 + dy2 * dy2) < T2_RADIUS) {
                    session.arrowActive = false;
                    session.score += 2;
                    checkWin(session);
                    break;
                }

                Thread gt = gameThread;
                if (gt != null) {
                    synchronized (gt) {
                        if (state == State.PAUSED) {
                            try {
                                gt.wait();
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                    }
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
            session.arrowActive = false;
        });
        session.arrowThread.setDaemon(true);
        session.arrowThread.start();
    }

    private void handlePause(PlayerSession session) {
        if (state != State.PLAYING || session.name == null) return;
        state = State.PAUSED;
        session.paused = true;
        session.ready = false;
        broadcast(buildSnapshot());
    }

    private synchronized void checkWin(PlayerSession session) {
        if (session.score >= WIN_SCORE) {
            winner = session.name;
            state = State.FINISHED;
            stopGameThread();
            for (PlayerSession s : sessions) s.ready = false;
        }
        broadcast(buildSnapshot());
    }

    private void startGame() {
        state = State.PLAYING;
        winner = null;
        recalcSpawnPositions();
        for (PlayerSession s : sessions) {
            s.score = 0;
            s.shots = 0;
            s.ready = false;
            s.paused = false;
        }
        t1y = 100; t1up = true;
        t2y = 200; t2up = true;

        gameThread = new Thread(() -> {
            while (state == State.PLAYING || state == State.PAUSED) {
                if (state == State.PLAYING) {
                    t1y += t1up ? 1 : -1;
                    if (t1y <= T_MIN_Y || t1y >= T_MAX_Y) t1up = !t1up;

                    t2y += t2up ? 1 : -1;
                    if (t2y <= T_MIN_Y || t2y >= T_MAX_Y) t2up = !t2up;

                    broadcast(buildSnapshot());
                }

                synchronized (gameThread) {
                    if (state == State.PAUSED) {
                        try {
                            gameThread.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        gameThread.setDaemon(true);
        gameThread.start();
        broadcast(buildSnapshot());
    }

    private void resumeGame() {
        state = State.PLAYING;
        Thread gt = gameThread;
        if (gt != null) {
            synchronized (gt) {
                gt.notifyAll();
            }
        }
        // wake arrow threads
        for (PlayerSession s : sessions) {
            if (s.arrowThread != null && s.arrowActive) {
                synchronized (gt != null ? gt : this) {
                    gt.notifyAll();
                }
            }
        }
        broadcast(buildSnapshot());
    }

    private void stopGameThread() {
        if (gameThread != null) {
            gameThread.interrupt();
            gameThread = null;
        }
        for (PlayerSession s : sessions) {
            s.arrowActive = false;
            if (s.arrowThread != null) s.arrowThread.interrupt();
        }
    }

    synchronized void onDisconnect(PlayerSession session) {
        if (!sessions.contains(session)) return;
        sessions.remove(session);
        session.close();
        System.out.println("Отключился: " + session.name);
        if (namedSessions().isEmpty() && state != State.WAITING) {
            stopGameThread();
            state = State.WAITING;
        }
        if (state == State.WAITING || state == State.FINISHED) {
            recalcSpawnPositions();
        }
        broadcast(buildSnapshot());
    }

    void broadcast(Msg msg) {
        for (PlayerSession s : sessions) {
            if (s.name != null) s.send(msg);
        }
    }

    Msg buildSnapshot() {
        GameSnapshot snap = new GameSnapshot();
        snap.state = state.name();
        snap.t1y = t1y;
        snap.t2y = t2y;
        snap.winner = winner;

        List<PlayerInfo> players = new ArrayList<>();
        for (PlayerSession s : sessions) {
            if (s.name != null) players.add(new PlayerInfo(s.name, s.score, s.shots, s.ready, s.playerY));
        }
        snap.players = players;

        List<ArrowInfo> arrows = new ArrayList<>();
        for (PlayerSession s : sessions) {
            if (s.arrowActive) arrows.add(new ArrowInfo(s.name, s.arrowX, s.playerY));
        }
        snap.arrows = arrows;

        return Msg.snapshot(snap);
    }

    private void recalcSpawnPositions() {
        List<PlayerSession> named = namedSessions();
        int n = named.size();
        if (n == 0) return;
        for (int r = 0; r < n; r++) {
            named.get(r).playerY = T_MAX_Y * (r + 0.5) / n;
        }
    }

    private int freeIndex() {
        Set<Integer> used = new HashSet<>();
        for (PlayerSession s : sessions) used.add(s.index);
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!used.contains(i)) return i;
        }
        return 0;
    }

    private List<PlayerSession> namedSessions() {
        return sessions.stream().filter(s -> s.name != null).toList();
    }
}
