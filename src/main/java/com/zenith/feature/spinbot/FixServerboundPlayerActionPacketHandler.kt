package com.zenith.feature.spinbot

import com.zenith.Shared.CACHE
import com.zenith.network.client.ClientSession
import com.zenith.network.registry.PacketHandler
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket

class FixServerboundPlayerActionPacketHandler: PacketHandler<ServerboundPlayerActionPacket, ClientSession> {

    override fun apply(packet: ServerboundPlayerActionPacket, session: ClientSession): ServerboundPlayerActionPacket {
        println("FixServerboundPlayerActionPacketHandler $packet, yaw: ${CACHE.playerCache.yaw}, pitch: ${CACHE.playerCache.pitch}")
        session.send(
            ServerboundMovePlayerRotPacket(
                true,
                CACHE.profileCache.yaw,
                CACHE.profileCache.pitch
            )
        )

        return packet
    }
}