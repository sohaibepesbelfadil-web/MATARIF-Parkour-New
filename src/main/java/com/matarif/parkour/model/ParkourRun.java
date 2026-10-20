package com.matarif.parkour.model;

import java.util.List;

/** Runtime state for one player attempt; its monotonic start time is never reset by checkpoint recovery. */
public final class ParkourRun {
    private final CourseDefinition course;
    private final long startedAtNanos;
    private int nextCheckpointIndex;
    private String lastCheckpointLocation;
    private RunStatus status = RunStatus.RUNNING;

    public ParkourRun(CourseDefinition course, long startedAtNanos) {
        if (course == null || !course.isFullyConfigured()) {
            throw new IllegalArgumentException("A run requires a configured start and finish.");
        }
        this.course = course.copy();
        this.startedAtNanos = startedAtNanos;
        this.lastCheckpointLocation = course.getStartLocation();
    }

    public CourseDefinition getCourse() { return course; }
    public int getNextCheckpointIndex() { return nextCheckpointIndex; }
    public String getLastCheckpointLocation() { return lastCheckpointLocation; }
    public RunStatus getStatus() { return status; }

    public Checkpoint getNextCheckpoint() {
        List<Checkpoint> checkpoints = course.getCheckpoints();
        return nextCheckpointIndex < checkpoints.size() ? checkpoints.get(nextCheckpointIndex) : null;
    }

    public boolean advanceCheckpoint(int number, String location) {
        Checkpoint expected = getNextCheckpoint();
        if (status != RunStatus.RUNNING || expected == null || expected.getNumber() != number
                || !expected.getLocation().equals(location)) return false;
        nextCheckpointIndex++;
        lastCheckpointLocation = location;
        return true;
    }

    public boolean complete() {
        if (status != RunStatus.RUNNING || course.getFinishLocation() == null || getNextCheckpoint() != null) return false;
        status = RunStatus.COMPLETED;
        return true;
    }

    public boolean invalidate() {
        if (status != RunStatus.RUNNING) return false;
        status = RunStatus.INVALIDATED;
        return true;
    }

    public long elapsedMillis() { return elapsedMillis(System.nanoTime()); }

    public long completionMillis() { return completionMillis(System.nanoTime()); }

    public long completionMillis(long nowNanos) { return Math.max(1L, elapsedMillis(nowNanos)); }

    public long elapsedMillis(long nowNanos) {
        return Math.max(0L, nowNanos - startedAtNanos) / 1_000_000L;
    }
}
