package net.firecraftmc.socket.server;

import net.firecraftmc.shared.MySQL;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.FirecraftServer;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.enums.Rank;
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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Level;

/**
 * Main class for the Socket Server
 * Controls the initialization of the socket and handles connections
 */
public class Main extends JavaPlugin implements Listener {
    
    final List<SocketWorker> socketWorkers = new ArrayList<>();
    private ServerSocket serverSocket;
    public final FirecraftServer server = new FirecraftServer("Socket", ChatColor.DARK_RED);
    
    private final HashMap<UUID, FirecraftPlayer> localPlayers = new HashMap<>();
    
    private MySQL database;
    
    private final UUID firestar311 = UUID.fromString("3f7891ce-5a73-4d52-a2ba-299839053fdc");
    private final UUID powercore122 = UUID.fromString("b30f4b1f-4252-45e5-ac2a-1f75ff6f5783");
    private final UUID assassinplayzyt = UUID.fromString("c292df56-5baa-4a11-87a3-cba08ce5f7a6");
    private final UUID jacob_3pot = UUID.fromString("b258795c-c056-4aac-b953-993b930f06a0");
    private final UUID ko_senpai = UUID.fromString("4d5527f8-df1e-4ecf-9cd7-4ba997b3d9cb");
    private final List<UUID> firecraftTeam = Arrays.asList(firestar311, powercore122, assassinplayzyt, jacob_3pot, ko_senpai);
    
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
                    SocketWorker worker = new SocketWorker(this, socket);
                    worker.start();
                    socketWorkers.add(worker);
                    getLogger().log(Level.INFO, "Received connection from: " + socket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
        
        database = new MySQL(getConfig().getString("mysql.user"), getConfig().getString("mysql.database"),
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
                socketWorkers.forEach(sw -> {
                    if (!sw.isConnected()) {
                        sw.interrupt();
                        System.out.println("Removed a socket worker.");
                        socketWorkers.remove(sw);
                    }
                });
            }
        }.runTaskTimer(this, 0L, 20);
        
        getLogger().log(Level.INFO, "Successfully loaded the plugin.");
    }
    
    public void onDisable() {
        database.closeConnection();
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void removeWorker(SocketWorker worker) {
        this.socketWorkers.remove(worker);
    }
    
    @EventHandler
    public void onPlayerPreJoin(AsyncPlayerPreLoginEvent e) {
        if (!firecraftTeam.contains(e.getUniqueId())) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§4Only Firecraft Team members may join this server.");
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        FirecraftPlayer player = Utils.Database.getPlayerFromDatabase(server, database, e.getPlayer().getUniqueId());
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
    
    public MySQL getDatabase() {
        return database;
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("createprofile")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = this.localPlayers.get(((Player) sender).getUniqueId());
                if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                    player.sendMessage("&cOnly Firecraft Team members can create player data.");
                    return true;
                }
                
                if (args.length != 2) {
                    player.sendMessage("&cInvalid amount of arguments.");
                    return true;
                }
                
                UUID uuid;
                try {
                    uuid = UUID.fromString(args[0]);
                } catch (Exception e) {
                    player.sendMessage("&cThat is not a valid UUID.");
                    return true;
                }
                
                Rank rank;
                try {
                    rank = Rank.valueOf(args[1].toUpperCase());
                } catch (Exception e) {
                    player.sendMessage("&cThat is not a valid rank.");
                    return true;
                }
                
                FirecraftPlayer created = new FirecraftPlayer(server, uuid, rank);
                Utils.Database.addPlayerToDatabase(database, created);
                player.sendMessage("&aSuccessfully created a profile for " + created.getDisplayName());
                
            } else {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("reloaddata")) {
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
                String u = players.getString("uniqueid");
                UUID uuid = Utils.convertToUUID(u);
                String lastName = players.getString("lastname");
                Rank rank;
                try {
                    rank = Rank.valueOf(players.getString("mainrank"));
                } catch (Exception e) {
                    rank = Rank.PRIVATE;
                    database.updateSQL("UPDATE `playerdata` SET `mainrank`='" + Rank.PRIVATE.toString() + "' WHERE `uniqueid`='{uuid}';".replace("{uuid}", u));
                }
                String mojangName = Utils.Mojang.getNameFromUUID(uuid.toString());
                if (mojangName != null && !mojangName.equalsIgnoreCase(lastName)) {
                    database.updateSQL("UPDATE `playerdata` SET `lastname`='" + mojangName + "' WHERE `uniqueid`='{uuid}';".replace("{uuid}", u));
                }
                
                if (firecraftTeam.contains(uuid)) {
                    if (!rank.equals(Rank.FIRECRAFT_TEAM)) {
                        database.updateSQL("UPDATE `playerdata` SET `mainrank`='" + Rank.FIRECRAFT_TEAM.toString() + "' WHERE `uniqueid`='{uuid}';".replace("{uuid}", u));
                        FPacketRankUpdate rankUpdate = new FPacketRankUpdate(new FirecraftServer("Socket", ChatColor.RED), null, uuid);
                        SocketWorker.sendToAll(rankUpdate);
                    }
                } else if (rank.equals(Rank.FIRECRAFT_TEAM)) {
                    database.updateSQL("UPDATE `playerdata` SET `mainrank`='" + Rank.PRIVATE.toString() + "'; WHERE `uniqueid`='{uuid}';".replace("{uuid}", u));
                    FPacketRankUpdate rankUpdate = new FPacketRankUpdate(new FirecraftServer("Socket", ChatColor.RED), null, uuid);
                    SocketWorker.sendToAll(rankUpdate);
                }
            }
        } catch (Exception e) {
            System.out.println("There was an error getting player data from the database.");
        }
        
        getLogger().log(Level.INFO, "Finished checking player data.");
    }
    
    public Collection<FirecraftPlayer> getPlayers() {
        return localPlayers.values();
    }
}