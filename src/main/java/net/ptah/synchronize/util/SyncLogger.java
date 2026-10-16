package net.ptah.synchronize.util;

import net.ptah.synchronize.Synchronize;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;

public class SyncLogger {
   private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
   private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
   private static File logDir;
   private static File logFile;
   private static boolean initialized = false;

   private static void init() {
      if (!initialized) {
         logDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "synchronize/logs");
         logFile = new File(logDir, "sync.log");
         if (!logDir.exists()) {
            try {
               Files.createDirectory(logDir.toPath());
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }

         if (logFile.exists() && logFile.length() > 0L) {
            archiveOldLog();
         }

         initialized = true;
      }
   }

   private static void archiveOldLog() {
      String dateStr = LocalDate.now().toString();
      int index = 1;

      File archiveFile;
      do {
         archiveFile = new File(logDir, dateStr + "-" + index + ".log.gz");
         ++index;
      } while(archiveFile.exists());

      try {
         GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(archiveFile));

         try {
            FileInputStream in = new FileInputStream(logFile);

            try {
               byte[] buffer = new byte[8192];

               int len;
               while((len = in.read(buffer)) > 0) {
                  out.write(buffer, 0, len);
               }
            } catch (Throwable var10) {
               try {
                  in.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }

               throw var10;
            }

            in.close();
         } catch (Throwable var11) {
            try {
               out.close();
            } catch (Throwable var8) {
               var11.addSuppressed(var8);
            }

            throw var11;
         }

         out.close();
      } catch (Exception e) {
         Synchronize.LOGGER.error("Failed to archive old sync log", e);
      }

      try {
         Files.deleteIfExists(logFile.toPath());
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   public static void info(String message) {
      log("INFO", message);
   }

   public static void error(String message) {
      log("ERROR", message);
   }

   private static void log(String level, String message) {
      if (!initialized) {
         init();
      }

      String time = LocalTime.now().format(TIME_FORMAT);
      String thread = Thread.currentThread().getName();
      String formatted = String.format("[%s] [%s/%s]: %s%n", time, thread, level, message);
      EXECUTOR.submit(() -> {
         try {
            Files.writeString(logFile.toPath(), formatted, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (IOException e) {
            Synchronize.LOGGER.error("Failed to write to sync.log", e);
         }

      });
   }

   public static void shutdown() {
      if (!EXECUTOR.isShutdown()) {
         EXECUTOR.shutdown();
      }

   }
}
