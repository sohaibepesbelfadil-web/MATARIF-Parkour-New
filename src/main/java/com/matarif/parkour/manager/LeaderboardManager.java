package com.matarif.parkour.manager;

import com.matarif.parkour.model.PlayerRecord;
import com.matarif.parkour.storage.StorageManager;

import java.util.Comparator;
import java.util.List;

/** Returns stable course rankings and a cached player name for each personal best. */
public final class LeaderboardManager {
    private static final Comparator<PlayerRecord> ORDER = Comparator
            .comparingLong((PlayerRecord record) -> record.getBestTimeMs())
            .thenComparing(record -> record.getPlayerName() == null ? "" : record.getPlayerName(),
                    String.CASE_INSENSITIVE_ORDER)
            .thenComparing(record -> record.getUuid().toString());

    private final StorageManager storage;

    public LeaderboardManager(StorageManager storage) { this.storage = storage; }

    public List<PlayerRecord> topThree(String courseName) {
        return topThreeFrom(storage.getRecords(courseName));
    }

    public static List<PlayerRecord> topThreeFrom(List<PlayerRecord> records) {
        return records.stream().filter(record -> record.getBestTimeMs() > 0)
                .sorted(ORDER).limit(3).toList();
    }

    public static String playerName(PlayerRecord record) {
        if (record.getPlayerName() != null && !record.getPlayerName().isBlank()) return record.getPlayerName();
        return record.getUuid().toString().substring(0, 8);
    }
}
