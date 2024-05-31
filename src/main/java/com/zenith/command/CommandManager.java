package com.zenith.command;

import com.google.common.base.Suppliers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.zenith.command.brigadier.CaseInsensitiveLiteralCommandNode;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.command.brigadier.CommandSource;
import com.zenith.command.impl.*;
import com.zenith.command.util.BrigadierToMCProtocolLibConverter;
import lombok.Getter;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.zenith.Shared.*;
import static java.util.Arrays.asList;

@Getter
public class CommandManager {
    private final List<Command> commandsList = asList(
            new ActionLimiterCommand(),
            new ActiveHoursCommand(),
            new AntiAFKCommand(),
            new AntiKickCommand(),
            new AntiLeakCommand(),
            new AuthCommand(),
            new AutoArmorCommand(),
            new AutoDisconnectCommand(),
            new AutoEatCommand(),
            new AutoFishCommand(),
            new AutoReconnectCommand(),
            new AutoReplyCommand(),
            new AutoRespawnCommand(),
            new AutoTotemCommand(),
            new AutoUpdateCommand(),
            new ChatHistoryCommand(),
            new ChatRelayCommand(),
            new ClientConnectionCommand(),
            new CommandConfigCommand(),
            new ConnectCommand(),
            new DatabaseCommand(),
            new DebugCommand(),
            new DisconnectCommand(),
            new DiscordManageCommand(),
            new DisplayCoordsCommand(),
            new ESPCommand(),
            new ExtraChatCommand(),
            new FriendCommand(),
            new HelpCommand(),
            new IgnoreCommand(),
            new InventoryCommand(),
            new KickCommand(),
            new KillAuraCommand(),
            new MapCommand(),
            new PlaytimeCommand(),
            new PrioCommand(),
            new ProxyClientConnectionCommand(),
            new QueueStatusCommand(),
            new QueueWarningCommand(),
            new RaycastCommand(),
            new ReconnectCommand(),
            new ReleaseChannelCommand(),
            new ReplayCommand(),
            new RespawnCommand(),
            new SeenCommand(),
            new SendMessageCommand(),
            new ServerCommand(),
            new ServerConnectionCommand(),
            new SkinCommand(),
            new SpammerCommand(),
            new SpectatorCommand(),
            new SpookCommand(),
            new StalkCommand(),
            new StatsCommand(),
            new StatusCommand(),
            new TablistCommand(),
            new ThemeCommand(),
            new UpdateCommand(),
            new ViaVersionCommand(),
            new SpinbotCommand(),
            new VisualRangeCommand(),
            new WhitelistCommand()
    );
    private final CommandDispatcher<CommandContext> dispatcher;
    private final Supplier<CommandNode[]> MCProtocolLibCommandNodesSupplier;

    public CommandManager() {
        this.dispatcher = new CommandDispatcher<>();
        registerCommands();
        this.MCProtocolLibCommandNodesSupplier = Suppliers.memoize(
                // should be safe to cache as we don't mutate zenith commands after startup
                () -> BrigadierToMCProtocolLibConverter.convertNodesToMCProtocolLibNodes(this.dispatcher));
    }

    public void registerCommands() {
        commandsList.forEach(this::registerCommand);
    }

    public List<Command> getCommands() {
        return commandsList;
    }

    public List<Command> getCommands(final CommandCategory category) {
        return commandsList.stream()
                .filter(command -> category == CommandCategory.ALL || command.commandUsage().getCategory() == category)
                .toList();
    }

    private void registerCommand(final Command command) {
        final LiteralCommandNode<CommandContext> node = dispatcher.register(command.register());
        command.commandUsage().getAliases().forEach(alias -> dispatcher.register(command.redirect(alias, node)));
    }

    public void execute(final CommandContext context, final ParseResults<CommandContext> parseResults) {
        try {
            executeWithHandlers(context, parseResults);
        } catch (final CommandSyntaxException e) {
            // fall through
            // errors handled by delegate
            // and if this not a matching root command we want to fallback to original commands
        }
        saveConfigAsync();
    }

    public void execute(final CommandContext context) {
        final ParseResults<CommandContext> parse = parse(context);
        execute(context, parse);
    }

    public ParseResults<CommandContext> parse(final CommandContext context) {
        return this.dispatcher.parse(downcaseFirstWord(context.getInput()), context);
    }

    public boolean hasCommandNode(final ParseResults<CommandContext> parse) {
        return parse.getContext().getNodes().stream().anyMatch(node -> node.getNode() instanceof CaseInsensitiveLiteralCommandNode);
    }

    private String downcaseFirstWord(final String sentence) {
        List<String> words = asList(sentence.split(" "));
        if (words.size() > 1) {
            return words.getFirst().toLowerCase() + words.stream().skip(1).collect(Collectors.joining(" ", " ", ""));
        } else {
            return sentence.toLowerCase();
        }
    }

    private int executeWithHandlers(final CommandContext context, final ParseResults<CommandContext> parse) throws CommandSyntaxException {
        var commandNodeOptional = parse.getContext()
                .getNodes()
                .stream()
                .findFirst()
                .map(ParsedCommandNode::getNode)
                .filter(node -> node instanceof CaseInsensitiveLiteralCommandNode)
                .map(node -> ((CaseInsensitiveLiteralCommandNode<CommandContext>) node));
        var errorHandler = commandNodeOptional.flatMap(CaseInsensitiveLiteralCommandNode::getErrorHandler);
        var successHandler = commandNodeOptional.flatMap(CaseInsensitiveLiteralCommandNode::getSuccessHandler);

        if (!parse.getExceptions().isEmpty() || parse.getReader().canRead()) {
            errorHandler.ifPresent(handler -> handler.handle(parse.getExceptions(), context));
            return -1;
        }
        dispatcher.setConsumer((commandContext, success, result) -> {
            if (success) successHandler.ifPresent(handler -> handler.handle(context));
            else errorHandler.ifPresent(handler -> handler.handle(parse.getExceptions(), context));
        });

        return dispatcher.execute(parse);
    }

    public String getCommandPrefix(final CommandSource source) {
        // todo: tie this to each output instead because multiple outputs can be used regardless of source
        //  insert a string that gets replaced?
        //      abstract the embed builder output to a mutable intermediary?
        return switch (source) {
            case DISCORD -> CONFIG.discord.prefix;
            case IN_GAME_PLAYER -> CONFIG.inGameCommands.slashCommands ? "/" : CONFIG.inGameCommands.prefix;
            case TERMINAL -> "";
        };
    }

    public List<String> getCommandCompletions(final String input) {
        final ParseResults<CommandContext> parse = this.dispatcher.parse(downcaseFirstWord(input), CommandContext.create(input, CommandSource.TERMINAL));
        try {
            var suggestions = this.dispatcher.getCompletionSuggestions(parse).get(2L, TimeUnit.SECONDS);
            return suggestions.getList().stream()
                    .map(Suggestion::getText)
                    .toList();
        } catch (final Exception e) {
            TERMINAL_LOG.warn("Failed to get command completions for input: " + input);
            return List.of();
        }
    }
}
