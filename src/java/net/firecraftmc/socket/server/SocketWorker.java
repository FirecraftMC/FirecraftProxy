package net.firecraftmc.socket.server;

import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.classes.utils.ChatUtils;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.*;
import net.firecraftmc.shared.packets.staffchat.*;

import java.io.*;
import java.net.Socket;
import java.util.Collection;

public class SocketWorker extends Thread {
    
    private static Main plugin;
    private final Socket socket;
    private FirecraftServer server;
    
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    
    SocketWorker(Main main, Socket socket) {
        plugin = main;
        this.socket = socket;
        try {
            this.outputStream = new ObjectOutputStream(socket.getOutputStream());
            this.inputStream = new ObjectInputStream(socket.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void run() {
        try {
            FirecraftPacket packet;
            while (socket.isConnected()) {
                Object obj = this.inputStream.readObject();
                System.out.println(obj);
                
                if (obj instanceof FirecraftPacket) {
                    packet = (FirecraftPacket) obj;
                } else {
                    System.out.println("Object received was not a FirecraftPacket.");
                    continue;
                }
                
                if (packet instanceof FPacketServerConnect) {
                    System.out.println("Packet is server connect");
                    FPacketServerConnect serverConnect = (FPacketServerConnect) packet;
                    this.server = packet.getServer();
                    String format = "&8[&9S&8] &8[<server>&8] &9<message>";
                    format = format.replace("<server>", serverConnect.getServer().toString());
                    format = format.replace("<message>", serverConnect.getServer().getName() + " has started.");
                    String finalFormat = format;
                    plugin.getPlayers().forEach(fp -> {
                        if (Rank.isStaff(fp.getMainRank())) {
                            fp.sendMessage(finalFormat);
                        }
                    });
                } else if (packet instanceof FPacketServerDisconnect) {
                    FPacketServerDisconnect serverDisconnect = (FPacketServerDisconnect) packet;
                    String format = "&8[&9S&8] &8[<server>&8] &9<message>";
                    format = format.replace("<server>", serverDisconnect.getServer().toString());
                    format = format.replace("<message>", serverDisconnect.getServer().getName() + " has stopped.");
                    String finalFormat = format;
                    plugin.getPlayers().forEach(fp -> {
                        if (Rank.isStaff(fp.getMainRank())) {
                            fp.sendMessage(finalFormat);
                        }
                    });
                    plugin.removeWorker(this);
                    disconnect();
                    break;
                } else if (packet instanceof FPacketServerPlayerJoin) {
                    FPacketServerPlayerJoin sPJ = (FPacketServerPlayerJoin) packet;
                    FPacketPlayerJoin nPacket = new FPacketPlayerJoin(sPJ.getServer(), sPJ.getUuid());
                    sendToAll(nPacket);
                } else if (packet instanceof FPacketStaffChat) {
                    FPacketStaffChat staffChatPacket = ((FPacketStaffChat) packet);
                    FirecraftPlayer staffMember = Utils.getPlayerFromDatabase(plugin.getDatabase(), plugin, staffChatPacket.getPlayer());
                    Collection<FirecraftPlayer> players = plugin.getPlayers();
                    if (packet instanceof FPStaffChatJoin) {
                        String format = ChatUtils.formatStaffJoin(server, staffMember);
                        ChatUtils.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPStaffChatQuit) {
                        String format = ChatUtils.formatStaffLeave(server, staffMember);
                        ChatUtils.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPStaffChatMessage) {
                        FPStaffChatMessage staffMessage = (FPStaffChatMessage) packet;
                        String format = ChatUtils.formatStaffMessage(staffMessage.getServer(), staffMember, staffMessage.getMessage());
                        if (players.isEmpty()) return;
                        players.forEach(p -> {
                            if (Rank.isStaff(p.getMainRank())) {
                                p.sendMessage(format);
                            }
                        });
                    } else if (packet instanceof FPStaffChatSetNick) {
                        FPStaffChatSetNick setNick = ((FPStaffChatSetNick) packet);
                        String format = ChatUtils.formatSetNick(setNick.getServer(), staffMember, setNick.getProfile());
                        ChatUtils.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPStaffChatResetNick) {
                        FPStaffChatResetNick resetNick = ((FPStaffChatResetNick) packet);
                        String format = ChatUtils.formatResetNick(resetNick.getServer(), staffMember);
                        ChatUtils.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPSCVanishToggle) {
                        FPSCVanishToggle toggleVanish = ((FPSCVanishToggle) packet);
                        String format = ChatUtils.formatVanishToggle(toggleVanish.getServer(), staffMember, staffMember.isVanished());
                        ChatUtils.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPSCSetGamemode) {
                        FPSCSetGamemode setGamemode = (FPSCSetGamemode) packet;
                        String format = ChatUtils.formatSetGamemode(server, staffMember, setGamemode.getMode());
                        ChatUtils.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPSCSetGamemodeOthers) {
                        FPSCSetGamemodeOthers setGamemodeOthers = (FPSCSetGamemodeOthers) packet;
                        FirecraftPlayer target = Utils.getPlayerFromDatabase(plugin.getDatabase(), plugin, setGamemodeOthers.getTarget());
                        String format = ChatUtils.formatSetGamemodeOthers(server, staffMember, setGamemodeOthers.getMode(), target);
                        ChatUtils.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPSCTeleport) {
                        FPSCTeleport teleport = (FPSCTeleport) packet;
                        FirecraftPlayer target = Utils.getPlayerFromDatabase(plugin.getDatabase(), plugin, teleport.getTarget());
                        String format = ChatUtils.formatTeleport(server, staffMember, target);
                        ChatUtils.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPSCTeleportOthers) {
                        FPSCTeleportOthers teleportOthers = (FPSCTeleportOthers) packet;
                        FirecraftPlayer target1 = Utils.getPlayerFromDatabase(plugin.getDatabase(), plugin, teleportOthers.getTarget1());
                        FirecraftPlayer target2 = Utils.getPlayerFromDatabase(plugin.getDatabase(), plugin, teleportOthers.getTarget2());
                        String format = ChatUtils.formatTeleportOthers(server, staffMember, target1, target2);
                        ChatUtils.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPSCTeleportHere) {
                        FPSCTeleportHere tpHere = (FPSCTeleportHere) packet;
                        FirecraftPlayer target = Utils.getPlayerFromDatabase(plugin.getDatabase(), plugin, tpHere.getTarget());
                        String format = ChatUtils.formatTeleportHere(server, staffMember, target);
                        ChatUtils.sendStaffChatMessage(players, staffMember, format);
                    }
                }
                sendToAll(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    static void sendToAll(FirecraftPacket packet) {
        for (SocketWorker worker : plugin.socketWorkers) {
            try {
                worker.outputStream.writeObject(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void disconnect() {
        System.out.println("Disconnect method called.");
        try {
            this.outputStream.close();
            this.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public FirecraftServer getServerName() {
        return server;
    }
    
    boolean isConnected() {
        return socket.isConnected();
    }
}