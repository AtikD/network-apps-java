package dev.atikdd.marksman.net;

import java.util.List;

public class Msg {
    public TypeMsg type;
    public String username;
    public GameSnapshot snapshot;
    public String error;
    public List<LeaderboardEntry> leaderboard;

    public Msg() {}

    public Msg(TypeMsg type) { this.type = type; }

    public static Msg join(String username) {
        Msg m = new Msg(TypeMsg.JOIN);
        m.username = username;
        return m;
    }

    public static Msg ready()   { return new Msg(TypeMsg.READY); }
    public static Msg shoot()   { return new Msg(TypeMsg.SHOOT); }
    public static Msg pause()   { return new Msg(TypeMsg.PAUSE); }
    public static Msg observe() { return new Msg(TypeMsg.OBSERVE); }

    public static Msg snapshot(GameSnapshot snapshot) {
        Msg m = new Msg(TypeMsg.SNAPSHOT);
        m.snapshot = snapshot;
        return m;
    }

    public static Msg error(String error) {
        Msg m = new Msg(TypeMsg.ERROR);
        m.error = error;
        return m;
    }

    public static Msg leaderboardRequest() {
        return new Msg(TypeMsg.LEADERBOARD_REQUEST);
    }

    public static Msg leaderboardResponse(List<LeaderboardEntry> entries) {
        Msg m = new Msg(TypeMsg.LEADERBOARD_RESPONSE);
        m.leaderboard = entries;
        return m;
    }
}
