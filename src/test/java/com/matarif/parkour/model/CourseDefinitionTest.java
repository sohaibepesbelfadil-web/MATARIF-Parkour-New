package com.matarif.parkour.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies stored route validation and support for the former location encoding. */
class CourseDefinitionTest {
    @Test
    void acceptsTheLegacyWorldNameLocationFormat() {
        CourseDefinition course = new CourseDefinition("Legacy_1", 40)
                .withStartLocation("world|0|64|0|0|0")
                .withCheckpoint(new Checkpoint(1, "world|2|64|0|0|0"))
                .withFinishLocation("world|4|64|0|0|0");

        assertDoesNotThrow(course::validateStoredData);
    }

    @Test
    void rejectsInvalidNamesCheckpointOrderAndNonFiniteLocations() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseDefinition("bad name", 40).validateStoredData());
        assertThrows(IllegalArgumentException.class,
                () -> new CourseDefinition("Course", 40).withCheckpoint(
                        new Checkpoint(2, "world|2|64|0|0|0")).validateStoredData());
        assertThrows(IllegalArgumentException.class,
                () -> new CourseDefinition("Course", 40).withStartLocation("world|NaN|64|0|0|0")
                        .validateStoredData());
    }

    @Test
    void rejectsOverlappingCoursePads() {
        CourseDefinition course = new CourseDefinition("Course", 40)
                .withStartLocation("world|0|64|0|0|0")
                .withCheckpoint(new Checkpoint(1, "world|0.4|64|0.3|0|0"));

        assertThrows(IllegalArgumentException.class, course::validateStoredData);
    }
}
