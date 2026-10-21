package de.simpleverse;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Locations als Text speichern ("welt;x;y;z;yaw;pitch"). */
final class Locs {
    private Locs() {}

    static String ser(Location l) {
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    static Location de(String s) {
        if (s == null) return null;
        String[] p = s.split(";");
        if (p.length < 6) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
