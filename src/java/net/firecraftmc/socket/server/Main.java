package net.firecraftmc.socket.server;

import net.firecraftmc.shared.MySQL;
import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.classes.utils.MojangUtils;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.FPacketRankUpdate;
import net.firecraftmc.shared.packets.FirecraftPacket;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.ServerSocket;
import java.net.Socket;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Level;

public class Main extends JavaPlugin implements Listener {
    
    private final List<MinecraftSocketWorker> minecraftSocketWorkers = new ArrayList<>();
    private ServerSocket serverSocket;
    
    private final HashMap<UUID, FirecraftPlayer> localPlayers = new HashMap<>();
    
    private MySQL database;
    
    private final UUID firestar311 = UUID.fromString("3f7891ce-5a73-4d52-a2ba-299839053fdc");
    private final UUID powercore122 = UUID.fromString("b30f4b1f-4252-45e5-ac2a-1f75ff6f5783");
    private final UUID assassinplayzyt = UUID.fromString("c292df56-5baa-4a11-87a3-cba08ce5f7a6");
    private final UUID jacob_3pot = UUID.fromString("b258795c-c056-4aac-b953-993b930f06a0");
    private final List<UUID> firecraftTeam = Arrays.asList(firestar311, powercore122, assassinplayzyt, jacob_3pot);
    
    public void onEnable() {
        this.saveDefaultConfig();
        if (!this.getConfig().contains("yamlStorage")) {
            this.getConfig().set("yamlStorage", false);
            this.saveConfig();
        }
        
        int port = this.getConfig().getInt("port");
        getLogger().log(Level.INFO, "Starting the thread used for the socket.");
        Thread thread = new Thread(() -> {
            getLogger().log(Level.INFO, "Creating a ServerSocket on port " + port);
            try {
                serverSocket = new ServerSocket(port);
                
                Socket socket;
                while ((socket = serverSocket.accept()) != null) {
                    MinecraftSocketWorker worker = new MinecraftSocketWorker(this, socket);
                    worker.start();
                    getLogger().log(Level.INFO, "Received connection from: " + socket);
                    minecraftSocketWorkers.add(worker);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
        
        new BukkitRunnable() {
            public void run() {
                checkPlayerInfo();
            }
        }.runTaskTimerAsynchronously(this, 0L, 20 * 60 * 5);
        
        this.getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().log(Level.INFO, "Starting the socket worker check runnable");
        new BukkitRunnable() {
            public void run() {
                minecraftSocketWorkers.forEach(sw -> {
                    if (!sw.isConnected()) {
                        sw.interrupt();
                        minecraftSocketWorkers.remove(sw);
                    }
                });
            }
        }.runTaskTimerAsynchronously(this, 0L, 20);
        
        getLogger().log(Level.INFO, "Successfully loaded the plugin.");
    }
    
    public void onDisable() {
        database.closeConnection();
    }
    
    public void removeWorker(MinecraftSocketWorker worker) {
        this.minecraftSocketWorkers.remove(worker);
    }
    
    public void sendToAll(FirecraftPacket packet) {
        if (!minecraftSocketWorkers.isEmpty()) {
            minecraftSocketWorkers.forEach(worker -> worker.send(packet));
        }
    }
    
    @EventHandler
    public void onPlayerPreJoin(AsyncPlayerPreLoginEvent e) {
        if (!firecraftTeam.contains(e.getUniqueId())) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§4Only Firecraft Team members may join this server.");
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        FirecraftPlayer player = Utils.getPlayerFromDatabase(database, this, e.getPlayer().getUniqueId());
        this.localPlayers.put(player.getUniqueId(), player);
        player.sendMessage(player.getDisplayName() + " §ejoined the game.");
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
        if (cmd.getName().equalsIgnoreCase("setrank")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage("§cIt is not currently implemented for console to set ranks.");
            } else if (sender instanceof Player) {
                FirecraftPlayer player = this.localPlayers.get(((Player) sender).getUniqueId());
                if (!(player.getMainRank().equals(Rank.FIRECRAFT_TEAM) || player.getMainRank().equals(Rank.HEAD_ADMIN))) {
                    player.sendMessage("&cYou are not allowed to set ranks.");
                    return true;
                }
                
                if (!(args.length == 2)) {
                    player.sendMessage("&cYou do not have enough arguments.");
                    return true;
                }
                String targetName = args[0];
                
                UUID uuid;
                try {
                    uuid = MojangUtils.getUUIDFromName(targetName);
                } catch (Exception e) {
                    player.sendMessage("&cThere was an error getting the uuid from the name you provided.");
                    return true;
                }
                
                if (uuid == null) {
                    player.sendMessage("&cThe name you provided could not be resolved to a unique id.");
                    return true;
                }
                
                FirecraftPlayer target = Utils.getPlayerFromDatabase(database, this, uuid);
                
                if (target == null) {
                    player.sendMessage("&cThere was no player found for the name: " + targetName);
                    return true;
                }
                
                String baseTR = args[1].toUpperCase();
                Rank targetRank;
                try {
                    targetRank = Rank.valueOf(baseTR);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("&cThe rank name provided was incorrect.");
                    return true;
                }
                
                if (targetRank.equals(Rank.FIRECRAFT_TEAM)) {
                    player.sendMessage("&cThe Firecraft Team rank cannot be assigned through a command.");
                    return true;
                }
                
                if (target.getMainRank().isEqualToOrHigher(player.getMainRank())) {
                    player.sendMessage("&cYou cannot assign the rank of someone of your current rank or higher.");
                    return true;
                }
                
                database.querySQL("UPDATE `playerdata` SET `mainrank`='" + target.getMainRank().toString() + "' WHERE `uniqueid`='" + uuid.toString().replace("-", "") + "';");
                String prefix = (!targetRank.equals(Rank.PRIVATE)) ? targetRank.getPrefix() : targetRank.getBaseColor() + "Private";
                player.sendMessage("&aSuccessfully set §e" + target.getName() + " &a's rank to " + prefix);
                sendToAll(new FPacketRankUpdate(new FirecraftServer("Socket", ChatColor.DARK_RED), player.getUniqueId(), target.getUniqueId()));
            }
        } else if (cmd.getName().equalsIgnoreCase("createprofile")) {
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
                
                FirecraftPlayer created = new FirecraftPlayer(this, uuid, rank);
                Utils.addPlayerToDatabase(database, created);
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
    
    private void checkPlayerInfo() {
        getLogger().log(Level.INFO, "Loading all player data.");
        
        if (!getConfig().contains("mysql")) {
            System.out.println("Config does not contain connection info, aborting sql tasks.");
            getConfig().set("mysql.user", "root");
            getConfig().set("mysql.database", "players");
            getConfig().set("mysql.password", "");
            getConfig().set("mysql.port", 3306);
            getConfig().set("mysql.hostname", "localhost");
        } else {
            database = new MySQL(getConfig().getString("mysql.user"), getConfig().getString("mysql.database"),
                    getConfig().getString("mysql.password"), getConfig().getInt("mysql.port"), getConfig().getString("mysql.hostname"));
            database.openConnection();
            ResultSet players = database.querySQL("SELECT * FROM `playerdata`");
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
                        database.querySQL("UPDATE `playerdata` SET `mainrank`='" + Rank.PRIVATE.toString() + "' WHERE `uniqueid`='{uuid}';".replace("{uuid}", u));
                    }
                    String mojangName = MojangUtils.getNameFromUUID(uuid.toString());
                    if (mojangName != null && !mojangName.equalsIgnoreCase(lastName)) {
                        database.querySQL("UPDATE `playerdata` SET `lastname`='" + mojangName + "';");
                    }
                    
                    if (firecraftTeam.contains(uuid)) {
                        if (!rank.equals(Rank.FIRECRAFT_TEAM)) {
                            database.querySQL("UPDATE `playerdata` SET `mainrank`='" + Rank.FIRECRAFT_TEAM.toString() + "' WHERE `uniqueid`='{uuid}';".replace("{uuid}", u));
                            FPacketRankUpdate rankUpdate = new FPacketRankUpdate(new FirecraftServer("Socket", ChatColor.RED), null, uuid);
                            sendToAll(rankUpdate);
                        }
                    } else if (rank.equals(Rank.FIRECRAFT_TEAM)) {
                        database.querySQL("UPDATE `playerdata` SET `mainrank`='" + Rank.PRIVATE.toString() + "'; WHERE `uniqueid`='{uuid}';".replace("{uuid}", u));
                        FPacketRankUpdate rankUpdate = new FPacketRankUpdate(new FirecraftServer("Socket", ChatColor.RED), null, uuid);
                        sendToAll(rankUpdate);
                    }
                }
            } catch (Exception e) {
                System.out.println("There was an error getting player data from the database.");
            }
        }
    }
    
    public Collection<FirecraftPlayer> getPlayers() {
        return localPlayers.values();
    }
}