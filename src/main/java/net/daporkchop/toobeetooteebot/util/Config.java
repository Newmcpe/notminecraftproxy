package net.daporkchop.toobeetooteebot.util;

import net.daporkchop.toobeetooteebot.TooBeeTooTeeBot;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

/**
 * settings
 * kek
 * ignore this
 * kek
 * again
 * kek
 * ok
 * i need to stop
 * saying kek
 * kek
 * note to self: make a meaningful header on this class
 * kek
 */
public class Config {
    public static String username;
    public static String password;
    public static boolean doAuth;

    public static String token;
    public static boolean doDiscord;

    public static String ip;
    public static int port;

    public static String clientId;

    public static boolean doWebsocket;
    public static int websocketPort;

    public static boolean doStatCollection;

    public static boolean processChat;

    public static boolean doAutoRespawn;
    public static boolean doAntiAFK;
    public static boolean doSpammer;
    public static int spamDelay;
    public static String[] spamMesages;

    public static boolean doServer;

    static {
        File configFile = new File(System.getProperty("user.dir") + File.separatorChar + "config.yml");
        if (!configFile.exists()) {
            URL inputUrl = TooBeeTooTeeBot.class.getResource("/config.yml");
            try {
                org.apache.commons.io.FileUtils.copyURLToFile(inputUrl, configFile);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
            }
        }
        YMLParser parser = new YMLParser(configFile);
        username = parser.getString("login.username", "Steve");
        password = parser.getString("login.password", "password");
        doAuth = parser.getBoolean("login.doAuthentication", false);
        token = parser.getString("discord.token", "");
        doDiscord = parser.getBoolean("discord.doDiscord", false);
        ip = parser.getString("client.hostIP", "mc.example.com");
        port = parser.getInt("client.hostPort", 25565);
        doWebsocket = parser.getBoolean("websocket.doWebsocket", false);
        websocketPort = parser.getInt("websocket.port", 8888);
        doStatCollection = parser.getBoolean("stats.doStats", false);
        processChat = parser.getBoolean("chat.doProcess", false);
        doAutoRespawn = parser.getBoolean("misc.autorespawn", true);
        doAntiAFK = parser.getBoolean("misc.antiafk", true);
        doSpammer = parser.getBoolean("chat.spam.doSpam", false);
        doServer = parser.getBoolean("server.doServer", true);
        spamDelay = parser.getInt("chat.spam.delay", 10000);

        List<String> spam = parser.getStringList("chat.spam.messages");
        spamMesages = spam.toArray(new String[spam.size()]);

        try {
            File clientId = new File(System.getProperty("user.dir") + File.separator + "clientId.txt");
            if (clientId.exists()) {
                Scanner scanner = new Scanner(clientId);
                Config.clientId = scanner.nextLine().trim();
                scanner.close();
            } else {
                PrintWriter writer = new PrintWriter(clientId, "UTF-8");
                writer.println(Config.clientId = UUID.randomUUID().toString());
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
