package com.zenith.client.handler.incoming;

import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerMapDataPacket;
import com.zenith.client.ClientSession;
import com.zenith.util.handler.HandlerRegistry;

import static com.zenith.util.Constants.CACHE;

public class MapDataHandler implements HandlerRegistry.AsyncIncomingHandler<ServerMapDataPacket, ClientSession> {
    @Override
    public boolean applyAsync(ServerMapDataPacket packet, ClientSession session) {
        CACHE.getMapDataCache().upsert(packet);
        return true;
    }

    @Override
    public Class<ServerMapDataPacket> getPacketClass() {
        return ServerMapDataPacket.class;
    }
}