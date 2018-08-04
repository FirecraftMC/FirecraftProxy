package net.firecraftmc.proxy;

import net.firecraftmc.api.FirecraftAPI;
import net.firecraftmc.api.enums.Rank;
import net.firecraftmc.api.enums.ServerType;
import net.firecraftmc.api.menus.MenuManager;
import net.firecraftmc.api.model.Database;
import net.firecraftmc.api.model.ProxyWorker;
import net.firecraftmc.api.model.player.FirecraftPlayer;
import net.firecraftmc.api.model.server.FirecraftServer;
import net.firecraftmc.api.packets.FPacketMuteExpire;
import net.firecraftmc.api.packets.FPacketRankUpdate;
import net.firecraftmc.api.plugin.IFirecraftProxy;
import net.firecraftmc.api.punishments.Punishment;
import net.firecraftmc.api.punishments.Punishment.Type;
import net.firecraftmc.api.util.Messages;
import net.firecraftmc.api.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.*;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Level;

/**
 * FirecraftProxy class for the Socket Server
 * Controls the initialization of the socket and handles connections
 */
@SuppressWarnings("MethodOnlyUsedFromInnerClass")
public class FirecraftProxy extends JavaPlugin implements Listener, IFirecraftProxy {

    private static final String profileUrlString = "https://sessionserver.mojang.com/session/minecraft/profile/{uuid}?unsigned=false";
    volatile List<ProxyWorker> proxyWorkers = new ArrayList<>();
    private ServerSocket serverSocket;
    private final HashMap<UUID, FirecraftPlayer> localPlayers = new HashMap<>();
    private Database database;
    private FirecraftServer server;
    private MenuManager menuManager;

    public void onEnable() {
        this.saveDefaultConfig();

        int port = this.getConfig().getInt("port");
        getLogger().log(Level.INFO, "Starting the thread used for the socket.");
        Thread thread = new Thread(() -> {
            getLogger().log(Level.INFO, "Creating a ServerSocket on port " + port);
            try {
                serverSocket = new ServerSocket(port);

                Socket socket;
                while ((socket = serverSocket.accept()) != null) {
                    ProxyWorker worker = new ProxyWorker(this, socket);
                    worker.start();
                    proxyWorkers.add(worker);
                    getLogger().log(Level.INFO, "Received connection from: " + socket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();

        FirecraftAPI.setFirecraftProxy(this);

        database = new Database(getConfig().getString("mysql.user"), getConfig().getString("mysql.database"),
                getConfig().getString("mysql.password"), getConfig().getInt("mysql.port"), getConfig().getString("mysql.hostname"));
        database.openConnection();

        this.server = database.getServer(getConfig().getString("server"));
        String ip = null;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
            ip = ip.replace("/", "");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        if (server != null) {
            server.setIp(ip);
            database.saveServer(server);
        }

        new BukkitRunnable() {
            public void run() {
                checkPlayerInfo();
            }
        }.runTaskTimerAsynchronously(this, 0L, 20 * 60 * 5);

        new BukkitRunnable() {
            public void run() {
                checkTempPunishments();
            }
        }.runTaskTimerAsynchronously(this, 0L, 20 * 60);

        this.getServer().getPluginManager().registerEvents(this, this);

        getLogger().log(Level.INFO, "Starting the socket worker check runnable");
        new BukkitRunnable() {
            public void run() {
                Iterator<ProxyWorker> iter = proxyWorkers.iterator();

                while (iter.hasNext()) {
                    ProxyWorker worker = iter.next();
                    if (!worker.isConnected()) {
                        worker.interrupt();
                        try {
                            worker.disconnect();
                        } catch (IOException e) {
                        }
                        System.out.println("Removed a socket worker.");
                        iter.remove();
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20);
        
        this.menuManager = new MenuManager(this);

        getLogger().log(Level.INFO, "Successfully loaded the plugin.");
    }

    public void onDisable() {
        try {
            for (ProxyWorker worker : proxyWorkers) {
                worker.disconnect();
            }
            this.serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        server.setIp("");
        database.saveServer(server);
        database.closeConnection();
    }

    public void removeWorker(ProxyWorker worker) {
        this.proxyWorkers.remove(worker);
    }

    @EventHandler
    public void onPlayerPreJoin(AsyncPlayerPreLoginEvent e) {
        if (!FirecraftAPI.firecraftTeam.contains(e.getUniqueId())) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§4Only Firecraft Team members may join this serverid.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        FirecraftPlayer player = database.getPlayer(e.getPlayer().getUniqueId());
        this.localPlayers.put(player.getUniqueId(), player);
        e.setJoinMessage(player.getDisplayName() + " §ejoined the game.");
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        FirecraftPlayer player = this.localPlayers.get(e.getPlayer().getUniqueId());
        e.setQuitMessage(player.getDisplayName() + " §eleft the game.");
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        FirecraftPlayer player = this.localPlayers.get(e.getPlayer().getUniqueId());
        e.setFormat(player.getDisplayName() + "§8: §f" + e.getMessage());
    }

    public Database getFCDatabase() {
        return database;
    }
    
    public MenuManager getMenuManager() {
        return menuManager;
    }
    
    public Collection<ProxyWorker> getProxyWorkers() {
        return proxyWorkers;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("reloaddata")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = this.localPlayers.get(((Player) sender).getUniqueId());
                if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                    player.sendMessage("Only Firecraft Team members can reload player data.");
                    return true;
                }

                player.sendMessage("&aStarting a reload of player data.");
                this.checkPlayerInfo();
                player.sendMessage("&aReload of player data is now complete.");
            } else {
                System.out.println("§cOnly players may reload the player data.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("firecraftserver")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Messages.onlyPlayers);
                return true;
            }

            FirecraftPlayer player = database.getPlayer(((Player) sender).getUniqueId());
            if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                player.sendMessage("<ec>Only members of The Firecraft Team can use that command.");
                return true;
            }

            if (!(args.length > 0)) {
                player.sendMessage("<ec>Invalid amount of arguments");
                return true;
            }

            if (args[0].equalsIgnoreCase("create")) {
                if (!(args.length == 5)) {
                    player.sendMessage("<ec>Invalid amount of arguments: /<label> <create|c> <id> <name> <color> <type>".replace("<label>", label));
                    return true;
                }

                ChatColor color = ChatColor.valueOf(args[3]);
                ServerType type = ServerType.valueOf(args[4]);

                FirecraftServer server = new FirecraftServer(args[1], args[2], color, type);
                String ip = null;
                try {
                    ip = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                server.setIp(ip.replace("/", ""));

                database.saveServer(server);
                this.server = server;
                player.sendMessage("<nc>Created a server with the id <vc>" + server.getId());
                getConfig().set("server", server.getId());
                saveConfig();
            }
        }

        return true;
    }

    private void checkTempPunishments() {
        getLogger().log(Level.INFO, "Checking temporary punishments.");

        ResultSet punishments = database.querySQL("SELECT * FROM `punishments` WHERE (`type`='TEMP_BAN' OR `type`='TEMP_MUTE') AND `active`='true';");
        try {
            while (punishments.next()) {
                int id = punishments.getInt("id");
                long expire = punishments.getLong("expire");
                if (expire <= System.currentTimeMillis()) {
                    database.updateSQL("UPDATE `punishments` SET `active`='false' WHERE `id`='" + id + "';");
                    Punishment.Type type = Punishment.Type.valueOf(punishments.getString("type"));
                    if (type.equals(Type.TEMP_MUTE)) {
                        FPacketMuteExpire muteExpire = new FPacketMuteExpire(server.getId(), id);
                        ProxyWorker.sendToAll(muteExpire);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        getLogger().log(Level.INFO, "Finished checking punishments.");
    }

    private void checkPlayerInfo() {
        getLogger().log(Level.INFO, "Checking all player data.");

        ResultSet players = database.querySQL("SELECT * FROM `playerdata`;");
        try {
            while (players.next()) {
                UUID uuid = UUID.fromString(players.getString("uniqueid"));
                Rank rank;
                try {
                    rank = Rank.valueOf(players.getString("mainrank"));
                } catch (Exception e) {
                    rank = Rank.DEFAULT;
                    database.updateSQL("UPDATE `playerdata` SET `mainrank`='" + Rank.DEFAULT.toString() + "' WHERE `uniqueid`='{uuid}';".replace("{uuid}", uuid.toString()));
                }

                String profileURL = profileUrlString.replace("{uuid}", uuid.toString().replace("-", ""));
                URL url = new URL(profileURL);
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                StringBuilder buffer = new StringBuilder();
                int read;
                char[] chars = new char[256];
                while ((read = reader.read(chars)) != -1) {
                    buffer.append(chars, 0, read);
                }

                JSONObject json = (JSONObject) new JSONParser().parse(buffer.toString());
                String n = (String) json.get("name");
                JSONArray properties = (JSONArray) json.get("properties");

                JSONObject property = (JSONObject) properties.get(0);
                String sN = (String) property.get("name");
                String sV = (String) property.get("value");
                String sS = (String) property.get("signature");

                String skinString = sN + ":" + sV + ":" + sS;
                database.updateSQL("UPDATE `playerdata` SET `lastname`='{name}',`skin`='{skin}' WHERE `uniqueid`='{uuid}';".replace("{skin}", skinString).replace("{uuid}", uuid.toString()).replace("{name}", n));

                if (FirecraftAPI.firecraftTeam.contains(uuid)) {
                    if (!rank.equals(Rank.FIRECRAFT_TEAM)) {
                        database.updateSQL("UPDATE `playerdata` SET `mainrank`='" + Rank.FIRECRAFT_TEAM.toString() + "' WHERE `uniqueid`='{uuid}';".replace("{uuid}", uuid.toString()));
                        FPacketRankUpdate rankUpdate = new FPacketRankUpdate(server.getId(), null, uuid);
                        ProxyWorker.sendToAll(rankUpdate);
                    }
                } else if (rank.equals(Rank.FIRECRAFT_TEAM)) {
                    database.updateSQL("UPDATE `playerdata` SET `mainrank`='" + Rank.DEFAULT.toString() + "'; WHERE `uniqueid`='{uuid}';".replace("{uuid}", uuid.toString()));
                    FPacketRankUpdate rankUpdate = new FPacketRankUpdate(server.getId(), null, uuid);
                    ProxyWorker.sendToAll(rankUpdate);
                }
            }
        } catch (Exception e) {
            System.out.println("There was an error getting player data from the database.");
            e.printStackTrace();
        }

        getLogger().log(Level.INFO, "Finished checking player data.");
    }

    public Collection<FirecraftPlayer> getPlayers() {
        return localPlayers.values();
    }

    public FirecraftServer getServer(String id) {
        return database.getServer(id);
    }

    public FirecraftPlayer getPlayer(UUID uuid) {
        FirecraftPlayer player = localPlayers.get(uuid);
        if (player == null) player = getFCDatabase().getPlayer(uuid);
        return player;
    }

    public FirecraftPlayer getPlayer(String name) {
        FirecraftPlayer target = Utils.getPlayer(name, localPlayers.values());
        if (target != null) {
            return target;
        }

        UUID uuid = Utils.Mojang.getUUIDFromName(name);
        if (uuid == null) {
            return null;
        }
        return getFCDatabase().getPlayer(uuid);
    }

    public FirecraftServer getFCServer() {
        return server;
    }
    
    @Override
    public FirecraftServer getFCServer(String id) {
        return database.getServer(id);
    }
}