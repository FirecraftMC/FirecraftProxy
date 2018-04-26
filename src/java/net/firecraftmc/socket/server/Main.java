package net.firecraftmc.socket.server;

import net.firecraftmc.shared.MySQL;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.FirecraftServer;
import net.firecraftmc.shared.enums.Channel;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.FPacketRankUpdate;
import net.firecraftmc.shared.packets.FirecraftPacket;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class Main extends JavaPlugin implements Listener {
    
    private final List<MinecraftSocketWorker> minecraftSocketWorkers = new ArrayList<>();
    private ServerSocket serverSocket;
    private final ConcurrentHashMap<UUID, FirecraftPlayer> firecraftPlayers = new ConcurrentHashMap<>();
    
    private File playerDataFile;
    private FileConfiguration playerDataConfig;
    
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
        
        playerDataFile = new File(getDataFolder() + File.separator + "playerdata.yml");
        this.loadData();
        
        this.getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().log(Level.INFO, "Starting the Firecraft Team runnable.");
        new BukkitRunnable() {
            public void run() {
                setFirecraftTeamMember(firestar311);
                setFirecraftTeamMember(powercore122);
                setFirecraftTeamMember(assassinplayzyt);
                setFirecraftTeamMember(jacob_3pot);
                checkFirecraftTeam();
            }
        }.runTaskTimerAsynchronously(this, 0, 20 * 60);
        
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
        saveData();
    }
    
    private void saveData() {
        if (playerDataFile.exists()) playerDataFile.delete();
        
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
            
            for (FirecraftPlayer fp : firecraftPlayers.values()) {
                String mainPath = "players." + fp.getUniqueId().toString();
                playerDataConfig.set(mainPath + ".rank", fp.getMainRank().toString());
                playerDataConfig.set(mainPath + ".channel", fp.getChannel().toString());
            }
            try {
                playerDataConfig.save(playerDataFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!database.checkConnection()) {
            database.openConnection();
        }
        
        try {
            for (FirecraftPlayer player : firecraftPlayers.values()) {
                ResultSet set = database.querySQL("SELECT `uniqueid` FROM playerdata WHERE uniqueid = \"" + player.getUniqueId().toString().replace("-", "") + "\"");
                
                if (set != null && set.next()) {
                    String sql = "UPDATE `playerdata` SET `uniqueid`=\"{uuid}\",`lastname`=\"{name}\",`mainrank`=\"{rank}\",`channel`=\"{channel}\",`vanished`=\"{vanished}\",`inventoryinteract`=\"{inventoryinteract}\",`itempickup`=\"{itempickup}\",`itemuse`=\"{itemuse}\",`blockbreak`=\"{blockbreak}\",`blockplace`=\"{blockplace}\",`entityinteract`=\"{entityinteract}\",`chatting`=\"{chatting}\",`silentinventories`=\"{silentinventories}\",`online`=\"{online}\" WHERE `uniqueid` = \"{uuid}\"";
                    sql = sql.replace("{uuid}", player.getUniqueId().toString().replace("-", ""));
                    sql = sql.replace("{name}", player.getName());
                    sql = sql.replace("{rank}", player.getMainRank().toString());
                    sql = sql.replace("{channel}", player.getChannel().toString());
                    sql = sql.replace("{vanished}", player.isVanished() + "");
                    if (player.isVanished()) {
                        sql = sql.replace("{inventoryinteract}", player.getVanishInfo().inventoryInteract() + "");
                        sql = sql.replace("{itempickup}", player.getVanishInfo().itemPickup() + "");
                        sql = sql.replace("{itemuse}", player.getVanishInfo().itemUse() + "");
                        sql = sql.replace("{blockbreak}", player.getVanishInfo().blockBreak() + "");
                        sql = sql.replace("{blockplace}", player.getVanishInfo().blockPlace() + "");
                        sql = sql.replace("{entityinteract}", player.getVanishInfo().entityInteract() + "");
                        sql = sql.replace("{chatting}", player.getVanishInfo().canChat() + "");
                        sql = sql.replace("{silentinventories}", player.getVanishInfo().silentInventoryOpen() + "");
                    } else {
                        sql = sql.replace("{inventoryinteract}", false + "");
                        sql = sql.replace("{itempickup}", false + "");
                        sql = sql.replace("{itemuse}", false + "");
                        sql = sql.replace("{blockbreak}", false + "");
                        sql = sql.replace("{blockplace}", false + "");
                        sql = sql.replace("{entityinteract}", false + "");
                        sql = sql.replace("{chatting}", false + "");
                        sql = sql.replace("{silentinventories}", false + "");
                    }
                    sql = sql.replace("{online}", player.isOnline() + "");
                    database.updateSQL(sql);
                } else {
                    String sql = "INSERT INTO `playerdata`(`uniqueid`, `lastname`, `mainrank`, `channel`, `vanished`, `inventoryinteract`, `itempickup`, `itemuse`, `blockbreak`, `blockplace`, `entityinteract`, `chatting`, `silentinventories`, `online`) VALUES (\"{uuid}\",\"{name}\",\"{rank}\",\"{channel}\",\"{vanished}\",\"{inventoryinteract}\",\"{itempickup}\",\"{itemuse}\",\"{blockbreak}\",\"{blockplace}\",\"{entityinteract}\",\"{chatting}\",\"{silentinventories}\",\"{online}\")";
                    sql = sql.replace("{uuid}", player.getUniqueId().toString().replace("-", ""));
                    sql = sql.replace("{name}", player.getName());
                    sql = sql.replace("{rank}", player.getMainRank().toString());
                    sql = sql.replace("{channel}", player.getChannel().toString());
                    sql = sql.replace("{vanished}", player.isVanished() + "");
                    if (player.isVanished()) {
                        sql = sql.replace("{inventoryinteract}", player.getVanishInfo().inventoryInteract() + "");
                        sql = sql.replace("{itempickup}", player.getVanishInfo().itemPickup() + "");
                        sql = sql.replace("{itemuse}", player.getVanishInfo().itemUse() + "");
                        sql = sql.replace("{blockbreak}", player.getVanishInfo().blockBreak() + "");
                        sql = sql.replace("{blockplace}", player.getVanishInfo().blockPlace() + "");
                        sql = sql.replace("{entityinteract}", player.getVanishInfo().entityInteract() + "");
                        sql = sql.replace("{chatting}", player.getVanishInfo().canChat() + "");
                        sql = sql.replace("{silentinventories}", player.getVanishInfo().silentInventoryOpen() + "");
                    } else {
                        sql = sql.replace("{inventoryinteract}", false + "");
                        sql = sql.replace("{itempickup}", false + "");
                        sql = sql.replace("{itemuse}", false + "");
                        sql = sql.replace("{blockbreak}", false + "");
                        sql = sql.replace("{blockplace}", false + "");
                        sql = sql.replace("{entityinteract}", false + "");
                        sql = sql.replace("{chatting}", false + "");
                        sql = sql.replace("{silentinventories}", false + "");
                    }
                    sql = sql.replace("{online}", player.isOnline() + "");
                    database.updateSQL(sql);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void removeWorker(MinecraftSocketWorker worker) {
        this.minecraftSocketWorkers.remove(worker);
    }
    
    public void sendToAll(FirecraftPacket packet) {
        if (!minecraftSocketWorkers.isEmpty()) {
            minecraftSocketWorkers.forEach(worker -> worker.send(packet));
        }
    }
    
    public Rank getRank(UUID uuid) {
        return firecraftPlayers.get(uuid).getMainRank();
    }
    
    private void checkFirecraftTeam() {
        for (Map.Entry<UUID, FirecraftPlayer> entry : firecraftPlayers.entrySet()) {
            if (entry.getValue().getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                UUID uuid = entry.getKey();
                if (!firecraftTeam.contains(uuid)) {
                    entry.getValue().setMainRank(Rank.PRIVATE);
                    this.getLogger().log(Level.INFO, Bukkit.getOfflinePlayer(uuid).getName() + " is not a Firecraft Team member and was set to Firecraft Team.");
                }
            }
        }
    }
    
    private void setFirecraftTeamMember(UUID uuid) {
        FirecraftPlayer player = getPlayer(uuid);
        if (player == null) {
            player = new FirecraftPlayer(this, uuid, Rank.FIRECRAFT_TEAM);
            this.firecraftPlayers.put(uuid, player);
        } else {
            if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                player.setMainRank(Rank.FIRECRAFT_TEAM);
                getLogger().log(Level.INFO, player.getName() + " is a Firecraft Team member but was not set to the Firecraft Team rank.");
            }
        }
    }
    
    public FirecraftPlayer getPlayer(UUID uuid) {
        return firecraftPlayers.get(uuid);
    }
    
    public void addPlayer(FirecraftPlayer player) {
        if (!this.firecraftPlayers.containsKey(player.getUniqueId())) {
            this.firecraftPlayers.put(player.getUniqueId(), player);
        } else {
            this.firecraftPlayers.replace(player.getUniqueId(), player);
        }
    }
    
    @EventHandler
    public void onPlayerPreJoin(AsyncPlayerPreLoginEvent e) {
        FirecraftPlayer player = firecraftPlayers.get(e.getUniqueId());
        if (player == null || !(player.getMainRank().isEqualToOrHigher(Rank.HEAD_ADMIN))) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "You are not allowed to join this server.");
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        FirecraftPlayer player = getPlayer(e.getPlayer().getUniqueId());
        e.setJoinMessage(player.getDisplayName() + " §ejoined the game.");
        player.setPlayer(e.getPlayer());
    }
    
    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        FirecraftPlayer player = getPlayer(e.getPlayer().getUniqueId());
        e.setQuitMessage(player.getDisplayName() + " §eleft the game.");
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        FirecraftPlayer player = getPlayer(e.getPlayer().getUniqueId());
        e.setFormat(player.getDisplayName() + "§8: §f" + e.getMessage());
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("setrank")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage("§cIt is not currently implemented for console to set ranks.");
            } else if (sender instanceof Player) {
                Player p = (Player) sender;
                final FirecraftPlayer player = getPlayer(p.getUniqueId());
                if (!(player.getMainRank().equals(Rank.FIRECRAFT_TEAM) || player.getMainRank().equals(Rank.HEAD_ADMIN))) {
                    player.sendMessage("&cYou are not allowed to set ranks.");
                    return true;
                }
                
                if (!(args.length == 2)) {
                    player.sendMessage("&cYou do not have enough arguments.");
                    return true;
                }
                String targetName = args[0];
                
                FirecraftPlayer target = null;
                for (FirecraftPlayer fp : firecraftPlayers.values()) {
                    if (fp.getName().equalsIgnoreCase(targetName)) {
                        target = fp;
                        break;
                    }
                }
                
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
                
                firecraftPlayers.get(target.getUniqueId()).setMainRank(targetRank);
                player.sendMessage("&aSuccessfully set §e" + firecraftPlayers.get(target.getUniqueId()).getDisplayName() + "&a's rank to " + targetRank.getDisplayName());
                saveData();
                loadData();
                sendToAll(new FPacketRankUpdate(new FirecraftServer("Socket", ChatColor.DARK_RED), player, target, targetRank));
            }
        } else if (cmd.getName().equalsIgnoreCase("createprofile")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = getPlayer(((Player) sender).getUniqueId());
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
                player.sendMessage("&aSuccessfully created a profile for " + created.getDisplayName());
                this.firecraftPlayers.put(uuid, created);
            } else {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("viewprofile")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = firecraftPlayers.get(((Player) sender).getUniqueId());
                if (player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                    if (args.length != 1) {
                        player.sendMessage("&cInvalid amount of arguments.");
                        return true;
                    }
                    UUID uuid;
                    FirecraftPlayer target = null;
                    try {
                        uuid = UUID.fromString(args[0]);
                        target = getPlayer(uuid);
                    } catch (Exception e) {
                        for (FirecraftPlayer p : firecraftPlayers.values()) {
                            if (p.getName().equalsIgnoreCase(args[0])) {
                                target = p;
                                break;
                            }
                        }
                    }
                    
                    if (target == null) {
                        player.sendMessage("&cCould not find a player with that name/uuid.");
                        return true;
                    }
                    
                    player.sendMessage("&6Displaying profile info for " + target.getName());
                    player.sendMessage("&7Rank: " + target.getMainRank().getBaseColor() + target.getMainRank().toString());
                    player.sendMessage("&7Channel: " + target.getChannel().getColor() + target.getChannel().toString());
                } else {
                    player.sendMessage("&cOnly Firecraft Team members can do that.");
                    return true;
                }
            }
        } else if (cmd.getName().equalsIgnoreCase("reloaddata")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = firecraftPlayers.get(((Player) sender).getUniqueId());
                if (!player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                    player.sendMessage("Only Firecraft Team members can reload player data.");
                    return true;
                }
                
                player.sendMessage("&aStarting a reload of player data.");
                this.saveData();
                this.loadData();
                player.sendMessage("&aReload of player data is now complete.");
            } else {
                System.out.println("§cOnly players may reload the player data.");
                return true;
            }
        }
        
        return true;
    }
    
    private void loadData() {
        this.firecraftPlayers.clear();
        getLogger().log(Level.INFO, "Loading all player data.");
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
        if (playerDataConfig.contains("players")) {
            for (String u : playerDataConfig.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(u);
                String mainPath = "players." + u;
                Rank rank = Rank.valueOf(playerDataConfig.getString(mainPath + ".rank"));
                Channel channel = Channel.valueOf(playerDataConfig.getString(mainPath + ".channel"));
                FirecraftPlayer firecraftPlayer = new FirecraftPlayer(this, uuid, rank);
                firecraftPlayer.setChannel(channel);
                this.firecraftPlayers.put(uuid, firecraftPlayer);
            }
        }
        getLogger().log(Level.INFO, "Successfully loaded all data.");
        
        new BukkitRunnable() {
            public void run() {
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
                            String finalUUIDString = u.substring(0, 8) + "-";
                            finalUUIDString += u.substring(8, 12) + "-";
                            finalUUIDString += u.substring(12, 16) + "-";
                            finalUUIDString += u.substring(16, 20) + "-";
                            finalUUIDString += u.substring(20, 32);
                            UUID uuid = UUID.fromString(finalUUIDString);
                            String lastName = players.getString("lastname");
                            Rank rank = Rank.valueOf(players.getString("mainrank"));
                            Channel channel = Channel.valueOf(players.getString("channel"));
                            boolean vanished = players.getBoolean("vanished");
                            boolean inventoryinteract, itempickup, itemuse, blockbreak, blockplace, entityinteract, chatting, silentinventories;
                            FirecraftPlayer.VanishInfo vanish = null;
                            if (vanished) {
                                inventoryinteract = players.getBoolean("inventoryinteract");
                                itempickup = players.getBoolean("itempickup");
                                itemuse = players.getBoolean("itemuse");
                                blockbreak = players.getBoolean("blockbreak");
                                blockplace = players.getBoolean("blockplace");
                                entityinteract = players.getBoolean("entityinteract");
                                chatting = players.getBoolean("chatting");
                                silentinventories = players.getBoolean("silentinventories");
                                vanish = new FirecraftPlayer.VanishInfo(inventoryinteract, itempickup, itemuse, blockbreak, blockplace, entityinteract, chatting, silentinventories);
                            }
                            boolean online = players.getBoolean("online");
                            
                            FirecraftPlayer player = new FirecraftPlayer(uuid, lastName, rank, channel, vanish, online);
                            firecraftPlayers.put(player.getUniqueId(), player);
                            //TODO Other stuff when implemented (firstjoined, timeplayed, lastseen, god, socialspy, balance, nick)
                        }
                    } catch (SQLException e) {
                        System.out.println("There was an error getting player data from the database.");
                    }
                }
            }
        }.runTaskAsynchronously(this);
    }
    
    public Collection<FirecraftPlayer> getPlayers() {
        return firecraftPlayers.values();
    }
}