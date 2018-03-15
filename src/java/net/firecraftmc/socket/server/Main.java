package net.firecraftmc.socket.server;

import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.FirecraftServer;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.enums.Channel;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.FPacketFCTMessage;
import net.firecraftmc.shared.packets.FPacketRankUpdate;
import net.firecraftmc.shared.packets.FirecraftPacket;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.logging.Level;

public class Main extends JavaPlugin {

    private static Main instance;
    private List<MinecraftSocketWorker> minecraftSocketWorkers = new ArrayList<>();

    private ServerSocket serverSocket;
    private String logPrefix = "";

    private HashMap<UUID, FirecraftPlayer> firecraftPlayers = new HashMap<>();

    private boolean yamlStorage;

    private File playerDataFile;
    private File playerDataTempFile;
    private FileConfiguration playerDataTempConfig;

    private boolean prefixes;

    private final UUID firestar311 = UUID.fromString("3f7891ce-5a73-4d52-a2ba-299839053fdc");
    private final UUID powercore122 = UUID.fromString("b30f4b1f-4252-45e5-ac2a-1f75ff6f5783");
    private final UUID fenixfirementat = UUID.fromString("d1f1514b-463a-46e9-8a64-18a8dad20361");
    private final UUID assassinplayzyt = UUID.fromString("c292df56-5baa-4a11-87a3-cba08ce5f7a6");
    private final UUID jacob_3pot = UUID.fromString("b258795c-c056-4aac-b953-993b930f06a0");
    private final List<UUID> firecraftTeam = Arrays.asList(firestar311, powercore122, fenixfirementat, assassinplayzyt, jacob_3pot);

    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        if (!this.getConfig().contains("yamlStorage")) {
            this.getConfig().set("yamlStorage", false);
            this.saveConfig();
        }

        this.yamlStorage = getConfig().getBoolean("yamlStorage");
        //int port = this.getConfig().getInt("port");
        int port = 1234;
        this.logPrefix = Utils.color(getConfig().getString("logprefix"));
        Thread thread = new Thread(() -> {
            getLogger().log(Level.INFO, "Creating a ServerSocket on port " + port);
            try {
                serverSocket = new ServerSocket(port);

                Socket socket;
                while ((socket = serverSocket.accept()) != null) {
                    MinecraftSocketWorker worker = new MinecraftSocketWorker(this, socket);
                    worker.start();
                    getLogger().log(Level.INFO, "Recieved connection from: " + socket);
                    minecraftSocketWorkers.add(worker);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
        playerDataFile = new File(getDataFolder() + File.separator + "playerdata.bin");
        playerDataTempFile = new File(getDataFolder() + File.separator + "playerdata.yml");

        if (!yamlStorage) {
            try (FileInputStream fs = new FileInputStream(playerDataFile)) {
                ObjectInputStream os = new ObjectInputStream(fs);

                int amount = os.readInt();

                for (int i = 0; i < amount; i++) {
                    FirecraftPlayer firecraftPlayer = (FirecraftPlayer) os.readObject();
                    this.firecraftPlayers.put(firecraftPlayer.getUuid(), firecraftPlayer);
                }

                os.close();
            } catch (FileNotFoundException e) {
                getLogger().log(Level.SEVERE, "Could not find the player data file!");
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not read from the player data file!");
            } catch (ClassNotFoundException e) {
                getLogger().log(Level.SEVERE, "There was an error retreiving some data!");
            }
        } else {
            playerDataTempConfig = YamlConfiguration.loadConfiguration(playerDataTempFile);
            if (playerDataTempConfig.contains("players")) {
                for (String u : playerDataTempConfig.getConfigurationSection("players").getKeys(false)) {
                    UUID uuid = UUID.fromString(u);
                    String mainPath = "players." + u;
                    Rank rank = Rank.valueOf(playerDataTempConfig.getString(mainPath + ".rank"));
                    Channel channel = Channel.valueOf(playerDataTempConfig.getString(mainPath + ".channel"));
                    FirecraftPlayer firecraftPlayer = new FirecraftPlayer(uuid, rank);
                    firecraftPlayer.setChannel(channel);
                    this.firecraftPlayers.put(uuid, firecraftPlayer);
                }
            }
        }

        instance = this;

        new BukkitRunnable() {
            public void run() {
                setFirecraftTeamMember(firestar311);
                setFirecraftTeamMember(powercore122);
                setFirecraftTeamMember(fenixfirementat);
                setFirecraftTeamMember(assassinplayzyt);
                setFirecraftTeamMember(jacob_3pot);
                checkFirecraftTeam();
            }
        }.runTaskTimerAsynchronously(this, 0, 20 * 60);

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
    }

    public void onDisable() {
        saveData();
        this.getConfig().set("yamlStorage", yamlStorage);
        this.saveConfig();
    }

    public void saveData() {
        if (yamlStorage) {
            if (!playerDataTempFile.exists()) {
                try {
                    playerDataTempFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                playerDataTempConfig = YamlConfiguration.loadConfiguration(playerDataTempFile);

                for (FirecraftPlayer fp : firecraftPlayers.values()) {
                    String mainPath = "players." + fp.getUuid().toString();
                    playerDataTempConfig.set(mainPath + ".rank", fp.getRank().toString());
                    playerDataTempConfig.set(mainPath + ".channel", fp.getChannel().toString());
                }
                try {
                    playerDataTempConfig.save(playerDataTempFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            playerDataFile.delete();
            if (!playerDataFile.exists()) {
                try {
                    playerDataFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try (FileOutputStream fs = new FileOutputStream(playerDataFile)) {
                ObjectOutputStream os = new ObjectOutputStream(fs);
                os.writeInt(this.firecraftPlayers.size()); //Amount of players for when reading them on load.

                for (FirecraftPlayer fp : this.firecraftPlayers.values()) {
                    os.writeObject(fp);
                }

                os.close();
            } catch (FileNotFoundException e) {
                getLogger().log(Level.SEVERE, "Could not find the player data file!");
            } catch (IOException e) {
                getLogger().log(Level.INFO, "Could not write to the player data file!");
            }
        }
    }

    public void removeWorker(MinecraftSocketWorker worker) {
        this.minecraftSocketWorkers.remove(worker);
    }

    public void sendToAll(FirecraftPacket packet) {
        minecraftSocketWorkers.forEach(worker -> worker.send(packet));
    }

    public void logMsg(String message) {
        this.getLogger().log(Level.INFO, ChatColor.stripColor(message));
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp()) {
                    player.sendMessage(message);
                }
            }
        }
    }

    public String getLogPrefix() {
        return logPrefix;
    }

    public Rank getRank(UUID uuid) {
        return firecraftPlayers.get(uuid).getRank();
    }

    public Rank getRank(Player player) {
        return getRank(player.getUniqueId());
    }

    public void updateDisplayName(Player player) {
        if (prefixes)
            player.setDisplayName(getRank(player).getPrefix() + player.getName() + "§r");
        else
            player.setDisplayName(getRank(player).getNoPrefix() + player.getName() + "§r");
    }

    private void checkFirecraftTeam() {
        for (Map.Entry<UUID, FirecraftPlayer> entry : firecraftPlayers.entrySet()) {
            if (entry.getValue().getRank().equals(Rank.FIRECRAFT_TEAM)) {
                UUID uuid = entry.getKey();
                if (!firecraftTeam.contains(uuid)) {
                    entry.getValue().setRank(Rank.DEFAULT);
                    this.getLogger().log(Level.INFO, Bukkit.getOfflinePlayer(uuid).getName() + " is not a Firecraft Team member and was set to Firecraft Team.");
                }
            }
        }
    }

    private void setFirecraftTeamMember(UUID uuid) {
        FirecraftPlayer player = getPlayer(uuid);
        if (player == null) {
            player = new FirecraftPlayer(uuid, Rank.FIRECRAFT_TEAM);
            this.firecraftPlayers.put(uuid, player);
        } else {
            if (!player.getRank().equals(Rank.FIRECRAFT_TEAM)) {
                player.setRank(Rank.FIRECRAFT_TEAM);
                getLogger().log(Level.INFO, player.getName() + " is a Firecraft Team member but was not set to the Firecraft Team rank.");
            }
        }
    }

    public FirecraftPlayer getPlayer(UUID uuid) {
        return firecraftPlayers.get(uuid);
    }

    public void addPlayer(FirecraftPlayer player) {
        this.firecraftPlayers.put(player.getUuid(), player);
    }

    public void removePlayer(UUID uuid) {
        this.firecraftPlayers.remove(uuid);
    }

    public static void msgFCTMembers(String message) {
        FPacketFCTMessage fctMessage = new FPacketFCTMessage(message);
        instance.sendToAll(fctMessage);
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("setrank")) {
            if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage("§cIt is not currently implemented for console to set ranks.");
            } else if (sender instanceof Player) {
                Player p = (Player) sender;
                final FirecraftPlayer player = getPlayer(p.getUniqueId());
                if (!player.getRank().equals(Rank.FIRECRAFT_TEAM)) {
                    player.sendMessage("&cOnly members of The Firecraft Team can change ranks.");
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

                if (targetRank.equals(Rank.CONSOLE)) {
                    player.sendMessage("&cThe Console rank cannot be set.");
                    return true;
                }

                target.setRank(targetRank);
                this.firecraftPlayers.replace(target.getUuid(), target);
                player.sendMessage("&aSuccessfully set " + target.getNameNoPrefix() + "&a's rank to " + targetRank.getDisplayName());
                sendToAll(new FPacketRankUpdate(new FirecraftServer("Socket", ChatColor.DARK_RED), player, target, targetRank));
            }
        } else if (cmd.getName().equalsIgnoreCase("toggleyaml")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = getPlayer(((Player) sender).getUniqueId());
                if (!player.getRank().equals(Rank.FIRECRAFT_TEAM)) {
                    player.sendMessage("&cOnly Firecraft Team members can toggle the storage option.");
                    return true;
                }

                this.yamlStorage = !this.yamlStorage;
                player.sendMessage("&aChanged player yaml storage to &b" + yamlStorage);
                player.sendMessage("&aNow saving all data.");
                if (yamlStorage) {
                    if (!playerDataTempFile.exists()) {
                        player.sendMessage("&aYaml File does not exist, creating it.");
                        try {
                            playerDataTempFile.createNewFile();
                        } catch (IOException e) {
                            player.sendMessage("&cThere was an error creating the file, see console for details.");
                            e.printStackTrace();
                        }

                        player.sendMessage("&aCreation complete, saving " + firecraftPlayers.values().size() + " players");

                        playerDataTempConfig = YamlConfiguration.loadConfiguration(playerDataTempFile);

                        for (FirecraftPlayer fp : firecraftPlayers.values()) {
                            String mainPath = "players." + fp.getUuid().toString();
                            playerDataTempConfig.set(mainPath + ".rank", fp.getRank().toString());
                            playerDataTempConfig.set(mainPath + ".channel", fp.getChannel().toString());
                        }
                        try {
                            playerDataTempConfig.save(playerDataTempFile);
                        } catch (IOException e) {
                            player.sendMessage("&cThere was an error saving the data, see console for details.");
                            e.printStackTrace();
                        }
                    }
                }
                player.sendMessage("&aSave Complete.");
            } else {
                sender.sendMessage("§cOnly Players may change the storage.");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("createprofile")) {
            if (sender instanceof Player) {
                FirecraftPlayer player = getPlayer(((Player) sender).getUniqueId());
                if (!player.getRank().equals(Rank.FIRECRAFT_TEAM)) {
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

                FirecraftPlayer created = new FirecraftPlayer(uuid, rank);
                player.sendMessage("&aSuccessfully created a profile for " + created.getName());
                this.firecraftPlayers.put(uuid, created);

            } else {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
        }

        return true;
    }


    public Collection<FirecraftPlayer> getPlayers() {
        return firecraftPlayers.values();
    }

    public void updatePlayer(FirecraftPlayer player) {
        firecraftPlayers.replace(player.getUuid(), player);
    }
}