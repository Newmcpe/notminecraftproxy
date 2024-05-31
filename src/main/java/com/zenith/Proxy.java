package com.zenith;

import ch.qos.logback.classic.LoggerContext;
import com.github.steveice10.mc.auth.data.GameProfile;
import com.zenith.event.proxy.*;
import com.zenith.feature.autoupdater.AutoUpdater;
import com.zenith.feature.autoupdater.GitAutoUpdater;
import com.zenith.feature.autoupdater.RestAutoUpdater;
import com.zenith.feature.queue.Queue;
import com.zenith.module.impl.AutoReconnect;
import com.zenith.network.client.Authenticator;
import com.zenith.network.client.ClientSession;
import com.zenith.network.server.CustomServerInfoBuilder;
import com.zenith.network.server.LanBroadcaster;
import com.zenith.network.server.ProxyServerListener;
import com.zenith.network.server.ServerConnection;
import com.zenith.network.server.handler.ProxyServerLoginHandler;
import com.zenith.util.ComponentSerializer;
import com.zenith.util.Config;
import com.zenith.util.FastArrayList;
import com.zenith.util.Wait;
import com.zenith.via.ZenithClientChannelInitializer;
import com.zenith.via.ZenithServerChannelInitializer;
import io.netty.util.ResourceLeakDetector;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import net.raphimc.minecraftauth.responsehandler.exception.MinecraftRequestException;
import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.ProxyInfo;
import org.geysermc.mcprotocollib.network.tcp.TcpConnectionManager;
import org.geysermc.mcprotocollib.network.tcp.TcpServer;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodecHelper;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundTabListPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Shared.*;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;


@Getter
public class Proxy {
    public static Proxy instance;
    public ClientSession client;
    public TcpServer server;
    public final Authenticator authenticator = new Authenticator();
    public byte[] serverIcon;
    public final AtomicReference<ServerConnection> currentPlayer = new AtomicReference<>();
    public final FastArrayList<ServerConnection> activeConnections = new FastArrayList<>(ServerConnection.class);
    private boolean inQueue = false;
    private int queuePosition = 0;
    @Setter
    private Instant connectTime;
    private Instant disconnectTime = Instant.now();
    @Getter
    private final AtomicBoolean loggingIn = new AtomicBoolean(false);
    @Setter
    private AutoUpdater autoUpdater;
    private LanBroadcaster lanBroadcaster;
    // might move to config and make the user deal with it when it changes
    private static final Duration twoB2tTimeLimit = Duration.ofHours(6);
    private TcpConnectionManager tcpManager;

    public static void main(String... args) {
        Locale.setDefault(Locale.ENGLISH);
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        if (System.getProperty("io.netty.leakDetection.level") == null)
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        instance = new Proxy();
        instance.start();
    }

    public static Proxy getInstance() {
        return instance;
    }

    public void initEventHandlers() {
        EVENT_BUS.subscribe(this,
                of(DisconnectEvent.class, this::handleDisconnectEvent),
                of(ConnectEvent.class, this::handleConnectEvent),
                of(StartQueueEvent.class, this::handleStartQueueEvent),
                of(QueuePositionUpdateEvent.class, this::handleQueuePositionUpdateEvent),
                of(QueueCompleteEvent.class, this::handleQueueCompleteEvent),
                of(ServerRestartingEvent.class, this::handleServerRestartingEvent),
                of(ServerPlayerConnectedEvent.class, this::handleServerPlayerConnectedEvent),
                of(ServerPlayerDisconnectedEvent.class, this::handleServerPlayerDisconnectedEvent)
        );
    }

    public void start() {
        loadConfig();
        loadLaunchConfig();
        DEFAULT_LOG.info("Starting ZenithProxy-{}", LAUNCH_CONFIG.version);
        initEventHandlers();
        try {
            if (CONFIG.debug.clearOldLogs) clearOldLogs();
            if (CONFIG.interactiveTerminal.enable) TERMINAL.start();
            MODULE.init();
            if (CONFIG.database.enabled) {
                DATABASE.start();
                DEFAULT_LOG.info("Started Databases");
            }
            if (CONFIG.discord.enable) {
                boolean err = false;
                try {
                    DISCORD.start();
                } catch (final Throwable e) {
                    err = true;
                    DISCORD_LOG.error("Failed starting discord bot", e);
                }
                if (!err) DISCORD_LOG.info("Started Discord Bot");
            }
            Queue.start();
            saveConfigAsync();
            MinecraftCodecHelper.useBinaryNbtComponentSerializer = CONFIG.debug.binaryNbtComponentSerializer;
            MinecraftConstants.CHUNK_SECTION_COUNT_PROVIDER = CACHE.getSectionCountProvider();
            this.tcpManager = new TcpConnectionManager();
            this.startServer();
            CACHE.reset(true);
            EXECUTOR.scheduleAtFixedRate(this::serverHealthCheck, 1L, 5L, TimeUnit.MINUTES);
            EXECUTOR.scheduleAtFixedRate(this::tablistUpdate, 20L, 3L, TimeUnit.SECONDS);
            EXECUTOR.scheduleAtFixedRate(this::maxPlaytimeTick, CONFIG.client.maxPlaytimeReconnectMins, 1L, TimeUnit.MINUTES);
            if (CONFIG.server.enabled && CONFIG.server.ping.favicon)
                EXECUTOR.submit(this::updateFavicon);
            boolean connected = false;
            if (CONFIG.client.autoConnect && !this.isConnected()) {
                this.connectAndCatchExceptions();
                connected = true;
            }
            if (!connected && CONFIG.autoUpdater.shouldReconnectAfterAutoUpdate) {
                CONFIG.autoUpdater.shouldReconnectAfterAutoUpdate = false;
                saveConfigAsync();
                if (!CONFIG.client.extra.utility.actions.autoDisconnect.autoClientDisconnect && !this.isConnected()) {
                    this.connectAndCatchExceptions();
                    connected = true;
                }
            }
            if (LAUNCH_CONFIG.auto_update) {
                if (LAUNCH_CONFIG.release_channel.equals("git")) autoUpdater = new GitAutoUpdater();
                else autoUpdater = new RestAutoUpdater();
                autoUpdater.start();
                DEFAULT_LOG.info("Started AutoUpdater");
            }
            DEFAULT_LOG.info("ZenithProxy started!");
            if (!connected)
                DEFAULT_LOG.info("Use the `connect` command to log in!");
            Wait.waitSpinLoop();
        } catch (Exception e) {
            DEFAULT_LOG.error("", e);
        } finally {
            DEFAULT_LOG.info("Shutting down...");
            if (this.server != null) this.server.close(true);
            saveConfig();
        }
    }

    private static void clearOldLogs() {
        try (Stream<Path> walk = Files.walk(Path.of("log/"))) {
            walk.filter(path -> path.toString().endsWith(".zip")).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (final IOException e) {
                    DEFAULT_LOG.error("Error deleting old log file", e);
                }
            });
        } catch (final IOException e) {
            DEFAULT_LOG.error("Error deleting old log file", e);
        }
    }

    private void serverHealthCheck() {
        if (!CONFIG.server.enabled || !CONFIG.server.healthCheck) return;
        if (server != null && server.isListening()) return;
        SERVER_LOG.error("Server is not listening! Is another service on this port?");
        this.startServer();
        EXECUTOR.schedule(() -> {
            if (server == null || !server.isListening()) {
                SERVER_LOG.error("Server is not listening and unable to quick restart, performing full restart...");
                CONFIG.autoUpdater.shouldReconnectAfterAutoUpdate = true;
                stop();
            }
        }, 30, TimeUnit.SECONDS);
    }

    private void maxPlaytimeTick() {
        if (CONFIG.client.maxPlaytimeReconnect) {
            CLIENT_LOG.info("Max playtime minutes reached: {}, reconnecting...", CONFIG.client.maxPlaytimeReconnectMins);
            disconnect(SYSTEM_DISCONNECT);
            MODULE.get(AutoReconnect.class).cancelAutoReconnect();
            connect();
        }
    }

    private void tablistUpdate() {
        var playerConnection = currentPlayer.get();
        if (!this.isConnected() || playerConnection == null) return;
        if (!playerConnection.isLoggedIn()) return;
        long lastUpdate = CACHE.getTabListCache().getLastUpdate();
        if (lastUpdate < System.currentTimeMillis() - 3000) {
            playerConnection.sendAsync(new ClientboundTabListPacket(CACHE.getTabListCache().getHeader(), CACHE.getTabListCache().getFooter()));
            CACHE.getTabListCache().setLastUpdate(System.currentTimeMillis());
        }
    }

    public void stop() {
        DEFAULT_LOG.info("Shutting Down...");
        try {
            CompletableFuture.runAsync(() -> {
                if (nonNull(this.client)) this.client.disconnect(MinecraftConstants.SERVER_CLOSING_MESSAGE);
                MODULE.get(AutoReconnect.class).cancelAutoReconnect();
                stopServer();
                tcpManager.close();
                saveConfig();
                int count = 0;
                while (!DISCORD.isMessageQueueEmpty() && count++ < 10) {
                    Wait.waitMs(100);
                }
                DISCORD.stop(true);
            }).get(10L, TimeUnit.SECONDS);
        } catch (final Exception e) {
            DEFAULT_LOG.error("Error shutting down gracefully", e);
        } finally {
            try {
                ((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
            } finally {
                System.exit(0);
            }
        }
    }

    public void disconnect() {
        disconnect(MANUAL_DISCONNECT);
    }

    public void disconnect(final String reason, final Throwable cause) {
        if (this.isConnected()) {
            if (CONFIG.debug.kickDisconnect) this.kickDisconnect(reason, cause);
            else this.client.disconnect(reason, cause);
        }
    }

    public void disconnect(final String reason) {
        if (this.isConnected()) {
            if (CONFIG.debug.kickDisconnect) this.kickDisconnect(reason, null);
            else this.client.disconnect(reason);
        }
    }

    public void kickDisconnect(final String reason, final Throwable cause) {
        if (!isConnected()) return;
        var client = this.client;
        try {
            // out of order timestamp causes server to kick us
            // must send direct to avoid our mitigation in the outgoing packet handler
            client.sendDirect(new ServerboundChatPacket("", -1L, 0L, null, 0, BitSet.valueOf(new byte[20])))
                    .get();
        } catch (final Exception e) {
            CLIENT_LOG.error("Error performing kick disconnect", e);
        }
        // note: this will occur before the server sends us back a disconnect packet, but before our channel close is received by the server
        client.disconnect(reason, cause);
    }

    public void connectAndCatchExceptions() {
        try {
            this.connect();
        } catch (final Exception e) {
            DEFAULT_LOG.error("Error connecting", e);
        }
    }

    /**
     * @throws IllegalStateException if already connected
     */
    public synchronized void connect() {
        connect(CONFIG.client.server.address, CONFIG.client.server.port);
    }

    public synchronized void connect(final String address, final int port) {
        if (this.isConnected()) throw new IllegalStateException("Already connected!");
        this.connectTime = Instant.now();
        final MinecraftProtocol minecraftProtocol;
        try {
            EVENT_BUS.postAsync(new StartConnectEvent());
            minecraftProtocol = this.logIn();
        } catch (final Exception e) {
            EVENT_BUS.post(new ProxyLoginFailedEvent());
            var connections = getActiveConnections().getArray();
            for (int i = 0; i < connections.length; i++) {
                var connection = connections[i];
                connection.disconnect("Login failed");
            }
            EXECUTOR.schedule(() -> {
                EVENT_BUS.post(new DisconnectEvent(LOGIN_FAILED));
            }, 1L, TimeUnit.SECONDS);
            return;
        }
        CLIENT_LOG.info("Connecting to {}:{}...", address, port);
        this.client = new ClientSession(address, port, CONFIG.client.bindAddress, minecraftProtocol, getClientProxyInfo(), tcpManager);
        if (Objects.equals(address, "connect.2b2t.org"))
            this.client.setFlag(BuiltinFlags.ATTEMPT_SRV_RESOLVE, false);
        this.client.setReadTimeout(CONFIG.client.timeout.enable ? CONFIG.client.timeout.seconds : 0);
        this.client.setFlag(BuiltinFlags.PRINT_DEBUG, true);
        this.client.setFlag(MinecraftConstants.CLIENT_CHANNEL_INITIALIZER, ZenithClientChannelInitializer.FACTORY);
        this.client.connect(true);
    }

    @Nullable
    private static ProxyInfo getClientProxyInfo() {
        ProxyInfo proxyInfo = null;
        if (CONFIG.client.connectionProxy.enabled) {
            if (!CONFIG.client.connectionProxy.user.isEmpty() || !CONFIG.client.connectionProxy.password.isEmpty())
                proxyInfo = new ProxyInfo(CONFIG.client.connectionProxy.type,
                        new InetSocketAddress(CONFIG.client.connectionProxy.host,
                                CONFIG.client.connectionProxy.port),
                        CONFIG.client.connectionProxy.user,
                        CONFIG.client.connectionProxy.password);
            else proxyInfo = new ProxyInfo(CONFIG.client.connectionProxy.type,
                    new InetSocketAddress(CONFIG.client.connectionProxy.host,
                            CONFIG.client.connectionProxy.port));
        }
        return proxyInfo;
    }

    public boolean isConnected() {
        return this.client != null && this.client.isConnected();
    }

    @SneakyThrows
    public synchronized void startServer() {
        if (this.server != null && this.server.isListening())
            throw new IllegalStateException("Server already started!");
        if (!CONFIG.server.enabled) return;
        this.serverIcon = getClass().getClassLoader().getResourceAsStream("servericon.png").readAllBytes();
        var address = CONFIG.server.bind.address;
        var port = CONFIG.server.bind.port;
        SERVER_LOG.info("Starting server on {}:{}...", address, port);
        this.server = new TcpServer(address, port, MinecraftProtocol::new, tcpManager);
        this.server.setGlobalFlag(MinecraftConstants.SERVER_CHANNEL_INITIALIZER, ZenithServerChannelInitializer.FACTORY);
        this.server.setGlobalFlag(MinecraftConstants.VERIFY_USERS_KEY, CONFIG.server.verifyUsers);
        var serverInfoBuilder = new CustomServerInfoBuilder();
        this.server.setGlobalFlag(MinecraftConstants.SERVER_INFO_BUILDER_KEY, serverInfoBuilder);
        if (this.lanBroadcaster == null && CONFIG.server.ping.lanBroadcast) {
            this.lanBroadcaster = new LanBroadcaster(serverInfoBuilder);
            lanBroadcaster.start();
        }
        this.server.setGlobalFlag(MinecraftConstants.SERVER_LOGIN_HANDLER_KEY, new ProxyServerLoginHandler());
        this.server.setGlobalFlag(MinecraftConstants.AUTOMATIC_KEEP_ALIVE_MANAGEMENT, true);
        this.server.addListener(new ProxyServerListener());
        this.server.bind(false);
    }

    public synchronized void stopServer() {
        SERVER_LOG.info("Stopping server...");
        if (this.server != null && this.server.isListening()) this.server.close(true);
        if (this.lanBroadcaster != null) {
            this.lanBroadcaster.stop();
            this.lanBroadcaster = null;
        }
    }

    public @NonNull MinecraftProtocol logIn() {
        loggingIn.set(true);
        AUTH_LOG.info("Logging in {}...", CONFIG.authentication.username);
        MinecraftProtocol minecraftProtocol = null;
        for (int tries = 0; tries < 3; tries++) {
            minecraftProtocol = retrieveLoginTaskResult(loginTask());
            if (minecraftProtocol != null || !loggingIn.get()) break;
            AUTH_LOG.warn("Failed login attempt " + (tries + 1));
            Wait.wait((int) (3 + (Math.random() * 7.0)));
        }
        if (!loggingIn.get()) throw new RuntimeException("Login Cancelled");
        loggingIn.set(false);
        if (minecraftProtocol == null) throw new RuntimeException("Auth failed");
        var username = minecraftProtocol.getProfile().getName();
        var uuid = minecraftProtocol.getProfile().getId();
        AUTH_LOG.info("Logged in as {} [{}].", username, uuid);
        if (CONFIG.server.extra.whitelist.autoAddClient)
            if (PLAYER_LISTS.getWhitelist().add(username, uuid))
                SERVER_LOG.info("Auto added {} [{}] to whitelist", username, uuid);
        EXECUTOR.execute(this::updateFavicon);
        return minecraftProtocol;
    }

    public Future<MinecraftProtocol> loginTask() {
        return EXECUTOR.submit(() -> {
            try {
                return this.authenticator.login();
            } catch (final Exception e) {
                if (e instanceof InterruptedException) {
                    return null;
                }
                CLIENT_LOG.error("Login failed", e);
                if (e instanceof MinecraftRequestException mre) {
                    if (mre.getResponse().getStatusCode() == 404) {
                        AUTH_LOG.error("[Help] Log into the account with the vanilla MC launcher and join a server. Then try again with ZenithProxy.");
                    }
                }
                return null;
            }
        });
    }

    public MinecraftProtocol retrieveLoginTaskResult(Future<MinecraftProtocol> loginTask) {
        try {
            var maxWait = CONFIG.authentication.accountType == Config.Authentication.AccountType.MSA ? 10 : 300;
            for (int currentWait = 0; currentWait < maxWait; currentWait++) {
                if (loginTask.isDone()) break;
                if (!loggingIn.get()) {
                    loginTask.cancel(true);
                    return null;
                }
                Wait.wait(1);
            }
            return loginTask.get(1L, TimeUnit.SECONDS);
        } catch (Exception e) {
            loginTask.cancel(true);
            return null;
        }
    }

    public URL getAvatarURL(UUID uuid) {
        return getAvatarURL(uuid.toString().replace("-", ""));
    }

    public URL getAvatarURL(String playerName) {
        try {
            return URI.create(String.format("https://minotar.net/helm/%s/64", playerName)).toURL();
        } catch (MalformedURLException e) {
            SERVER_LOG.error("Failed to get avatar URL for player: " + playerName, e);
            throw new UncheckedIOException(e);
        }
    }

    // returns true if we were previously trying to log in
    public boolean cancelLogin() {
        return this.loggingIn.getAndSet(false);
    }

    public List<ServerConnection> getSpectatorConnections() {
        var connections = getActiveConnections().getArray();
        if (connections.length == 0) return Collections.emptyList();
        if (connections.length == 1 && hasActivePlayer()) return Collections.emptyList();
        final List<ServerConnection> result = new ArrayList<>(hasActivePlayer() ? connections.length - 1 : connections.length);
        for (int i = 0; i < connections.length; i++) {
            var connection = connections[i];
            if (connection.isSpectator()) {
                result.add(connection);
            }
        }
        return result;
    }

    public boolean hasActivePlayer() {
        ServerConnection player = this.currentPlayer.get();
        return player != null && player.isLoggedIn();
    }

    public @Nullable ServerConnection getActivePlayer() {
        ServerConnection player = this.currentPlayer.get();
        if (player != null && player.isLoggedIn()) return player;
        else return null;
    }


    public void kickNonWhitelistedPlayers() {
        var connections = Proxy.getInstance().getActiveConnections().getArray();
        for (int i = 0; i < connections.length; i++) {
            var connection = connections[i];
            if (connection.getProfileCache().getProfile() == null) continue;
            if (PLAYER_LISTS.getWhitelist().contains(connection.getProfileCache().getProfile())) continue;
            if (PLAYER_LISTS.getSpectatorWhitelist().contains(connection.getProfileCache().getProfile()) && connection.isSpectator())
                continue;
            connection.disconnect("Not whitelisted");
        }
    }

    public void updateFavicon() {
        if (!CONFIG.authentication.username.equals("Unknown")) { // else use default icon
            try {
                final GameProfile profile = CACHE.getProfileCache().getProfile();
                if (profile != null && profile.getId() != null) {
                    // do uuid lookup
                    final UUID uuid = profile.getId();
                    this.serverIcon = MINOTAR.getAvatar(uuid).or(() -> CRAFTHEAD.getAvatar(uuid))
                            .orElseThrow(() -> new IOException("Unable to download server icon for \"" + uuid + "\""));
                } else {
                    // do username lookup
                    final String username = CONFIG.authentication.username;
                    this.serverIcon = MINOTAR.getAvatar(username).or(() -> CRAFTHEAD.getAvatar(username))
                            .orElseThrow(() -> new IOException("Unable to download server icon for \"" + username + "\""));
                }
                if (DISCORD.isRunning()) {
                    if (CONFIG.discord.manageNickname)
                        DISCORD.setBotNickname(CONFIG.authentication.username + " | ZenithProxy");
                    if (CONFIG.discord.manageDescription) DISCORD.setBotDescription(
                            """
                                    ZenithProxy %s
                                    **Official Discord**:
                                      https://discord.gg/nJZrSaRKtb
                                    **Github**:
                                      https://github.com/rfresh2/ZenithProxy
                                    """.formatted(LAUNCH_CONFIG.version));
                }
            } catch (final Throwable e) {
                SERVER_LOG.error("Failed updating favicon");
                SERVER_LOG.debug("Failed updating favicon", e);
            }
        }
        if (DISCORD.isRunning() && this.serverIcon != null)
            if (CONFIG.discord.manageProfileImage) DISCORD.updateProfileImage(this.serverIcon);
    }

    public void handleDisconnectEvent(DisconnectEvent event) {
        CACHE.reset(true);
        this.disconnectTime = Instant.now();
        this.inQueue = false;
        this.queuePosition = 0;
        TPS.reset();
        if (!DISCORD.isRunning()
                && event.reason().startsWith("You have lost connection")) {
            if (event.onlineDuration().toSeconds() >= 0L
                    && event.onlineDuration().toSeconds() <= 1L) {
                CLIENT_LOG.warn("""
                        You have likely been kicked for reaching the 2b2t non-prio account IP limit.
                        Consider configuring a connection proxy with the `clientConnection` command.
                        Or migrate ZenithProxy instances to multiple hosts/IP's.
                        """);
            } else if (event.wasInQueue() && event.queuePosition() <= 1) {
                CLIENT_LOG.warn("""
                        You have likely been kicked due to being IP banned by 2b2t.
                                                      
                        To check, try connecting and waiting through queue with the same account from a different IP.
                        """);
            }
        }
    }

    public void handleConnectEvent(ConnectEvent event) {
        this.connectTime = Instant.now();
    }

    public void handleStartQueueEvent(StartQueueEvent event) {
        this.inQueue = true;
        this.queuePosition = 0;
    }

    public void handleQueuePositionUpdateEvent(QueuePositionUpdateEvent event) {
        this.queuePosition = event.position();
    }

    public void handleQueueCompleteEvent(QueueCompleteEvent event) {
        this.inQueue = false;
        this.connectTime = Instant.now();
    }

    public void handleServerRestartingEvent(ServerRestartingEvent event) {
        if (isNull(getCurrentPlayer().get())) {
            EXECUTOR.schedule(() -> {
                if (isNull(getCurrentPlayer().get()))
                    disconnect(SERVER_RESTARTING);
            }, ((int) (Math.random() * 20)), TimeUnit.SECONDS);
        }
    }

    public void handleServerPlayerConnectedEvent(ServerPlayerConnectedEvent event) {
        if (!CONFIG.client.extra.chat.showConnectionMessages) return;
        var serverConnection = getCurrentPlayer().get();
        if (nonNull(serverConnection) && serverConnection.isLoggedIn())
            serverConnection.send(new ClientboundSystemChatPacket(ComponentSerializer.minedown("&b" + event.playerEntry().getName() + "&r&e connected"), false));
    }

    public void handleServerPlayerDisconnectedEvent(ServerPlayerDisconnectedEvent event) {
        if (!CONFIG.client.extra.chat.showConnectionMessages) return;
        var serverConnection = getCurrentPlayer().get();
        if (nonNull(serverConnection) && serverConnection.isLoggedIn())
            serverConnection.send(new ClientboundSystemChatPacket(ComponentSerializer.minedown("&b" + event.playerEntry().getName() + "&r&e disconnected"), false));
    }

    public boolean isOn2b2t() {
        return false;
    }

    public boolean isPrio() {
        return false;
    }

    public boolean isOnlineOn2b2tForAtLeastDuration(Duration duration) {
        return false;
    }

    public boolean isOnlineForAtLeastDuration(Duration duration) {
        return false;
    }
}
