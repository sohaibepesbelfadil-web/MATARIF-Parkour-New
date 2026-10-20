package com.matarif.parkour.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates persisted location encodings and block comparisons without requiring loaded worlds. */
class LocationUtilTest {
    private static final String WORLD_ID = "00000000-0000-0000-0000-000000000001";

    @Test
    void acceptsBothLocationFormatsAndMatchesTheirBlockCoordinates() {
        String legacy = "world|0|64|0|0|0";
        String uuidBacked = WORLD_ID + "|world|0.5|64|0.5|90|0";

        assertTrue(LocationUtil.isSerializedLocationValid(legacy));
        assertTrue(LocationUtil.isSerializedLocationValid(uuidBacked));
        assertTrue(LocationUtil.sameSerializedBlock(legacy, uuidBacked));
    }

    @Test
    void rejectsMalformedWorldIdentityAndCoordinatesOutsideTheMinecraftBorder() {
        assertFalse(LocationUtil.isSerializedLocationValid("not-a-uuid|world|0.5|64|0.5|0|0"));
        assertFalse(LocationUtil.isSerializedLocationValid("world|30000001|64|0|0|0"));
        assertFalse(LocationUtil.isSerializedLocationValid("world|0|64|NaN|0|0"));
    }

    @Test
    void doesNotTreatDifferentUuidWorldsAsTheSameBlock() {
        String first = WORLD_ID + "|world|0.5|64|0.5|0|0";
        String second = "00000000-0000-0000-0000-000000000002|world|0.5|64|0.5|0|0";

        assertFalse(LocationUtil.sameSerializedBlock(first, second));
    }
}

