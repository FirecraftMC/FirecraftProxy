package net.firecraftmc.socket.server;

import net.firecraftmc.shared.classes.FirecraftConnection;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.classes.FirecraftServer;
import net.firecraftmc.shared.enums.Rank;
import net.firecraftmc.shared.packets.*;

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