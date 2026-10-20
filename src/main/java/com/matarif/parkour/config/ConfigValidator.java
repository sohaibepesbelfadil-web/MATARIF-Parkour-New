package com.matarif.parkour.config;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Normalizes configurable values once at startup so runtime event handlers can stay predictable. */
public final class ConfigValidator {
    private ConfigValidator() { }

    public static void validate(JavaPlugin plugin) {
        var config = plugin.getConfig();
        Object failYValue = config.get("settings.default-fail-y");
        if (!(failYValue instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            plugin.getLogger().warning("settings.default-fail-y must be a finite number; using 60.0.");
            config.set("settings.default-fail-y", 60.0);
        }
        clampInt(plugin, "settings.actionbar-period-ticks", 2, 1, 100);
        clampInt(plugin, "settings.particle-period-ticks", 10, 2, 1200);
        clampInt(plugin, "settings.particle-count", 3, 1, 12);
        clampInt(plugin, "settings.title-fade-in-ticks", 5, 0, 100);
        clampInt(plugin, "settings.title-stay-ticks", 25, 0, 200);
        clampInt(plugin, "settings.title-fade-out-ticks", 8, 0, 100);
        clampInt(plugin, "settings.feedback-cooldown-ms", 900, 0, 60_000);
        clampInt(plugin, "settings.recovery-notice-cooldown-ms", 1500, 0, 60_000);
        clampInt(plugin, "settings.start-trigger-cooldown-ms", 1000, 0, 60_000);
        clampInt(plugin, "settings.recovery-search-up-blocks", 2, 0, 8);
        validateBoolean(plugin, "settings.actionbar-enabled", true);
        validateBoolean(plugin, "settings.particles-enabled", true);
        validateBoolean(plugin, "settings.titles-enabled", true);
        validateBoolean(plugin, "rules.teleports-invalidate-run", true);
        validateBoolean(plugin, "rules.flight-invalidates-run", true);
        validateBoolean(plugin, "rules.gliding-invalidates-run", true);
        validateBoolean(plugin, "rules.ender-pearls-invalidate-run", true);
        validateString(plugin, "prefix", "&8[&cMATARIF Parkour&8] &7");
        validateString(plugin, "actionbar", "&e{time} &8| &f{course}");
        validateString(plugin, "settings.particle-type", "END_ROD");

        String particleName = config.getString("settings.particle-type", "END_ROD");
        try {
            Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            plugin.getLogger().warning("Invalid settings.particle-type; using END_ROD.");
            config.set("settings.particle-type", "END_ROD");
        }
        normalizeAction(plugin, "rules.water-action", "allow");
        normalizeAction(plugin, "rules.lava-action", "recover");
        validateSounds(plugin);
    }

    private static void clampInt(JavaPlugin plugin, String path, int fallback, int min, int max) {
        Object raw = plugin.getConfig().get(path);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.doubleValue() < Integer.MIN_VALUE || number.doubleValue() > Integer.MAX_VALUE) {
            plugin.getLogger().warning(path + " must be a finite integer; using " + fallback + ".");
            plugin.getConfig().set(path, fallback);
            return;
        }
        int value = number.intValue();
        int normalized = Math.max(min, Math.min(max, value));
        if (normalized != value) {
            plugin.getLogger().warning(path + " is outside " + min + ".." + max + "; using " + normalized + ".");
            plugin.getConfig().set(path, normalized);
        }
    }

    private static void validateBoolean(JavaPlugin plugin, String path, boolean fallback) {
        if (!(plugin.getConfig().get(path) instanceof Boolean)) {
            plugin.getLogger().warning(path + " must be true or false; using " + fallback + ".");
            plugin.getConfig().set(path, fallback);
        }
    }

    private static void validateString(JavaPlugin plugin, String path, String fallback) {
        if (!(plugin.getConfig().get(path) instanceof String)) {
            plugin.getLogger().warning(path + " must be text; using its default value.");
            plugin.getConfig().set(path, fallback);
        }
    }

    private static void normalizeAction(JavaPlugin plugin, String path, String fallback) {
        String value = plugin.getConfig().getString(path, fallback);
        if (value != null && (value.equalsIgnoreCase("allow")
                || value.equalsIgnoreCase("recover") || value.equalsIgnoreCase("invalidate"))) {
            plugin.getConfig().set(path, value.toLowerCase(Locale.ROOT));
            return;
        }
        plugin.getLogger().warning("Invalid " + path + "; using " + fallback + ".");
        plugin.getConfig().set(path, fallback);
    }

    private static void validateSounds(JavaPlugin plugin) {
        ConfigurationSection sounds = plugin.getConfig().getConfigurationSection("sounds");
        if (sounds == null) return;
        for (String key : sounds.getKeys(false)) {
            ConfigurationSection section = sounds.getConfigurationSection(key);
            if (section == null) continue;
            String path = "sounds." + key + ".";
            if (!(section.get("enabled") instanceof Boolean)) {
                plugin.getLogger().warning(path + "enabled must be true or false; disabling this sound.");
                section.set("enabled", false);
            }
            if (!(section.get("type") instanceof String)) {
                plugin.getLogger().warning(path + "type must be a sound key; using the experience-orb sound.");
                section.set("type", "minecraft:entity.experience_orb.pickup");
            }
            String type = section.getString("type", "minecraft:entity.experience_orb.pickup");
            NamespacedKey soundKey = resolveSoundKey(type);
            if (soundKey == null || Registry.SOUNDS.get(soundKey) == null) {
                plugin.getLogger().warning("Invalid " + path + "type; sound " + key + " will be skipped.");
                section.set("enabled", false);
            } else {
                section.set("type", soundKey.toString());
            }
            double volume = normalizedDouble(plugin, section, path + "volume", "volume", 0.8, 0, 2);
            double pitch = normalizedDouble(plugin, section, path + "pitch", "pitch", 1.0, 0.5, 2);
            section.set("volume", volume);
            section.set("pitch", pitch);
        }
    }

    private static double normalizedDouble(JavaPlugin plugin, ConfigurationSection section,
                                          String path, String child, double fallback, double min, double max) {
        Object raw = section.get(child);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < min || number.doubleValue() > max) {
            plugin.getLogger().warning("Invalid " + path + "; using " + fallback + ".");
            return fallback;
        }
        return number.doubleValue();
    }

    private static NamespacedKey resolveSoundKey(String configured) {
        if (configured == null) return null;
        NamespacedKey key = null;
        try {
            key = NamespacedKey.fromString(configured);
        } catch (IllegalArgumentException exception) {
            // An older config may use enum-style names, which are not valid namespaced keys.
        }
        if (key != null && Registry.SOUNDS.get(key) != null) return key;
        String legacyName = configured.toUpperCase(Locale.ROOT);
        return Registry.SOUNDS.stream()
                .map(Registry.SOUNDS::getKey)
                .filter(soundKey -> soundKey != null && matchesLegacyEnumName(soundKey, legacyName))
                .findFirst().orElse(null);
    }

    static boolean matchesLegacyEnumName(NamespacedKey soundKey, String legacyName) {
        return soundKey.getNamespace().equals("minecraft")
                && soundKey.getKey().replace('.', '_').equalsIgnoreCase(legacyName);
    }
}
