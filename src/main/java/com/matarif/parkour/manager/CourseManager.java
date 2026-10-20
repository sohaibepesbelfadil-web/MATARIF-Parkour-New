package com.matarif.parkour.manager;

import com.matarif.parkour.model.Checkpoint;
import com.matarif.parkour.model.CourseDefinition;
import com.matarif.parkour.storage.StorageManager;
import com.matarif.parkour.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Applies course setup rules and maintains an indexed start-pad lookup for movement events. */
public final class CourseManager {
    private final StorageManager storage;
    private final Map<String, CourseDefinition> startsByBlock = new HashMap<>();

    public CourseManager(StorageManager storage) {
        this.storage = storage;
        rebuildStartIndex();
    }

    public CourseDefinition get(String name) { return storage.getCourse(name); }

    public Collection<CourseDefinition> all() {
        List<CourseDefinition> courses = new ArrayList<>(storage.getCourses());
        courses.sort(Comparator.comparing(course -> course.getName(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(courses);
    }

    public boolean create(String name, double defaultFailY) {
        if (!CourseDefinition.isValidName(name) || !Double.isFinite(defaultFailY)) return false;
        boolean created = storage.createCourse(new CourseDefinition(name, defaultFailY));
        if (created) rebuildStartIndex();
        return created;
    }

    public CourseDefinition findStart(Location location) {
        String key = LocationUtil.blockKey(location);
        return key == null ? null : startsByBlock.get(key);
    }

    public PadUpdateResult setPad(String courseName, PadType type, Location location) {
        if (type == PadType.CHECKPOINT) return PadUpdateResult.INVALID_LOCATION;
        CourseDefinition course = get(courseName);
        if (course == null) return PadUpdateResult.COURSE_MISSING;
        if (!LocationUtil.isSafePad(location)) return PadUpdateResult.INVALID_LOCATION;
        String encoded = LocationUtil.serializePad(location);
        if (!sameWorldAsConfiguredPads(course, type, location.getWorld())) return PadUpdateResult.DIFFERENT_WORLD;
        if (overlapsAnotherPad(course, type, encoded)) return PadUpdateResult.DUPLICATE_PAD;
        if (type == PadType.START && startsAlreadyUsedByAnotherCourse(course, encoded)) {
            return PadUpdateResult.START_ALREADY_USED;
        }

        CourseDefinition updated = type == PadType.START
                ? course.withStartLocation(encoded)
                : course.withFinishLocation(encoded);
        if (!storage.updateCourse(updated)) return PadUpdateResult.COURSE_MISSING;
        rebuildStartIndex();
        return PadUpdateResult.SAVED;
    }

    public PadUpdateResult addCheckpoint(String courseName, int number, Location location) {
        CourseDefinition course = get(courseName);
        if (course == null) return PadUpdateResult.COURSE_MISSING;
        int expected = course.getCheckpoints().size() + 1;
        if (number != expected) return PadUpdateResult.INVALID_CHECKPOINT_NUMBER;
        if (!LocationUtil.isSafePad(location)) return PadUpdateResult.INVALID_LOCATION;
        if (!sameWorldAsConfiguredPads(course, null, location.getWorld())) return PadUpdateResult.DIFFERENT_WORLD;
        String encoded = LocationUtil.serializePad(location);
        if (overlapsAnotherPad(course, PadType.CHECKPOINT, encoded)) return PadUpdateResult.DUPLICATE_PAD;
        CourseDefinition updated = course.withCheckpoint(new Checkpoint(number, encoded));
        if (!storage.updateCourse(updated)) return PadUpdateResult.COURSE_MISSING;
        rebuildStartIndex();
        return PadUpdateResult.SAVED;
    }

    public boolean setFailY(String courseName, double y, World commandWorld) {
        CourseDefinition course = get(courseName);
        if (course == null || commandWorld == null || !Double.isFinite(y)
                || y < commandWorld.getMinHeight() || y >= commandWorld.getMaxHeight()) return false;
        if (!sameWorldAsConfiguredPads(course, null, commandWorld)) return false;
        for (String pad : allPadLocations(course, null)) {
            Location location = LocationUtil.deserialize(pad);
            if (location == null || y >= location.getY()) return false;
        }
        return storage.updateCourse(course.withFailY(y));
    }

    public boolean isPlayable(CourseDefinition course) {
        if (course == null || !course.isFullyConfigured()) return false;
        Location start = LocationUtil.deserialize(course.getStartLocation());
        Location finish = LocationUtil.deserialize(course.getFinishLocation());
        if (start == null || finish == null || !sameWorld(start, finish)
                || course.getFailY() < start.getWorld().getMinHeight()
                || course.getFailY() >= start.getY()) return false;
        if (!LocationUtil.isSafePadIfLoaded(start) || !LocationUtil.isSafePadIfLoaded(finish)
                || finish.getY() <= course.getFailY()) return false;
        for (Checkpoint checkpoint : course.getCheckpoints()) {
            Location location = LocationUtil.deserialize(checkpoint.getLocation());
            if (location == null || !sameWorld(start, location) || !LocationUtil.isSafePadIfLoaded(location)
                    || location.getY() <= course.getFailY()) return false;
        }
        return true;
    }

    public void rebuildStartIndex() {
        startsByBlock.clear();
        for (CourseDefinition course : storage.getCourses()) {
            Location start = LocationUtil.deserialize(course.getStartLocation());
            String key = LocationUtil.blockKey(start);
            if (key != null) startsByBlock.putIfAbsent(key, course);
        }
    }

    private boolean sameWorldAsConfiguredPads(CourseDefinition course, PadType replaced, World world) {
        if (world == null) return false;
        List<String> locations = allPadLocations(course, replaced);
        for (String encoded : locations) if (!LocationUtil.belongsToWorld(encoded, world)) return false;
        return true;
    }

    private boolean overlapsAnotherPad(CourseDefinition course, PadType target, String candidate) {
        List<String> existing = new ArrayList<>();
        if (target != PadType.START && course.getStartLocation() != null) existing.add(course.getStartLocation());
        if (target != PadType.FINISH && course.getFinishLocation() != null) existing.add(course.getFinishLocation());
        for (Checkpoint checkpoint : course.getCheckpoints()) existing.add(checkpoint.getLocation());
        return existing.stream().anyMatch(location -> LocationUtil.sameSerializedBlock(location, candidate));
    }

    private boolean startsAlreadyUsedByAnotherCourse(CourseDefinition selected, String candidate) {
        return storage.getCourses().stream()
                .filter(course -> !course.getName().equalsIgnoreCase(selected.getName()))
                .map(course -> course.getStartLocation())
                .filter(location -> location != null)
                .anyMatch(location -> LocationUtil.sameSerializedBlock(location, candidate));
    }

    private List<String> allPadLocations(CourseDefinition course, PadType replaced) {
        List<String> locations = new ArrayList<>();
        if (replaced != PadType.START && course.getStartLocation() != null) locations.add(course.getStartLocation());
        if (replaced != PadType.FINISH && course.getFinishLocation() != null) locations.add(course.getFinishLocation());
        if (replaced != PadType.CHECKPOINT) {
            for (Checkpoint checkpoint : course.getCheckpoints()) locations.add(checkpoint.getLocation());
        }
        return locations;
    }

    private boolean sameWorld(Location first, Location second) {
        return first.getWorld().getUID().equals(second.getWorld().getUID());
    }

    public enum PadType { START, FINISH, CHECKPOINT }

    public enum PadUpdateResult {
        SAVED,
        COURSE_MISSING,
        INVALID_LOCATION,
        DIFFERENT_WORLD,
        DUPLICATE_PAD,
        START_ALREADY_USED,
        INVALID_CHECKPOINT_NUMBER
    }
}
