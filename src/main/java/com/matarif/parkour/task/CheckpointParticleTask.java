package com.matarif.parkour.task;

import com.matarif.parkour.MatarifParkour;
import com.matarif.parkour.model.Checkpoint;
import com.matarif.parkour.model.ParkourRun;
import com.matarif.parkour.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Marks the next checkpoint for nearby players without scanning blocks or loading chunks. */
public final class CheckpointParticleTask implements Runnable {
    private final MatarifParkour plugin;

    public CheckpointParticleTask(MatarifParkour plugin) { this.plugin = plugin; }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("settings.particles-enabled", true)) return;
        Particle particle;
        try {
            particle = Particle.valueOf(plugin.getConfig().getString("settings.particle-type", "END_ROD")
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            particle = Particle.END_ROD;
        }
        int count = plugin.getConfig().getInt("settings.particle-count", 3);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ParkourRun run = plugin.getRunManager().getRunning(player);
            if (run == null) continue;
            Checkpoint checkpoint = run.getNextCheckpoint();
            if (checkpoint == null) continue;
            Location target = LocationUtil.deserialize(checkpoint.getLocation());
            if (target == null || target.getWorld() == null || !target.getWorld().equals(player.getWorld())
                    || target.distanceSquared(player.getLocation()) > 48 * 48) continue;
            target.add(0.5, 0.8, 0.5);
            player.spawnParticle(particle, target, count, 0.22, 0.32, 0.22, 0.0);
        }
    }
}

