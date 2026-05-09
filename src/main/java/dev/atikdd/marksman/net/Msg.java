package dev.atikdd.marksman.net;

public class Msg {
    public TypeMsg type;
    public String username;
    public GameSnapshot snapshot;
    public String error;

    public Msg() {}

    public Msg(TypeMsg type) { this.type = type; }

    public static Msg join(String username) {
        Msg m = new Msg(TypeMsg.JOIN);
        m.username = username;
        return m;
    }

    public static Msg ready()  { return new Msg(TypeMsg.READY); }
    public static Msg shoot()  { return new Msg(TypeMsg.SHOOT); }
    public static Msg pause()  { return new Msg(TypeMsg.PAUSE); }

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
}
