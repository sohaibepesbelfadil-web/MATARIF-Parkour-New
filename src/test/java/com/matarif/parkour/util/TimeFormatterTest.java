package com.matarif.parkour.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Covers the shared player-facing time format, including edge values. */
class TimeFormatterTest {
    @Test
    void formatsMinutesSecondsAndMilliseconds() {
        assertEquals("00:00.000", TimeFormatter.format(0));
        assertEquals("01:01.002", TimeFormatter.format(61_002));
        assertEquals("123:45.678", TimeFormatter.format(7_425_678));
    }

    @Test
    void clampsNegativeTimesToZero() {
        assertEquals("00:00.000", TimeFormatter.format(-42));
    }
}
