package net.ptah.synchronize.client;

import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.client.screen.DownloadProgressScreen;
import net.ptah.synchronize.config.FileRegistry;
import net.ptah.synchronize.config.FileRegistry.FileEntry;
import net.ptah.synchronize.util.CryptoUtil;
import net.ptah.synchronize.util.PathSecurity;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;

public class ClientExternalDownloader {
    public static void startDownload(String serverIp, String sessionId, byte[] aesKey, int port, List<String> filePaths, DownloadProgressScreen progressScreen, Runnable onComplete) {
        new Thread(() -> {
            try {
                int totalFiles = filePaths.size();
                int currentFileIndex = 0;

                for (String path : filePaths) {
                    ++currentFileIndex;
                    String safePath = path.replace('\\', '/');

                    if (safePath.contains("..")) {
                        throw new RuntimeException("Security violation: Invalid path " + safePath);
                    }

                    FileEntry entry = ClientSyncHandler.getActiveSyncEntries().get(safePath);
                    if (entry == null) {
                        throw new RuntimeException("Security Error: File not in active sync list: " + safePath);
                    }

                    String fileName = new File(safePath).getName();
                    final int startingIndex = currentFileIndex;
                    MinecraftClient.getInstance().execute(() -> progressScreen.updateProgress((float) (startingIndex - 1) / (float) totalFiles,
                            Text.translatable("synchronize.progress.receiving", startingIndex, totalFiles, fileName)));

                    String encodedSession = URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
                    String encodedPath = URLEncoder.encode(safePath, StandardCharsets.UTF_8);
                    URL url = new URI("http://" + serverIp + ":" + port + "/download?session=" + encodedSession + "&file=" + encodedPath).toURL();
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10_000);
                    connection.setReadTimeout(30_000);
                    connection.setUseCaches(false);

                    int responseCode = connection.getResponseCode();
                    if (responseCode == 200) {
                        Path targetPath = PathSecurity.resolveUnder(ClientSyncHandler.getTempDir().toPath(), safePath);
                        File targetFile = targetPath.toFile();
                        try (InputStream is = connection.getInputStream()) {
                            byte[] iv = new byte[12];
                            int offset = 0;
                            while (offset < iv.length) {
                                int read = is.read(iv, offset, iv.length - offset);
                                if (read < 0) throw new RuntimeException("Failed to read IV");
                                offset += read;
                            }

                            Cipher cipher = CryptoUtil.getDecryptionCipher(aesKey, iv);
                            Files.createDirectories(targetFile.getParentFile().toPath());

                            try (OutputStream os = Files.newOutputStream(targetFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                                 CipherInputStream cis = new CipherInputStream(is, cipher)) {
                                byte[] buffer = new byte[65536];
                                int read;
                                while ((read = cis.read(buffer)) != -1) {
                                    os.write(buffer, 0, read);
                                }
                            }
                        }

                        String actualHash = FileRegistry.calculateHash(targetFile.toPath());
                        if (!actualHash.equalsIgnoreCase(entry.hash)) {
                            Files.deleteIfExists(targetFile.toPath());
                            throw new SecurityException("Hash mismatch for " + safePath);
                        }
                        ClientSyncHandler.markDownloaded(safePath);

                        final int finalCurrentFileIndex = currentFileIndex;
                        MinecraftClient.getInstance().execute(() -> {
                            progressScreen.addLog(I18n.translate("synchronize.log.downloaded", safePath));
                            float overallProgress = (float) finalCurrentFileIndex / (float) totalFiles;
                            progressScreen.updateProgress(overallProgress, Text.translatable("synchronize.progress.receiving", finalCurrentFileIndex, totalFiles, fileName));
                        });

                    } else if (responseCode == 404) {
                        final int finalCurrentFileIndex = currentFileIndex;
                        Synchronize.LOGGER.error("File missing on server (HTTP 404): {}", safePath);
                        MinecraftClient.getInstance().execute(() -> {
                            progressScreen.addError(I18n.translate("synchronize.log.server_missing_file", fileName));
                            progressScreen.setHasMissingFiles();
                            float overallProgress = (float) finalCurrentFileIndex / (float) totalFiles;
                            progressScreen.updateProgress(overallProgress, Text.translatable("synchronize.progress.receiving", finalCurrentFileIndex, totalFiles, fileName));
                        });
                    } else {
                        Synchronize.LOGGER.error("Failed to download file: {} (HTTP {})", safePath, responseCode);
                        final int code = responseCode;
                        MinecraftClient.getInstance().execute(() -> progressScreen.addError(I18n.translate("synchronize.log.http_error", fileName, code)));
                        throw new RuntimeException("HTTP Error " + responseCode);
                    }
                }

                MinecraftClient.getInstance().execute(() -> progressScreen.setNetworkFinished(() ->
                    ClientSyncHandler.startApplying(filePaths, progressScreen, onComplete)
                ));

            } catch (Exception e) {
                Synchronize.LOGGER.error("External download sequence failed", e);
                MinecraftClient.getInstance().execute(() -> {
                    if (MinecraftClient.getInstance().getNetworkHandler() != null) {
                        MinecraftClient.getInstance().getNetworkHandler().getConnection().disconnect(Text.translatable("synchronize.disconnect.download_failed", String.valueOf(e.getMessage())));
                    }
                });
            }
        }, "Synchronize-ExternalDownload").start();
    }
}