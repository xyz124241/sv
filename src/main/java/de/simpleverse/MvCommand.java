package de.simpleverse;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public final class MvCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("create", "import", "delete", "unload", "load", "list", "info", "state",
            "reload", "setspawn", "spawn", "meta", "tp", "op", "deop", "setlobby", "lobby", "portal", "nether", "end", "chat", "lock", "unlock", "rename", "help");
    private static final List<String> TYPES = List.of("normal", "flat", "amplified", "large_biomes", "void");
    private static final List<String> ENVS = List.of("normal", "nether", "end");

    private final SimpleVerse pl;
    private final Set<String> pendingDelete = new HashSet<>();

    public MvCommand(SimpleVerse pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(s);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create", "neu" -> create(s, a, false);
            case "import" -> create(s, a, true);
            case "delete" -> delete(s, a);
            case "unload" -> {
                if (a.length < 2) return usage(s, "/mv unload <welt>");
                result(s, pl.worlds().unload(a[1]), "Welt &f" + a[1] + " &fentladen.");
            }
            case "load" -> {
                if (a.length < 2) return usage(s, "/mv load <welt>");
                result(s, pl.worlds().load(a[1]), "Welt &f" + a[1] + " &fgeladen.");
            }
            case "list" -> list(s);
            case "info", "state" -> info(s, a);
            case "reload" -> {
                pl.reloadAll();
                pl.msg(s, "&fKonfiguration, Welten und Portale neu geladen.");
            }
            case "setspawn" -> {
                if (!(s instanceof Player p)) return onlyPlayer(s);
                pl.worlds().setSpawn(p.getWorld(), p.getLocation());
                pl.msg(s, "&fSpawn von &f" + pl.worlds().displayName(p.getWorld()) + " &fgesetzt.");
            }
            case "spawn" -> spawn(s, a);
            case "meta" -> meta(s, a);
            case "tp" -> tp(s, a);
            case "op" -> op(s, a, true);
            case "deop" -> op(s, a, false);
            case "setlobby" -> {
                if (!(s instanceof Player p)) return onlyPlayer(s);
                pl.worlds().setLobby(p.getLocation());
                pl.msg(s, "&fLobby gesetzt.");
            }
            case "lobby" -> {
                if (!(s instanceof Player p)) return onlyPlayer(s);
                p.teleport(pl.worlds().lobby());
            }
            case "portal" -> portal(s, a);
            case "nether", "end" -> dimension(s, a);
            case "chat" -> chat(s, a);
            case "lock", "unlock" -> {
                if (a.length < 2) return usage(s, "/sv <welt> lock|unlock");
                lock(s, a[1], a[0].equalsIgnoreCase("lock"));
            }
            case "rename" -> {
                if (a.length < 3) return usage(s, "/sv <welt> rename <name>");
                rename(s, a[1], String.join(" ", Arrays.copyOfRange(a, 2, a.length)));
            }
            default -> {
                // /sv <welt> lock|unlock|rename <name>
                if (a.length >= 2 && pl.worlds().find(a[0]) != null) {
                    String sub = a[1].toLowerCase(Locale.ROOT);
                    if (sub.equals("lock") || sub.equals("unlock")) lock(s, a[0], sub.equals("lock"));
                    else if (sub.equals("rename")) {
                        if (a.length < 3) return usage(s, "/sv " + a[0] + " rename <name>");
                        rename(s, a[0], String.join(" ", Arrays.copyOfRange(a, 2, a.length)));
                    } else help(s);
                } else help(s);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- Hilfen

    private boolean usage(CommandSender s, String u) {
        pl.msg(s, "&fNutzung: " + u);
        return true;
    }

    private boolean onlyPlayer(CommandSender s) {
        pl.msg(s, "&fDas geht nur als Spieler.");
        return true;
    }

    private void result(CommandSender s, String err, String ok) {
        if (err == null) pl.msg(s, "&f" + ok);
        else pl.msg(s, "&f" + err);
    }

    private void help(CommandSender s) {
        pl.raw(s, "&8--- &f&lSimpleVerse &8---");
        String[][] h = {
                {"/mv create <name> <normal|void> [seed]", "Neue Welt (normal bekommt eigenen Nether + End, void nicht)"},
                {"/mv nether|end enable|disable [welt]", "Nether/End einer Welt ein-/ausschalten"},
                {"/mv chat enable|disable", "Chat aller Welten sehen, in denen du Op bist"},
                {"/sv <welt> rename <name>", "Anzeigenamen der Welt aendern"},
                {"/svtp <welt>", "Zum Spawn einer offenen Welt (alle Spieler)"},
                {"/sv <welt> lock|unlock","Welt sperren (nur Op in allen Welten darf rein)"},
                {"/mv import <name> [normal|nether|end]", "Vorhandenen Weltordner einbinden"},
                {"/mv delete <welt>", "Welt loeschen (mit Bestaetigung)"},
                {"/mv load|unload <welt>", "Welt laden / entladen"},
                {"/mv list", "Alle Welten"},
                {"/mv info|state [welt]", "Details zu einer Welt"},
                {"/mv meta <welt> [schluessel] [wert]", "alias, mode, gamemode, pvp, difficulty, nether, end, autoload"},
                {"/mv setspawn | /mv spawn [welt]", "Spawn der aktuellen Welt setzen / hingehen"},
                {"/mv tp <welt> [spieler]  (oder /tp <welt>)", "In eine Welt teleportieren"},
                {"/mv op|deop <spieler> [welt]", "Op nur fuer eine Welt"},
                {"/mv setlobby, /setlobby, /lobby", "Lobby setzen / hingehen"},
                {"/mv portal pos1|pos2|create|delete|list", "Eigene Portale"},
                {"/mv reload", "Neu laden"},
        };
        for (String[] l : h) pl.raw(s, "&f" + l[0] + " &8- &f" + l[1]);
    }

    private World world(CommandSender s, String name) {
        World w = pl.worlds().find(name);
        if (w == null) pl.msg(s, "&fWelt '" + name + "' nicht gefunden oder nicht geladen.");
        return w;
    }

    // ---------------------------------------------------------------- Welten

    private void create(CommandSender s, String[] a, boolean imp) {
        if (a.length < 2) {
            usage(s, imp ? "/mv import <name> [normal|nether|end]" : "/mv create <name> [normal|nether|end] [typ] [seed]");
            return;
        }
        World.Environment env = World.Environment.NORMAL;
        String type = "NORMAL";
        Long seed = null;
        int i = 2;
        if (a.length > i) {
            switch (a[i].toLowerCase(Locale.ROOT)) {
                case "normal" -> { env = World.Environment.NORMAL; i++; }
                case "nether" -> { env = World.Environment.NETHER; i++; }
                case "end", "the_end" -> { env = World.Environment.THE_END; i++; }
                default -> {}
            }
        }
        if (!imp && a.length > i && TYPES.contains(a[i].toLowerCase(Locale.ROOT))) {
            type = a[i].toUpperCase(Locale.ROOT);
            i++;
        }
        if (!imp && a.length > i) {
            try {
                seed = Long.parseLong(a[i]);
            } catch (NumberFormatException e) {
                seed = (long) a[i].hashCode();
            }
        }
        pl.msg(s, "&fWelt &f" + a[1] + " &fwird " + (imp ? "importiert" : "erstellt") + " ...");
        result(s, pl.worlds().create(a[1], env, type, seed, imp), "Welt &f" + a[1] + " &fbereit. &8(/tp " + a[1] + ")");
    }

    private void delete(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/mv delete <welt>");
            return;
        }
        String key = s.getName() + ":" + a[1].toLowerCase(Locale.ROOT);
        if (a.length >= 3 && a[2].equalsIgnoreCase("confirm") && pendingDelete.remove(key)) {
            result(s, pl.worlds().delete(a[1]), "Welt &f" + a[1] + " &fgeloescht.");
            return;
        }
        pendingDelete.add(key);
        pl.msg(s, "&fAchtung: Das loescht die Welt &f" + a[1] + " &fendgueltig! Bestaetige mit &f/mv delete " + a[1] + " confirm");
    }

    private void list(CommandSender s) {
        pl.raw(s, "&8--- &f&lWelten &8---");
        for (String n : pl.worlds().configuredNames()) {
            World w = Bukkit.getWorld(n);
            String line = (w != null ? "&f● " : "&8● ") + "&f" + n;
            if (w != null && pl.worlds().alias(w) != null) line += " &8(" + pl.worlds().alias(w) + "&8)";
            line += " &f" + pl.worlds().env(n).toLowerCase(Locale.ROOT);
            line += w != null ? " &8| &fSpieler: &f" + w.getPlayers().size() : " &8| &fnicht geladen";
            pl.raw(s, line);
        }
    }

    private void info(CommandSender s, String[] a) {
        World w;
        if (a.length >= 2) w = world(s, a[1]);
        else if (s instanceof Player p) w = p.getWorld();
        else {
            usage(s, "/mv info <welt>");
            return;
        }
        if (w == null) return;
        pl.raw(s, "&8--- &f&l" + w.getName() + " &8---");
        pl.raw(s, "&fTyp: &f" + w.getEnvironment().name().toLowerCase(Locale.ROOT) + " &8| &fSpieler: &f" + w.getPlayers().size()
                + " &8| &fGeladene Chunks: &f" + w.getLoadedChunks().length);
        pl.worlds().metaView(w).forEach((k, v) -> pl.raw(s, "&f" + k + ": &f" + v));
        pl.raw(s, "&fSpawn: &f" + (int) pl.worlds().spawn(w).getX() + " " + (int) pl.worlds().spawn(w).getY() + " " + (int) pl.worlds().spawn(w).getZ());
    }

    private void spawn(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            onlyPlayer(s);
            return;
        }
        World w = a.length >= 2 ? world(s, a[1]) : p.getWorld();
        if (w == null) return;
        p.teleport(pl.worlds().spawn(w));
    }

    private void meta(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/mv meta <welt> [schluessel] [wert]");
            return;
        }
        World w = world(s, a[1]);
        if (w == null) return;
        if (a.length == 2) {
            pl.raw(s, "&8--- &f&lMeta: " + w.getName() + " &8---");
            pl.worlds().metaView(w).forEach((k, v) -> pl.raw(s, "&f" + k + ": &f" + v));
            return;
        }
        if (a.length < 4) {
            usage(s, "/mv meta " + w.getName() + " " + a[2] + " <wert|->");
            return;
        }
        String val = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
        result(s, pl.worlds().setMeta(w, a[2], val), a[2] + " von &f" + w.getName() + " &fgesetzt.");
    }

    private void tp(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/mv tp <welt> [spieler]");
            return;
        }
        World w = world(s, a[1]);
        if (w == null) return;
        Player t;
        if (a.length >= 3) {
            t = Bukkit.getPlayerExact(a[2]);
            if (t == null) {
                pl.msg(s, "&fSpieler nicht gefunden.");
                return;
            }
        } else if (s instanceof Player p) t = p;
        else {
            onlyPlayer(s);
            return;
        }
        pl.sendToWorld(t, w);
    }

    private void op(CommandSender s, String[] a, boolean value) {
        if (a.length < 2) {
            usage(s, "/mv " + (value ? "op" : "deop") + " <spieler> [welt]");
            return;
        }
        World w;
        if (a.length >= 3) w = world(s, a[2]);
        else if (s instanceof Player p) w = p.getWorld();
        else {
            usage(s, "/mv " + (value ? "op" : "deop") + " <spieler> <welt>");
            return;
        }
        if (w == null) return;
        @SuppressWarnings("deprecation")
        OfflinePlayer off = Bukkit.getOfflinePlayer(a[1]);
        if (!off.hasPlayedBefore() && !off.isOnline()) {
            pl.msg(s, "&fSpieler '" + a[1] + "' war noch nie auf dem Server.");
            return;
        }
        String key = pl.store().key(w.getName());
        pl.store().edit(off.getUniqueId(), d -> d.set("states." + key + ".op", value));
        Player on = off.getPlayer();
        if (on != null && key.equals(pl.store().currentKey(on))) {
            on.setOp(value);
            on.updateCommands();
        }
        pl.msg(s, "&f" + a[1] + " &fist jetzt " + (value ? "&fOp" : "&fkein Op") + " &fin &f" + w.getName() + "&f.");
    }

    /** Anzeigenamen (Alias) einer Welt aendern. Der Ordnername bleibt gleich. "-" setzt zurueck. */
    private void rename(CommandSender s, String name, String newName) {
        World w = world(s, name);
        if (w == null) return;
        result(s, pl.worlds().setMeta(w, "alias", newName), w.getName() + " heisst jetzt " + newName + "&f.");
    }

    /** Welt sperren/entsperren: nur fuer Spieler mit Op in ALLEN Welten (und Konsole). */
    private void lock(CommandSender s, String name, boolean lock) {
        if (s instanceof Player p && !pl.opEverywhere(p)) {
            pl.msg(s, "&fDas darfst du nur, wenn du in jeder Welt Op bist.");
            return;
        }
        World w = world(s, name);
        if (w == null) return;
        if (lock) {
            if (pl.worlds().lobby().getWorld().equals(w)) {
                pl.msg(s, "&fDie Lobby-Welt kann nicht gesperrt werden.");
                return;
            }
            pl.worlds().setLocked(w, true);
            for (Player v : new ArrayList<>(w.getPlayers()))
                if (!pl.opEverywhere(v)) {
                    v.teleport(pl.worlds().lobby());
                    pl.msg(v, "&fDie Welt wurde gesperrt.");
                }
            pl.msg(s, "&f" + w.getName() + " &fist jetzt &fgesperrt&f.");
        } else {
            pl.worlds().setLocked(w, false);
            pl.msg(s, "&f" + w.getName() + " &fist jetzt &fentsperrt&f.");
        }
    }

    /** /mv nether|end enable|disable [welt] */
    private void dimension(CommandSender s, String[] a) {
        String kind = a[0].toLowerCase(Locale.ROOT);
        if (a.length < 2 || !(a[1].equalsIgnoreCase("enable") || a[1].equalsIgnoreCase("disable"))) {
            usage(s, "/mv " + kind + " enable|disable [welt]");
            return;
        }
        World w;
        if (a.length >= 3) w = world(s, a[2]);
        else if (s instanceof Player p) w = p.getWorld();
        else {
            usage(s, "/mv " + kind + " " + a[1] + " <welt>");
            return;
        }
        if (w == null) return;
        boolean enable = a[1].equalsIgnoreCase("enable");
        if (enable) pl.msg(s, "&fWird aktiviert (Welt wird bei Bedarf erstellt) ...");
        result(s, pl.worlds().setDimension(w, kind, enable),
                kind + " in &f" + w.getName() + " &f" + (enable ? "aktiviert." : "deaktiviert."));
    }

    /** /mv chat enable|disable: Chat aller Welten sehen (nur Op). */
    private void chat(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            onlyPlayer(s);
            return;
        }
        if (a.length < 2 || !(a[1].equalsIgnoreCase("enable") || a[1].equalsIgnoreCase("disable"))) {
            usage(s, "/mv chat enable|disable");
            return;
        }
        boolean on = a[1].equalsIgnoreCase("enable");
        pl.store().data(p.getUniqueId()).set("globalchat", on);
        pl.store().save(p.getUniqueId());
        pl.msg(s, on ? "&fDu siehst jetzt den Chat aller Welten." : "&fDu siehst nur noch den Chat deiner Welt.");
    }

    private void portal(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/mv portal pos1|pos2|create <name> <zielwelt>|delete <name>|list");
            return;
        }
        PortalManager pm = pl.portals();
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "pos1", "pos2" -> {
                if (!(s instanceof Player p)) {
                    onlyPlayer(s);
                    return;
                }
                pm.setPos(p, a[1].equalsIgnoreCase("pos1") ? 1 : 2);
                pl.msg(s, "&f" + a[1] + " gesetzt.");
            }
            case "create" -> {
                if (!(s instanceof Player p)) {
                    onlyPlayer(s);
                    return;
                }
                if (a.length < 4) {
                    usage(s, "/mv portal create <name> <zielwelt>");
                    return;
                }
                result(s, pm.create(p, a[2], a[3]), "Portal &f" + a[2] + " &ferstellt.");
            }
            case "delete" -> {
                if (a.length < 3) {
                    usage(s, "/mv portal delete <name>");
                    return;
                }
                if (pm.delete(a[2])) pl.msg(s, "&fPortal geloescht.");
                else pl.msg(s, "&fPortal nicht gefunden.");
            }
            case "list" -> pm.all().forEach((n, p) -> pl.raw(s, "&f" + n + " &8- &f" + p.world() + " &8-> &f" + p.target()));
            default -> usage(s, "/mv portal pos1|pos2|create|delete|list");
        }
    }

    // ---------------------------------------------------------------- Tab-Completion

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(SUBS);
            out.addAll(pl.worlds().completions());
        } else if (a.length == 2 && pl.worlds().find(a[0]) != null && !SUBS.contains(a[0].toLowerCase(Locale.ROOT))) {
            out.addAll(List.of("lock", "unlock", "rename"));
        } else {
            String sub = a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "create", "import" -> {
                    if (a.length == 3) out.addAll(sub.equals("import") ? ENVS : List.of("normal", "void"));
                }
                case "nether", "end" -> {
                    if (a.length == 2) out.addAll(List.of("enable", "disable"));
                    else if (a.length == 3) out.addAll(pl.worlds().completions());
                }
                case "chat" -> {
                    if (a.length == 2) out.addAll(List.of("enable", "disable"));
                }
                case "lock", "unlock", "rename" -> {
                    if (a.length == 2) out.addAll(pl.worlds().completions());
                }
                case "unload", "delete", "info", "state", "spawn" -> {
                    if (a.length == 2) out.addAll(pl.worlds().completions());
                    else if (sub.equals("delete") && a.length == 3) out.add("confirm");
                }
                case "load" -> {
                    if (a.length == 2) out.addAll(pl.worlds().configuredNames());
                }
                case "tp" -> {
                    if (a.length == 2) out.addAll(pl.worlds().completions());
                    else if (a.length == 3) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                }
                case "meta" -> {
                    if (a.length == 2) out.addAll(pl.worlds().completions());
                    else if (a.length == 3) out.addAll(WorldManager.META_KEYS);
                    else if (a.length == 4) {
                        switch (a[2].toLowerCase(Locale.ROOT)) {
                            case "mode" -> out.addAll(List.of("spawn", "last"));
                            case "gamemode" -> out.addAll(List.of("survival", "creative", "adventure", "spectator"));
                            case "pvp", "autoload" -> out.addAll(List.of("true", "false"));
                            case "difficulty" -> out.addAll(List.of("peaceful", "easy", "normal", "hard"));
                            case "nether", "end" -> {
                                out.addAll(pl.worlds().completions());
                                out.add("-");
                            }
                            case "alias" -> out.add("-");
                            default -> {}
                        }
                    }
                }
                case "op", "deop" -> {
                    if (a.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    else if (a.length == 3) out.addAll(pl.worlds().completions());
                }
                case "portal" -> {
                    if (a.length == 2) out.addAll(List.of("pos1", "pos2", "create", "delete", "list"));
                    else if (a.length == 3 && a[1].equalsIgnoreCase("delete")) out.addAll(pl.portals().all().keySet());
                    else if (a.length == 4 && a[1].equalsIgnoreCase("create")) out.addAll(pl.worlds().completions());
                }
                default -> {}
            }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
