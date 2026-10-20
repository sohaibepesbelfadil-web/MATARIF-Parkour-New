package com.matarif.parkour.command;

import com.matarif.parkour.MatarifParkour;
import com.matarif.parkour.manager.CourseManager;
import com.matarif.parkour.manager.LeaderboardManager;
import com.matarif.parkour.model.CourseDefinition;
import com.matarif.parkour.model.PlayerRecord;
import com.matarif.parkour.util.TimeFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Validates command input and delegates course, leaderboard, and permission rules to their owners. */
public final class ParkourCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "matarifparkour.admin";
    private static final String LEADERBOARD_PERMISSION = "matarifparkour.leaderboard";
    private static final List<String> ADMIN_COMMANDS = List.of(
            "create", "setstart", "setfinish", "addcheckpoint", "setfaily");

    private final MatarifParkour plugin;

    public ParkourCommand(MatarifParkour plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            plugin.getFeedback().send(sender, "help");
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("leaderboard")) {
            showLeaderboard(sender, args);
            return true;
        }
        if (!ADMIN_COMMANDS.contains(subcommand)) {
            plugin.getFeedback().send(sender, "unknown-subcommand");
            return true;
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            plugin.getFeedback().send(sender, "no-permission");
            if (sender instanceof Player player) plugin.getFeedback().sound(player, "command-error");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getFeedback().send(sender, "player-only");
            return true;
        }

        switch (subcommand) {
            case "create" -> create(player, args);
            case "setstart" -> setPad(player, args, CourseManager.PadType.START);
            case "setfinish" -> setPad(player, args, CourseManager.PadType.FINISH);
            case "addcheckpoint" -> addCheckpoint(player, args);
            case "setfaily" -> setFailY(player, args);
            default -> plugin.getFeedback().send(player, "unknown-subcommand");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission(ADMIN_PERMISSION)) completions.addAll(ADMIN_COMMANDS);
            if (sender.hasPermission(LEADERBOARD_PERMISSION)) completions.add("leaderboard");
            completions.add("help");
            return matching(completions, args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create")) return List.of();
            if (args[0].equalsIgnoreCase("leaderboard") && !sender.hasPermission(LEADERBOARD_PERMISSION)) {
                return List.of();
            }
            if (!sender.hasPermission(ADMIN_PERMISSION) && !args[0].equalsIgnoreCase("leaderboard")) return List.of();
            return matching(plugin.getCourseManager().all().stream().map(course -> course.getName()).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addcheckpoint")
                && sender.hasPermission(ADMIN_PERMISSION)) {
            CourseDefinition course = plugin.getCourseManager().get(args[1]);
            return List.of(Integer.toString(course == null ? 1 : course.getCheckpoints().size() + 1));
        }
        return List.of();
    }

    private void create(Player player, String[] args) {
        if (args.length != 2) {
            plugin.getFeedback().send(player, "usage");
            return;
        }
        String name = args[1];
        if (!CourseDefinition.isValidName(name)) {
            plugin.getFeedback().send(player, "invalid-course-name");
            return;
        }
        if (plugin.getCourseManager().get(name) != null) {
            plugin.getFeedback().send(player, "course-exists", Map.of("course", name));
            plugin.getFeedback().sound(player, "command-error");
            return;
        }
        if (!plugin.getCourseManager().create(name, plugin.getConfig().getDouble("settings.default-fail-y", 60.0))) {
            plugin.getFeedback().send(player, "command-error");
            return;
        }
        plugin.getFeedback().send(player, "course-created", Map.of("course", name));
        plugin.getFeedback().sound(player, "command-success");
    }

    private void setPad(Player player, String[] args, CourseManager.PadType type) {
        if (args.length != 2) {
            plugin.getFeedback().send(player, "usage");
            return;
        }
        CourseDefinition course = course(player, args[1]);
        if (course == null) return;
        CourseManager.PadUpdateResult result = plugin.getCourseManager().setPad(args[1], type, player.getLocation());
        if (!handlePadResult(player, result, course, 0)) return;
        plugin.getFeedback().send(player, type == CourseManager.PadType.START ? "start-set" : "finish-set",
                Map.of("course", course.getName()));
        plugin.getFeedback().sound(player, "command-success");
    }

    private void addCheckpoint(Player player, String[] args) {
        if (args.length != 3) {
            plugin.getFeedback().send(player, "usage");
            return;
        }
        CourseDefinition course = course(player, args[1]);
        if (course == null) return;
        int number;
        try {
            number = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            plugin.getFeedback().send(player, "invalid-number");
            return;
        }
        int expected = course.getCheckpoints().size() + 1;
        CourseManager.PadUpdateResult result = plugin.getCourseManager().addCheckpoint(
                args[1], number, player.getLocation());
        if (!handlePadResult(player, result, course, expected)) return;
        plugin.getFeedback().send(player, "checkpoint-added",
                Map.of("number", Integer.toString(number), "course", course.getName()));
        plugin.getFeedback().sound(player, "command-success");
    }

    private void setFailY(Player player, String[] args) {
        if (args.length != 3) {
            plugin.getFeedback().send(player, "usage");
            return;
        }
        CourseDefinition course = course(player, args[1]);
        if (course == null) return;
        double y;
        try {
            y = Double.parseDouble(args[2]);
        } catch (NumberFormatException exception) {
            plugin.getFeedback().send(player, "invalid-number");
            return;
        }
        if (!Double.isFinite(y)) {
            plugin.getFeedback().send(player, "invalid-number");
            return;
        }
        if (!plugin.getCourseManager().setFailY(args[1], y, player.getWorld())) {
            plugin.getFeedback().send(player, "invalid-fail-y");
            return;
        }
        plugin.getFeedback().send(player, "fail-y-set",
                Map.of("y", Double.toString(y), "course", course.getName()));
        plugin.getFeedback().sound(player, "command-success");
    }

    private void showLeaderboard(CommandSender sender, String[] args) {
        if (!sender.hasPermission(LEADERBOARD_PERMISSION)) {
            plugin.getFeedback().send(sender, "leaderboard-no-permission");
            return;
        }
        if (args.length != 2) {
            plugin.getFeedback().send(sender, "usage");
            return;
        }
        CourseDefinition course = plugin.getCourseManager().get(args[1]);
        if (course == null) {
            plugin.getFeedback().send(sender, "leaderboard-no-course", Map.of("course", args[1]));
            return;
        }
        plugin.getFeedback().send(sender, "leaderboard-title", Map.of("course", course.getName()));
        if (sender instanceof Player player) plugin.getFeedback().sound(player, "command-success");
        List<PlayerRecord> top = plugin.getLeaderboardManager().topThree(course.getName());
        if (top.isEmpty()) {
            plugin.getFeedback().send(sender, "no-records", Map.of("course", course.getName()));
            return;
        }
        for (int index = 0; index < top.size(); index++) {
            PlayerRecord record = top.get(index);
            plugin.getFeedback().send(sender, "leaderboard-line", Map.of(
                    "rank", Integer.toString(index + 1),
                    "player", LeaderboardManager.playerName(record),
                    "time", TimeFormatter.format(record.getBestTimeMs())));
        }
    }

    private CourseDefinition course(Player player, String name) {
        CourseDefinition course = plugin.getCourseManager().get(name);
        if (course == null) {
            plugin.getFeedback().send(player, "course-not-found", Map.of("course", name));
            plugin.getFeedback().sound(player, "command-error");
        }
        return course;
    }

    private boolean handlePadResult(Player player, CourseManager.PadUpdateResult result,
                                    CourseDefinition course, int expected) {
        switch (result) {
            case SAVED -> { return true; }
            case COURSE_MISSING -> plugin.getFeedback().send(player, "course-not-found",
                    Map.of("course", course.getName()));
            case INVALID_LOCATION -> plugin.getFeedback().send(player, "invalid-pad");
            case DIFFERENT_WORLD -> plugin.getFeedback().send(player, "different-world");
            case DUPLICATE_PAD -> plugin.getFeedback().send(player, "duplicate-pad");
            case START_ALREADY_USED -> plugin.getFeedback().send(player, "start-already-used");
            case INVALID_CHECKPOINT_NUMBER -> plugin.getFeedback().send(player, "invalid-checkpoint",
                    Map.of("expected", Integer.toString(expected)));
        }
        plugin.getFeedback().sound(player, "command-error");
        return false;
    }

    private List<String> matching(List<String> candidates, String input) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered)).toList();
    }
}
