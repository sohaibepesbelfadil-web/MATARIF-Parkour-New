package com.matarif.parkour.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.matarif.parkour.model.CourseDefinition;
import com.matarif.parkour.model.PlayerRecord;
import com.matarif.parkour.util.LocationUtil;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Owns strict JSON parsing and atomic file replacement without touching Bukkit state. */
public final class JsonStore {
    private static final Type COURSE_LIST = new TypeToken<List<CourseDefinition>>() { }.getType();
    private static final Type RECORD_LIST = new TypeToken<List<PlayerRecord>>() { }.getType();

    private final Path folder;
    private final Path coursesFile;
    private final Path recordsFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JsonStore(Path folder) {
        this.folder = folder;
        this.coursesFile = folder.resolve("courses.json");
        this.recordsFile = folder.resolve("records.json");
    }

    /** Existing malformed files fail closed, so a later save cannot silently replace user data. */
    public LoadedData load() throws IOException {
        Files.createDirectories(folder);
        boolean coursesExist = Files.exists(coursesFile);
        boolean recordsExist = Files.exists(recordsFile);
        List<CourseDefinition> courses = coursesExist ? readCourses() : new ArrayList<>();
        List<PlayerRecord> records = recordsExist ? readRecords() : new ArrayList<>();
        if (!coursesExist) writeCourses("[]");
        if (!recordsExist) writeRecords("[]");
        return new LoadedData(List.copyOf(courses), List.copyOf(records));
    }

    public String encodeCourses(List<CourseDefinition> courses) {
        return gson.toJson(courses, COURSE_LIST);
    }

    public String encodeRecords(List<PlayerRecord> records) {
        return gson.toJson(records, RECORD_LIST);
    }

    public void writeCourses(String json) throws IOException {
        atomicWrite(coursesFile, json);
    }

    public void writeRecords(String json) throws IOException {
        atomicWrite(recordsFile, json);
    }

    private List<CourseDefinition> readCourses() throws IOException {
        List<CourseDefinition> values = readList(coursesFile, COURSE_LIST, "courses.json");
        Set<String> names = new HashSet<>();
        List<String> startPads = new ArrayList<>();
        for (CourseDefinition course : values) {
            if (course == null) throw invalidFile("courses.json", "contains a null course");
            try {
                course.validateStoredData();
            } catch (IllegalArgumentException exception) {
                throw invalidFile("courses.json", exception.getMessage());
            }
            String key = course.getName().toLowerCase(Locale.ROOT);
            if (!names.add(key)) throw invalidFile("courses.json", "contains duplicate course names");
            if (course.getStartLocation() != null) {
                for (String previousStart : startPads) {
                    if (LocationUtil.sameSerializedBlock(previousStart, course.getStartLocation())) {
                        throw invalidFile("courses.json", "contains start pads shared by multiple courses");
                    }
                }
                startPads.add(course.getStartLocation());
            }
        }
        return values;
    }

    private List<PlayerRecord> readRecords() throws IOException {
        List<PlayerRecord> values = readList(recordsFile, RECORD_LIST, "records.json");
        Set<String> keys = new HashSet<>();
        for (PlayerRecord record : values) {
            if (record == null || record.getUuid() == null || !CourseDefinition.isValidName(record.getCourseName())
                    || record.getBestTimeMs() <= 0 || record.getCompletedAtEpochMillis() < 0) {
                throw invalidFile("records.json", "contains an invalid player record or timing value");
            }
            String key = record.getUuid() + "|" + record.getCourseName().toLowerCase(Locale.ROOT);
            if (!keys.add(key)) throw invalidFile("records.json", "contains duplicate player/course records");
        }
        return values;
    }

    private <T> List<T> readList(Path file, Type type, String label) throws IOException {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (root == null || !root.isJsonArray()) throw invalidFile(label, "root must be a JSON array");
            List<T> values = gson.fromJson(root, type);
            return values == null ? new ArrayList<>() : values;
        } catch (JsonParseException | IllegalStateException exception) {
            throw invalidFile(label, "could not be parsed; the file was left unchanged", exception);
        }
    }

    private void atomicWrite(Path target, String content) throws IOException {
        Files.createDirectories(folder);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static IOException invalidFile(String label, String reason) {
        return new IOException(label + " " + reason + ". The file was not overwritten.");
    }

    private static IOException invalidFile(String label, String reason, Throwable cause) {
        return new IOException(label + " " + reason + ". The file was not overwritten.", cause);
    }

    public record LoadedData(List<CourseDefinition> courses, List<PlayerRecord> records) { }
}
