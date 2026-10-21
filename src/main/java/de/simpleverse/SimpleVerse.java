package de.simpleverse;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleVerse extends JavaPlugin {
    public static final String PREFIX = "&8[&f&lSimpleVerse&8] &f";
    private static final LegacyComponentSerializer LEG = LegacyComponentSerializer.legacyAmpersand();

    private WorldManager worlds;
    private PlayerStore store;
    private PortalManager portals;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        worlds = new WorldManager(this);
        store = new PlayerStore(this);
        portals = new PortalManager(this);
        worlds.load();
        portals.load();

        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        getServer().getPluginManager().registerEvents(portals, this);

        bind("mv", new MvCommand(this));
        TpCommand tp = new TpCommand(this);
        bind("mvtp", tp);
        bind("tp", tp);
        bind("svtp", new SvtpCommand(this));
        LobbyCommand lobby = new LobbyCommand(this);
        bind("lobby", lobby);
        bind("setlobby", lobby);

        for (Player p : Bukkit.getOnlinePlayers()) store.switchTo(p, p.getWorld());
    }

    @Override
    public void onDisable() {
        if (store == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) saveAndRelease(p);
    }

    /** Aktuellen Zustand und letzte Position sichern (Quit / Shutdown). */
    public void saveAndRelease(Player p) {
        store.setLast(p, p.getLocation());
        String cur = store.currentKey(p);
        if (cur != null) store.capture(p, cur);
        store.release(p.getUniqueId());
    }

    private void bind(String name, Object executor) {
        PluginCommand c = getCommand(name);
        if (c == null) return;
        c.setExecutor((CommandExecutor) executor);
        if (executor instanceof TabCompleter t) c.setTabCompleter(t);
    }

    public void reloadAll() {
        reloadConfig();
        worlds.load();
        portals.load();
    }

    public WorldManager worlds() {
        return worlds;
    }

    public PlayerStore store() {
        return store;
    }

    public PortalManager portals() {
        return portals;
    }

    public void msg(CommandSender s, String text) {
        s.sendMessage(LEG.deserialize(PREFIX + text));
    }

    public void raw(CommandSender s, String text) {
        s.sendMessage(LEG.deserialize(text));
    }

    /**
     * Ziel beim Betreten einer Welt:
     * Modus "spawn" (z.B. PvP) -> immer Spawn, Modus "last" -> letzte Position (sonst Spawn).
     */
    public Location destination(Player p, World w) {
        if (p.getWorld().equals(w)) return worlds.spawn(w);
        if ("spawn".equals(worlds.mode(w))) return worlds.spawn(w);
        Location last = store.getLast(p, w);
        return last != null ? last : worlds.spawn(w);
    }

    /** Ist der Spieler in dieser Welt Op? (aktuelle Welt live, andere aus den gespeicherten Daten) */
    public boolean opIn(Player v, World w) {
        String key = store.key(w.getName());
        if (key.equals(store.currentKey(v))) return v.isOp();
        return store.data(v.getUniqueId()).getBoolean("states." + key + ".op", false);
    }

    /** Op in wirklich jeder geladenen Welt. */
    public boolean opEverywhere(Player p) {
        for (World w : Bukkit.getWorlds()) if (!opIn(p, w)) return false;
        return true;
    }

    /** Sieht v den Chat dieser Welt? Eigene Welt immer; sonst mit /sv chat enable in allen Welten, in denen v Op ist. */
    public boolean canSeeChatOf(Player v, World w) {
        if (v.getWorld().equals(w)) return true;
        return store.data(v.getUniqueId()).getBoolean("globalchat", false) && opIn(v, w);
    }

    /** Nachricht an Spieler, die den Chat dieser Welt sehen, + Konsole. */
    public void broadcastInWorld(World w, net.kyori.adventure.text.Component m) {
        if (m == null) return;
        for (Player v : Bukkit.getOnlinePlayers()) if (canSeeChatOf(v, w)) v.sendMessage(m);
        Bukkit.getConsoleSender().sendMessage(m);
    }

    public void sendToWorld(Player p, World w) {
        p.teleport(destination(p, w));
    }
}
