package de.simpleverse;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LobbyCommand implements CommandExecutor {
    private final SimpleVerse pl;

    public LobbyCommand(SimpleVerse pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) {
            pl.msg(s, "&fNur fuer Spieler.");
            return true;
        }
        if (c.getName().equalsIgnoreCase("setlobby")) {
            pl.worlds().setLobby(p.getLocation());
            pl.msg(p, "&fLobby gesetzt.");
        } else {
            p.teleport(pl.worlds().lobby());
            pl.msg(p, "&fDu bist in der &fLobby&f.");
        }
        return true;
    }
}
