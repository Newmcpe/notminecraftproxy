package com.zenith.feature.spinbot

import com.zenith.Proxy
import com.zenith.Shared
import com.zenith.module.Module
import com.zenith.network.client.ClientSession
import com.zenith.network.registry.PacketHandlerCodec
import com.zenith.network.registry.PacketHandlerStateCodec
import com.zenith.network.registry.ZenithHandlerCodec
import org.geysermc.mcprotocollib.protocol.data.ProtocolState
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket
import java.util.*
import kotlin.concurrent.fixedRateTimer

class SpinBot : Module() {

    private var currentYaw = 0.0
    private var currentPitch = 0

    private lateinit var timerTask: Timer
    var codec: PacketHandlerCodec = PacketHandlerCodec
        .builder()
        .setId("spinbot")
        .state(
            ProtocolState.GAME, PacketHandlerStateCodec.builder<ClientSession>()
                .registerOutbound(ServerboundPlayerActionPacket::class.java, FixServerboundPlayerActionPacketHandler())
                .build()
        )
        .build()

    override fun subscribeEvents() {}

    override fun shouldBeEnabled(): Boolean = Shared.CONFIG.server.extra.spinbot.enable

    override fun onEnable() {
        ZenithHandlerCodec.CLIENT_REGISTRY.register(codec)
        timerTask =
            fixedRateTimer("spinbot", period = Shared.CONFIG.server.extra.spinbot.delay.toLong(), daemon = true) {
                if (Proxy.getInstance().isConnected) {
                    val player = Proxy.getInstance().client ?: return@fixedRateTimer
                    currentPitch = -currentPitch
                    player.send(
                        ServerboundMovePlayerRotPacket(
                            true,
                            newYaw(),
                            newPitch(),
                        )
                    )
                } else {
                    Thread.sleep(10000)
                }
            }

    }

    override fun onDisable() {
        ZenithHandlerCodec.CLIENT_REGISTRY.unregister(codec)
        timerTask.cancel()
    }

    private fun newYaw(): Float {
        currentYaw += Shared.CONFIG.server.extra.spinbot.speed
        if (currentYaw > 180) {
            currentYaw = -180.0
        }
        return currentYaw.toFloat()
    }

    private fun newPitch(): Float {
        currentPitch += Shared.CONFIG.server.extra.spinbot.speed
        if (currentPitch > 90) {
            currentPitch = -90
        }
        return currentPitch.toFloat()
    }
}
