package com.zenith.network.registry;

import com.zenith.network.server.ServerConnection;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;

import static com.zenith.Shared.CACHE;

public class PlayerRotationHandler implements  AsyncPacketHandler<ServerboundMovePlayerRotPacket, ServerConnection> {
    @Override
    public boolean applyAsync(ServerboundMovePlayerRotPacket packet, ServerConnection session) {
        CACHE.getProfileCache()
                .setYaw(packet.getYaw())
                .setPitch(packet.getPitch());
        return true;
    }
}
