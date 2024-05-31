# NotMinecraftProxy

<p align="center">
  <img src="https://img.shields.io/badge/MC-1.20.4-brightgreen.svg" alt="Minecraft"/>
</p>


Minecraft proxy/bot

Unlike a traditional MC bot, you can login to it as a normal MC server and control the account.

The primary purpose is to have accounts always online in-game and securely shared by multiple people.

This project is also used to support the [2b2t.vc API](https://api.2b2t.vc) and [Discord Bot](https://bot.2b2t.vc).

<details>
    <summary>What is a proxy?</summary>

    This proxy itself consists of two components:
    1. A Minecraft Server ("Proxy Server")
    2. A Minecraft Client ("Proxy Client")

    Players use a Minecraft client to connect to the Proxy Server as you would a normal MC server.
    The Proxy Client connects to a destination MC server (i.e. 2b2t.org).
    The Player's packets to the Proxy Server get forwarded to the Proxy Client which 
    forwards them to the destination MC server.
    
    Player MC Client -> Proxy Server -> Proxy Client -> MC Server
    
    When no Player Client is connected the Proxy Client will act 
    as a bot: moving around, chatting, etc.
</details>


# Features

* High performance and efficiency on minimal hardware, <300MB RAM per java instance or <150MB on linux.
* Integrated ViaVersion
  * Can connect to (almost) any MC server and players can connect with (almost) any MC client
* Secure Whitelist system - share MC accounts without sharing passwords
* Extensive Discord Bot integration for management and monitoring
    * Chat relay
    * Customizable pings, alerts, and notifications. e.g. Player in visual range alerts
* Command System - Discord, In-game, and Terminal
  * 25+ commands and modules to configure every feature
* Spectator mode
  * Multiple players can connect to the proxy and spectate the player
* Advanced AntiAFK with full player movement simulation
* Integrated ReplayMod Recording
* Modules including AutoEat, AutoDisconnect, AutoReconnect, AutoRespawn, AutoTotem, KillAura, Spammer, AutoReply
* Many, many, more features.

# Getting Started

## Setup and Download

### System Requirements

1. Linux, Windows, or Mac computer.
2. Java 21+ (Not required for `linux` release channel on supported CPU)
    * The ZenithProxy launcher will install Java 21 automatically if not already installed

### Launcher

1. Download [the launcher zip](https://github.com/rfresh2/ZenithProxy/releases/launcher-v3) for your system
    * For other systems, download the Python version (Universal). Requires [Python 3.10+](https://www.python.org/downloads/)
2. Unzip the file.
3. Double click or run the launcher executable in a terminal:
   * Windows: `.\launch.exe`
   * Linux/Mac: `./launch`
   * (Terminal Only) Python Universal: `./launch.sh` (Linux/Mac) or `.\launch.bat` (Windows)

<details>
    <summary>How do I download a file from a Linux terminal?</summary>

* Use [wget](https://linuxize.com/post/wget-command-examples/#how-to-download-a-file-with-wget) in the terminal
* Example: `wget https://github.com/rfresh2/ZenithProxy/releases/download/launcher-v3/ZenithProxy-launcher-linux-amd64.zip`
</details>

<details> 
<summary>Recommended unzip tools</summary>

* Windows: [7zip](https://www.7-zip.org/download.html)
* Linux: [unzip](https://linuxize.com/post/how-to-unzip-files-in-linux/)
* Mac: [The Unarchiver](https://theunarchiver.com/)
</details>

<details>
    <summary>Recommended Terminals</summary>

* Windows: [Windows Terminal](https://apps.microsoft.com/detail/9N8G5RFZ9XK3)
* Mac: [iterm2](https://iterm2.com/)
</details>

### Run

* The launcher will ask for all configuration on first launch
    * Or run the launcher with the `--setup` flag. e.g. `.\launch.exe --setup`
* Use the `connect` command to link an MC account and log in once ZenithProxy is launched
* Command Prefixes:
    * Discord: `.` (e.g. `.help`)
    * In-game: `/` OR `!` -> (e.g. `/help`)
    * Terminal: N/A -> (e.g. `help`)
* [Full Commands Documentation](https://github.com/rfresh2/ZenithProxy/wiki/Commands)