package com.matarif.parkour.util;

import java.util.Locale;

/** Formats elapsed milliseconds consistently in action bars, messages, and leaderboards. */
public final class TimeFormatter {
    private TimeFormatter() { }

    public static String format(long millis) {
        long safeMillis = Math.max(0L, millis);
        long minutes = safeMillis / 60_000L;
        long seconds = (safeMillis / 1_000L) % 60L;
        long remainder = safeMillis % 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, remainder);
    }
}
