package com.zenith.feature.actionlimiter.handlers.inbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerConnection;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import static com.zenith.Shared.CONFIG;

public class ALChatHandler implements PacketHandler<ServerboundChatPacket, ServerConnection> {
    @Override
    public ServerboundChatPacket apply(final ServerboundChatPacket packet, final ServerConnection session) {
        if (CONFIG.client.extra.actionLimiter.allowChat) return packet;
        else return null;
    }
}
