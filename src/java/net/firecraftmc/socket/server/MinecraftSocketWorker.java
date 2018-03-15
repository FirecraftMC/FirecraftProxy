package net.firecraftmc.socket.server;

import net.firecraftmc.shared.classes.FirecraftConnection;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.FirecraftServer;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.*;

import java.io.IOException;
import java.net.Socket;

public class MinecraftSocketWorker extends Thread {

    private Main plugin;
    private Socket socket;
    private FirecraftServer server;

    private FirecraftConnection connection;

    public MinecraftSocketWorker(Main main, Socket socket) {
        this.plugin = main;
        this.socket = socket;
        this.connection = new FirecraftConnection(socket);
    }

    public void run() {
        FirecraftPacket packet;
        while (connection.isOpen()) {
            packet = connection.readPacket();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                return;
            }
            if (packet instanceof FPacketServerConnect) {
                FPacketServerConnect serverConnect = (FPacketServerConnect) packet;
                this.server = packet.getServer();
                String format = "&8[&dS&8] &8[<server>&8] &d<message>";
                format = format.replace("<server>", serverConnect.getServer().toString());
                format = format.replace("<message>", "has started.");
                String finalFormat = format;
                plugin.getPlayers().forEach(fp -> {
                    if (fp.getRank().equals(Rank.HELPER) || fp.getRank().isHigher(Rank.HELPER)) {
                        fp.sendMessage(finalFormat);
                    }
                });
            } else if (packet instanceof FPacketServerDisconnect) {
                FPacketServerDisconnect serverDisconnect = (FPacketServerDisconnect) packet;
                this.server = packet.getServer();
                String format = "&8[&dS&8] &8[<server>&8] &d<message>";
                format = format.replace("<server>", serverDisconnect.getServer().toString());
                format = format.replace("<message>", "has stopped.");
                String finalFormat = format;
                plugin.getPlayers().forEach(fp -> {
                    if (fp.getRank().equals(Rank.HELPER) || fp.getRank().isHigher(Rank.HELPER)) {
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
            } else if (packet instanceof FPacketStaffChatMessage) {
                FPacketStaffChatMessage staffChatMessage = (FPacketStaffChatMessage) packet;
                String format = "&8[&dS&8] &8[<server>&8] §r<displayname>§8: &d<message>";
                format = format.replace("<server>", staffChatMessage.getServer().toString());
                format = format.replace("<displayname>", staffChatMessage.getPlayer().getNameNoPrefix());
                format = format.replace("<message>", staffChatMessage.getMessage());
                String finalFormat = format;
                plugin.getPlayers().forEach(firecraftPlayer -> {
                    if (firecraftPlayer.getRank().equals(Rank.HELPER) || firecraftPlayer.getRank().isHigher(Rank.HELPER)) {
                        firecraftPlayer.sendMessage(finalFormat);
                    }
                });
            } else if (packet instanceof FPacketStaffChatStaffJoin) {
                FPacketStaffChatStaffJoin staffChatStaffJoin = (FPacketStaffChatStaffJoin) packet;
                String format = "&8[&dS&8] &8[<server>&8] §r<displayname> &d<message>";
                format = format.replace("<server>", staffChatStaffJoin.getServer().toString());
                format = format.replace("<displayname>", staffChatStaffJoin.getPlayer().getNameNoPrefix());
                format = format.replace("<message>", "joined");
                String finalFormat = format;
                plugin.getPlayers().forEach(firecraftPlayer -> {
                    if (firecraftPlayer.getRank().equals(Rank.HELPER) || firecraftPlayer.getRank().isHigher(Rank.HELPER)) {
                        firecraftPlayer.sendMessage(finalFormat);
                    }
                });
            } else if (packet instanceof FPacketStaffChatStaffQuit) {
                FPacketStaffChatStaffQuit staffChatStaffQuit = (FPacketStaffChatStaffQuit) packet;
                String format = "&8[&dS&8] &8[<server>&8] §r<displayname> &d<message>";
                format = format.replace("<server>", staffChatStaffQuit.getServer().toString());
                format = format.replace("<displayname>", staffChatStaffQuit.getPlayer().getNameNoPrefix());
                format = format.replace("<message>", "left");
                String finalFormat = format;
                plugin.getPlayers().forEach(firecraftPlayer -> {
                    if (firecraftPlayer.getRank().equals(Rank.HELPER) || firecraftPlayer.getRank().isHigher(Rank.HELPER)) {
                        firecraftPlayer.sendMessage(finalFormat);
                    }
                });
                plugin.updatePlayer(staffChatStaffQuit.getPlayer());
            }

            plugin.sendToAll(packet);
        }
    }

    public void send(FirecraftPacket packet) {
        connection.sendPacket(packet);
    }

    public void disconnect() {
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

    public boolean isConnected() {
        return socket.isConnected();
    }
}