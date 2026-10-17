<img src="https://github.com/theptah/synchronize/blob/main/src/main/resources/assets/synchronize/icon.png?raw=true" alt="Synchronize Icon" width="200px">

# Synchronize

![License](https://img.shields.io/github/license/theptah/synchronize?style=for-the-badge&labelColor=2c2e33&color=555555)
![Side](https://img.shields.io/badge/Server%20and%20Client%20-%20%23555555?style=for-the-badge&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIGZpbGw9Im5vbmUiIGhlaWdodD0iNTEyIiB2aWV3Qm94PSIwIDAgMjQgMjQiIHdpZHRoPSI1MTIiPgogIDxwYXRoIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0ibTEuMjUgNmMwLTEuNTE4NzggMS4yMzEyMi0yLjc1IDIuNzUtMi43NWgxNmMxLjUxODggMCAyLjc1IDEuMjMxMjIgMi43NSAyLjc1djEwYzAgMS41MTg4LTEuMjMxMiAyLjc1LTIuNzUgMi43NWgtNS43ODY1bC43NSAxLjVoMi4wMzY1Yy40MTQyIDAgLjc1LjMzNTguNzUuNzVzLS4zMzU4Ljc1LS43NS43NWgtMTBjLS40MTQyMSAwLS43NS0uMzM1OC0uNzUtLjc1cy4zMzU3OS0uNzUuNzUtLjc1aDIuMDM2NDdsLjc1LTEuNWgtNS43ODY0N2MtMS41MTg3OCAwLTIuNzUtMS4yMzEyLTIuNzUtMi43NXptMTguNzUgMTEuMjVoLTE2Yy0uNjkwMzYgMC0xLjI1LS41NTk2LTEuMjUtMS4yNXYtMTBjMC0uNjkwMzYuNTU5NjQtMS4yNSAxLjI1LTEuMjVoMTZjLjY5MDQgMCAxLjI1LjU1OTY0IDEuMjUgMS4yNXYxMGMwIC42OTA0LS41NTk2IDEuMjUtMS4yNSAxLjI1eiIgZmlsbD0iI2ZmZmZmZiIgZmlsbC1ydWxlPSJldmVub2RkIj48L3BhdGg%2BCjwvc3ZnPgo%3D&label=Required&labelColor=%232c2e33)
![Supported Versions](https://img.shields.io/badge/-1.19.2_to_1.21.11-4f7942?style=for-the-badge&logo=data:image/svg%2Bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA1MTIgNTEyIiB3aWR0aD0iNTEyIiBoZWlnaHQ9IjUxMiI%2BPGcgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjZmZmZmZmIiBzdHJva2Utd2lkdGg9IjMyIiBzdHJva2UtbGluZWNhcD0icm91bmQiIHN0cm9rZS1saW5lam9pbj0icm91bmQiPjxwYXRoIGQ9Ik0gNDUgMTM1IEMgMjAgMTQ1IDE2IDE3MCAxNiAyMDAgTCAxNiAzMTIgQyAxNiAzNDIgMjAgMzY3IDQ1IDM3NyIgLz48cGF0aCBkPSJNIDEzMCA5MCBDIDk1IDEwMiA4NSAxMzUgODUgMTcwIEwgODUgMzQyIEMgODUgMzc3IDk1IDQxMCAxMzAgNDIyIiAvPjxyZWN0IHg9IjE3MiIgeT0iNDgiIHdpZHRoPSIzMTgiIGhlaWdodD0iNDE2IiByeD0iNDIiIHJ5PSI0MiIgLz48L2c%2BPC9zdmc%2B)
![Fabric Loader](https://img.shields.io/badge/Fabric%20-%20%232c2e33?style=for-the-badge&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA1MiA1NiIgc3Ryb2tlLXdpZHRoPSIxLjA1IiBzdHJva2UtbGluZWNhcD0ic3F1YXJlIiBzaGFwZS1yZW5kZXJpbmc9Imdlb21ldHJpY1ByZWNpc2lvbiI%2BCiAgPHBhdGggc3Ryb2tlPSIjMzgzNDJhIiBkPSJNMjggMGg0bS00IDFoNG0tNCAxaDRtLTQgMWg0bS04IDFoNG00IDBoNE0yNCA1aDRtNCAwaDRNMjQgNmg0bTQgMGg0TTI0IDdoNG00IDBoNE0yNCA4aDRtOCAwaDRNMjQgOWg0bTggMGg0bS0xNiAxaDRtOCAwaDRtLTE2IDFoNG04IDBoNG0tMjAgMWg0bTQgMGg0bTggMGg0bS0yNCAxaDRtNCAwaDRtOCAwaDRtLTI0IDFoNG00IDBoNG04IDBoNG0tMjQgMWg0bTQgMGg0bTggMGg0bS0yOCAxaDRtMTIgMGg0bTggMGg0bS0zMiAxaDRtMTIgMGg0bTggMGg0bS0zMiAxaDRtMTIgMGg0bTggMGg0bS0zMiAxaDRtMTIgMGg0bTggMGg0bS0zNiAxaDRtMjAgMGg0bTQgMGg4bS00MCAxaDRtMjAgMGg0bTQgMGg4bS00MCAxaDRtMjAgMGg0bTQgMGg4bS00MCAxaDRtMjAgMGg0bTQgMGg4TTggMjRoNG0yOCAwaDRtNCAwaDRNOCAyNWg0bTI4IDBoNG00IDBoNE04IDI2aDRtMjggMGg0bTQgMGg0TTggMjdoNG0yOCAwaDRtNCAwaDRNNCAyOGg0bTMyIDBoOE00IDI5aDRtMzIgMGg4TTQgMzBoNG0zMiAwaDhNNCAzMWg0bTMyIDBoOE0wIDMyaDRtMzIgMGg0TTAgMzNoNG0zMiAwaDRNMCAzNGg0bTMyIDBoNE0wIDM1aDRtMzIgMGg0TTAgMzZoNG0yOCAwaDRNMCAzN2g0bTI4IDBoNE0wIDM4aDRtMjggMGg0TTAgMzloNG0yOCAwaDRNNCA0MGg0bTIwIDBoNE00IDQxaDRtMjAgMGg0TTQgNDJoNG0yMCAwaDRNNCA0M2g0bTIwIDBoNE04IDQ0aDRtMTIgMGg0TTggNDVoNG0xMiAwaDRNOCA0Nmg0bTEyIDBoNE04IDQ3aDRtMTIgMGg0bS0xNiAxaDRtNCAwaDhtLTE2IDFoNG00IDBoOG0tMTYgMWg0bTQgMGg4bS0xNiAxaDRtNCAwaDhtLTEyIDFoOG0tOCAxaDhtLTggMWg4bS04IDFoOCI%2BPC9wYXRoPgogIDxwYXRoIHN0cm9rZT0iI2RiZDBiNCIgZD0iTTI4IDRoNG0tNCAxaDRtLTQgMWg0bS00IDFoNG0wIDFoNG0tNCAxaDRtLTQgMWg0bS00IDFoNG0tMTIgMWg0bTggMGg0bS0xNiAxaDRtOCAwaDRtLTE2IDFoNG04IDBoNG0tMTYgMWg0bTggMGg0bS0xNiAxaDhtOCAwaDRtLTIwIDFoOG04IDBoNG0tMjAgMWg4bTggMGg0bS0yMCAxaDhtOCAwaDRtLTI4IDFoOG00IDBoOG0tMjAgMWg4bTQgMGg4bS0yMCAxaDhtNCAwaDhtLTIwIDFoOG00IDBoOG0tMjQgMWgxNm0tMTYgMWgxNm0tMTYgMWgxNm0tMTYgMWgxNk04IDI4aDhtNCAwaDEyTTggMjloOG00IDBoMTJNOCAzMGg4bTQgMGgxMk04IDMxaDhtNCAwaDEyTTggMzJoMTJtOCAwaDRNOCAzM2gxMm04IDBoNE04IDM0aDEybTggMGg0TTggMzVoMTJtOCAwaDRtLTIwIDFoMTJtLTEyIDFoMTJtLTEyIDFoMTJtLTEyIDFoMTJtLTggMWg4bS04IDFoOG0tOCAxaDhtLTggMWg4Ij48L3BhdGg%2BCiAgPHBhdGggc3Ryb2tlPSIjYzZiY2E1IiBkPSJNMjggOGg0bS00IDFoNG0tNCAxaDRtLTQgMWg0bS0xMiA1aDRtLTQgMWg0bS00IDFoNG0tNCAxaDRtMCAxaDRtLTQgMWg0bS00IDFoNG0tNCAxaDRtMCAxaDhtLTggMWg4bS04IDFoOG0tOCAxaDhtLTIwIDFoNG0tNCAxaDRtLTQgMWg0bS00IDFoNG0wIDFoOG0tOCAxaDhtLTggMWg4bS04IDFoOCI%2BPC9wYXRoPgogIDxwYXRoIHN0cm9rZT0iI2JjYjI5YyIgZD0iTTMyIDEyaDRtLTQgMWg0bS00IDFoNG0tNCAxaDRtMCAxaDRtLTQgMWg0bS00IDFoNG0tNCAxaDRtMCAxaDRtLTQgMWg0bS00IDFoNG0tNCAxaDRtLTggMWg0bS00IDFoNG0tNCAxaDRtLTQgMWg0bS04IDFoOG0tOCAxaDhtLTggMWg4bS04IDFoOG0tOCAxaDRtLTQgMWg0bS00IDFoNG0tNCAxaDRtLTEyIDFoOG0tOCAxaDhtLTggMWg4bS04IDFoOG0tOCAxaDRtLTQgMWg0bS00IDFoNG0tNCAxaDRtLTggMWg0bS00IDFoNG0tNCAxaDRtLTQgMWg0Ij48L3BhdGg%2BCiAgPHBhdGggc3Ryb2tlPSIjODA3YTZkIiBkPSJNNDQgMjRoNG0tNCAxaDRtLTQgMWg0bS00IDFoNCI%2BPC9wYXRoPgogIDxwYXRoIHN0cm9rZT0iI2FlYTY5NCIgZD0iTTQgMzJoNG0tNCAxaDRtLTQgMWg0bS00IDFoNG0wIDFoNG0tNCAxaDRtLTQgMWg0bS00IDFoNG0wIDFoNG0tNCAxaDRtLTQgMWg0bS00IDFoNG0wIDFoNG0tNCAxaDRtLTQgMWg0bS00IDFoNCI%2BPC9wYXRoPgogIDxwYXRoIHN0cm9rZT0iIzlhOTI3ZSIgZD0iTTQgMzZoNG0tNCAxaDRtLTQgMWg0bS00IDFoNG0wIDFoNG0tNCAxaDRtLTQgMWg0bS00IDFoNG0wIDFoNG0tNCAxaDRtLTQgMWg0bS00IDFoNG0wIDFoNG0tNCAxaDRtLTQgMWg0bS00IDFoNCI%2BPC9wYXRoPgo8L3N2Zz4K&color=%23ECDBAC
)

A feature-rich synchronization mod that **automatically installs** and syncs mods and various assets when the client closes, while also utilizing **Limbo mode** to let vanilla players join and receive interactive chat prompts to download the required files. **All currently compiled Minecraft versions will receive future updates.**

**REMEMBER!** Synchronize is currently in **Beta**. Bugs and unexpected behavior may occur. Back up your files before use and report any issues you encounter.
**Use only official JARs published Modrinth and GitHub Releases page** on both the server and client. 
<br/>
<br/>
You can show your support for my development efforts by using this mod and if you’d like to treat me to a snack or drink while i'm working on this, you can make a donation:

<a href="https://www.buymeacoffee.com/theptah">
  <img src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20snack%20%2F%20drink&emoji=&slug=theptah&button_colour=FF5F5F&font_colour=ffffff&font_family=Comic&outline_colour=000000&coffee_colour=FFDD00" alt="Buy Me A snack/drink" height="50px">
</a>


## Preview

<table align="center">
  <tr>
    <td align="center">
      <a href="/preview-3.gif" alt="Click to see" target="_blank">
        <img src="https://raw.githubusercontent.com/theptah/synchronize/refs/heads/main/preview-3.gif" alt="Synchronization Screen Preview" width="100%" />
      </a>
      <br />
      <b>Synchronization</b>
      <br />
      <sub>Synchronization progress</sub>
    </td>    
	<td align="center">
      <a href="/preview.gif" alt="Click to see" target="_blank">
        <img src="https://raw.githubusercontent.com/theptah/synchronize/refs/heads/main/preview.gif" alt="Untrusted Server Preview" width="100%" />
      </a>
      <br />
      <b>Untrusted Server</b>
      <br />
      <sub>When server uses unofficial jar</sub>
    </td>
    <td align="center">
      <a href="/preview-2.gif" alt="Click to see" target="_blank">
        <img src="https://raw.githubusercontent.com/theptah/synchronize/refs/heads/main/preview-2.gif" alt="Untrusted Client Preview" width="100%" />
      </a>
      <br />
      <b>Untrusted Client</b>
      <br />
      <sub>When client uses different jar from server that is unofficial</sub>
    </td>
  </tr>
</table> 

## Supported Versions

- For now, it only supports the **Fabric** loader but in the future, **depending on your feedback**, I may add support for other loaders.

| JAR File | Supported Versions |
| :--- | :--- | 
| `1.19.2` | 1.19, 1.19.1, 1.19.2 | 
| `1.19.3` | 1.19.3 | 
| `1.19.4` | 1.19.4 | 
| `1.20.1` | 1.20, 1.20.1 | 
| `1.20.2` | 1.20.2 | 
| `1.20.4` | 1.20.3, 1.20.4 | 
| `1.20.6` | 1.20.5, 1.20.6 | 
| `1.21.1` | 1.21, 1.21.1 | 
| `1.21.3` | 1.21.2, 1.21.3 | 
| `1.21.4` | 1.21.4 | 
| `1.21.5` | 1.21.5 | 
| `1.21.8` | 1.21.6, 1.21.7, 1.21.8 | 
| `1.21.10` | 1.21.9, 1.21.10 | 
| `1.21.11` | 1.21.11 | 

Requires [Fabric API](https://modrinth.com/mod/fabric-api). 

## Download

- [Modrinth](https://modrinth.com/mod/synchronize)
- [Github Releases](https://github.com/theptah/synchronize/releases)

## User Guide

1. Install Fabric Loader and the appropriate Synchronize build for your Minecraft version.
2. Start the server to generate the configuration.
3. Place the files you want to distribute inside the server's game directory.
4. Register those files using [commands below](#commands).
5. Connect with a client that also has Synchronize installed.

Synchronization is enabled for **dedicated servers** and worlds opened to **LAN**.

### Commands
Synchronize commands are available from the **server console**, not from in-game player chat—even for operators. Enter the commands below without a leading `/`.

| Command | Description | Example |
| :--- | :--- | :--- |
| `synchronize help` | Displays available commands and options. | `synchronize help` |
| `synchronize add_files <path> [optional] [shallow] [restart] [destination <path>]` | Registers a file or directory for synchronization. Directories are scanned recursively unless `shallow` is specified. | `synchronize add_files mods` |
| `synchronize remove_files <path>` | Removes a file or directory's entries from the synchronization list. Does not delete the actual files. | `synchronize remove_files mods/example.jar` |
| `synchronize refresh` | Recalculates tracked file hashes, processes missing tracked files, and reloads configuration, translations, and rate-limit settings. | `synchronize refresh` |

#### Options for add_files

| Option | Description | Example |
| :--- | :--- | :--- |
| `optional` | Lets players choose whether to download the registered files. Without this option, files are mandatory. | `synchronize add_files resourcepacks optional` |
| `shallow` | Registers only files directly inside the specified directory, excluding subdirectories. | `synchronize add_files mods shallow` |
| `restart` | Requires a game restart after applying the registered files. Intended for files outside `mods/`; files installed into `mods/` already require a restart automatically. | `synchronize add_files config/example.json restart` |
| `destination <path>` | Overrides the installation path on the client. When registering a directory, its internal directory structure is preserved under the destination. | `synchronize add_files client-files/config destination config` |

- New files are **not automatically discovered** you have to register them with `synchronize add_files`, even if their parent directory was registered previously.

- Paths are relative to the server's game directory. Absolute paths and parent-directory traversal are not supported. For `add_files`, wrap paths containing spaces in double quotes.

Example Usages:

```text
synchronize add_files mods shallow
synchronize add_files resourcepacks optional
synchronize add_files config/example.json restart
synchronize add_files client-files/config restart destination config
synchronize add_files "client-files/My Pack.zip" optional destination "resourcepacks/My Pack.zip"
synchronize remove_files mods/example.jar
synchronize refresh
```
 
### Managing Shared Files

**To update an existing tracked files:**

1. Replace or edit the file on the server.
2. Run `synchronize refresh`.
3. Players receive the updated file during their next synchronization.

**To request deleting a previously distributed file from clients:**

1. Just delete its tracked source file from the server.
2. Run `synchronize refresh` while the file is still registered.
3. Synchronize moves its client destination to the deleted-file list.
4. Players are shown a removal prompt when connecting.

**To stop distributing a file without requesting its removal from clients:**

Run `synchronize remove_files <path>`. This unregisters the file but does not delete it from either side.

## Configuration

### config/synchronize/config.json

- `language`: Used to translate server-side terminal messages into the languages supported by the mod.
- `fileTransferMethod`: Determines how files are sent from the server to the client.
	- `MINECRAFT`: Transfers files through the Minecraft connection. This is the default method.
	- `EXTERNAL`: Transfers files through a separate HTTP service using `externalPort`, which defaults to `25652`.
	- **Restart required to take these changes.**
- `limboEnabled`: When activated, it brings vanilla players into the world and sends them an interactive message prompting them to download and install Synchronize Mod.
- `limboDurationSeconds`: Specifies how many seconds after entering the limbo a player will kick.
- `rateLimitMaxRequests`: Maximum number of bulk file-transfer requests allowed **per IP address** within the configured time window. A bulk request can include multiple files; this is not a limit on individual files or download speed. 
- `rateLimitWindowSeconds`: Duration of the rolling time window, in seconds. Accepted requests count toward the limit until they fall outside this window.
- `handshake`: If the request to verify whether the client is using the official version via GitHub returns an error, the code provided by the server owner in this field is used to force access to the server.

## Joining a Server

When connecting, Synchronize checks the registered files against the client's local files.

- Missing or different mandatory files must be synchronized to continue.
- Optional files can be selected by the player.
- Files are downloaded and checked against the expected hashes before being applied.
- Changes inside `mods/` require a game restart.
- Registered files with the `restart` option will require a game restart.

Follow the on-screen instructions. If a restart is required, fully close Minecraft, launch it again, and reconnect.

### Version Verification

Synchronize's own JAR is checked separately from ordinary synchronized files.

| Situation | Result |
| :--- | :--- |
| Client and server JAR hashes match | Connection continues without a GitHub verification request. |
| Hashes differ and the server JAR cannot be matched to an official release | **Untrusted Server** screen. |
| The server JAR is official, but the client JAR cannot be matched to an official release | **Untrusted Client** screen. |
| Both JARs are official, but their hashes differ | The client is offered the exact official JAR used by the server. |
| Verification cannot be completed | A verification-failed screen is shown, with a [handshake fallback](#handshake-fallback) when available. |

> **NOTE:** Matching client and server hashes mean the files are identical—not necessarily official. GitHub verification is intentionally skipped when both hashes match.

### Handshake Fallback

The handshake provides a user-approved fallback when normal verification cannot be completed, such as when GitHub is unavailable.

1. The server generates a handshake code and saves it in `config/synchronize/config.json` under `handshake`.
2. The server owner privately shares the code with trusted players.
3. Players enter it in the handshake field on Minecraft's **Add Server / Edit Server** screen.
4. When the fallback is offered, players explicitly choose to reconnect using the handshake.

The client and server must prove that they know the same code.

> **REMEMBER!** A successful handshake does not prove that either JAR is an official GitHub release. Only use this fallback with a server owner you trust, and keep the code private.

Preview

![Handshake Edit Preview](https://raw.githubusercontent.com/theptah/synchronize/refs/heads/main/preview-4.gif)

## Build Guide

> **INFO:** Any missing JDK toolchains are downloaded automatically by Gradle. A compatible Java installation is still required to launch Gradle. Collected artifacts are saved to `./build/libs/` as `synchronize-{mod_version}-{game_version}-{loader}.jar`.

Targeted subproject tasks follow the syntax below:

```bash
./gradlew :{version}-{loader}:{command}
```

| Action | Command | Example |
| :--- | :--- | :--- |
| **Build all versions** | `./gradlew buildAndCollect` | `./gradlew buildAndCollect` |
| **Build specific version** | `./gradlew :{version}-{loader}:buildAndCollect` | `./gradlew :1.20.1-fabric:buildAndCollect` |
| **Clean build specific version** | `./gradlew clean :{version}-{loader}:buildAndCollect` | `./gradlew clean :1.20.1-fabric:buildAndCollect` |
| **Clean all build outputs** | `./gradlew clean` | `./gradlew clean` |
| **List available versions** | `./gradlew projects` | `./gradlew projects` |

> **TIP:** Append `--stacktrace` to any command above to display detailed error logs if a build fails.

### Project Layout

| Path | Contents |
| :--- | :--- |
| `src/main` | Common initialization, server synchronization, commands, configuration, networking, compatibility helpers, and shared utilities. |
| `src/client` | Client synchronization, download and update handling, screens, handshake integration, client mixins, and the standalone updater. |
| `templates/synchronize.accesswidener` | Source template used to generate the version-specific access widener. |
| `stonecutter.properties.toml` | Mod version, shared properties, and per-version dependencies. |
| `settings.gradle.kts` | Supported Minecraft versions and Stonecutter subproject registration. |
| `stonecutter.gradle.kts` | Active development version, version constants, and cross-version source replacements. |

### Adding a New Version

- This project relies on [Stonecutter](https://stonecutter.kikugie.dev/) for multi-version targeting. Version-specific code is managed using `//? if` preprocessor comments, and simple API renames (such as `ResourceLocation` → `Identifier` on 1.21.11+) are resolved via replacements in `stonecutter.gradle.kts`.
- To add support for a new version, register `match("<version>", 'fabric')` inside `settings.gradle.kts` and define the matching `[fabric."<version>"]` table in `stonecutter.properties.toml`.
