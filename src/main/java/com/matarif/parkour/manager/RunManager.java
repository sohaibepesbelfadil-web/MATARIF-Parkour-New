package com.matarif.parkour.manager;

import com.matarif.parkour.MatarifParkour;
import com.matarif.parkour.model.Checkpoint;
import com.matarif.parkour.model.CourseDefinition;
import com.matarif.parkour.model.LiquidAction;
import com.matarif.parkour.model.ParkourRun;
import com.matarif.parkour.model.PlayerRecord;
import com.matarif.parkour.model.RunStatus;
import com.matarif.parkour.util.LocationUtil;
import com.matarif.parkour.util.TimeFormatter;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Owns each player's run lifecycle, checkpoint progression, recovery, and one-time leaderboard submission. */
public final class RunManager {
    private final MatarifParkour plugin;
    private final Map<UUID, ParkourRun> runs = new HashMap<>();
    private final Map<UUID, Long> startCooldowns = new HashMap<>();
    private final Map<UUID, Long> recoveryNotices = new HashMap<>();
    private final Map<UUID, Location> recoveryTeleports = new HashMap<>();
    private final Map<UUID, PendingRecovery> pendingRecoveries = new HashMap<>();

    public RunManager(MatarifParkour plugin) { this.plugin = plugin; }

    public ParkourRun getRunning(Player player) {
        ParkourRun run = runs.get(player.getUniqueId());
        return run != null && run.getStatus() == RunStatus.RUNNING ? run : null;
    }

    public boolean isRunning(Player player) { return getRunning(player) != null; }

    public boolean isRecoveryTeleport(PlayerTeleportEvent event) {
        Location expected = recoveryTeleports.get(event.getPlayer().getUniqueId());
        return expected != null && LocationUtil.samePosition(expected, event.getTo());
    }

    public boolean isRecoveryPending(Player player) {
        return pendingRecoveries.containsKey(player.getUniqueId());
    }

    /** Called only when a player's feet enter a different block; pressure plates also call this method. */
    public void onEnteredBlock(Player player, Location feetLocation) {
        ParkourRun run = getRunning(player);
        if (run == null) {
            CourseDefinition course = plugin.getCourseManager().findStart(feetLocation);
            if (course != null) start(player, course);
            return;
        }

        Checkpoint expected = run.getNextCheckpoint();
        if (expected != null && LocationUtil.sameBlock(feetLocation,
                LocationUtil.deserialize(expected.getLocation()))) {
            if (!LocationUtil.isSafePad(feetLocation)) {
                plugin.getFeedback().send(player, "checkpoint-unavailable");
                return;
            }
            if (run.advanceCheckpoint(expected.getNumber(), expected.getLocation())) {
                String number = Integer.toString(expected.getNumber());
                plugin.getFeedback().send(player, "checkpoint-reached", Map.of("number", number));
                plugin.getFeedback().title(player, "checkpoint", Map.of("number", number));
                plugin.getFeedback().sound(player, "checkpoint");
            }
            return;
        }

        if (touchesLaterCheckpoint(run, feetLocation)) {
            plugin.getFeedback().send(player, "checkpoint-order",
                    Map.of("expected", Integer.toString(expected == null ? run.getCourse().getCheckpoints().size() + 1
                            : expected.getNumber())));
            return;
        }

        Location finish = LocationUtil.deserialize(run.getCourse().getFinishLocation());
        if (LocationUtil.sameBlock(feetLocation, finish)) {
            if (expected != null) {
                plugin.getFeedback().send(player, "checkpoints-required",
                        Map.of("expected", Integer.toString(expected.getNumber())));
            } else {
                finish(player, run);
            }
            return;
        }

        if (plugin.getCourseManager().findStart(feetLocation) != null) {
            plugin.getFeedback().send(player, "already-running", Map.of("course", run.getCourse().getName()));
        }
    }

    public void recover(Player player, String reason) {
        ParkourRun run = getRunning(player);
        if (run == null || isRecoveryPending(player)) return;
        Location checkpoint = LocationUtil.deserialize(run.getLastCheckpointLocation());
        if (checkpoint == null || checkpoint.getWorld() == null) {
            failRecovery(player, "the latest checkpoint is unavailable");
            return;
        }
        notifyRecovery(player, reason);

        int chunkX = checkpoint.getBlockX() >> 4;
        int chunkZ = checkpoint.getBlockZ() >> 4;
        if (!checkpoint.getWorld().isChunkLoaded(chunkX, chunkZ)) {
            requestCheckpointChunk(player, run, checkpoint, chunkX, chunkZ);
            return;
        }
        teleportToCheckpoint(player, run);
    }

    public boolean applyLiquidRule(Player player, Material liquid) {
        String path = liquid == Material.LAVA ? "rules.lava-action" : "rules.water-action";
        LiquidAction action = LiquidAction.parse(plugin.getConfig().getString(path, "allow"));
        if (action == LiquidAction.RECOVER) {
            recover(player, liquid == Material.LAVA ? "lava" : "water");
            return true;
        }
        if (action == LiquidAction.INVALIDATE) {
            invalidate(player, liquid == Material.LAVA ? "entered lava" : "entered water");
            return true;
        }
        return false;
    }

    public void invalidate(Player player, String reason) {
        ParkourRun run = getRunning(player);
        if (run == null || !run.invalidate()) return;
        cancelPendingRecovery(player);
        plugin.getFeedback().send(player, "run-invalidated", Map.of("reason", reason));
        plugin.getFeedback().title(player, "invalid", Map.of("reason", reason));
        plugin.getFeedback().sound(player, "invalid");
    }

    public void remove(Player player) {
        cancelPendingRecovery(player);
        runs.remove(player.getUniqueId());
        startCooldowns.remove(player.getUniqueId());
        recoveryNotices.remove(player.getUniqueId());
        recoveryTeleports.remove(player.getUniqueId());
        plugin.getFeedback().forget(player);
    }

    /** Clears live state during disable without producing messages for players as the server stops. */
    public void clearForShutdown() {
        for (PendingRecovery pending : pendingRecoveries.values()) {
            if (pending.player().isOnline()) pending.player().setInvulnerable(pending.wasInvulnerable());
        }
        pendingRecoveries.clear();
        for (ParkourRun run : runs.values()) run.invalidate();
        runs.clear();
        startCooldowns.clear();
        recoveryNotices.clear();
        recoveryTeleports.clear();
    }

    public void cancelPendingRecovery(Player player) {
        PendingRecovery pending = pendingRecoveries.remove(player.getUniqueId());
        if (pending != null && player.isOnline()) player.setInvulnerable(pending.wasInvulnerable());
    }

    private void start(Player player, CourseDefinition course) {
        if (!course.isFullyConfigured() || !plugin.getCourseManager().isPlayable(course)) {
            plugin.getFeedback().send(player, "course-incomplete", Map.of("course", course.getName()));
            plugin.getFeedback().sound(player, "command-error");
            return;
        }
        if ((plugin.getConfig().getBoolean("rules.flight-invalidates-run", true) && player.isFlying())
                || (plugin.getConfig().getBoolean("rules.gliding-invalidates-run", true) && player.isGliding())) {
            plugin.getFeedback().send(player, "cannot-start-while-flying");
            return;
        }
        long now = System.nanoTime();
        Long previous = startCooldowns.get(player.getUniqueId());
        long cooldownNanos = plugin.getConfig().getLong("settings.start-trigger-cooldown-ms", 1000)
                * 1_000_000L;
        if (previous != null && now - previous < cooldownNanos) {
            plugin.getFeedback().send(player, "start-cooldown");
            return;
        }
        runs.put(player.getUniqueId(), new ParkourRun(course, now));
        startCooldowns.put(player.getUniqueId(), now);
        plugin.getFeedback().send(player, "run-started", Map.of("course", course.getName()));
        plugin.getFeedback().title(player, "start", Map.of("course", course.getName()));
        plugin.getFeedback().sound(player, "start");
    }

    private boolean touchesLaterCheckpoint(ParkourRun run, Location feetLocation) {
        var checkpoints = run.getCourse().getCheckpoints();
        for (int index = run.getNextCheckpointIndex() + 1; index < checkpoints.size(); index++) {
            if (LocationUtil.sameBlock(feetLocation, LocationUtil.deserialize(checkpoints.get(index).getLocation()))) {
                return true;
            }
        }
        return false;
    }

    private void finish(Player player, ParkourRun run) {
        if (run.getNextCheckpoint() != null) return;
        long elapsed = run.completionMillis();
        if (!run.complete()) return;
        String formatted = TimeFormatter.format(elapsed);
        String courseName = run.getCourse().getName();
        plugin.getFeedback().send(player, "run-finished", Map.of("course", courseName, "time", formatted));
        plugin.getFeedback().title(player, "finish", Map.of("course", courseName, "time", formatted));
        plugin.getFeedback().sound(player, "finish");

        boolean personalBest = plugin.getStorageManager().saveBest(player.getUniqueId(), courseName, elapsed,
                player.getName(), System.currentTimeMillis());
        if (personalBest) {
            plugin.getFeedback().send(player, "new-record", Map.of("time", formatted));
            plugin.getFeedback().title(player, "record", Map.of("time", formatted));
            plugin.getFeedback().sound(player, "record");
        } else {
            PlayerRecord previous = plugin.getStorageManager().getRecord(player.getUniqueId(), courseName);
            if (previous != null) plugin.getFeedback().send(player, "previous-record",
                    Map.of("time", TimeFormatter.format(previous.getBestTimeMs())));
        }
    }

    private void notifyRecovery(Player player, String reason) {
        long now = System.nanoTime();
        long cooldownNanos = plugin.getConfig().getLong("settings.recovery-notice-cooldown-ms", 1500)
                * 1_000_000L;
        Long previous = recoveryNotices.get(player.getUniqueId());
        if (previous == null || now - previous >= cooldownNanos) {
            recoveryNotices.put(player.getUniqueId(), now);
            plugin.getFeedback().send(player, "recovery", Map.of("reason", reason));
            plugin.getFeedback().sound(player, "recovery");
        }
    }

    private void requestCheckpointChunk(Player player, ParkourRun run, Location checkpoint,
                                        int chunkX, int chunkZ) {
        UUID playerId = player.getUniqueId();
        PendingRecovery pending = new PendingRecovery(player, run, player.isInvulnerable());
        pendingRecoveries.put(playerId, pending);
        player.setInvulnerable(true);
        player.setFallDistance(0);
        try {
            // Paper completes chunk futures on the main thread, so recovery block checks stay thread-safe.
            checkpoint.getWorld().getChunkAtAsync(chunkX, chunkZ, false)
                    .whenComplete((chunk, failure) -> completePendingRecovery(playerId, pending, chunk, failure));
        } catch (RuntimeException exception) {
            completePendingRecovery(playerId, pending, null, exception);
        }
    }

    private void completePendingRecovery(UUID playerId, PendingRecovery pending, Chunk chunk, Throwable failure) {
        if (!pendingRecoveries.remove(playerId, pending)) return;
        Player player = pending.player();
        if (player.isOnline()) player.setInvulnerable(pending.wasInvulnerable());
        if (!player.isOnline() || getRunning(player) != pending.run()) return;
        if (failure != null || chunk == null) {
            if (failure != null) plugin.getLogger().log(Level.WARNING,
                    "Could not load the checkpoint chunk for " + player.getName() + "'s recovery.", failure);
            failRecovery(player, "the checkpoint chunk could not be loaded");
            return;
        }
        teleportToCheckpoint(player, pending.run());
    }

    private void teleportToCheckpoint(Player player, ParkourRun run) {
        Location destination = LocationUtil.safeRecoveryLocation(run.getLastCheckpointLocation(),
                plugin.getConfig().getInt("settings.recovery-search-up-blocks", 2));
        if (destination == null) {
            failRecovery(player, "the latest checkpoint is no longer safe");
            return;
        }
        player.setFallDistance(0);
        player.setVelocity(new Vector(0, 0, 0));
        UUID playerId = player.getUniqueId();
        recoveryTeleports.put(playerId, destination.clone());
        boolean teleported;
        try {
            teleported = player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            recoveryTeleports.remove(playerId);
        }
        if (!teleported) invalidate(player, "checkpoint recovery failed");
    }

    private void failRecovery(Player player, String reason) {
        plugin.getFeedback().send(player, "recovery-failed");
        invalidate(player, reason);
    }

    private record PendingRecovery(Player player, ParkourRun run, boolean wasInvulnerable) { }
}
