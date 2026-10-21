package de.simpleverse;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/** /svtp <welt>: immer zum Spawn einer offenen Hauptwelt (kein Nether/End, keine gesperrten Welten). */
public final class SvtpCommand implements CommandExecutor, TabCompleter {
    private final SimpleVerse pl;

    public SvtpCommand(SimpleVerse pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) {
            pl.msg(s, "Nur fuer Spieler.");
            return true;
        }
        if (a.length < 1) {
            pl.msg(p, "Nutzung: /svtp <welt>");
            return true;
        }
        World w = pl.worlds().find(a[0]);
        if (w == null || w.getEnvironment() != World.Environment.NORMAL) {
            pl.msg(p, "Welt nicht gefunden.");
            return true;
        }
        if (pl.worlds().isLocked(w) && !pl.opEverywhere(p)) {
            pl.msg(p, "Dieser Server ist geschlossen.");
            return true;
        }
        p.teleport(pl.worlds().spawn(w));
        pl.msg(p, "Teleportiert nach " + pl.worlds().displayName(w) + "&f.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length != 1 || !(s instanceof Player p)) return out;
        boolean priv = pl.opEverywhere(p);
        String last = a[0].toLowerCase(Locale.ROOT);
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() != World.Environment.NORMAL) continue;
            if (pl.worlds().isLocked(w) && !priv) continue;
            String alias = pl.worlds().shortAlias(w);
            for (String n : alias == null ? List.of(w.getName()) : List.of(w.getName(), alias))
                if (n.toLowerCase(Locale.ROOT).startsWith(last)) out.add(n);
        }
        return out;
    }
}
