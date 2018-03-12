package net.firecraftmc.socket.server;

import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.FPacketFCTMessage;
import net.firecraftmc.shared.packets.FirecraftPacket;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

    private File playerDataFile;

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
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (FileInputStream fs = new FileInputStream(playerDataFile)) {
            ObjectInputStream os = new ObjectInputStream(fs);

            int amount = os.readInt();

            for (int i = 0; i < amount; i++) {
                FirecraftPlayer firecraftPlayer = (FirecraftPlayer) os.readObject();
            }

            os.close();
        } catch (FileNotFoundException e) {
            getLogger().log(Level.SEVERE, "Could not find the player data file!");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not read from the player data file!");
        } catch (ClassNotFoundException e) {
            getLogger().log(Level.SEVERE, "There was an error retreiving some data!");
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

//TODO
//        getCommand("rank").setExecutor(new FirecraftCommand(this, Rank.SENIOR_MOD) {
//            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
//                if (!(args.length > 0)) {
//                    sender.sendMessage("§cYou did not provide a subcommand.");
//                    return true;
//                }
//                if (canExecuteBaseCommand(sender)) {
//                    if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("s")) {
//                        if (!(args.length == 3)) {
//                            sender.sendMessage("§cUsage: /" + label + " set|s <player> <rank>");
//                            return true;
//                        }
//                        Rank rank;
//
//                        try {
//                            rank = Rank.valueOf(args[2].toUpperCase());
//                        } catch (Exception e) {
//                            sender.sendMessage("§cThe provided rank name is not valid.");
//                            return true;
//                        }
//
//                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
//                        if (rank.equals(Rank.FIRECRAFT_TEAM)) {
//                            sender.sendMessage("§cYou cannot set the rank of a player to " + Rank.FIRECRAFT_TEAM.getDisplayName());
//                            msgFCTMembers("§4§l[WARNING] §b" + sender.getName() + " §7tried to set §b" + target.getName() + "§7's rank to " + Rank.FIRECRAFT_TEAM.getDisplayName());
//                            return true;
//                        }
//
//                        Rank currentRank = getRank(target.getUniqueId());
//                        if (currentRank.equals(Rank.FIRECRAFT_TEAM)) {
//                            sender.sendMessage("§cThe ranks of the members of §4§lThe Firecraft Team §ccannot be changed.");
//                            msgFCTMembers("§4§l[WARNING] §b" + sender.getName() + " §7tried to change §b" + target.getName() + "§7's rank to " + rank.getDisplayName());
//                            return true;
//                        }
//
//                        if (sender instanceof Player) {
//                            Player player = (Player) sender;
//                            Rank senderRank = getRank(player);
//                            if (currentRank.equals(senderRank) || Rank.isHigher(currentRank, senderRank)) {
//                                player.sendMessage("§cYou are not allowed to set the rank of someone who is equal or above your rank.");
//                                //msgFCTMembers("§4§l[WARNING] §b" + sender.getName() + " §7tried to change §b" + target.getName() + "§7's rank to " + rank.getDisplayName());
//                                return true;
//                            } else {
//                                if (ranks.containsKey(target.getUniqueId())) {
//                                    ranks.replace(target.getUniqueId(), rank);
//                                } else {
//                                    ranks.put(target.getUniqueId(), rank);
//                                }
//                                sender.sendMessage("§aSuccessfully set §b" + target.getName() + "§a's rank to §b" + rank.getDisplayName());
//                                if (target.isOnline()) {
//                                    ((Player) target).sendMessage("§aYour rank was set to " + rank.getDisplayName() + " §aby " + senderRank.getBaseColor() + player.getName());
//                                    updateDisplayName((Player) target);
//                                }
//                            }
//                        } else {
//                            if (ranks.containsKey(target.getUniqueId())) {
//                                ranks.replace(target.getUniqueId(), rank);
//                            } else {
//                                ranks.put(target.getUniqueId(), rank);
//                            }
//                            sender.sendMessage("§aSuccessfully set §b" + target.getName() + "§a's rank to §b" + rank.getDisplayName());
//                            if (target.isOnline()) {
//                                ((Player) target).sendMessage("§aYour rank was set to " + rank.getDisplayName() + " §aby §1§lCONSOLE");
//                                updateDisplayName((Player) target);
//                            }
//                        }
//                    } else if (args[0].equalsIgnoreCase("prefixes")) {
//                        if (!(sender instanceof Player)) {
//                            sender.sendMessage("§cOnly players can use that command.");
//                            return true;
//                        }
//
//                        Player player = (Player) sender;
//                        if (!getRank(player).equals(Rank.FIRECRAFT_TEAM)) {
//                            player.sendMessage("§cOnly Firecraft Team members can set the use of prefixes.");
//                            return true;
//                        }
//
//                        if (!(args.length > 1)) {
//                            player.sendMessage("§cYou must provide true/false/on/off");
//                            return true;
//                        }
//
//                        if (args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("on")) {
//                            if (prefixes) {
//                                player.sendMessage("§cPrefixes are already turned on.");
//                                return true;
//                            }
//
//                            prefixes = true;
//                            player.sendMessage("§aYou turned on the displaying of prefixes.");
//                            Bukkit.getOnlinePlayers().forEach(p -> updateDisplayName(p));
//                        } else if (args[1].equalsIgnoreCase("false") || args[1].equalsIgnoreCase("off")) {
//                            if (!prefixes) {
//                                player.sendMessage("§cPrefixes are already turned off.");
//                                return true;
//                            }
//
//                            prefixes = false;
//                            player.sendMessage("§aYou turned off the displaying of prefixes.");
//                            Bukkit.getOnlinePlayers().forEach(p -> updateDisplayName(p));
//                        }
//                    }
//                } else {
//                    sender.sendMessage("§cYou are not allowed to use that command.");
//                    return true;
//                }
//
//                return true;
//            }
//        });

        new BukkitRunnable() {
            public void run() {
                minecraftSocketWorkers.forEach(sw -> {
                    if (!sw.isConnected()) {
                        minecraftSocketWorkers.remove(sw);
                    }
                });
            }
        }.runTaskTimerAsynchronously(this, 0L, 20);
    }

    public void onDisable() {
        saveData();
    }

    public void saveData() {
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
            player = new FirecraftPlayer("", uuid, Rank.FIRECRAFT_TEAM);
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
}