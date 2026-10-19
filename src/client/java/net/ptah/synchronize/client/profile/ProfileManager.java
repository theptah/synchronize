package net.ptah.synchronize.client.profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.ptah.synchronize.client.ClientSyncHandler;
import net.ptah.synchronize.client.screen.ProfileMessageScreen;
import net.ptah.synchronize.client.screen.ProfilesScreen;
import net.ptah.synchronize.config.FileRegistry;
import net.ptah.synchronize.config.FileRegistry.FileEntry;
import net.ptah.synchronize.util.ModJar;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;

public final class ProfileManager {
    public static final long MAX_FILE_BYTES = 512L * 1024L * 1024L;
    public static final long MAX_TRANSFER_BYTES = 2L * 1024L * 1024L * 1024L;
    private static String sessionProfile;
    private static Path sessionDirectory;
    private static Map<String, FileEntry> sessionEntries = Map.of();
    private static final Set<String> deletions = new LinkedHashSet<>();
    private static long receivedBytes;
    private static boolean noticeShown;
    private static String initializationError;
    private static java.nio.channels.FileChannel gameChannel;
    private static java.nio.channels.FileLock gameLock;
    private static volatile boolean cancelled;
    private ProfileManager() {}

    public static Path root() { return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize(); }
    public static String minecraft() { return FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString(); }

    public static void initialize() {
        try {
            ProfileStorage.initialize(root());
            gameChannel = java.nio.channels.FileChannel.open(ProfileStorage.resolve(ProfileStorage.home(root()), "game.lock"), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
            gameLock = gameChannel.tryLock();
            if (gameLock == null) throw new IOException("Another Minecraft instance is already using this profile directory");
            Properties core = new Properties();
            for (var mod : FabricLoader.getInstance().getAllMods()) {
                if (!Set.of("synchronize").contains(mod.getMetadata().getId())) continue;
                for (Path origin : mod.getOrigin().getPaths()) {
                    Path path = origin.toAbsolutePath().normalize();
                    if (path.startsWith(root()) && Files.isRegularFile(path)) core.setProperty(root().relativize(path).toString().replace('\\', '/'), "1");
                }
            }
            writeCore(core);
        } catch (Exception e) { initializationError = e.getMessage(); }
    }

    private static void writeCore(Properties core) throws IOException { ProfileStorage.writeCore(root(), core); }
    public static String active() throws IOException { return ProfileStorage.active(root()); }
    public static String name(String id) throws IOException { return ProfileStorage.name(root(), id); }
    public static String activeName() {
        try { return name(active()); } catch (IOException e) { return "Unavailable"; }
    }
    public static String pendingName() {
        try { String id = ProfileStorage.state(root()).getProperty("pending", ""); return id.isEmpty() ? "" : name(id); }
        catch (IOException e) { return "Unavailable"; }
    }
    public static List<String> profiles() throws IOException { return ProfileStorage.list(root()); }

    public static synchronized void begin(ServerInfo info, String fallback, Map<String, FileEntry> entries) throws IOException {
        if (initializationError != null) throw new IOException(initializationError);
        if (ProfileStorage.pending(root()) && Files.exists(ProfileStorage.resolve(ProfileStorage.home(root()), "transaction/journal.properties"))) throw new IOException("Profile recovery requires a restart");
        String endpoint;
        String display;
        if (info != null) {
            ServerAddress address = ServerAddress.parse(info.address);
            endpoint = address.getAddress().toLowerCase(Locale.ROOT) + ":" + address.getPort();
            display = info.name == null || info.name.isBlank() ? endpoint : info.name;
        } else { endpoint = fallback.toLowerCase(Locale.ROOT); display = endpoint; }
        sessionProfile = ProfileStorage.server(root(), endpoint, display, minecraft());
        Set<String> targets = new LinkedHashSet<>();
        for (Map.Entry<String, FileEntry> e : entries.entrySet()) {
            String dest = destination(e.getKey(), e.getValue()); ProfileStorage.allow(root(), dest);
            String normalized = dest.toLowerCase(Locale.ROOT);
            if (!targets.add(normalized)) throw new IOException("Duplicate client destination");
            for (String other : targets) if (!other.equals(normalized) && (other.startsWith(normalized + "/") || normalized.startsWith(other + "/"))) throw new IOException("Conflicting client destinations");
        }
        Set<String> actual = new LinkedHashSet<>();
        for (Map.Entry<String, FileEntry> e : entries.entrySet()) actual.add(destination(e.getKey(), e.getValue()));
        ProfileStorage.registerTargets(root(), actual);
        ClientSyncHandler.clearTempFolder();
        sessionEntries = Map.copyOf(entries); deletions.clear(); receivedBytes = 0; cancelled = false;
        Path sessions = ProfileStorage.resolve(root(), "config/synchronize/temp/downloads");
        ProfileStorage.erase(sessions);
        sessionDirectory = ProfileStorage.resolve(sessions, java.util.UUID.randomUUID().toString());
        Files.createDirectories(sessionDirectory);
    }

    public static String destination(String source, FileEntry entry) { return entry == null || entry.destination == null || entry.destination.isBlank() ? source : entry.destination.replace('\\', '/'); }
    public static synchronized Path comparison(String destination) throws IOException {
        if (sessionProfile == null) throw new IOException("No profile session");
        return ProfileStorage.comparisonFile(root(), sessionProfile, destination);
    }
    public static synchronized boolean owns(String destination) throws IOException { return sessionProfile != null && ProfileStorage.owned(root(), sessionProfile).contains(destination); }
    public static synchronized void deleteOnSwitch(List<String> paths) throws IOException {
        for (String p : paths) { ProfileStorage.allow(root(), p); if (owns(p)) deletions.add(p); }
    }
    public static void cancelDownload() { cancelled = true; }
    public static synchronized String ticket() throws IOException { if (sessionDirectory == null) throw new IOException("No download session"); return sessionDirectory.toString(); }
    public static synchronized void requireTicket(String ticket) throws IOException {
        if (cancelled || !ticket.equals(ticket())) throw new IOException("Profile download cancelled or replaced");
    }
    public static synchronized void receive(long bytes) throws IOException {
        if (cancelled) throw new IOException("Profile download cancelled");
        if (bytes < 0 || receivedBytes > MAX_TRANSFER_BYTES - bytes) throw new IOException("Transfer exceeds 2 GiB profile limit");
        receivedBytes += bytes;
    }

    public static synchronized String prepare(List<String> files, String ticket) throws Exception {
        requireTicket(ticket);
        if (sessionProfile == null || sessionDirectory == null) throw new IOException("No profile session");
        Set<String> installed = new LinkedHashSet<>();
        for (String source : files) {
            FileEntry entry = sessionEntries.get(source);
            if (entry == null) throw new IOException("File not in profile manifest");
            String destination = destination(source, entry);
            ProfileStorage.allow(root(), destination);
            Path temp = net.ptah.synchronize.util.PathSecurity.resolveUnder(ClientSyncHandler.getTempDir().toPath(), source);
            if (Files.size(temp) > MAX_FILE_BYTES || !FileRegistry.calculateHash(temp).equalsIgnoreCase(entry.hash)) throw new IOException("Prepared file hash mismatch: " + source);
            if (ModJar.isSynchronizeJar(temp)) throw new IOException("Synchronize cannot be installed as a profile file");
            ProfileStorage.copy(temp, ProfileStorage.resolve(sessionDirectory, destination)); installed.add(destination);
        }
        ProfileStorage.commitDownload(root(), sessionProfile, sessionDirectory, installed, new LinkedHashSet<>(deletions));
        ProfileStorage.erase(sessionDirectory);
        ClientSyncHandler.clearTempFolder();
        return sessionProfile;
    }

    public static synchronized boolean mustSwitch() throws IOException {
        if (sessionProfile == null) throw new IOException("No profile session");
        return !sessionProfile.equals(active()) || !ProfileStorage.profileData(root(), sessionProfile, "delta").isEmpty();
    }
    public static synchronized String session() throws IOException { if (sessionProfile == null) throw new IOException("No profile session"); return sessionProfile; }

    public static void queue(String id, boolean close) throws IOException {
        if (initializationError != null) throw new IOException(initializationError);
        ProfileStorage.queue(root(), id, null, minecraft());
        if (close) MinecraftClient.getInstance().scheduleStop();
    }
    public static void delete(String id) throws IOException {
        if (initializationError != null) throw new IOException(initializationError);
        if (id.equals(active())) { ProfileStorage.queue(root(), "default", id, minecraft()); MinecraftClient.getInstance().scheduleStop(); }
        else ProfileStorage.delete(root(), id);
    }

    public static void tick(MinecraftClient client) {
        if (noticeShown || !(client.currentScreen instanceof TitleScreen)) return;
        noticeShown = true;
        try {
            String error = ProfileStorage.state(root()).getProperty("error", initializationError);
            if (error != null) {
                var parent = client.currentScreen;
                client.setScreen(new ProfileMessageScreen(parent, Text.translatable("synchronize.profiles.error"), Text.literal(error), Text.translatable(initializationError == null ? "synchronize.profiles.okay" : "synchronize.button.quit_game"), () -> {
                    if (initializationError == null) {
                        try { Properties state = ProfileStorage.state(root()); state.remove("error"); ProfileStorage.writeState(root(), state); }
                        catch (IOException e) { initializationError = e.getMessage(); client.scheduleStop(); return; }
                        client.setScreen(parent);
                    } else client.scheduleStop();
                }, null, null));
            } else if (!active().equals("default")) {
                var parent = client.currentScreen;
                client.setScreen(new ProfileMessageScreen(parent, Text.translatable("synchronize.profiles.notice"), Text.translatable("synchronize.profiles.notice_body", activeName()), Text.translatable("synchronize.profiles.okay"), () -> client.setScreen(parent), Text.translatable("synchronize.profiles.title"), () -> client.setScreen(new ProfilesScreen(parent))));
            }
        } catch (IOException e) { initializationError = e.getMessage(); }
    }
}
