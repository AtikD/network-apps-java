package dev.atikdd.marksman.net;

import java.util.List;

public class GameSnapshot {
    public String state;
    public List<PlayerInfo> players;
    public double t1y;
    public double t2y;
    public List<ArrowInfo> arrows;
    public String winner;

    public GameSnapshot() {}
}
