package de.simpleverse;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** Verwaltet Welten und ihre Meta-Daten (worlds.yml). */
public final class WorldManager {
    public static final List<String> META_KEYS = List.of("alias", "mode", "gamemode", "pvp", "difficulty", "nether", "end", "autoload");

    private final SimpleVerse pl;
    private final File file;
    private YamlConfiguration cfg;

    public WorldManager(SimpleVerse pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "worlds.yml");
    }

    // ---------------------------------------------------------------- laden / speichern

    public void load() {
        cfg = YamlConfiguration.loadConfiguration(file);
        for (World w : Bukkit.getWorlds()) ensure(w);
        ConfigurationSection sec = cfg.getConfigurationSection("worlds");
        if (sec != null) {
            for (String n : new ArrayList<>(sec.getKeys(false))) {
                if (Bukkit.getWorld(n) == null && sec.getBoolean(n + ".autoload", true)) {
                    try {
                        createFromConfig(n);
                    } catch (Exception ex) {
                        pl.getLogger().warning("Konnte Welt " + n + " nicht laden: " + ex.getMessage());
                    }
                }
            }
        }
        for (World w : Bukkit.getWorlds()) applySettings(w);
        save();
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            pl.getLogger().warning("worlds.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private String path(String world) {
        return "worlds." + world;
    }

    /** Eine bereits geladene Welt registrieren, falls noch unbekannt. */
    private void ensure(World w) {
        String p = path(w.getName());
        if (cfg.contains(p)) return;
        cfg.set(p + ".env", w.getEnvironment().name());
        cfg.set(p + ".type", "NORMAL");
        cfg.set(p + ".mode", pl.getConfig().getString("default-mode", "last"));
        cfg.set(p + ".autoload", true);
        String n = w.getName();
        String base = n.endsWith("_nether") ? n.substring(0, n.length() - 7)
                : n.endsWith("_the_end") ? n.substring(0, n.length() - 8) : n;
        switch (w.getEnvironment()) {
            case NORMAL -> {
                if (Bukkit.getWorld(n + "_nether") != null) cfg.set(p + ".nether", n + "_nether");
                if (Bukkit.getWorld(n + "_the_end") != null) cfg.set(p + ".end", n + "_the_end");
            }
            case NETHER -> {
                if (Bukkit.getWorld(base) != null && !base.equals(n)) cfg.set(p + ".nether", base);
            }
            case THE_END -> {
                if (Bukkit.getWorld(base) != null && !base.equals(n)) cfg.set(p + ".end", base);
            }
            default -> {}
        }
    }

    // ---------------------------------------------------------------- erstellen / laden / loeschen

    private WorldCreator creator(String name) {
        String p = path(name);
        WorldCreator c = new WorldCreator(name);
        c.environment(World.Environment.valueOf(cfg.getString(p + ".env", "NORMAL")));
        String type = cfg.getString(p + ".type", "NORMAL");
        if (type.equals("VOID")) c.generator(new VoidGenerator());
        else c.type(WorldType.valueOf(type));
        if (cfg.contains(p + ".seed")) c.seed(cfg.getLong(p + ".seed"));
        return c;
    }

    private World createFromConfig(String name) {
        World w = creator(name).createWorld();
        if (w == null) throw new IllegalStateException("Welt konnte nicht erstellt werden");
        applySettings(w);
        return w;
    }

    /** Neue Welt anlegen (oder vorhandenen Ordner importieren). Gibt Fehlermeldung oder null zurueck. */
    public String create(String name, World.Environment env, String type, Long seed, boolean importOnly) {
        if (!name.matches("[A-Za-z0-9_\\-]+")) return "Ungueltiger Name (erlaubt: Buchstaben, Zahlen, _ und -).";
        if (Bukkit.getWorld(name) != null) return "Diese Welt ist bereits geladen.";
        File folder = new File(Bukkit.getWorldContainer(), name);
        if (importOnly && !new File(folder, "level.dat").exists()) return "Kein Weltordner '" + name + "' mit level.dat gefunden.";
        if (!importOnly && folder.exists()) return "Ein Ordner '" + name + "' existiert bereits. Nutze /mv import.";
        String p = path(name);
        cfg.set(p + ".env", env.name());
        cfg.set(p + ".type", type);
        if (seed != null) cfg.set(p + ".seed", seed);
        cfg.set(p + ".mode", pl.getConfig().getString("default-mode", "last"));
        cfg.set(p + ".autoload", true);
        try {
            World w = createFromConfig(name);
            if (type.equals("VOID") && !importOnly) {
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) w.getBlockAt(dx, 63, dz).setType(Material.STONE);
                setSpawn(w, new Location(w, 0.5, 64, 0.5));
            }
        } catch (Exception ex) {
            cfg.set(p, null);
            return "Fehler: " + ex.getMessage();
        }
        save();
        // Jede normale Welt bekommt ihren eigenen Nether und ihr eigenes End (Void-Welten nicht)
        if (!importOnly && env == World.Environment.NORMAL && !type.equals("VOID")) {
            World base = Bukkit.getWorld(name);
            for (String kind : List.of("nether", "end")) {
                String err = setDimension(base, kind, true);
                if (err != null) pl.getLogger().warning("Dimension " + kind + " fuer " + name + ": " + err);
            }
        }
        return null;
    }

    /** Nether/End einer normalen Welt aktivieren (legt die Welt bei Bedarf an) oder deaktivieren. */
    public String setDimension(World base, String kind, boolean enable) {
        String p = path(base.getName());
        if (!enable) {
            // Dimension merken und als deaktiviert markieren (wirkt auch auf RTP)
            String dim = cfg.getString(p + "." + kind + "-world", cfg.getString(p + "." + kind));
            if (dim != null) {
                cfg.set(p + "." + kind + "-world", dim);
                cfg.set(path(dim) + ".disabled", true);
            }
            cfg.set(p + "." + kind, null);
            save();
            return null;
        }
        if (base.getEnvironment() != World.Environment.NORMAL) return "Das geht nur in normalen Welten.";
        boolean nether = kind.equals("nether");
        String dim = cfg.getString(p + "." + kind + "-world", base.getName() + (nether ? "_nether" : "_the_end"));
        if (Bukkit.getWorld(dim) == null) {
            String err;
            if (cfg.contains(path(dim))) err = load(dim);
            else {
                boolean exists = new File(Bukkit.getWorldContainer(), dim).exists();
                err = create(dim, nether ? World.Environment.NETHER : World.Environment.THE_END, "NORMAL", base.getSeed(), exists);
            }
            if (err != null) return err;
        }
        cfg.set(p + "." + kind, dim);
        cfg.set(p + "." + kind + "-world", dim);
        cfg.set(path(dim) + "." + kind, base.getName());
        cfg.set(path(dim) + ".disabled", null);
        save();
        return null;
    }

    /** Nether/End, das per /mv nether|end disable abgeschaltet wurde. */
    public boolean isDisabledDimension(World w) {
        return cfg.getBoolean(path(w.getName()) + ".disabled", false);
    }

    public boolean isLocked(World w) {
        return cfg.getBoolean(path(w.getName()) + ".locked", false);
    }

    public void setLocked(World w, boolean locked) {
        cfg.set(path(w.getName()) + ".locked", locked ? true : null);
        save();
    }

    public String load(String name) {
        if (Bukkit.getWorld(name) != null) return "Welt ist bereits geladen.";
        if (!cfg.contains(path(name))) return "Unbekannte Welt. Nutze /mv import " + name;
        try {
            createFromConfig(name);
        } catch (Exception ex) {
            return "Fehler: " + ex.getMessage();
        }
        cfg.set(path(name) + ".autoload", true);
        save();
        return null;
    }

    private String evacuate(World w) {
        if (w.equals(Bukkit.getWorlds().get(0))) return "Die Hauptwelt kann nicht entladen werden.";
        Location lobby = lobby();
        if (lobby.getWorld().equals(w)) return "Die Lobby liegt in dieser Welt. Setze zuerst eine neue Lobby.";
        for (Player p : new ArrayList<>(w.getPlayers())) p.teleport(lobby);
        return null;
    }

    public String unload(String name) {
        World w = Bukkit.getWorld(name);
        if (w == null) return "Welt nicht geladen.";
        String err = evacuate(w);
        if (err != null) return err;
        if (!Bukkit.unloadWorld(w, true)) return "Welt konnte nicht entladen werden.";
        cfg.set(path(w.getName()) + ".autoload", false);
        save();
        return null;
    }

    public String delete(String name) {
        World w = Bukkit.getWorld(name);
        String real = name;
        if (w != null) {
            real = w.getName();
            String err = evacuate(w);
            if (err != null) return err;
            if (!Bukkit.unloadWorld(w, false)) return "Welt konnte nicht entladen werden.";
        } else if (!cfg.contains(path(name))) {
            return "Unbekannte Welt.";
        }
        File folder = new File(Bukkit.getWorldContainer(), real);
        if (folder.exists()) {
            try (Stream<Path> s = Files.walk(folder.toPath())) {
                s.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
            } catch (IOException e) {
                return "Ordner konnte nicht geloescht werden: " + e.getMessage();
            }
        }
        cfg.set(path(real), null);
        save();
        return null;
    }

    // ---------------------------------------------------------------- Abfragen

    public World find(String in) {
        if (in == null || in.isEmpty()) return null;
        World w = Bukkit.getWorld(in);
        if (w != null) return w;
        for (World x : Bukkit.getWorlds()) {
            String a = alias(x);
            if (a != null && normalize(a).equalsIgnoreCase(in)) return x;
        }
        return null;
    }

    private static String normalize(String alias) {
        return alias.replaceAll("&[0-9a-fk-orA-FK-OR]", "").replace(' ', '_');
    }

    /** Alias ohne Farbcodes und Leerzeichen (zum Eintippen), oder null. */
    public String shortAlias(World w) {
        String a = alias(w);
        return a == null ? null : normalize(a);
    }

    public String alias(World w) {
        return cfg.getString(path(w.getName()) + ".alias");
    }

    public String displayName(World w) {
        String a = alias(w);
        return a != null ? a : w.getName();
    }

    public String mode(World w) {
        return cfg.getString(path(w.getName()) + ".mode", "last");
    }

    public GameMode gamemode(World w) {
        String g = cfg.getString(path(w.getName()) + ".gamemode", pl.getConfig().getString("default-gamemode", "SURVIVAL"));
        try {
            return GameMode.valueOf(g.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GameMode.SURVIVAL;
        }
    }

    public String linkTarget(World w, String key) {
        return cfg.getString(path(w.getName()) + "." + key);
    }

    public List<String> configuredNames() {
        ConfigurationSection s = cfg.getConfigurationSection("worlds");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    public boolean isConfigured(String name) {
        return cfg.contains(path(name));
    }

    public String env(String name) {
        return cfg.getString(path(name) + ".env", "?");
    }

    /** Alle Namen (Welten + Aliase) fuer Tab-Completion. */
    public List<String> completions() {
        Set<String> out = new LinkedHashSet<>();
        for (World w : Bukkit.getWorlds()) {
            out.add(w.getName());
            String a = alias(w);
            if (a != null) out.add(normalize(a));
        }
        return new ArrayList<>(out);
    }

    // ---------------------------------------------------------------- Spawn / Lobby

    public Location spawn(World w) {
        Location l = Locs.de(cfg.getString(path(w.getName()) + ".spawn"));
        if (l != null) return l;
        Location s = w.getSpawnLocation();
        return s.add(0.5, 0, 0.5);
    }

    public void setSpawn(World w, Location l) {
        cfg.set(path(w.getName()) + ".spawn", Locs.ser(l));
        w.setSpawnLocation(l);
        save();
    }

    public Location lobby() {
        Location l = Locs.de(cfg.getString("lobby"));
        return l != null ? l : spawn(Bukkit.getWorlds().get(0));
    }

    public boolean hasLobby() {
        return Locs.de(cfg.getString("lobby")) != null;
    }

    public void setLobby(Location l) {
        cfg.set("lobby", Locs.ser(l));
        save();
    }

    // ---------------------------------------------------------------- Meta

    public void applySettings(World w) {
        String p = path(w.getName());
        w.setPVP(cfg.getBoolean(p + ".pvp", true));
        String d = cfg.getString(p + ".difficulty");
        if (d != null) {
            try {
                w.setDifficulty(Difficulty.valueOf(d));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    /** Gibt Fehlermeldung oder null zurueck. */
    public String setMeta(World w, String key, String val) {
        String p = path(w.getName()) + "." + key;
        boolean clear = val.equals("-");
        switch (key.toLowerCase(Locale.ROOT)) {
            case "alias" -> cfg.set(p, clear ? null : val);
            case "mode" -> {
                if (!val.equalsIgnoreCase("spawn") && !val.equalsIgnoreCase("last")) return "Erlaubt: spawn, last";
                cfg.set(p, val.toLowerCase(Locale.ROOT));
            }
            case "gamemode" -> {
                try {
                    cfg.set(p, GameMode.valueOf(val.toUpperCase(Locale.ROOT)).name());
                } catch (IllegalArgumentException e) {
                    return "Erlaubt: survival, creative, adventure, spectator";
                }
            }
            case "pvp", "autoload" -> {
                if (!val.equalsIgnoreCase("true") && !val.equalsIgnoreCase("false")) return "Erlaubt: true, false";
                cfg.set(p, Boolean.parseBoolean(val));
            }
            case "difficulty" -> {
                try {
                    cfg.set(p, Difficulty.valueOf(val.toUpperCase(Locale.ROOT)).name());
                } catch (IllegalArgumentException e) {
                    return "Erlaubt: peaceful, easy, normal, hard";
                }
            }
            case "nether", "end" -> {
                if (clear) cfg.set(p, null);
                else {
                    World t = find(val);
                    if (t == null) return "Zielwelt nicht gefunden.";
                    cfg.set(p, t.getName());
                }
            }
            default -> {
                return "Unbekannter Schluessel. Moeglich: " + String.join(", ", META_KEYS);
            }
        }
        applySettings(w);
        save();
        return null;
    }

    public Map<String, Object> metaView(World w) {
        Map<String, Object> m = new LinkedHashMap<>();
        String p = path(w.getName());
        m.put("alias", cfg.getString(p + ".alias", "-"));
        m.put("mode", mode(w));
        m.put("gamemode", gamemode(w).name().toLowerCase(Locale.ROOT));
        m.put("pvp", cfg.getBoolean(p + ".pvp", true));
        m.put("difficulty", w.getDifficulty().name().toLowerCase(Locale.ROOT));
        m.put("nether", cfg.getString(p + ".nether", "-"));
        m.put("end", cfg.getString(p + ".end", "-"));
        m.put("autoload", cfg.getBoolean(p + ".autoload", true));
        m.put("locked", isLocked(w));
        m.put("disabled", isDisabledDimension(w));
        return m;
    }
}
