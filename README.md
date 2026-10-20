# MATARIF Parkour

Paper plugin for timed parkour courses, ordered checkpoints, fall recovery, and personal best leaderboards.

## Requirements and build

- Java 21
- Maven 3.8 or newer
- Paper 1.21.0 (compiled against the matching Paper API `1.21-R0.1-SNAPSHOT`)

Run `mvn clean verify` to compile the plugin and run its unit tests. The shaded plugin jar is `target/MATARIF-Parkour.jar`.

## Install

Copy the jar into the Paper server's `plugins` directory and restart. Course and leaderboard data are stored in `plugins/MATARIFParkour/` as `courses.json` and `records.json`.

Configure each pad while standing in the clear air block above a solid floor. The saved position is centered in that block, with the direction you faced retained for recovery.

1. `/parkour create CloudHop`
2. Stand at the start and run `/parkour setstart CloudHop`.
3. Stand at the finish and run `/parkour setfinish CloudHop`.
4. Add checkpoints in route order with `/parkour addcheckpoint CloudHop 1`, then `2`, and so on.
5. Set a failure boundary below all course pads with `/parkour setfaily CloudHop 40`.

A run begins when a player enters the start block or steps on its pressure plate. The player must enter every checkpoint in numeric order before entering the finish block. Checkpoint recovery keeps the original timer. A run ends on completion, death, quit, an external teleport, world change, flight, gliding, or ender pearl use when its rule is enabled.

## Commands and permissions

| Command | Permission | Purpose |
| --- | --- | --- |
| `/parkour create <name>` | `matarifparkour.admin` | Create a course. Names use 1-32 letters, numbers, `_`, or `-`. |
| `/parkour setstart <name>` | `matarifparkour.admin` | Save the start pad at your current block. |
| `/parkour setfinish <name>` | `matarifparkour.admin` | Save the finish pad. |
| `/parkour addcheckpoint <name> <number>` | `matarifparkour.admin` | Add the next sequential checkpoint. |
| `/parkour setfaily <name> <y>` | `matarifparkour.admin` | Set the recovery threshold below every course pad. |
| `/parkour leaderboard <name>` | `matarifparkour.leaderboard` | Show the fastest three personal bests. |
| `/parkour help` | none | Show command help. |

Admin permission defaults to operators; leaderboard permission defaults to all players. The `pk` alias is also available.

## Rules and data

- Elapsed time uses `System.nanoTime()` and displays as `Minutes:Seconds.Milliseconds`.
- Checkpoint locations and run routes are snapshotted when an attempt begins. Editing a course will not alter an in-progress route.
- Water is allowed by default. Lava returns the player to their latest safe pad. Set `rules.water-action` and `rules.lava-action` to `allow`, `recover`, or `invalidate` to change either rule.
- Teleports, flight, gliding, and ender pearls invalidate a run by default. Their rules can be changed in `config.yml`; changing worlds always invalidates a run.
- JSON is validated before use. A malformed or structurally invalid file causes a clear startup error and is left untouched instead of being replaced with empty data.
- Data writes use a single background writer, temporary files, and atomic replacement where the filesystem supports it. Shutdown waits for queued saves.
- Saved location strings support the older six-field world-name format as well as the new UUID-backed format.

Player-facing messages, sounds, titles, action bars, particles, cooldowns, fall recovery, and anti-exploit rules are configurable in `config.yml`. Invalid numeric values are normalized at startup and invalid sound names are disabled with a warning.
