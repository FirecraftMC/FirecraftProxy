package net.firecraftmc.socket.server;

import net.firecraftmc.shared.classes.*;
import net.firecraftmc.shared.classes.utils.ChatUtils;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.*;
import net.firecraftmc.shared.packets.staffchat.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.Socket;

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
            System.out.println(packet);
            if (packet instanceof FPacketServerConnect) {
                FPacketServerConnect serverConnect = (FPacketServerConnect) packet;
                this.server = packet.getServer();
                String format = "&8[&dS&8] &8[<server>&8] &d<message>";
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
                String format = "&8[&dS&8] &8[<server>&8] &d<message>";
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
            } else if (packet instanceof FPStaffChatJoin) {
                FPStaffChatJoin staffJoin = (FPStaffChatJoin) packet;
                FirecraftPlayer player = staffJoin.getPlayer();
                if (player.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                    String format = ChatUtils.formatFCTJoin(staffJoin.getServer(), staffJoin.getPlayer());

                    for (FirecraftPlayer p : plugin.getPlayers()) {
                        if (p.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            p.sendMessage(format);
                        }
                    }
                } else {
                    if (!player.getMainRank().equals(plugin.getRank(player.getUniqueId()))) {
                        staffJoin.getPlayer().setMainRank(plugin.getRank(player.getUniqueId()));
                    }
                    String format = ChatUtils.formatStaffJoin(staffJoin.getServer(), staffJoin.getPlayer());
                    for (FirecraftPlayer p : plugin.getPlayers()) {
                        if (Rank.isStaff(p.getMainRank())) {
                            p.sendMessage(format);
                        }
                    }
                }
            } else if (packet instanceof FPStaffChatQuit) {
                FPStaffChatQuit staffQuit = (FPStaffChatQuit) packet;
                FirecraftPlayer player = staffQuit.getPlayer();
                if (staffQuit.getPlayer().getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                    String format = ChatUtils.formatFCTLeave(staffQuit.getServer(), staffQuit.getPlayer());

                    for (FirecraftPlayer p : plugin.getPlayers()) {
                        if (p.getMainRank().equals(Rank.FIRECRAFT_TEAM)) {
                            p.sendMessage(format);
                        }
                    }
                } else {
                    if (!player.getMainRank().equals(plugin.getRank(player.getUniqueId()))) {
                        staffQuit.getPlayer().setMainRank(plugin.getRank(player.getUniqueId()));
                    }
                    String format = ChatUtils.formatStaffLeave(staffQuit.getServer(), staffQuit.getPlayer());
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage(Utils.color(format));
                    }
                }
            } else if (packet instanceof FPStaffChatMessage) {
                FPStaffChatMessage staffMessage = (FPStaffChatMessage) packet;
                if (!staffMessage.getPlayer().getMainRank().equals(plugin.getRank(staffMessage.getPlayer().getUniqueId()))) {
                    staffMessage.getPlayer().setMainRank(plugin.getRank(staffMessage.getPlayer().getUniqueId()));
                }
                if (!Bukkit.getOnlinePlayers().isEmpty()) {
                    String format = ChatUtils.formatStaffMessage(staffMessage.getServer(), staffMessage.getPlayer(), staffMessage.getMessage());
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage(Utils.color(format));
                    }
                }
            } else if (packet instanceof FPRequestProfile) {
                FPRequestProfile profileRequest = (FPRequestProfile) packet;

                FirecraftPlayer profile = plugin.getPlayer(profileRequest.getUniqueId());
                if (profile == null) {
                    profile = new FirecraftPlayer(plugin, profileRequest.getUniqueId(), Rank.PRIVATE);
                }
                FPacketSendProfile sendProfile = new FPacketSendProfile(new FirecraftServer("Socket", ChatColor.DARK_RED), profile);
                this.connection.sendPacket(sendProfile);
                continue;
            } else if (packet instanceof FPStaffChatSetNick) {
                FPStaffChatSetNick setNick = ((FPStaffChatSetNick) packet);
                String format = ChatUtils.formatSetNick(setNick.getServer(), setNick.getPlayer(), setNick.getProfile());
                for (FirecraftPlayer p : plugin.getPlayers()) {
                    p.sendMessage(format);
                }
            } else if (packet instanceof FPStaffChatResetNick) {
                FPStaffChatResetNick resetNick = ((FPStaffChatResetNick) packet);
                String format = ChatUtils.formatResetNick(resetNick.getServer(), resetNick.getPlayer());
                for (FirecraftPlayer p : plugin.getPlayers()) {
                    p.sendMessage(format);
                }
            } else if (packet instanceof FPacketServerPlayerLeave) {
                FPacketServerPlayerLeave sPL = ((FPacketServerPlayerLeave) packet);
                FirecraftPlayer localPlayer = plugin.getPlayer(sPL.getPlayer().getUniqueId());
                if (localPlayer == null) {
                    localPlayer = new FirecraftPlayer(plugin, sPL.getPlayer().getUniqueId(), Rank.PRIVATE);
                    plugin.addPlayer(localPlayer);
                } else {
                    plugin.addPlayer(sPL.getPlayer());
                }
                FPacketPlayerLeave nPacket = new FPacketPlayerLeave(sPL.getServer(), sPL.getPlayer());
                plugin.sendToAll(nPacket);
                continue;
            } else if (packet instanceof FPStaffChatVanishToggle) {
                FPStaffChatVanishToggle toggleVanish = ((FPStaffChatVanishToggle) packet);
                if (!toggleVanish.getPlayer().getMainRank().equals(plugin.getPlayer(toggleVanish.getPlayer().getUniqueId()).getMainRank())) {
                    toggleVanish.getPlayer().setMainRank(plugin.getPlayer(toggleVanish.getPlayer().getUniqueId()).getMainRank());
                }
                String format = ChatUtils.formatVanishToggle(toggleVanish.getServer(), toggleVanish.getPlayer(), toggleVanish.isVanished());
                if (!plugin.getPlayers().isEmpty()) {
                    for (FirecraftPlayer p : plugin.getPlayers()) {
                        if (Rank.isStaff(p.getMainRank())) {
                            p.sendMessage(format);
                        }
                    }
                }
            } else if (packet instanceof FPStaffChatVanishToggleOthers) {
                FPStaffChatVanishToggleOthers toggleVanish = ((FPStaffChatVanishToggleOthers) packet);
                if (!toggleVanish.getPlayer().getMainRank().equals(plugin.getPlayer(toggleVanish.getPlayer().getUniqueId()).getMainRank())) {
                    toggleVanish.getPlayer().setMainRank(plugin.getPlayer(toggleVanish.getPlayer().getUniqueId()).getMainRank());
                }
                String format = ChatUtils.formatVanishToggleOthers(toggleVanish.getServer(), toggleVanish.getPlayer(), toggleVanish.getTarget());
                if (!plugin.getPlayers().isEmpty()) {
                    for (FirecraftPlayer p : plugin.getPlayers()) {
                        if (Rank.isStaff(p.getMainRank())) {
                            p.sendMessage(format);
                        }
                    }
                }
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