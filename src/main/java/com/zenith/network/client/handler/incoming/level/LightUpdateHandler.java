package com.zenith.network.client.handler.incoming.level;

import com.zenith.network.client.ClientSession;
import com.zenith.network.registry.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLightUpdatePacket;

import static com.zenith.Shared.CACHE;

public class LightUpdateHandler implements ClientEventLoopPacketHandler<ClientboundLightUpdatePacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundLightUpdatePacket packet, final ClientSession session) {
        return CACHE.getChunkCache().handleLightUpdate(packet);
    }
}
