package com.matarif.parkour.model;

import java.util.UUID;

/** A player's best valid completion time for one course, plus display and completion metadata. */
public final class PlayerRecord {
    private UUID uuid;
    private String courseName;
    private long bestTimeMs;
    private String playerName;
    private long completedAtEpochMillis;

    public PlayerRecord() { }

    public PlayerRecord(UUID uuid, String courseName, long bestTimeMs,
                        String playerName, long completedAtEpochMillis) {
        this.uuid = uuid;
        this.courseName = courseName;
        this.bestTimeMs = bestTimeMs;
        this.playerName = playerName;
        this.completedAtEpochMillis = completedAtEpochMillis;
    }

    public UUID getUuid() { return uuid; }
    public String getCourseName() { return courseName; }
    public long getBestTimeMs() { return bestTimeMs; }
    public String getPlayerName() { return playerName; }
    public long getCompletedAtEpochMillis() { return completedAtEpochMillis; }
}

