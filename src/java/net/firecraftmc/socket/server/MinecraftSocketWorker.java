package net.firecraftmc.socket.server;

import net.firecraftmc.shared.classes.FirecraftConnection;
import net.firecraftmc.shared.classes.FirecraftPlayer;
import net.firecraftmc.shared.packets.*;

import java.io.IOException;
import java.net.Socket;

public class MinecraftSocketWorker extends Thread {

    private Main plugin;
    private Socket socket;
    private String name;

    private FirecraftConnection connection;

    public MinecraftSocketWorker(Main main, Socket socket) {
        this.plugin = main;
        this.socket = socket;
    }

    public void run() {
        this.connection = new FirecraftConnection(socket);

        FirecraftPacket packet;
        while (connection.isOpen()) {
            packet = connection.readPacket();
            if (packet instanceof FPacketServerConnect) {
                this.name = packet.getServer();
            } else if (packet instanceof FPacketServerDisconnect) {
                plugin.removeWorker(this);
                disconnect();
                break;
            } else if (packet instanceof FPacketServerPlayerJoin) {
                FPacketServerPlayerJoin sPJ = (FPacketServerPlayerJoin) packet;
                FirecraftPlayer player = new FirecraftPlayer(name, sPJ.getUuid(), plugin.getRank(sPJ.getUuid()));
                FPacketPlayerJoin nPacket = new FPacketPlayerJoin(sPJ.getServer(), player);
                plugin.sendToAll(nPacket);
                plugin.addPlayer(player);
                continue;
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

    public String getServerName() {
        return name;
    }
}