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

/** Checks the server descriptor declares the implemented entry point, command, and permissions. */
class PluginDescriptorTest {
    @Test
    void declaresMainCommandAndPermissionDefaults() throws IOException, InvalidConfigurationException {
        YamlConfiguration descriptor = new YamlConfiguration();
        try (InputStream input = getClass().getResourceAsStream("/plugin.yml")) {
            if (input == null) throw new IOException("plugin.yml is missing from test resources");
            descriptor.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }

        assertEquals("com.matarif.parkour.MatarifParkour", descriptor.getString("main"));
        assertEquals("1.21", descriptor.getString("api-version"));
        assertTrue(descriptor.isConfigurationSection("commands.parkour"));
        assertEquals("op", descriptor.getString("permissions.matarifparkour.admin.default"));
        assertTrue(descriptor.getBoolean("permissions.matarifparkour.leaderboard.default"));
    }
}

