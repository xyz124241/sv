package de.simpleverse;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public final class Listeners implements Listener {
    private final SimpleVerse pl;

    public Listeners(SimpleVerse pl) {
        this.pl = pl;
    }

    /** Beim Betreten immer in der Lobby spawnen. */
    @EventHandler
    public void onSpawnLocation(PlayerSpawnLocationEvent e) {
        if (!pl.getConfig().getBoolean("join-lobby", true)) return;
        e.setSpawnLocation(pl.worlds().lobby());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        pl.store().switchTo(p, p.getWorld());
        if (pl.getConfig().getBoolean("separate-chat", true)) {
            net.kyori.adventure.text.Component m = e.joinMessage();
            e.joinMessage(null);
            pl.broadcastInWorld(p.getWorld(), m);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (pl.getConfig().getBoolean("separate-chat", true)) {
            net.kyori.adventure.text.Component m = e.quitMessage();
            e.quitMessage(null);
            pl.broadcastInWorld(p.getWorld(), m);
        }
        pl.saveAndRelease(p);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        pl.store().switchTo(e.getPlayer(), e.getPlayer().getWorld());
    }

    /** Letzte Position der Welt merken, die verlassen wird. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        World f = e.getFrom().getWorld();
        World t = e.getTo().getWorld();
        if (f != null && t != null && !f.equals(t)) pl.store().setLast(e.getPlayer(), e.getFrom());
    }

    /** Gesperrte Welten (/sv <welt> lock): nur Spieler mit Op in allen Welten duerfen rein. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLockedTeleport(PlayerTeleportEvent e) {
        World t = e.getTo().getWorld();
        if (t == null || t.equals(e.getFrom().getWorld())) return;
        if (!pl.worlds().isLocked(t) || pl.opEverywhere(e.getPlayer())) return;
        e.setCancelled(true);
        pl.msg(e.getPlayer(), "&fDieser Server ist geschlossen.");
    }

    /** RTP-Plugins (z.B. BetterRTP) in deaktivierten Dimensionen blockieren. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRtp(PlayerCommandPreprocessEvent e) {
        String[] a = e.getMessage().substring(1).trim().split("\\s+");
        String label = a[0].toLowerCase(Locale.ROOT);
        int c = label.indexOf(':');
        if (c >= 0) label = label.substring(c + 1);
        List<String> cmds = pl.getConfig().getStringList("rtp-commands");
        if (cmds.isEmpty()) cmds = List.of("rtp", "wild", "betterrtp");
        if (!cmds.contains(label)) return;
        Player p = e.getPlayer();
        World target = p.getWorld();
        for (int i = 1; i < a.length; i++) {
            World w = pl.worlds().find(a[i]);
            if (w != null) {
                target = w;
                break;
            }
        }
        if (pl.worlds().isDisabledDimension(target)) {
            e.setCancelled(true);
            pl.msg(p, "&fRTP ist hier deaktiviert (Nether/End ist ausgeschaltet).");
        }
    }

    /** Ohne Bett/Anker in der Welt respawnen, in der man gestorben ist. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (e.isBedSpawn() || e.isAnchorSpawn()) return;
        Location l = pl.worlds().spawn(e.getPlayer().getWorld());
        e.setRespawnLocation(l);
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        if (!pl.getConfig().getBoolean("separate-chat", true)) return;
        World w = e.getPlayer().getWorld();
        e.viewers().removeIf(a -> a instanceof Player v && !pl.canSeeChatOf(v, w));
    }

    /** Falls /tp nicht ueberschrieben werden konnte: "/tp <welt>" trotzdem abfangen. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String[] a = e.getMessage().substring(1).trim().split("\\s+");
        if (a.length != 2) return;
        if (!a[0].equalsIgnoreCase("tp") && !a[0].equalsIgnoreCase("teleport")) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("simpleverse.tp")) return;
        World w = pl.worlds().find(a[1]);
        if (w == null) return;
        e.setCancelled(true);
        pl.sendToWorld(p, w);
    }
}
