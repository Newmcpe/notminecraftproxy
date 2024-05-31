package com.zenith.command.impl

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType.integer
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.zenith.Shared
import com.zenith.command.Command
import com.zenith.command.CommandUsage
import com.zenith.command.brigadier.CommandCategory
import com.zenith.command.brigadier.CommandContext
import com.zenith.command.brigadier.ToggleArgumentType.getToggle
import com.zenith.command.brigadier.ToggleArgumentType.toggle
import com.zenith.feature.spinbot.SpinBot

class SpinbotCommand : Command() {
    override fun commandUsage(): CommandUsage {
        return CommandUsage.args(
            "spinbot", CommandCategory.MODULE, """
                |Spinbot module settings
            """, listOf(
                "toggle", "speed", "delay"
            )
        )
    }

    override fun register(): LiteralArgumentBuilder<CommandContext> {
        return command("spinbot")
            .then(argument("toggle", toggle()).executes { c ->
                Shared.CONFIG.server.extra.spinbot.enable = getToggle(c, "toggle")
                Shared.MODULE.get(SpinBot::class.java).syncEnabledFromConfig()

                c.source.embed.title("Spinbot").description("Toggled spinbot").primaryColor()
                OK
            })
            .then(literal("speed").then(argument("speed", integer(0, 10000)).executes { c ->
                val speed = IntegerArgumentType.getInteger(c, "speed")
                c.source.embed.title("Spinbot").description("Set speed to $speed").primaryColor()
                Shared.CONFIG.server.extra.spinbot.speed = speed
                OK
            }))
            .then(literal("delay").then(argument("delay", integer(0, 10000)).executes { c ->
                val delay = IntegerArgumentType.getInteger(c, "delay")
                c.source.embed.title("Spinbot").description("Set delay to $delay").primaryColor()
                Shared.CONFIG.server.extra.spinbot.delay = delay
                OK
            }))
    }
}