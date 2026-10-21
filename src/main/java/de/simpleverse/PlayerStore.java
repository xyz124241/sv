package de.simpleverse;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Speichert pro Spieler und Welt: Inventar, Enderchest, Leben, Hunger, XP, Gamemode und Op-Status.
 * Dazu die letzte Position pro Welt.
 */
public final class PlayerStore {
    private final SimpleVerse pl;
    private final File dir;
    private final Map<UUID, YamlConfiguration> cache = new HashMap<>();

    public PlayerStore(SimpleVerse pl) {
        this.pl = pl;
        this.dir = new File(pl.getDataFolder(), "players");
        dir.mkdirs();
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    public YamlConfiguration data(UUID id) {
        return cache.computeIfAbsent(id, i -> YamlConfiguration.loadConfiguration(file(i)));
    }

    public void save(UUID id) {
        YamlConfiguration c = cache.get(id);
        if (c == null) return;
        try {
            c.save(file(id));
        } catch (IOException e) {
            pl.getLogger().warning("Spielerdaten nicht gespeichert (" + id + "): " + e.getMessage());
        }
    }

    public void release(UUID id) {
        save(id);
        cache.remove(id);
    }

    /** Daten eines (auch offline) Spielers aendern und speichern. */
    public void edit(UUID id, Consumer<YamlConfiguration> c) {
        boolean cached = cache.containsKey(id);
        c.accept(data(id));
        save(id);
        if (!cached) cache.remove(id);
    }

    public String key(String world) {
        return pl.getConfig().getString("state-groups." + world, world);
    }

    public String currentKey(Player p) {
        return data(p.getUniqueId()).getString("current");
    }

    // ---------------------------------------------------------------- letzte Position

    public void setLast(Player p, Location l) {
        data(p.getUniqueId()).set("last." + l.getWorld().getName(), Locs.ser(l));
    }

    public Location getLast(Player p, World w) {
        return Locs.de(data(p.getUniqueId()).getString("last." + w.getName()));
    }

    // ---------------------------------------------------------------- Zustand

    private static String enc(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    private static ItemStack[] dec(String s) {
        return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s));
    }

    public void capture(Player p, String key) {
        YamlConfiguration d = data(p.getUniqueId());
        String b = "states." + key + ".";
        d.set(b + "inv", enc(p.getInventory().getContents()));
        d.set(b + "ender", enc(p.getEnderChest().getContents()));
        d.set(b + "health", p.isDead() ? 20.0 : p.getHealth());
        d.set(b + "food", p.getFoodLevel());
        d.set(b + "saturation", (double) p.getSaturation());
        d.set(b + "level", p.getLevel());
        d.set(b + "exp", (double) p.getExp());
        d.set(b + "gamemode", p.getGameMode().name());
        d.set(b + "op", p.isOp());
    }

    public void apply(Player p, String key, World to) {
        ConfigurationSection s = data(p.getUniqueId()).getConfigurationSection("states." + key);

        p.getInventory().clear();
        p.getEnderChest().clear();
        if (s != null && s.getString("inv") != null) p.getInventory().setContents(dec(s.getString("inv")));
        if (s != null && s.getString("ender") != null) p.getEnderChest().setContents(dec(s.getString("ender")));

        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setFireTicks(0);
        p.setFallDistance(0);

        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double maxH = max != null ? max.getValue() : 20.0;
        double h = s != null && s.contains("health") ? s.getDouble("health") : maxH;
        p.setHealth(Math.max(0.5, Math.min(maxH, h)));
        p.setFoodLevel(s != null ? s.getInt("food", 20) : 20);
        p.setSaturation((float) (s != null ? s.getDouble("saturation", 5) : 5));
        p.setLevel(s != null ? s.getInt("level", 0) : 0);
        p.setExp((float) Math.max(0, Math.min(1, s != null ? s.getDouble("exp", 0) : 0)));

        GameMode gm = pl.worlds().gamemode(to);
        if (s != null && s.contains("gamemode")) {
            try {
                gm = GameMode.valueOf(s.getString("gamemode"));
            } catch (IllegalArgumentException ignored) {}
        }
        p.setGameMode(gm);

        boolean op = s != null && s.getBoolean("op", false);
        if (p.isOp() != op) p.setOp(op);
        p.updateCommands();
    }

    /** Spieler betritt eine Welt: alten Zustand sichern, Zustand der neuen Welt laden. */
    public void switchTo(Player p, World to) {
        YamlConfiguration d = data(p.getUniqueId());
        String cur = d.getString("current");
        String next = key(to.getName());
        if (cur == null) {
            d.set("current", next);
            if (!p.hasPlayedBefore()) apply(p, next, to);
            return;
        }
        if (cur.equals(next)) return;
        capture(p, cur);
        apply(p, next, to);
        d.set("current", next);
        save(p.getUniqueId());
    }
}
