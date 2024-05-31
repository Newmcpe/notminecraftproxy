package com.zenith.network.registry;

import com.zenith.network.server.ServerConnection;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;

import static com.zenith.Shared.CACHE;

public class PlayerPositionRotationHandler implements  AsyncPacketHandler<ServerboundMovePlayerPosRotPacket, ServerConnection> {
    @Override
    public boolean applyAsync(ServerboundMovePlayerPosRotPacket packet, ServerConnection session) {
        CACHE.getProfileCache().setYaw(packet.getYaw()).setPitch(packet.getPitch());
        return true;
    }
}
