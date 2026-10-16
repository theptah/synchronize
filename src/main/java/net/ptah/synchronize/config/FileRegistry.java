package net.ptah.synchronize.config;

import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.util.ModJar;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

public class FileRegistry {
   public static final String SYNC_LIST_FILE_NAME = "sync_list.json";
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   public static Map<String, FileEntry> FILE_ENTRIES = new ConcurrentHashMap<>();
   public static Map<String, Boolean> GARBAGE_DESTINATIONS = new ConcurrentHashMap<>();

   public static class FileEntry {
      public String hash;
      public String destination;
      public boolean optional;
      public boolean restart;

      public FileEntry() {
      }

      public FileEntry(String hash, String destination, boolean optional) {
         this(hash, destination, optional, false);
      }

      public FileEntry(String hash, String destination, boolean optional, boolean restart) {
         this.hash = hash;
         this.destination = destination;
         this.optional = optional;
         this.restart = restart;
      }
   }

   public static void load() {
      File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "synchronize/" + SYNC_LIST_FILE_NAME);
      if (!configFile.exists()) return;
      try {
         String raw = Files.readString(configFile.toPath());
         Manifest manifest = parseManifest(raw);
         if (manifest != null) {
            FILE_ENTRIES.putAll(manifest.files);
            GARBAGE_DESTINATIONS.putAll(manifest.garbage);
            if (purgeMainModEntries()) {
               save();
            }
         }
      } catch (Exception e) {
         Synchronize.LOGGER.error("Failed to load file hashes", e);
      }
   }

   private static final class Manifest {
      final Map<String, FileEntry> files;
      final Map<String, Boolean> garbage;
      Manifest(Map<String, FileEntry> files, Map<String, Boolean> garbage) {
         this.files = files;
         this.garbage = garbage;
      }
   }

   private static Manifest parseManifest(String raw) {
      try {
         Type wrapperType = (new TypeToken<java.util.Map<String, Object>>() { }).getType();
         Map<String, Object> wrapper = GSON.fromJson(raw, wrapperType);
         if (wrapper != null && wrapper.containsKey("files")) {
            Type filesType = (new TypeToken<HashMap<String, FileEntry>>() { }).getType();
            Map<String, FileEntry> files = GSON.fromJson(GSON.toJson(wrapper.get("files")), filesType);
            if (files == null) files = new HashMap<>();
            for (Map.Entry<String, FileEntry> e : files.entrySet()) {
               FileEntry entry = e.getValue();
               if (entry == null || entry.hash == null) throw new IllegalStateException("Invalid file entry");
               if (entry.destination == null || entry.destination.isBlank()) entry.destination = e.getKey();
            }
            Map<String, Boolean> garbage = new HashMap<>();
            Object garbageRaw = wrapper.get("garbage");
            if (garbageRaw instanceof Map<?, ?> map) {
               for (Map.Entry<?, ?> entry : map.entrySet()) {
                  if (entry.getKey() instanceof String path && !path.isBlank()) {
                     boolean restart = entry.getValue() instanceof Boolean b && b;
                     garbage.put(path.replace('\\', '/'), restart);
                  }
               }
            }
            return new Manifest(files, garbage);
         }
      } catch (Exception ignored) {
      }

      try {
         Type currentType = (new TypeToken<HashMap<String, FileEntry>>() { }).getType();
         Map<String, FileEntry> loaded = GSON.fromJson(raw, currentType);
         if (loaded == null) return null;
         for (Map.Entry<String, FileEntry> e : loaded.entrySet()) {
            FileEntry entry = e.getValue();
            if (entry == null || entry.hash == null) throw new IllegalStateException("Not a current-format sync list");
            if (entry.destination == null || entry.destination.isBlank()) entry.destination = e.getKey();
         }
         return new Manifest(loaded, new HashMap<>());
      } catch (Exception currentFormatFailure) {
         Type legacyType = (new TypeToken<HashMap<String, String>>() { }).getType();
         Map<String, String> legacy = GSON.fromJson(raw, legacyType);
         if (legacy == null) return null;
         Map<String, FileEntry> upgraded = new HashMap<>();
         for (Map.Entry<String, String> e : legacy.entrySet()) {
            upgraded.put(e.getKey(), new FileEntry(e.getValue(), e.getKey(), false));
         }
         Synchronize.LOGGER.info("[Synchronize] Upgraded sync_list.json from the legacy hash-only format to the optional/destination format.");
         return new Manifest(upgraded, new HashMap<>());
      }
   }

   public static void save() {
      File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "synchronize/" + SYNC_LIST_FILE_NAME);
      try {
         Files.createDirectories(configFile.getParentFile().toPath());
         try (FileWriter writer = new FileWriter(configFile)) {
            Map<String, FileEntry> sortedFiles = new TreeMap<>(FILE_ENTRIES);
            Map<String, Object> wrapper = new java.util.LinkedHashMap<>();
            wrapper.put("files", sortedFiles);
            Map<String, Boolean> sortedGarbage = new TreeMap<>(GARBAGE_DESTINATIONS);
            wrapper.put("garbage", sortedGarbage);
            GSON.toJson(wrapper, writer);
         }
      } catch (Exception e) {
         Synchronize.LOGGER.error("Failed to save file hashes", e);
      }
   }

   public static boolean isMainModSource(String relativeSource, FileEntry entry) {
      if (entry != null && ModJar.isSelfHash(entry.hash)) return true;
      if (relativeSource == null) return false;
      try {
         Path file = FabricLoader.getInstance().getGameDir().resolve(relativeSource.replace('\\', '/'));
         return ModJar.isSynchronizeJar(file);
      } catch (Exception e) {
         return false;
      }
   }

   public static boolean purgeMainModEntries() {
      boolean changed = false;
      Iterator<Map.Entry<String, FileEntry>> iterator = FILE_ENTRIES.entrySet().iterator();
      while (iterator.hasNext()) {
         Map.Entry<String, FileEntry> entry = iterator.next();
         if (isMainModSource(entry.getKey(), entry.getValue())) {
            Synchronize.LOGGER.warn("[Synchronize] Ignoring '{}': the Synchronize main mod jar cannot be part of the sync list.", entry.getKey());
            iterator.remove();
            changed = true;
         }
      }
      Path gameDir = FabricLoader.getInstance().getGameDir();
      Iterator<String> garbage = GARBAGE_DESTINATIONS.keySet().iterator();
      while (garbage.hasNext()) {
         String destination = garbage.next();
         try {
            Path local = gameDir.resolve(destination);
            if (Files.exists(local) && ModJar.isSynchronizeJar(local)) {
               Synchronize.LOGGER.warn("[Synchronize] Ignoring deleted-file entry '{}': it points at the Synchronize main mod jar.", destination);
               garbage.remove();
               changed = true;
            }
         } catch (Exception ignored) {
         }
      }
      return changed;
   }

   public static void markGarbage(String destination, boolean restartRequired) {
      if (destination == null || destination.isBlank()) return;
      String normalized = destination.replace('\\', '/');
      boolean requiresRestart = restartRequired || normalized.equalsIgnoreCase("mods")
            || normalized.toLowerCase(java.util.Locale.ROOT).startsWith("mods/");
      GARBAGE_DESTINATIONS.put(normalized, requiresRestart);
   }

   public static void unmarkGarbage(String destination) {
      if (destination == null) return;
      GARBAGE_DESTINATIONS.remove(destination.replace('\\', '/'));
   }

   public static String calculateHash(Path file) throws Exception {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var in = Files.newInputStream(file)) {
         byte[] buffer = new byte[65536];
         int read;
         while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
         }
      }
      byte[] hashBytes = digest.digest();
      StringBuilder sb = new StringBuilder();
      for(byte b : hashBytes) {
         sb.append(String.format("%02x", b));
      }
      return sb.toString();
   }

   public static void refreshHashes() {
      Path gameDir = FabricLoader.getInstance().getGameDir();
      boolean changed = purgeMainModEntries();
      Iterator<Map.Entry<String, FileEntry>> iterator = FILE_ENTRIES.entrySet().iterator();

      while (iterator.hasNext()) {
         Map.Entry<String, FileEntry> entry = iterator.next();
         Path filePath = gameDir.resolve(entry.getKey());

         if (!Files.exists(filePath)) {
            FileEntry removedEntry = entry.getValue();
            String destination = removedEntry == null || removedEntry.destination == null || removedEntry.destination.isBlank()
                  ? entry.getKey() : removedEntry.destination;
            boolean restartRequired = removedEntry != null && removedEntry.restart;
            String normalizedDestination = destination.replace('\\', '/');
            if (normalizedDestination.equalsIgnoreCase("mods") || normalizedDestination.toLowerCase(java.util.Locale.ROOT).startsWith("mods/")) {
               restartRequired = true;
            }
            markGarbage(normalizedDestination, restartRequired);
            Synchronize.LOGGER.warn("[Synchronize] Tracked file no longer exists on disk; moving destination to deleted list: {}", normalizedDestination);
            iterator.remove();
            changed = true;
            continue;
         }

         try {
            if (Files.size(filePath) == 0) {
               Synchronize.LOGGER.warn("[Synchronize] Tracked file is 0 bytes; removing it from the sync list: {}", entry.getKey());
               iterator.remove();
               changed = true;
               continue;
            }

            String currentHash = calculateHash(filePath);
            if (ModJar.isSelfHash(currentHash) || ModJar.isSynchronizeJar(filePath)) {
               Synchronize.LOGGER.warn("[Synchronize] Ignoring '{}': the Synchronize main mod jar cannot be part of the sync list.", entry.getKey());
               iterator.remove();
               changed = true;
               continue;
            }
            if (!currentHash.equals(entry.getValue().hash)) {
               Synchronize.LOGGER.warn("[Synchronize] Stored hash for '{}' was stale (registry out of date with the file on disk); refreshing it.", entry.getKey());
               entry.getValue().hash = currentHash;
               changed = true;
            }
         } catch (Exception e) {
            Synchronize.LOGGER.error("Failed to refresh hash for {}", entry.getKey(), e);
         }
      }
      if (changed) {
         save();
      }
   }

   public static void autoRefresh() {
      refreshHashes();
      Synchronize.LOGGER.info("[Synchronize] Startup auto-refresh: existing hashes re-validated. Auto-discovery is disabled.");
   }
}