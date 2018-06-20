package net.firecraftmc.proxy;

import net.firecraftmc.shared.classes.model.player.FirecraftPlayer;
import net.firecraftmc.shared.classes.model.FirecraftServer;
import net.firecraftmc.shared.classes.Messages;
import net.firecraftmc.shared.classes.Utils;
import net.firecraftmc.shared.classes.enums.Rank;
import net.firecraftmc.shared.packets.*;
import net.firecraftmc.shared.packets.staffchat.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;

/**
 * Represents a ProxyWorker, similar to FirecraftSocket
 */
public class ProxyWorker extends Thread {

    private static Main plugin;
    private final java.net.Socket socket;
    private FirecraftServer server;

    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    ProxyWorker(Main main, java.net.Socket socket) {
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
                if (obj instanceof Integer) continue;
                if (obj instanceof FirecraftPacket) {
                    packet = (FirecraftPacket) obj;
                } else {
                    System.out.println("Object received was not a FirecraftPacket.");
                    continue;
                }

                if (packet instanceof FPacketServerConnect) {
                    this.server = packet.getServer();
                    String format = Utils.Chat.formatServerConnect(server);
                    if (!plugin.getPlayers().isEmpty())
                        plugin.getPlayers().forEach(fp -> fp.sendMessage(format));
                } else if (packet instanceof FPacketServerDisconnect) {
                    FPacketServerDisconnect serverDisconnect = (FPacketServerDisconnect) packet;
                    String format = Utils.Chat.formatServerDisconnect(serverDisconnect.getServer());
                    if (!plugin.getPlayers().isEmpty())
                        plugin.getPlayers().forEach(fp -> fp.sendMessage(format));
                    plugin.removeWorker(this);
                    disconnect();
                    sendToAll(packet);
                    break;
                } else if (packet instanceof FPacketServerPlayerJoin) {
                    FPacketServerPlayerJoin sPJ = (FPacketServerPlayerJoin) packet;
                    FPacketPlayerJoin nPacket = new FPacketPlayerJoin(sPJ.getServer(), sPJ.getUuid());
                    sendToAll(nPacket);
                    continue;
                } else if (packet instanceof FPacketPunish) {
                    Utils.Socket.handlePunish(packet, plugin.getFCDatabase(), plugin.getPlayers());
                } else if (packet instanceof FPacketPunishRemove) {
                    Utils.Socket.handleRemovePunish(packet, plugin.getFCDatabase(), plugin.getPlayers());
                } else if (packet instanceof FPacketAcknowledgeWarning) {
                    String format = Utils.Chat.formatAckWarning(packet.getServer().getName(), ((FPacketAcknowledgeWarning) packet).getWarnedName());
                    if (plugin.getPlayers().isEmpty()) {
                        plugin.getPlayers().forEach(p -> {
                            p.sendMessage("");
                            p.sendMessage(format);
                            p.sendMessage("");
                        });
                    }
                } else if (packet instanceof FPacketSocketBroadcast) {
                    FPacketSocketBroadcast socketBroadcast = ((FPacketSocketBroadcast) packet);
                    String message = Messages.socketBroadcast(socketBroadcast.getMessage());
                    if (plugin.getPlayers().isEmpty())
                        plugin.getPlayers().forEach(p -> p.sendMessage(message));
                } else if (packet instanceof FPacketReport) {
                    if (plugin.getPlayers().isEmpty())
                        Utils.Socket.handleReport(packet, server, plugin.getFCDatabase(), plugin.getPlayers());
                } else if (packet instanceof FPacketStaffChat) {
                    FPacketStaffChat staffChatPacket = ((FPacketStaffChat) packet);
                    FirecraftPlayer staffMember = plugin.getFCDatabase().getPlayer(staffChatPacket.getPlayer());
                    Collection<FirecraftPlayer> players = plugin.getPlayers();
                    if (packet instanceof FPStaffChatJoin) {
                        String format = Utils.Chat.formatStaffJoinLeave(server, staffMember, "joined");
                        Utils.Chat.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPStaffChatQuit) {
                        String format = Utils.Chat.formatStaffJoinLeave(server, staffMember, "left");
                        Utils.Chat.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPStaffChatMessage) {
                        FPStaffChatMessage staffMessage = (FPStaffChatMessage) packet;
                        String format = Utils.Chat.formatStaffMessage(staffMessage.getServer(), staffMember, staffMessage.getMessage());
                        if (!players.isEmpty()) {
                            players.forEach(p -> {
                                if (Rank.isStaff(p.getMainRank())) {
                                    p.sendMessage(format);
                                }
                            });
                        }
                    } else if (packet instanceof FPStaffChatSetNick) {
                        FPStaffChatSetNick setNick = ((FPStaffChatSetNick) packet);
                        String format = Utils.Chat.formatSetNick(setNick.getServer(), staffMember, setNick.getProfile());
                        Utils.Chat.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPStaffChatResetNick) {
                        FPStaffChatResetNick resetNick = ((FPStaffChatResetNick) packet);
                        String format = Utils.Chat.formatResetNick(resetNick.getServer(), staffMember);
                        Utils.Chat.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPSCVanishToggle) {
                        FPSCVanishToggle toggleVanish = ((FPSCVanishToggle) packet);
                        String format = Utils.Chat.formatVanishToggle(toggleVanish.getServer(), staffMember, staffMember.isVanished());
                        Utils.Chat.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPSCSetGamemode) {
                        FPSCSetGamemode setGamemode = (FPSCSetGamemode) packet;
                        String format = Utils.Chat.formatSetGamemode(server, staffMember, setGamemode.getMode());
                        Utils.Chat.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPSCSetGamemodeOthers) {
                        FPSCSetGamemodeOthers setGamemodeOthers = (FPSCSetGamemodeOthers) packet;
                        FirecraftPlayer target = plugin.getFCDatabase().getPlayer(setGamemodeOthers.getTarget());
                        String format = Utils.Chat.formatSetGamemodeOthers(server, staffMember, setGamemodeOthers.getMode(), target);
                        Utils.Chat.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPSCTeleport) {
                        FPSCTeleport teleport = (FPSCTeleport) packet;
                        FirecraftPlayer target = plugin.getFCDatabase().getPlayer(teleport.getTarget());
                        String format = Utils.Chat.formatTeleport(server, staffMember, target);
                        Utils.Chat.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPSCTeleportOthers) {
                        FPSCTeleportOthers teleportOthers = (FPSCTeleportOthers) packet;
                        FirecraftPlayer target1 = plugin.getFCDatabase().getPlayer(teleportOthers.getTarget1());
                        FirecraftPlayer target2 = plugin.getFCDatabase().getPlayer(teleportOthers.getTarget2());
                        String format = Utils.Chat.formatTeleportOthers(server, staffMember, target1, target2);
                        Utils.Chat.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPSCTeleportHere) {
                        FPSCTeleportHere tpHere = (FPSCTeleportHere) packet;
                        FirecraftPlayer target = plugin.getFCDatabase().getPlayer(tpHere.getTarget());
                        String format = Utils.Chat.formatTeleportHere(server, staffMember, target);
                        Utils.Chat.sendStaffChatMessage(players, staffMember, format);
                    } else if (packet instanceof FPReportAssignOthers) {
                        FPReportAssignOthers assignOthers = ((FPReportAssignOthers) packet);
                        String format = Utils.Chat.formatReportAssignOthers(server.getName(), staffMember.getName(), assignOthers.getAssignee(), assignOthers.getId());
                        if (!players.isEmpty()) {
                            players.forEach(p -> {
                                if (Rank.isStaff(p.getMainRank())) {
                                    p.sendMessage(format);
                                }
                            });
                        }
                    } else if (packet instanceof FPReportAssignSelf) {
                        FPReportAssignSelf assignSelf = ((FPReportAssignSelf) packet);
                        String format = Utils.Chat.formatReportAssignSelf(server.getName(), staffMember.getName(), assignSelf.getId());
                        if (!players.isEmpty()) {
                            players.forEach(p -> {
                                if (Rank.isStaff(p.getMainRank())) {
                                    p.sendMessage(format);
                                }
                            });
                        }
                    } else if (packet instanceof FPReportSetOutcome) {
                        FPReportSetOutcome setOutcome = ((FPReportSetOutcome) packet);
                        String format = Utils.Chat.formatReportSetOutcome(server.getName(), staffMember.getName(), setOutcome.getId(), setOutcome.getOutcome());
                        if (!players.isEmpty()) {
                            players.forEach(p -> {
                                if (Rank.isStaff(p.getMainRank())) {
                                    p.sendMessage(format);
                                }
                            });
                        }
                    } else if (packet instanceof FPReportSetStatus) {
                        FPReportSetStatus setStatus = ((FPReportSetStatus) packet);
                        String format = Utils.Chat.formatReportSetStatus(server.getName(), staffMember.getName(), setStatus.getId(), setStatus.getStatus());
                        if (!players.isEmpty()) {
                            players.forEach(p -> {
                                if (Rank.isStaff(p.getMainRank())) {
                                    p.sendMessage(format);
                                }
                            });
                        }
                    }
                }
                sendToAll(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void sendToAll(FirecraftPacket packet) {
        for (ProxyWorker worker: plugin.proxyWorkers) {
            try {
                if (worker.isConnected()) {
                    worker.outputStream.writeObject(packet);
                } else {
                    worker.disconnect();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void disconnect() throws IOException {
        System.out.println("Disconnect method called.");
        try {
            outputStream.close();
        } finally {
            try {
                inputStream.close();
            } finally {
                socket.close();
            }
        }
    }

    public FirecraftServer getServerName() {
        return server;
    }

    boolean isConnected() {
        boolean connected = true;
        try {
            outputStream.writeObject(0);
        } catch (IOException e) {
            if (e.getMessage().toLowerCase().contains("socket closed") || e.getMessage().toLowerCase().contains("connection reset") || e.getMessage().toLowerCase().contains("broken pipe")) {
                connected = false;
            }
        }

        return connected;
    }
}