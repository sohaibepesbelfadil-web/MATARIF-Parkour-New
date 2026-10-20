package com.matarif.parkour.service;

import com.matarif.parkour.util.TimeFormatter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Centralizes configured text, titles, sounds, and short feedback cooldowns. */
public final class FeedbackService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Set<String> RATE_LIMITED = Set.of(
            "command-error", "checkpoints-required", "checkpoint-order", "already-running");

    private final JavaPlugin plugin;
    private final Map<UUID, Map<String, Long>> lastMessages = new HashMap<>();

    public FeedbackService(JavaPlugin plugin) { this.plugin = plugin; }

    public void send(CommandSender sender, String key) { send(sender, key, Map.of()); }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender instanceof Player player && RATE_LIMITED.contains(key) && isCoolingDown(player, key)) return;
        String prefix = plugin.getConfig().getString("prefix", "");
        String message = plugin.getConfig().getString("messages." + key, "&cMissing message: " + key);
        sender.sendMessage(LEGACY.deserialize(
                replacePlaceholders(prefix + message, placeholders).replace("\\n", "\n")));
    }

    public void title(Player player, String key, Map<String, String> placeholders) {
        if (!plugin.getConfig().getBoolean("settings.titles-enabled", true)) return;
        String path = "titles." + key + ".";
        String title = plugin.getConfig().getString(path + "title", "");
        String subtitle = plugin.getConfig().getString(path + "subtitle", "");
        Duration fadeIn = Duration.ofMillis(plugin.getConfig().getInt("settings.title-fade-in-ticks", 5) * 50L);
        Duration stay = Duration.ofMillis(plugin.getConfig().getInt("settings.title-stay-ticks", 25) * 50L);
        Duration fadeOut = Duration.ofMillis(plugin.getConfig().getInt("settings.title-fade-out-ticks", 8) * 50L);
        player.showTitle(Title.title(LEGACY.deserialize(replacePlaceholders(title, placeholders)),
                LEGACY.deserialize(replacePlaceholders(subtitle, placeholders)),
                Title.Times.times(fadeIn, stay, fadeOut)));
    }

    public void sound(Player player, String key) {
        String path = "sounds." + key + ".";
        if (!plugin.getConfig().getBoolean(path + "enabled", false)) return;
        String configured = plugin.getConfig().getString(path + "type", "minecraft:entity.experience_orb.pickup");
        try {
            NamespacedKey soundKey = NamespacedKey.fromString(configured);
            if (soundKey == null) return;
            float volume = (float) plugin.getConfig().getDouble(path + "volume", 0.8);
            float pitch = (float) plugin.getConfig().getDouble(path + "pitch", 1.0);
            Sound sound = Sound.sound(Key.key(soundKey.toString()), Sound.Source.MASTER, volume, pitch);
            player.playSound(sound);
        } catch (IllegalArgumentException ignored) {
            // Startup validation has already warned about invalid sound names.
        }
    }

    public void actionBar(Player player, String course, long elapsedMillis) {
        String template = plugin.getConfig().getString("actionbar", "&e{time} &8| &f{course}");
        String rendered = replacePlaceholders(template,
                Map.of("time", TimeFormatter.format(elapsedMillis), "course", course));
        Component component = LEGACY.deserialize(rendered);
        player.sendActionBar(component);
    }

    public void forget(Player player) { lastMessages.remove(player.getUniqueId()); }

    private boolean isCoolingDown(Player player, String key) {
        long cooldownNanos = plugin.getConfig().getLong("settings.feedback-cooldown-ms", 900) * 1_000_000L;
        if (cooldownNanos <= 0) return false;
        Map<String, Long> playerMessages = lastMessages.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long now = System.nanoTime();
        Long previous = playerMessages.get(key);
        if (previous != null && now - previous < cooldownNanos) return true;
        playerMessages.put(key, now);
        return false;
    }

    private static String replacePlaceholders(String template, Map<String, String> placeholders) {
        String rendered = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }
}
