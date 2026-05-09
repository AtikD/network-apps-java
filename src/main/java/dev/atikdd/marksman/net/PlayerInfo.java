package dev.atikdd.marksman.net;

public class PlayerInfo {
    public String name;
    public int score;
    public int shots;
    public boolean ready;
    public double y;

    public PlayerInfo() {}

    public PlayerInfo(String name, int score, int shots, boolean ready, double y) {
        this.name = name;
        this.score = score;
        this.shots = shots;
        this.ready = ready;
        this.y = y;
    }
}
