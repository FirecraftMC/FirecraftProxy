package net.firecraftmc.proxy;

import net.firecraftmc.shared.classes.FirecraftMC;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.classes.model.Database;
import net.firecraftmc.shared.classes.model.FirecraftServer;
import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.packets.FPacketRankUpdate;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Level;

/**
 * Main class for the Socket Server
 * Controls the initialization of the socket and handles connections
 */
public class Main extends JavaPlugin implements Listener {

    private static final String profileUrlString = "https://sessionserver.mojang.com/session/minecraft/profile/{uuid}?unsigned=false";

    volatile List<ProxyWorker> proxyWorkers = new ArrayList<>();
    private ServerSocket serverSocket;

    private final HashMap<UUID, FirecraftPlayer> localPlayers = new HashMap<>();

    private Database database;

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

        database = new Database(getConfig().getString("mysql.user"), getConfig().getString("mysql.database"),
                getConfig().getString("mysql.password"), getConfig().getInt("mysql.port"), getConfig().getString("mysql.hostname"));
        database.openConnection();

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

        getLogger().log(Level.INFO, "Successfully loaded the plugin.");
    }

    public void onDisable() {
        database.closeConnection();
        try {
            for (ProxyWorker worker : proxyWorkers) {
                worker.disconnect();
            }
            this.serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void removeWorker(ProxyWorker worker) {
        this.proxyWorkers.remove(worker);
    }

    @EventHandler
    public void onPlayerPreJoin(AsyncPlayerPreLoginEvent e) {
        if (!FirecraftMC.firecraftTeam.contains(e.getUniqueId())) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§4Only Firecraft Team members may join this server.");
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

                if (FirecraftMC.firecraftTeam.contains(uuid)) {
                    if (!rank.equals(Rank.FIRECRAFT_TEAM)) {
                        database.updateSQL("UPDATE `playerdata` SET `mainrank`='" + Rank.FIRECRAFT_TEAM.toString() + "' WHERE `uniqueid`='{uuid}';".replace("{uuid}", uuid.toString()));
                        FPacketRankUpdate rankUpdate = new FPacketRankUpdate(new FirecraftServer("Socket", ChatColor.RED), null, uuid);
                        ProxyWorker.sendToAll(rankUpdate);
                    }
                } else if (rank.equals(Rank.FIRECRAFT_TEAM)) {
                    database.updateSQL("UPDATE `playerdata` SET `mainrank`='" + Rank.DEFAULT.toString() + "'; WHERE `uniqueid`='{uuid}';".replace("{uuid}", uuid.toString()));
                    FPacketRankUpdate rankUpdate = new FPacketRankUpdate(new FirecraftServer("Socket", ChatColor.RED), null, uuid);
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
}