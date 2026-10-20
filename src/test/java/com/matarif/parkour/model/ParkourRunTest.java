package com.matarif.parkour.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises per-player state transitions, ordered checkpoints, and non-resetting elapsed time. */
class ParkourRunTest {
    private static final String START = "world|0|64|0|0|0";
    private static final String CHECKPOINT = "world|5|64|0|0|0";
    private static final String FINISH = "world|10|64|0|0|0";

    @Test
    void requiresCheckpointsInOrderAndDoesNotResetTimer() {
        CourseDefinition course = new CourseDefinition("CloudHop", 40)
                .withStartLocation(START)
                .withCheckpoint(new Checkpoint(1, CHECKPOINT))
                .withFinishLocation(FINISH);
        ParkourRun run = new ParkourRun(course, 10_000_000_000L);

        assertEquals(2_500L, run.elapsedMillis(12_500_000_000L));
        assertEquals(1L, run.completionMillis(10_000_000_000L));
        assertFalse(run.advanceCheckpoint(2, FINISH));
        assertFalse(run.advanceCheckpoint(1, FINISH));
        assertEquals(0, run.getNextCheckpointIndex());
        assertTrue(run.advanceCheckpoint(1, CHECKPOINT));
        assertNull(run.getNextCheckpoint());
        assertEquals(2_500L, run.elapsedMillis(12_500_000_000L));
        assertTrue(run.complete());
        assertEquals(RunStatus.COMPLETED, run.getStatus());
        assertFalse(run.complete());
        assertFalse(run.invalidate());
    }

    @Test
    void separateRunsKeepIndependentStateAndTerminalTransitionsAreOneWay() {
        CourseDefinition course = new CourseDefinition("Solo", 40)
                .withStartLocation(START)
                .withFinishLocation(FINISH);
        ParkourRun first = new ParkourRun(course, 1_000_000L);
        ParkourRun second = new ParkourRun(course, 9_000_000L);

        assertTrue(first.invalidate());
        assertFalse(first.invalidate());
        assertEquals(RunStatus.INVALIDATED, first.getStatus());
        assertEquals(RunStatus.RUNNING, second.getStatus());
        assertEquals(8L, second.elapsedMillis(17_000_000L));
    }
}
