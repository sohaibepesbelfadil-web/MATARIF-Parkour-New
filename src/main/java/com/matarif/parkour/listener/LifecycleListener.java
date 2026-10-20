package com.matarif.parkour.listener;

import com.matarif.parkour.MatarifParkour;
import com.matarif.parkour.model.CourseDefinition;
import com.matarif.parkour.model.ParkourRun;
import com.matarif.parkour.util.LocationUtil;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/** Cleans up per-player state and invalidates abilities that can bypass a course route. */
public final class LifecycleListener implements Listener {
    private final MatarifParkour plugin;

    public LifecycleListener(MatarifParkour plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getRunManager().remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        plugin.getRunManager().invalidate(event.getEntity(), "you died");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.getRunManager().isRecoveryTeleport(event)) return;
        plugin.getRunManager().cancelPendingRecovery(event.getPlayer());
        if (!plugin.getConfig().getBoolean("rules.teleports-invalidate-run", true)) return;
        plugin.getRunManager().invalidate(event.getPlayer(), "you teleported");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.getRunManager().invalidate(event.getPlayer(), "you changed worlds");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (event.isFlying() && plugin.getConfig().getBoolean("rules.flight-invalidates-run", true)) {
            plugin.getRunManager().invalidate(event.getPlayer(), "you started flying");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player && event.isGliding()
                && plugin.getConfig().getBoolean("rules.gliding-invalidates-run", true)) {
            plugin.getRunManager().invalidate(player, "you started gliding");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnderPearl(ProjectileLaunchEvent event) {
        if (plugin.getConfig().getBoolean("rules.ender-pearls-invalidate-run", true)
                && event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player player) {
            plugin.getRunManager().invalidate(player, "you threw an ender pearl");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.getCourseManager().rebuildStartIndex();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        World unloading = event.getWorld();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ParkourRun run = plugin.getRunManager().getRunning(player);
            if (run == null) continue;
            CourseDefinition course = run.getCourse();
            if (player.getWorld().getUID().equals(unloading.getUID())
                    || LocationUtil.belongsToWorld(course.getStartLocation(), unloading)
                    || LocationUtil.belongsToWorld(course.getFinishLocation(), unloading)) {
                plugin.getRunManager().invalidate(player, "the course world unloaded");
                continue;
            }
            for (var checkpoint : course.getCheckpoints()) {
                if (LocationUtil.belongsToWorld(checkpoint.getLocation(), unloading)) {
                    plugin.getRunManager().invalidate(player, "the course world unloaded");
                    break;
                }
            }
        }
    }
}
