package com.zenith.network.client;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.ProxyInfo;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.network.tcp.TcpConnectionManager;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;

import java.io.IOException;

import static com.zenith.Shared.CLIENT_LOG;

@Getter
@Setter
public class ClientSession extends TcpClientSession {
    private final EventLoop eventLoop = new DefaultEventLoop(new DefaultThreadFactory("Client Event Loop", true));
    protected boolean serverProbablyOff;
    protected long ping = 0L;
    protected long lastPingId = 0L;
    protected long lastPingSentTime = 0L;

    private boolean inQueue = false;
    private int lastQueuePosition = Integer.MAX_VALUE;
    // in game
    private boolean online = false;
    private boolean disconnected = true;
    private static final ClientTickManager clientTickManager = new ClientTickManager();

    public ClientSession(String host, int port, String bindAddress, MinecraftProtocol protocol, ProxyInfo proxyInfo, TcpConnectionManager tcpManager) {
        super(host, port, bindAddress, 0, protocol, proxyInfo, tcpManager);
        this.addListener(new ClientListener(this));
    }

    public ClientSession(String host, int port, String bindAddress, MinecraftProtocol protocol, TcpConnectionManager tcpManager) {
        this(host, port, bindAddress, protocol, null, tcpManager);
    }

    public void setOnline(final boolean online) {
        this.online = online;
        if (online) clientTickManager.startClientTicks();
        else clientTickManager.stopClientTicks();
    }

    public void setDisconnected(final boolean disconnected) {
        this.disconnected = disconnected;
        setOnline(false);
    }

    @Override
    public void disconnect(Component reason, Throwable cause) {
        super.disconnect(reason, cause);
        serverProbablyOff = false;
        if (cause == null) {
            serverProbablyOff = true;
        } else if (cause instanceof IOException)    {
            CLIENT_LOG.error("Error during client disconnect", cause);
        } else {
            CLIENT_LOG.error("", cause);
        }
        this.online = false;
    }

    @Override
    public void connect(boolean wait) {
        super.connect(wait);
    }

    public EventLoop getClientEventLoop() {
        return eventLoop;
    }
}
