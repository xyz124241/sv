package de.simpleverse;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/** /tp und /mvtp: Welten als Ziel mit Tab-Completion, sonst normales Vanilla-/tp. */
public final class TpCommand implements CommandExecutor, TabCompleter {
    private final SimpleVerse pl;

    public TpCommand(SimpleVerse pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        boolean vanilla = c.getName().equalsIgnoreCase("tp");
        if (a.length >= 1 && a.length <= 2) {
            World w = pl.worlds().find(a[0]);
            if (w != null) {
                Player target;
                if (a.length == 2) {
                    target = Bukkit.getPlayerExact(a[1]);
                    if (target == null) {
                        pl.msg(s, "&fSpieler nicht gefunden.");
                        return true;
                    }
                    if (!target.equals(s) && !s.hasPermission("simpleverse.tp.others")) {
                        pl.msg(s, "&fKeine Berechtigung, andere zu teleportieren.");
                        return true;
                    }
                } else if (s instanceof Player p) {
                    target = p;
                } else {
                    pl.msg(s, "&fNutzung: /" + label + " <welt> <spieler>");
                    return true;
                }
                pl.sendToWorld(target, w);
                pl.msg(target, "&fTeleportiert nach &f" + pl.worlds().displayName(w) + "&f.");
                return true;
            }
        }
        if (vanilla) {
            String cmd = ("minecraft:tp " + String.join(" ", a)).trim();
            Bukkit.dispatchCommand(s, cmd);
        } else {
            pl.msg(s, "&fNutzung: /" + label + " <welt> [spieler]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        Set<String> out = new LinkedHashSet<>();
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            for (String n : pl.worlds().completions()) if (n.toLowerCase(Locale.ROOT).startsWith(last)) out.add(n);
        } else if (a.length == 2 && pl.worlds().find(a[0]) != null) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        }
        if (c.getName().equalsIgnoreCase("tp")) {
            Command v = pl.getServer().getCommandMap().getCommand("minecraft:tp");
            if (v != null) {
                try {
                    List<String> vc = v.tabComplete(s, "tp", a);
                    out.addAll(vc);
                } catch (Exception ignored) {}
            }
        }
        return new ArrayList<>(out);
    }
}
