package de.simpleverse;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Portale pro Welt:
 *  - Vanilla Nether-/End-Portale fuehren dorthin, was per /mv meta <welt> nether|end gesetzt ist.
 *    Ohne Eintrag ist das Portal in dieser Welt deaktiviert.
 *  - Eigene Portale (Bereiche) per /mv portal create.
 */
public final class PortalManager implements Listener {
    record Portal(String world, int x1, int y1, int z1, int x2, int y2, int z2, String target) {
        boolean contains(Location l) {
            return l.getWorld().getName().equals(world)
                    && l.getBlockX() >= x1 && l.getBlockX() <= x2
                    && l.getBlockY() >= y1 && l.getBlockY() <= y2
                    && l.getBlockZ() >= z1 && l.getBlockZ() <= z2;
        }
    }

    private final SimpleVerse pl;
    private final File file;
    private final Map<String, Portal> portals = new LinkedHashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public PortalManager(SimpleVerse pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "portals.yml");
    }

    public void load() {
        portals.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("portals");
        if (sec == null) return;
        for (String n : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(n);
            if (s == null) continue;
            portals.put(n, new Portal(s.getString("world"), s.getInt("x1"), s.getInt("y1"), s.getInt("z1"),
                    s.getInt("x2"), s.getInt("y2"), s.getInt("z2"), s.getString("target")));
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        portals.forEach((n, p) -> {
            String b = "portals." + n + ".";
            cfg.set(b + "world", p.world());
            cfg.set(b + "x1", p.x1());
            cfg.set(b + "y1", p.y1());
            cfg.set(b + "z1", p.z1());
            cfg.set(b + "x2", p.x2());
            cfg.set(b + "y2", p.y2());
            cfg.set(b + "z2", p.z2());
            cfg.set(b + "target", p.target());
        });
        try {
            cfg.save(file);
        } catch (IOException e) {
            pl.getLogger().warning("portals.yml nicht gespeichert: " + e.getMessage());
        }
    }

    public void setPos(Player p, int which) {
        Location l = p.getLocation().getBlock().getLocation();
        (which == 1 ? pos1 : pos2).put(p.getUniqueId(), l);
    }

    public String create(Player p, String name, String targetWorld) {
        if (!name.matches("[A-Za-z0-9_\\-]+")) return "Ungueltiger Name.";
        if (portals.containsKey(name)) return "Portal existiert bereits.";
        World t = pl.worlds().find(targetWorld);
        if (t == null) return "Zielwelt nicht gefunden.";
        Location a = pos1.get(p.getUniqueId()), b = pos2.get(p.getUniqueId());
        if (a == null || b == null) return "Setze zuerst /mv portal pos1 und pos2.";
        if (!a.getWorld().equals(b.getWorld())) return "Beide Punkte muessen in der gleichen Welt liegen.";
        portals.put(name, new Portal(a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()),
                t.getName()));
        save();
        return null;
    }

    public boolean delete(String name) {
        boolean r = portals.remove(name) != null;
        if (r) save();
        return r;
    }

    public Map<String, Portal> all() {
        return portals;
    }

    // ---------------------------------------------------------------- eigene Portale

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (portals.isEmpty()) return;
        Location f = e.getFrom(), t = e.getTo();
        if (t == null) return;
        if (f.getWorld() == t.getWorld() && f.getBlockX() == t.getBlockX() && f.getBlockY() == t.getBlockY()
                && f.getBlockZ() == t.getBlockZ()) return;
        Player p = e.getPlayer();
        for (Portal portal : portals.values()) {
            if (!portal.contains(t)) continue;
            long now = System.currentTimeMillis();
            if (cooldown.getOrDefault(p.getUniqueId(), 0L) > now) return;
            World w = Bukkit.getWorld(portal.target());
            if (w == null) return;
            cooldown.put(p.getUniqueId(), now + 3000);
            Bukkit.getScheduler().runTask(pl, () -> pl.sendToWorld(p, w));
            return;
        }
    }

    // ---------------------------------------------------------------- Vanilla Portale

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        TeleportCause c = e.getCause();
        String key;
        if (c == TeleportCause.NETHER_PORTAL) key = "nether";
        else if (c == TeleportCause.END_PORTAL) key = "end";
        else return;

        World from = e.getFrom().getWorld();
        String targetName = pl.worlds().linkTarget(from, key);
        World tw = targetName == null ? null : Bukkit.getWorld(targetName);
        if (tw == null) {
            e.setCancelled(true);
            pl.msg(e.getPlayer(), "&fDieses Portal ist in dieser Welt nicht verbunden. &f(/mv meta " + from.getName() + " " + key + " <welt>)");
            return;
        }

        if (pl.worlds().isLocked(tw) && !pl.opEverywhere(e.getPlayer())) {
            e.setCancelled(true);
            pl.msg(e.getPlayer(), "&fDieser Server ist geschlossen.");
            return;
        }

        Location to;
        if (c == TeleportCause.NETHER_PORTAL) {
            double f = 1;
            if (from.getEnvironment() == World.Environment.NORMAL && tw.getEnvironment() == World.Environment.NETHER) f = 0.125;
            else if (from.getEnvironment() == World.Environment.NETHER && tw.getEnvironment() == World.Environment.NORMAL) f = 8;
            Location o = e.getFrom();
            to = new Location(tw, o.getX() * f, o.getY(), o.getZ() * f, o.getYaw(), o.getPitch());
            e.setCanCreatePortal(true);
        } else {
            to = tw.getEnvironment() == World.Environment.THE_END
                    ? new Location(tw, 100.5, 49, 0.5, 90, 0)
                    : pl.worlds().spawn(tw);
        }
        e.setTo(to);
    }
}
