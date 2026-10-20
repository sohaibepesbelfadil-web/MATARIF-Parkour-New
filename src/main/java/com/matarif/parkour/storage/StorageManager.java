package com.matarif.parkour.storage;

import com.matarif.parkour.model.CourseDefinition;
import com.matarif.parkour.model.PlayerRecord;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Keeps validated data in memory and serializes immutable JSON snapshots on one background writer. */
public final class StorageManager {
    private final JavaPlugin plugin;
    private final JsonStore jsonStore;
    private final ExecutorService writer;
    private final Map<String, CourseDefinition> courses = new LinkedHashMap<>();
    private final Map<String, PlayerRecord> records = new LinkedHashMap<>();
    private boolean closing;

    public StorageManager(JavaPlugin plugin) throws IOException {
        this.plugin = plugin;
        this.jsonStore = new JsonStore(plugin.getDataFolder().toPath());
        JsonStore.LoadedData loaded = jsonStore.load();
        for (CourseDefinition course : loaded.courses()) courses.put(courseKey(course.getName()), course.copy());
        for (PlayerRecord record : loaded.records()) records.put(recordKey(record.getUuid(), record.getCourseName()), record);
        this.writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "MATARIF-Parkour-Storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized Collection<CourseDefinition> getCourses() {
        return courses.values().stream().map(course -> course.copy()).toList();
    }

    public synchronized CourseDefinition getCourse(String name) {
        CourseDefinition course = courses.get(courseKey(name));
        return course == null ? null : course.copy();
    }

    public synchronized boolean createCourse(CourseDefinition course) {
        if (closing || course == null) return false;
        course.validateStoredData();
        String key = courseKey(course.getName());
        if (courses.containsKey(key)) return false;
        courses.put(key, course.copy());
        saveCourses();
        return true;
    }

    public synchronized boolean updateCourse(CourseDefinition course) {
        if (closing || course == null) return false;
        course.validateStoredData();
        String key = courseKey(course.getName());
        if (!courses.containsKey(key)) return false;
        courses.put(key, course.copy());
        saveCourses();
        return true;
    }

    public synchronized PlayerRecord getRecord(UUID uuid, String courseName) {
        return records.get(recordKey(uuid, courseName));
    }

    public synchronized List<PlayerRecord> getRecords(String courseName) {
        return records.values().stream()
                .filter(record -> record.getCourseName().equalsIgnoreCase(courseName))
                .toList();
    }

    public synchronized boolean saveBest(UUID uuid, String courseName, long millis,
                                          String playerName, long completedAtEpochMillis) {
        if (closing || uuid == null || !CourseDefinition.isValidName(courseName)
                || millis <= 0 || completedAtEpochMillis < 0) return false;
        String key = recordKey(uuid, courseName);
        PlayerRecord current = records.get(key);
        if (current != null && current.getBestTimeMs() <= millis) return false;
        records.put(key, new PlayerRecord(uuid, courseName, millis, playerName, completedAtEpochMillis));
        saveRecords();
        return true;
    }

    private void saveCourses() {
        String snapshot = jsonStore.encodeCourses(new ArrayList<>(courses.values()));
        submit(() -> jsonStore.writeCourses(snapshot), "courses.json");
    }

    private void saveRecords() {
        String snapshot = jsonStore.encodeRecords(new ArrayList<>(records.values()));
        submit(() -> jsonStore.writeRecords(snapshot), "records.json");
    }

    private void submit(IoAction action, String fileName) {
        if (closing) return;
        writer.execute(() -> {
            try {
                action.run();
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not save " + fileName + ". The in-memory change remains active; check disk access and restart after fixing it.",
                        exception);
            }
        });
    }

    /** Queues a final snapshot, then waits for every earlier and final write before plugin shutdown. */
    public void flushAndShutdown() {
        synchronized (this) {
            if (closing) return;
            String courseSnapshot = jsonStore.encodeCourses(new ArrayList<>(courses.values()));
            String recordSnapshot = jsonStore.encodeRecords(new ArrayList<>(records.values()));
            writer.execute(() -> writeFinal(() -> jsonStore.writeCourses(courseSnapshot), "courses.json"));
            writer.execute(() -> writeFinal(() -> jsonStore.writeRecords(recordSnapshot), "records.json"));
            closing = true;
            writer.shutdown();
        }
        try {
            if (!writer.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Parkour data writes did not finish before shutdown; unsaved data may remain in memory only.");
                writer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.SEVERE, "Interrupted while waiting for parkour data writes to finish.", exception);
            writer.shutdownNow();
        }
    }

    private void writeFinal(IoAction action, String fileName) {
        try {
            action.run();
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Final save of " + fileName + " failed.", exception);
        }
    }

    private static String courseKey(String courseName) {
        return courseName == null ? "" : courseName.toLowerCase(Locale.ROOT);
    }

    private static String recordKey(UUID uuid, String courseName) {
        return uuid + "|" + courseKey(courseName);
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }
}
