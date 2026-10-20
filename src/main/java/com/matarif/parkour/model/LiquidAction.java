package com.matarif.parkour.model;

import java.util.Locale;

/** Configured outcome when a running player enters water or lava. */
public enum LiquidAction {
    ALLOW,
    RECOVER,
    INVALIDATE;

    public static LiquidAction parse(String value) {
        if (value == null) return ALLOW;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ALLOW;
        }
    }
}

