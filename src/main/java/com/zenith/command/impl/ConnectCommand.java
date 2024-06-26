package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;

import static com.zenith.Shared.EXECUTOR;
import static java.util.Arrays.asList;

public class ConnectCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.simpleAliases(
            "connect",
            CommandCategory.CORE,
            "Connects ZenithProxy to the destination MC server",
            asList("c")
        );
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("connect").executes(c -> {
            if (Proxy.getInstance().isConnected()) {
                c.getSource().getEmbed()
                        .title("Already Connected!");
            } else {
                EXECUTOR.execute(Proxy.getInstance()::connectAndCatchExceptions);
            }
        });
    }
}
