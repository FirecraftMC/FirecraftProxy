package net.firecraftmc.socket.server;

import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.classes.utils.ChatUtils;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.*;
import net.firecraftmc.shared.packets.staffchat.*;

import java.io.IOException;
import java.net.Socket;
import java.util.*;

public class MinecraftSocketWorker extends Thread {
    
    private final Main plugin;
    private final Socket socket;
    private FirecraftServer server;
    
    private FirecraftConnection connection;
    
    MinecraftSocketWorker(Main main, Socket socket) {
        this.plugin = main;
        this.socket = socket;
        this.connection = new FirecraftConnection(socket);
    }
    
    public void run() {
        FirecraftPacket packet;
        while (connection != null && connection.isOpen()) {
            packet = connection.readPacket();
            if (packet instanceof FPacketServerConnect) {
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
                this.server = packet.getServer();
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
                FirecraftPlayer player = plugin.getPlayer(sPJ.getUuid());
                if (player == null) {
                    player = new FirecraftPlayer(plugin, sPJ.getUuid(), Rank.PRIVATE);
                    plugin.addPlayer(player);
                }
                FPacketPlayerJoin nPacket = new FPacketPlayerJoin(sPJ.getServer(), player);
                plugin.sendToAll(nPacket);
                continue;
            } else if (packet instanceof FPRequestRandomProfile) {
                FPRequestRandomProfile randomProfile = ((FPRequestRandomProfile) packet);
                List<FirecraftPlayer> allowedProfiles = new ArrayList<>(plugin.getPlayers().size());
                plugin.getPlayers().forEach(p -> {
                    if (!Rank.isStaff(p.getMainRank())) {
                        allowedProfiles.add(p);
                    }
                });
                
                Random random = new Random();
                for (int i = 0; i < random.nextInt(10); i++) {
                    Collections.shuffle(allowedProfiles);
                }
                FirecraftPlayer profile = allowedProfiles.get(random.nextInt(allowedProfiles.size() - 1));
                FPSendRandomProfile sendProfile = new FPSendRandomProfile(server, profile, randomProfile.getRequester());
                send(sendProfile);
            } else if (packet instanceof FPRequestProfile) {
                FPRequestProfile request = ((FPRequestProfile) packet);
                FirecraftPlayer player = plugin.getPlayer(request.getUniqueId());
                if (player == null) {
                    player = new FirecraftPlayer(plugin, request.getUniqueId(), Rank.PRIVATE);
                }
                FPacketSendProfile sendProfile = new FPacketSendProfile(server, player);
                send(sendProfile);
            } else if (packet instanceof FPacketStaffChat) {
                FPacketStaffChat staffChatPacket = ((FPacketStaffChat) packet);
                FirecraftPlayer staffMember = staffChatPacket.getPlayer();
                staffMember = plugin.getPlayer(staffMember.getUniqueId());
                if (packet instanceof FPStaffChatJoin) {
                    String format = ChatUtils.formatStaffJoin(server, staffMember);
                    ChatUtils.sendStaffChatMessage(plugin.getPlayers(), staffMember, format);
                } else if (packet instanceof FPStaffChatQuit) {
                    String format = ChatUtils.formatStaffLeave(server, staffMember);
                    ChatUtils.sendStaffChatMessage(plugin.getPlayers(), staffMember, format);
                } else if (packet instanceof FPStaffChatMessage) {
                    FPStaffChatMessage staffMessage = (FPStaffChatMessage) packet;
                    String format = ChatUtils.formatStaffMessage(staffMessage.getServer(), staffMessage.getPlayer(), staffMessage.getMessage());
                    ChatUtils.sendStaffChatMessage(plugin.getPlayers(), staffMember, format);
                } else if (packet instanceof FPStaffChatSetNick) {
                    FPStaffChatSetNick setNick = ((FPStaffChatSetNick) packet);
                    String format = ChatUtils.formatSetNick(setNick.getServer(), setNick.getPlayer(), setNick.getProfile());
                    ChatUtils.sendStaffChatMessage(plugin.getPlayers(), staffMember, format);
                } else if (packet instanceof FPStaffChatResetNick) {
                    FPStaffChatResetNick resetNick = ((FPStaffChatResetNick) packet);
                    String format = ChatUtils.formatResetNick(resetNick.getServer(), resetNick.getPlayer());
                    ChatUtils.sendStaffChatMessage(plugin.getPlayers(), staffMember, format);
                } else if (packet instanceof FPSCVanishToggle) {
                    FPSCVanishToggle toggleVanish = ((FPSCVanishToggle) packet);
                    String format = ChatUtils.formatVanishToggle(toggleVanish.getServer(), toggleVanish.getPlayer(), toggleVanish.isVanished());
                    ChatUtils.sendStaffChatMessage(plugin.getPlayers(), staffMember, format);
                } else if (packet instanceof FPSCSetGamemode) {
                    FPSCSetGamemode setGamemode = (FPSCSetGamemode) packet;
                    String format = ChatUtils.formatSetGamemode(server, staffMember, setGamemode.getMode());
                    ChatUtils.sendStaffChatMessage(plugin.getPlayers(), staffMember, format);
                } else if (packet instanceof FPSCSetGamemodeOthers) {
                    FPSCSetGamemodeOthers setGamemodeOthers = (FPSCSetGamemodeOthers) packet;
                    String format = ChatUtils.formatSetGamemodeOthers(server, staffMember, setGamemodeOthers.getMode(), setGamemodeOthers.getTarget());
                    ChatUtils.sendStaffChatMessage(plugin.getPlayers(), staffMember, format);
                } else if (packet instanceof FPSCTeleport) {
                    FPSCTeleport teleport = (FPSCTeleport) packet;
                    String format = ChatUtils.formatTeleport(server, staffMember, teleport.getTarget());
                    ChatUtils.sendStaffChatMessage(plugin.getPlayers(), staffMember, format);
                } else if (packet instanceof FPSCTeleportOthers) {
                    FPSCTeleportOthers teleportOthers = (FPSCTeleportOthers) packet;
                    String format = ChatUtils.formatTeleportOthers(server, staffMember, teleportOthers.getTarget1(), teleportOthers.getTarget2());
                    ChatUtils.sendStaffChatMessage(plugin.getPlayers(), staffMember, format);
                } else if (packet instanceof FPSCTeleportHere) {
                    FPSCTeleportHere tpHere = (FPSCTeleportHere) packet;
                    String format = ChatUtils.formatTeleportHere(server, staffMember, tpHere.getTarget());
                    ChatUtils.sendStaffChatMessage(plugin.getPlayers(), staffMember, format);
                }
            }
            
            plugin.sendToAll(packet);
        }
    }
    
    void send(FirecraftPacket packet) {
        connection.sendPacket(packet);
    }
    
    private void disconnect() {
        connection.close();
        try {
            this.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.connection = null;
    }
    
    public FirecraftServer getServerName() {
        return server;
    }
    
    boolean isConnected() {
        return socket.isConnected();
    }
}