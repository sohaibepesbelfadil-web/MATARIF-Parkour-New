package com.matarif.parkour.config;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards migration from enum-style sound names to current registry keys. */
class ConfigValidatorTest {
    @Test
    void matchesOldSoundEnumNamesToRegistryKeyPaths() {
        NamespacedKey key = NamespacedKey.minecraft("block.amethyst_block.chime");

        assertTrue(ConfigValidator.matchesLegacyEnumName(key, "BLOCK_AMETHYST_BLOCK_CHIME"));
        assertFalse(ConfigValidator.matchesLegacyEnumName(key, "ENTITY_VILLAGER_NO"));
    }
}
