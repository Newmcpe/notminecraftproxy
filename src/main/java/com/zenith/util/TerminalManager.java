package com.zenith.util;

import com.zenith.Proxy;
import com.zenith.discord.command.CommandContext;
import com.zenith.discord.command.CommandSource;
import com.zenith.util.log.TerminalConsoleAppender;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.zenith.util.Constants.*;

public class TerminalManager {
    private LineReader lineReader;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Optional<Future<?>> executorFuture = Optional.empty();

    public TerminalManager() {
    }

    public void start() {
        if (executorFuture.isEmpty() && isRunning.compareAndSet(false, true)) {
            Terminal terminal = TerminalConsoleAppender.getTerminal();
            if (terminal != null && !(terminal instanceof DumbTerminal)) {
                DEFAULT_LOG.info("Starting Interactive Terminal...");
                this.lineReader = LineReaderBuilder.builder()
                        .terminal(terminal)
                        .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                        .option(LineReader.Option.INSERT_TAB, false)
                        .build();
                TerminalConsoleAppender.setReader(lineReader);
                executorFuture = Optional.of(SCHEDULED_EXECUTOR_SERVICE.submit(interactiveRunnable));
            } else {
                DEFAULT_LOG.warn("Unsupported Terminal. Interactive Terminal will not be started.");
            }
        }
    }

    public void stop() {
        if (executorFuture.isPresent()) {
            executorFuture.get().cancel(true);
            executorFuture = Optional.empty();
        }
        isRunning.set(false);
        TerminalConsoleAppender.setReader(null);
    }

    private final Runnable interactiveRunnable = () -> {
        while (true) {
            try {
                String line;
                try {
                    line = lineReader.readLine("> ");
                } catch (final EndOfFileException e) {
                    continue;
                }
                if (line == null) {
                    break;
                }
                handleTerminalCommand(line);
            } catch (final UserInterruptException e) {
                // ignore. terminal is closing
                TERMINAL_LOG.info("Exiting...");
                Proxy.getInstance().stop();
                break;
            } catch (final Exception e) {
                TERMINAL_LOG.error("Error while reading terminal input", e);
            }
        }
    };

    private void handleTerminalCommand(final String command) {
        switch (command) {
            case "exit":
                TERMINAL_LOG.info("Exiting...");
                Proxy.getInstance().stop();
                break;
            default:
                executeDiscordCommand(command);
        }
    }

    private void executeDiscordCommand(final String command) {
        if (CONFIG.interactiveTerminal.logToDiscord) logInputToDiscord(command);
        CommandContext commandContext = CommandContext.create(command, CommandSource.TERMINAL);
        COMMAND_MANAGER.execute(commandContext);
        logEmbedOutput(commandContext);
        if (CONFIG.interactiveTerminal.logToDiscord) logEmbedOutputToDiscord(commandContext);
        logMultiLineOutput(commandContext);
        if (CONFIG.interactiveTerminal.logToDiscord) logMultiLineOutputToDiscord(commandContext);
    }

    private void logMultiLineOutputToDiscord(CommandContext commandContext) {
        if (DISCORD_BOT.isRunning()) {
            commandContext.getMultiLineOutput().forEach(DISCORD_BOT::sendMessage);
        }
    }

    private void logEmbedOutputToDiscord(CommandContext commandContext) {
        if (DISCORD_BOT.isRunning()) {
            EmbedCreateSpec embedCreateSpec = commandContext.getEmbedBuilder().build();
            if (embedCreateSpec.isTitlePresent()) {
                DISCORD_BOT.sendEmbedMessage(embedCreateSpec);
            }
        }
    }

    private void logInputToDiscord(String command) {
        if (DISCORD_BOT.isRunning()) {
            DISCORD_BOT.sendEmbedMessage(EmbedCreateSpec.builder()
                            .title("Terminal Command Executed")
                            .description(command)
                    .build());
        }
    }

    private void logEmbedOutput(final CommandContext context) {
        // todo: handle formatted bold, italicized, or underlined text
        EmbedCreateSpec embedCreateSpec = context.getEmbedBuilder().build();
        if (!embedCreateSpec.isTitlePresent()) return;
        final AttributedStringBuilder output = new AttributedStringBuilder();
        if (embedCreateSpec.isColorPresent()) {
            final Color color = embedCreateSpec.color().get();
            output.style(AttributedStyle.DEFAULT.foreground(color.getRed(), color.getBlue(), color.getGreen()));
        }
        output.append("\n");
        output.append(embedCreateSpec.title().get());
        if (embedCreateSpec.isDescriptionPresent()) {
            output.append("\n");
            output.append(embedCreateSpec.description().get());
        }
        if (embedCreateSpec.isUrlPresent()) {
            output.append("\n");
            output.append(embedCreateSpec.url().get());
        }
        embedCreateSpec.fields().forEach(field -> {
            // todo: format fields as in discord where there can be multiple on a line
            output.append("\n");
            output.append(field.name());
            output.append(": ");
            output.append(field.value());
        });
        TERMINAL_LOG.info(unescape(output.toAnsi()));
    }

    private void logMultiLineOutput(CommandContext context) {
        context.getMultiLineOutput().forEach(TERMINAL_LOG::info);
    }

    private static String unescape(String s) {
        return s.replace("\\\\_", "_");
    }
}