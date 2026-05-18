package dev.atikdd.marksman.server;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "players")
class PlayerEntity {

    @Id
    @Column(length = 50)
    private String username;

    @Column(nullable = false)
    private int wins = 0;

    PlayerEntity() {}

    PlayerEntity(String username) { this.username = username; }

    String getUsername() { return username; }
    int getWins() { return wins; }
    void setWins(int wins) { this.wins = wins; }
}
