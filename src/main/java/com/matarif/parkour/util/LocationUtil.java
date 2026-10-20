package com.matarif.parkour.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

/** Encodes stable world identity and centralizes pad, recovery, and block-space checks. */
public final class LocationUtil {
    private static final double MAX_COORDINATE = 30_000_000.0;

    private LocationUtil() { }

    /** New entries use world UUID identity and retain the name for diagnostics; legacy names remain readable. */
    public static String serializePad(Location location) {
        if (location == null || location.getWorld() == null) return null;
        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();
        return location.getWorld().getUID() + "|" + location.getWorld().getName() + "|"
                + (blockX + 0.5) + "|" + blockY + "|" + (blockZ + 0.5) + "|"
                + location.getYaw() + "|" + location.getPitch();
    }

    public static Location deserialize(String value) {
        if (!isSerializedLocationValid(value)) return null;
        try {
            String[] parts = value.split("\\|", -1);
            World world;
            int offset;
            if (parts.length == 7) {
                world = Bukkit.getWorld(UUID.fromString(parts[0]));
                offset = 2;
            } else {
                world = Bukkit.getWorld(parts[0]);
                offset = 1;
            }
            if (world == null) return null;
            return new Location(world, Double.parseDouble(parts[offset]),
                    Double.parseDouble(parts[offset + 1]), Double.parseDouble(parts[offset + 2]),
                    Float.parseFloat(parts[offset + 3]), Float.parseFloat(parts[offset + 4]));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Structural validation is safe before Bukkit worlds have been loaded. */
    public static boolean isSerializedLocationValid(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            String[] parts = value.split("\\|", -1);
            int offset;
            if (parts.length == 7) {
                UUID.fromString(parts[0]);
                if (parts[1].isBlank()) return false;
                offset = 2;
            } else if (parts.length == 6) {
                if (parts[0].isBlank()) return false;
                offset = 1;
            } else {
                return false;
            }
            double x = Double.parseDouble(parts[offset]);
            double y = Double.parseDouble(parts[offset + 1]);
            double z = Double.parseDouble(parts[offset + 2]);
            float yaw = Float.parseFloat(parts[offset + 3]);
            float pitch = Float.parseFloat(parts[offset + 4]);
            return Double.isFinite(x) && Math.abs(x) <= MAX_COORDINATE
                    && Double.isFinite(y) && Math.abs(y) <= MAX_COORDINATE
                    && Double.isFinite(z) && Math.abs(z) <= MAX_COORDINATE
                    && Float.isFinite(yaw) && Float.isFinite(pitch);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean sameBlock(Location first, Location second) {
        return first != null && second != null && first.getWorld() != null && second.getWorld() != null
                && first.getWorld().getUID().equals(second.getWorld().getUID())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    public static boolean isSafePad(Location location) {
        if (location == null || location.getWorld() == null) return false;
        World world = location.getWorld();
        if (!insideWorldHeight(location)) return false;
        int feetY = location.getBlockY();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return false;
        Block feet = world.getBlockAt(location.getBlockX(), feetY, location.getBlockZ());
        Block head = world.getBlockAt(location.getBlockX(), feetY + 1, location.getBlockZ());
        return feet.getType() != Material.LAVA && head.getType() != Material.LAVA
                && feet.isPassable() && head.isPassable()
                && !world.getBlockAt(location.getBlockX(), feetY - 1, location.getBlockZ()).isPassable();
    }

    /** Avoids loading distant route chunks during run start; unloaded pads are checked when reached. */
    public static boolean isSafePadIfLoaded(Location location) {
        if (location == null || location.getWorld() == null) return false;
        World world = location.getWorld();
        if (!insideWorldHeight(location)) return false;
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return true;
        return isSafePad(location);
    }

    public static boolean samePosition(Location first, Location second) {
        return first != null && second != null && first.getWorld() != null && second.getWorld() != null
                && first.getWorld().getUID().equals(second.getWorld().getUID())
                && Double.compare(first.getX(), second.getX()) == 0
                && Double.compare(first.getY(), second.getY()) == 0
                && Double.compare(first.getZ(), second.getZ()) == 0
                && Float.compare(first.getYaw(), second.getYaw()) == 0
                && Float.compare(first.getPitch(), second.getPitch()) == 0;
    }

    public static Location safeRecoveryLocation(String encoded, int searchUpBlocks) {
        Location base = deserialize(encoded);
        if (base == null || base.getWorld() == null) return null;
        World world = base.getWorld();
        int chunkX = base.getBlockX() >> 4;
        int chunkZ = base.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) return null;
        Location centered = new Location(base.getWorld(), base.getBlockX() + 0.5, base.getBlockY(),
                base.getBlockZ() + 0.5, base.getYaw(), base.getPitch());
        int maxOffset = Math.max(0, Math.min(8, searchUpBlocks));
        for (int offset = 0; offset <= maxOffset; offset++) {
            Location candidate = centered.clone().add(0, offset, 0);
            if (isSafePad(candidate)) return candidate;
        }
        return null;
    }

    public static String blockKey(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":"
                + location.getBlockY() + ":" + location.getBlockZ();
    }

    public static boolean belongsToWorld(String encoded, World world) {
        if (world == null || !isSerializedLocationValid(encoded)) return false;
        String[] parts = encoded.split("\\|", -1);
        if (parts.length == 7) {
            try {
                return world.getUID().equals(UUID.fromString(parts[0]));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        return world.getName().equalsIgnoreCase(parts[0]);
    }

    public static boolean sameSerializedBlock(String first, String second) {
        if (!isSerializedLocationValid(first) || !isSerializedLocationValid(second)) return false;
        String[] a = first.split("\\|", -1);
        String[] b = second.split("\\|", -1);
        int aOffset = a.length == 7 ? 2 : 1;
        int bOffset = b.length == 7 ? 2 : 1;
        String aWorldName = a.length == 7 ? a[1] : a[0];
        String bWorldName = b.length == 7 ? b[1] : b[0];
        boolean bothHaveIds = a.length == 7 && b.length == 7;
        boolean sameWorld = bothHaveIds ? a[0].equalsIgnoreCase(b[0]) : aWorldName.equalsIgnoreCase(bWorldName);
        return sameWorld
                && floorCoordinate(a[aOffset]) == floorCoordinate(b[bOffset])
                && floorCoordinate(a[aOffset + 1]) == floorCoordinate(b[bOffset + 1])
                && floorCoordinate(a[aOffset + 2]) == floorCoordinate(b[bOffset + 2]);
    }

    private static boolean insideWorldHeight(Location location) {
        int feetY = location.getBlockY();
        return feetY > location.getWorld().getMinHeight() && feetY + 1 < location.getWorld().getMaxHeight();
    }

    private static int floorCoordinate(String value) {
        return (int) Math.floor(Double.parseDouble(value));
    }
}
