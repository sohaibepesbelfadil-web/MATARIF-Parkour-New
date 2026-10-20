package com.matarif.parkour.model;

import com.matarif.parkour.util.LocationUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Saved course configuration. Updates return a new value so live runs keep a stable route snapshot. */
public final class CourseDefinition {
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private String name;
    private String startLocation;
    private String finishLocation;
    private double failY = 60.0;
    private List<Checkpoint> checkpoints = new ArrayList<>();

    public CourseDefinition() { }

    public CourseDefinition(String name, double failY) {
        this.name = name;
        this.failY = failY;
    }

    private CourseDefinition(String name, String startLocation, String finishLocation,
                             double failY, List<Checkpoint> checkpoints) {
        this.name = name;
        this.startLocation = startLocation;
        this.finishLocation = finishLocation;
        this.failY = failY;
        this.checkpoints = new ArrayList<>(checkpoints);
    }

    public String getName() { return name; }
    public String getStartLocation() { return startLocation; }
    public String getFinishLocation() { return finishLocation; }
    public double getFailY() { return failY; }
    public List<Checkpoint> getCheckpoints() {
        return checkpoints == null ? List.of() : Collections.unmodifiableList(checkpoints);
    }

    public CourseDefinition withStartLocation(String value) {
        return new CourseDefinition(name, value, finishLocation, failY, getCheckpoints());
    }

    public CourseDefinition withFinishLocation(String value) {
        return new CourseDefinition(name, startLocation, value, failY, getCheckpoints());
    }

    public CourseDefinition withFailY(double value) {
        return new CourseDefinition(name, startLocation, finishLocation, value, getCheckpoints());
    }

    public CourseDefinition withCheckpoint(Checkpoint checkpoint) {
        List<Checkpoint> updated = new ArrayList<>(getCheckpoints());
        updated.add(checkpoint);
        return new CourseDefinition(name, startLocation, finishLocation, failY, updated);
    }

    public CourseDefinition copy() {
        return new CourseDefinition(name, startLocation, finishLocation, failY, getCheckpoints());
    }

    public boolean isFullyConfigured() {
        return startLocation != null && finishLocation != null;
    }

    public static boolean isValidName(String value) {
        return value != null && NAME_PATTERN.matcher(value).matches();
    }

    /** Checks fields that can be validated without loading a Bukkit world. */
    public void validateStoredData() {
        if (!isValidName(name)) throw new IllegalArgumentException("invalid course name");
        if (!Double.isFinite(failY)) throw new IllegalArgumentException("non-finite failY");
        if (startLocation != null && !LocationUtil.isSerializedLocationValid(startLocation)) {
            throw new IllegalArgumentException("invalid start location");
        }
        if (finishLocation != null && !LocationUtil.isSerializedLocationValid(finishLocation)) {
            throw new IllegalArgumentException("invalid finish location");
        }
        List<Checkpoint> savedCheckpoints = getCheckpoints();
        List<String> routePads = new ArrayList<>();
        if (startLocation != null) routePads.add(startLocation);
        if (finishLocation != null) routePads.add(finishLocation);
        for (int index = 0; index < savedCheckpoints.size(); index++) {
            Checkpoint checkpoint = savedCheckpoints.get(index);
            if (checkpoint == null || checkpoint.getNumber() != index + 1
                    || !LocationUtil.isSerializedLocationValid(checkpoint.getLocation())) {
                throw new IllegalArgumentException("invalid checkpoint sequence or location");
            }
            routePads.add(checkpoint.getLocation());
        }
        for (int first = 0; first < routePads.size(); first++) {
            for (int second = first + 1; second < routePads.size(); second++) {
                if (LocationUtil.sameSerializedBlock(routePads.get(first), routePads.get(second))) {
                    throw new IllegalArgumentException("course pads overlap");
                }
            }
        }
    }
}
