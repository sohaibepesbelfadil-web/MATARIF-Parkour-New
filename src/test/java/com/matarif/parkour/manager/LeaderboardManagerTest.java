package com.matarif.parkour.manager;

import com.matarif.parkour.model.PlayerRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Confirms fastest-three ordering remains deterministic when completion times tie. */
class LeaderboardManagerTest {
    @Test
    void returnsFastestThreeAndUsesPlayerNameForStableTies() {
        List<PlayerRecord> records = List.of(
                record("00000000-0000-0000-0000-000000000004", "Zed", 1000),
                record("00000000-0000-0000-0000-000000000003", "Cleo", 900),
                record("00000000-0000-0000-0000-000000000002", "Alex", 1000),
                record("00000000-0000-0000-0000-000000000001", "Milo", 1200));

        List<PlayerRecord> top = LeaderboardManager.topThreeFrom(records);

        assertEquals(List.of("Cleo", "Alex", "Zed"), top.stream().map(record -> record.getPlayerName()).toList());
    }

    @Test
    void legacyRecordsUseAnOfflineSafeUuidLabelWhenTheyHaveNoSavedName() {
        PlayerRecord legacy = new PlayerRecord(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "CloudHop", 1000, null, 0);

        assertEquals("00000000", LeaderboardManager.playerName(legacy));
    }

    private PlayerRecord record(String id, String name, long millis) {
        return new PlayerRecord(UUID.fromString(id), "CloudHop", millis, name, 1_700_000_000_000L);
    }
}
