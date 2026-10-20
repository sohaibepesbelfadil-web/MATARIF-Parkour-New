package com.matarif.parkour.storage;

import com.matarif.parkour.model.CourseDefinition;
import com.matarif.parkour.model.PlayerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks initialization, round trips, legacy loading, and non-destructive corrupt-file handling. */
class JsonStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void initializesMissingFilesAndRoundTripsValidatedData() throws IOException {
        JsonStore store = new JsonStore(tempDirectory);
        JsonStore.LoadedData empty = store.load();
        assertTrue(empty.courses().isEmpty());
        assertTrue(empty.records().isEmpty());
        assertTrue(Files.exists(tempDirectory.resolve("courses.json")));
        assertTrue(Files.exists(tempDirectory.resolve("records.json")));

        CourseDefinition course = new CourseDefinition("CloudHop", 40)
                .withStartLocation("world|0|64|0|0|0")
                .withFinishLocation("world|5|64|0|0|0");
        store.writeCourses(store.encodeCourses(java.util.List.of(course)));
        JsonStore.LoadedData loaded = store.load();
        assertEquals("CloudHop", loaded.courses().getFirst().getName());
        try (var files = Files.list(tempDirectory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().contains(".tmp-")));
        }
    }

    @Test
    void readsExistingLegacyCourseJson() throws IOException {
        Files.writeString(tempDirectory.resolve("courses.json"), """
                [{"name":"Legacy","startLocation":"world|0|64|0|0|0","finishLocation":"world|4|64|0|0|0","failY":40.0,"checkpoints":[{"number":1,"location":"world|2|64|0|0|0"}]}]
                """);
        JsonStore.LoadedData loaded = new JsonStore(tempDirectory).load();

        assertEquals("Legacy", loaded.courses().getFirst().getName());
        assertEquals(1, loaded.courses().getFirst().getCheckpoints().size());
    }

    @Test
    void persistsLeaderboardIdentityTimeAndCompletionMetadataAcrossReloads() throws IOException {
        JsonStore store = new JsonStore(tempDirectory);
        store.load();
        PlayerRecord record = new PlayerRecord(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "CloudHop", 12_345, "ParkourFan", 1_700_000_000_000L);
        store.writeRecords(store.encodeRecords(List.of(record)));

        PlayerRecord loaded = new JsonStore(tempDirectory).load().records().getFirst();
        assertEquals(record.getUuid(), loaded.getUuid());
        assertEquals("CloudHop", loaded.getCourseName());
        assertEquals(12_345, loaded.getBestTimeMs());
        assertEquals("ParkourFan", loaded.getPlayerName());
        assertEquals(1_700_000_000_000L, loaded.getCompletedAtEpochMillis());
    }

    @Test
    void preservesMalformedFilesInsteadOfReplacingThem() throws IOException {
        Path coursesFile = tempDirectory.resolve("courses.json");
        Files.writeString(coursesFile, "{ definitely not json");

        assertThrows(IOException.class, () -> new JsonStore(tempDirectory).load());
        assertEquals("{ definitely not json", Files.readString(coursesFile));
        assertFalse(Files.exists(tempDirectory.resolve("records.json")));
    }

    @Test
    void rejectsInvalidPersistedTimesWithoutOverwritingTheRecordFile() throws IOException {
        Path recordsFile = tempDirectory.resolve("records.json");
        String invalidRecord = """
                [{"uuid":"00000000-0000-0000-0000-000000000001","courseName":"CloudHop","bestTimeMs":0}]
                """;
        Files.writeString(recordsFile, invalidRecord);

        assertThrows(IOException.class, () -> new JsonStore(tempDirectory).load());
        assertEquals(invalidRecord, Files.readString(recordsFile));
        assertFalse(Files.exists(tempDirectory.resolve("courses.json")));
    }
}
