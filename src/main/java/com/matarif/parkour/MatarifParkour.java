package com.matarif.parkour;

import com.matarif.parkour.command.ParkourCommand;
import com.matarif.parkour.config.ConfigValidator;
import com.matarif.parkour.listener.LifecycleListener;
import com.matarif.parkour.listener.ParkourListener;
import com.matarif.parkour.manager.CourseManager;
import com.matarif.parkour.manager.LeaderboardManager;
import com.matarif.parkour.manager.RunManager;
import com.matarif.parkour.service.FeedbackService;
import com.matarif.parkour.storage.StorageManager;
import com.matarif.parkour.task.ActionBarTask;
import com.matarif.parkour.task.CheckpointParticleTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.logging.Level;

/** Plugin lifecycle coordinator; domain rules live in focused managers and listeners. */
public final class MatarifParkour extends JavaPlugin {
    private StorageManager storageManager;
    private CourseManager courseManager;
    private RunManager runManager;
    private LeaderboardManager leaderboardManager;
    private FeedbackService feedback;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigValidator.validate(this);
        feedback = new FeedbackService(this);
        try {
            storageManager = new StorageManager(this);
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE,
                    "Parkour data could not be loaded safely. Existing JSON files were left unchanged; fix the reported file and restart.",
                    exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        courseManager = new CourseManager(storageManager);
        runManager = new RunManager(this);
        leaderboardManager = new LeaderboardManager(storageManager);

        PluginCommand command = getCommand("parkour");
        if (command == null) {
            getLogger().severe("The parkour command is missing from plugin.yml; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            storageManager.flushAndShutdown();
            return;
        }
        ParkourCommand executor = new ParkourCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getServer().getPluginManager().registerEvents(new ParkourListener(this), this);
        getServer().getPluginManager().registerEvents(new LifecycleListener(this), this);
        if (getConfig().getBoolean("settings.actionbar-enabled", true)) {
            long period = Math.max(1L, getConfig().getLong("settings.actionbar-period-ticks", 2));
            getServer().getScheduler().runTaskTimer(this, new ActionBarTask(this), 1L, period);
        }
        if (getConfig().getBoolean("settings.particles-enabled", true)) {
            long period = Math.max(2L, getConfig().getLong("settings.particle-period-ticks", 10));
            getServer().getScheduler().runTaskTimer(this, new CheckpointParticleTask(this), 2L, period);
        }
        getLogger().info("MATARIF Parkour enabled with " + storageManager.getCourses().size() + " saved course(s).");
    }

    @Override
    public void onDisable() {
        if (runManager != null) runManager.clearForShutdown();
        if (storageManager != null) storageManager.flushAndShutdown();
    }

    public StorageManager getStorageManager() { return storageManager; }
    public CourseManager getCourseManager() { return courseManager; }
    public RunManager getRunManager() { return runManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public FeedbackService getFeedback() { return feedback; }
}

