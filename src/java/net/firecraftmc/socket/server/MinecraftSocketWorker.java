package net.firecraftmc.socket.server;

import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.*;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatJoin;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatMessage;
import net.firecraftmc.shared.packets.staffchat.FPStaffChatQuit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;

public class MinecraftSocketWorker extends Thread {

    private Main plugin;
    private Socket socket;
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
                String format = "&8[&dS&8] &8[<server>&8] &d<message>";
                format = format.replace("<server>", serverConnect.getServer().toString());
                format = format.replace("<message>", serverConnect.getServer().getName() + " has started.");
                String finalFormat = format;
                plugin.getPlayers().forEach(fp -> {
                    if (Rank.isStaff(fp.getRank())) {
                        fp.sendMessage(finalFormat);
                    }
                });
            } else if (packet instanceof FPacketServerDisconnect) {
                FPacketServerDisconnect serverDisconnect = (FPacketServerDisconnect) packet;
                this.server = packet.getServer();
                String format = "&8[&dS&8] &8[<server>&8] &d<message>";
                format = format.replace("<server>", serverDisconnect.getServer().toString());
                format = format.replace("<message>", serverDisconnect.getServer().getName() + " has stopped.");
                String finalFormat = format;
                plugin.getPlayers().forEach(fp -> {
                    if (Rank.isStaff(fp.getRank())) {
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
                    player = new FirecraftPlayer(sPJ.getUuid(), Rank.DEFAULT);
                    plugin.addPlayer(player);
                }
                FPacketPlayerJoin nPacket = new FPacketPlayerJoin(sPJ.getServer(), player);
                plugin.sendToAll(nPacket);
                continue;
            } else if (packet instanceof FPStaffChatJoin) {
                FPStaffChatJoin staffJoin = (FPStaffChatJoin) packet;
                FirecraftPlayer player = staffJoin.getPlayer();
                if (player.getRank().equals(Rank.FIRECRAFT_TEAM)) {
                    String format = ChatUtils.formatFCTJoin(staffJoin.getServer(), staffJoin.getPlayer());

                    for (FirecraftPlayer p : plugin.getPlayers()) {
                        if (p.getRank().equals(Rank.FIRECRAFT_TEAM)) {
                            p.sendMessage(format);
                        }
                    }
                } else {
                    if (!player.getRank().equals(plugin.getRank(player.getUuid()))) {
                        staffJoin.getPlayer().setRank(plugin.getRank(player.getUuid()));
                    }
                    String format = ChatUtils.formatStaffJoin(staffJoin.getServer(), staffJoin.getPlayer());
                    for (FirecraftPlayer p : plugin.getPlayers()) {
                        if (Rank.isStaff(p.getRank())) {
                            p.sendMessage(format);
                        }
                    }
                }
            } else if (packet instanceof FPStaffChatQuit) {
                FPStaffChatQuit staffQuit = (FPStaffChatQuit) packet;
                FirecraftPlayer player = staffQuit.getPlayer();
                if (staffQuit.getPlayer().getRank().equals(Rank.FIRECRAFT_TEAM)) {
                    String format = ChatUtils.formatFCTLeave(staffQuit.getServer(), staffQuit.getPlayer());

                    for (FirecraftPlayer p : plugin.getPlayers()) {
                        if (p.getRank().equals(Rank.FIRECRAFT_TEAM)) {
                            p.sendMessage(format);
                        }
                    }
                } else {
                    if (!player.getRank().equals(plugin.getRank(player.getUuid()))) {
                        staffQuit.getPlayer().setRank(plugin.getRank(player.getUuid()));
                    }
                    String format = ChatUtils.formatStaffLeave(staffQuit.getServer(), staffQuit.getPlayer());
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage(Utils.color(format));
                    }
                }
            } else if (packet instanceof FPStaffChatMessage) {
                FPStaffChatMessage staffMessage = (FPStaffChatMessage) packet;
                if (!staffMessage.getPlayer().getRank().equals(plugin.getRank(staffMessage.getPlayer().getUuid()))) {
                    staffMessage.getPlayer().setRank(plugin.getRank(staffMessage.getPlayer().getUuid()));
                }
                String format = ChatUtils.formatStaffMessage(staffMessage.getServer(), staffMessage.getPlayer(), staffMessage.getMessage());
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(Utils.color(format));
                }
            } else if (packet instanceof FPRequestProfile) {
                FPRequestProfile profileRequest = (FPRequestProfile) packet;

                FirecraftPlayer profile = plugin.getPlayer(profileRequest.getUniqueId());
                FPacketSendProfile sendProfile = new FPacketSendProfile(new FirecraftServer("Socket", ChatColor.DARK_RED), profile);
                this.connection.sendPacket(sendProfile);
                return;
            }

            plugin.sendToAll(packet);
        }
    }

    void send(FirecraftPacket packet) { connection.sendPacket(packet); }

    private void disconnect() {
        connection.close();
        try {
            this.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.connection = null;
    }

    public FirecraftServer getServerName() { return server; }

    boolean isConnected() { return socket.isConnected(); }
}