package com.matarif.parkour.task;

import com.matarif.parkour.MatarifParkour;
import com.matarif.parkour.model.ParkourRun;
import org.bukkit.entity.Player;

/** Displays elapsed monotonic run time without advancing or mutating the timer. */
public final class ActionBarTask implements Runnable {
    private final MatarifParkour plugin;

    public ActionBarTask(MatarifParkour plugin) { this.plugin = plugin; }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ParkourRun run = plugin.getRunManager().getRunning(player);
            if (run != null) plugin.getFeedback().actionBar(player, run.getCourse().getName(), run.elapsedMillis());
        }
    }
}

