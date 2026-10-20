package com.matarif.parkour;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parses the shipped configuration and checks the documented recovery defaults. */
class ConfigDefaultsTest {
    @Test
    void configIsValidYamlWithExplicitWaterAndLavaRules() throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream input = getClass().getResourceAsStream("/config.yml")) {
            if (input == null) throw new IOException("config.yml is missing from test resources");
            config.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }

        assertEquals("allow", config.getString("rules.water-action"));
        assertEquals("recover", config.getString("rules.lava-action"));
        assertEquals("minecraft:block.note_block.pling", config.getString("sounds.start.type"));
        assertTrue(config.getBoolean("settings.actionbar-enabled"));
    }
}
