package dev.atikdd.marksman.net;

public class PlayerInfo {
    public int index;
    public String name;
    public int score;
    public int shots;
    public boolean ready;
    public double y;

    public PlayerInfo() {}

    public PlayerInfo(int index, String name, int score, int shots, boolean ready, double y) {
        this.index = index;
        this.name = name;
        this.score = score;
        this.shots = shots;
        this.ready = ready;
        this.y = y;
    }
}
