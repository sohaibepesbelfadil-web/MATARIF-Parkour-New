package com.matarif.parkour.model;

/** Terminal states are retained until the player's next run or disconnect to guard duplicate events. */
public enum RunStatus {
    RUNNING,
    COMPLETED,
    INVALIDATED
}

