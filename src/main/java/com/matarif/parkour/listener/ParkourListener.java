package com.matarif.parkour.listener;

import com.matarif.parkour.MatarifParkour;
import com.matarif.parkour.model.ParkourRun;
import com.matarif.parkour.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

/** Detects pad entry, fall boundaries, and configurable liquid behavior for active attempts. */
public final class ParkourListener implements Listener {
    private final MatarifParkour plugin;

    public ParkourListener(MatarifParkour plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        ParkourRun run = plugin.getRunManager().getRunning(player);

        if (run != null && to.getY() < run.getCourse().getFailY()) {
            plugin.getRunManager().recover(player, "fall");
            return;
        }
        if (LocationUtil.sameBlock(from, to)) return;

        if (run != null) {
            Material fromLiquid = liquidAt(from);
            Material toLiquid = liquidAt(to);
            if (toLiquid != null && toLiquid != fromLiquid) {
                if (plugin.getRunManager().applyLiquidRule(player, toLiquid)) return;
            }
        }
        plugin.getRunManager().onEnteredBlock(player, to);
        if (run == null && plugin.getRunManager().isRunning(player)) {
            Material startLiquid = liquidAt(to);
            if (startLiquid != null) plugin.getRunManager().applyLiquidRule(player, startLiquid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void holdPlayerDuringRecovery(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || !plugin.getRunManager().isRecoveryPending(event.getPlayer())) return;
        event.setTo(event.getFrom());
        event.getPlayer().setVelocity(new Vector(0, 0, 0));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPressurePlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null
                || !isPressurePlate(event.getClickedBlock())) return;
        Player player = event.getPlayer();
        boolean wasRunning = plugin.getRunManager().isRunning(player);
        Location location = player.getLocation();
        plugin.getRunManager().onEnteredBlock(player, location);
        if (!wasRunning && plugin.getRunManager().isRunning(player)) {
            Material startLiquid = liquidAt(location);
            if (startLiquid != null) plugin.getRunManager().applyLiquidRule(player, startLiquid);
        }
    }

    private Material liquidAt(Location location) {
        Material feet = location.getBlock().getType();
        Material head = location.clone().add(0, 1, 0).getBlock().getType();
        if (feet == Material.LAVA || head == Material.LAVA) return Material.LAVA;
        if (isWater(feet) || isWater(head)) return Material.WATER;
        return null;
    }

    private boolean isWater(Material material) {
        return material == Material.WATER || material == Material.BUBBLE_COLUMN;
    }

    private boolean isPressurePlate(Block block) {
        return block.getType().name().endsWith("_PRESSURE_PLATE");
    }
}
