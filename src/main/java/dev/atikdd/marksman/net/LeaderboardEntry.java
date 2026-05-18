package dev.atikdd.marksman.net;

public class LeaderboardEntry {
    public String name;
    public int wins;

    public LeaderboardEntry() {}

    public LeaderboardEntry(String name, int wins) {
        this.name = name;
        this.wins = wins;
    }
}
