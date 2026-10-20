package com.matarif.parkour.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks each configurable liquid outcome and ensures an invalid value fails safely. */
class LiquidActionTest {
    @Test
    void parsesWaterAndLavaPoliciesCaseInsensitively() {
        assertEquals(LiquidAction.ALLOW, LiquidAction.parse("allow"));
        assertEquals(LiquidAction.RECOVER, LiquidAction.parse("RECOVER"));
        assertEquals(LiquidAction.INVALIDATE, LiquidAction.parse("invalidate"));
    }

    @Test
    void unknownPoliciesFallBackToAllow() {
        assertEquals(LiquidAction.ALLOW, LiquidAction.parse("teleport"));
        assertEquals(LiquidAction.ALLOW, LiquidAction.parse(null));
    }
}

