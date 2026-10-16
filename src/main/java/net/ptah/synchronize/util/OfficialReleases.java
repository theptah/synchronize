package net.ptah.synchronize.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;
import java.util.regex.Pattern;

public final class OfficialReleases {
    public static final String GITHUB_OWNER = "theptah";
    public static final String GITHUB_REPO = "synchronize";
    public static final String REPOSITORY_URL = "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO;
    public static final String RELEASES_URL = REPOSITORY_URL + "/releases";
    public static final String RELEASE_TAG_FORMAT = "v%s";

    private static final String API_RELEASES = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases?per_page=100";
    private static final String DOWNLOAD_PREFIX = REPOSITORY_URL + "/releases/download/";
    private static final Pattern SAFE_JAR_NAME = Pattern.compile("[A-Za-z0-9._+\\-]{1,128}\\.jar");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    public static final long MAX_JAR_BYTES = 64L * 1024L * 1024L;

    private OfficialReleases() {
    }

    public record Asset(String name, String downloadUrl, String sha256, long size) {}

    public record Release(String tag, String pageUrl, boolean prerelease, List<Asset> assets) {}

    public record Lookup(Release versionRelease, Release matchedRelease, Asset matchedAsset, Path downloadedFile) {
        public boolean verified() {
            return matchedAsset != null;
        }

        public String bestPageUrl(String version) {
            if (matchedRelease != null) return matchedRelease.pageUrl();
            if (versionRelease != null) return versionRelease.pageUrl();
            return fallbackReleasePage(version);
        }
    }

    public static String fallbackReleasePage(String version) {
        if (version == null || version.isBlank() || "unknown".equals(version)) {
            return RELEASES_URL;
        }
        return RELEASES_URL + "/tag/" + String.format(Locale.ROOT, RELEASE_TAG_FORMAT, version);
    }

    public static String normalizeVersion(String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v.toLowerCase(Locale.ROOT);
    }

    public static int compareVersions(String a, String b) {
        String[] pa = normalizeVersion(a).split("[.+\\-]");
        String[] pb = normalizeVersion(b).split("[.+\\-]");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            String sa = i < pa.length ? pa[i] : "0";
            String sb = i < pb.length ? pb[i] : "0";
            int cmp;
            if (sa.matches("\\d{1,9}") && sb.matches("\\d{1,9}")) {
                cmp = Integer.compare(Integer.parseInt(sa), Integer.parseInt(sb));
            } else {
                cmp = sa.compareTo(sb);
            }
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    public static boolean isSafeJarName(String name) {
        return name != null && SAFE_JAR_NAME.matcher(name).matches();
    }

    private static boolean isModJar(Asset asset) {
        String lower = asset.name().toLowerCase(Locale.ROOT);
        return isSafeJarName(asset.name())
                && !lower.endsWith("-sources.jar")
                && !lower.endsWith("-javadoc.jar")
                && !lower.contains("-dev")
                && asset.downloadUrl() != null
                && asset.downloadUrl().startsWith(DOWNLOAD_PREFIX);
    }

    public static List<Release> fetchReleases() throws IOException {
        List<Release> releases = new ArrayList<>();
        for (int page = 1; ; page++) {
            int received = fetchReleasePage(API_RELEASES + "&page=" + page, releases);
            if (received < 100) return releases;
        }
    }

    private static int fetchReleasePage(String url, List<Release> releases) throws IOException {
        HttpURLConnection connection = open(url);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        int code = connection.getResponseCode();
        if (code != 200) {
            connection.disconnect();
            throw new IOException("GitHub API returned HTTP " + code);
        }
        try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonArray()) {
                throw new IOException("Unexpected GitHub API response");
            }
            for (JsonElement el : root.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                if (bool(obj, "draft")) continue;
                List<Asset> assets = new ArrayList<>();
                JsonElement assetsEl = obj.get("assets");
                if (assetsEl != null && assetsEl.isJsonArray()) {
                    for (JsonElement a : (JsonArray) assetsEl) {
                        if (!a.isJsonObject()) continue;
                        JsonObject ao = a.getAsJsonObject();
                        String digest = str(ao, "digest");
                        String sha = null;
                        if (digest != null && digest.toLowerCase(Locale.ROOT).startsWith("sha256:")) {
                            String hex = digest.substring("sha256:".length());
                            if (SHA256.matcher(hex).matches()) sha = hex.toLowerCase(Locale.ROOT);
                        }
                        long size = ao.has("size") && ao.get("size").isJsonPrimitive() ? ao.get("size").getAsLong() : -1L;
                        assets.add(new Asset(str(ao, "name"), str(ao, "browser_download_url"), sha, size));
                    }
                }
                String page = str(obj, "html_url");
                if (page == null || !page.startsWith(RELEASES_URL)) page = RELEASES_URL;
                releases.add(new Release(str(obj, "tag_name"), page, bool(obj, "prerelease"), assets));
            }
            return root.getAsJsonArray().size();
        } finally {
            connection.disconnect();
        }
    }

    public static Lookup lookup(String version, String expectedSha256, Path scratchDir) throws IOException {
        return lookup(fetchReleases(), version, expectedSha256, scratchDir);
    }

    public static Lookup lookup(List<Release> releases, String version, String expectedSha256, Path scratchDir) throws IOException {
        String wanted = normalizeVersion(version);
        Release versionRelease = null;
        for (Release r : releases) {
            if (r.tag() != null && normalizeVersion(r.tag()).equals(wanted)) {
                versionRelease = r;
                break;
            }
        }
         
         
        if (versionRelease == null && wanted.contains("+")) {
            String baseVersion = wanted.substring(0, wanted.indexOf('+'));
            for (Release r : releases) {
                if (r.tag() != null && normalizeVersion(r.tag()).equals(baseVersion)) {
                    versionRelease = r;
                    break;
                }
            }
        }

        if (expectedSha256 == null || expectedSha256.isBlank()) {
            return new Lookup(versionRelease, null, null, null);
        }

        for (Release r : releases) {
            for (Asset a : r.assets()) {
                if (isModJar(a) && a.sha256() != null && a.sha256().equalsIgnoreCase(expectedSha256)) {
                    return new Lookup(versionRelease, r, a, null);
                }
            }
        }

        IOException downloadFailure = null;
        if (versionRelease != null && scratchDir != null) {
            for (Asset a : versionRelease.assets()) {
                if (!isModJar(a) || a.sha256() != null) continue;
                Path target = scratchDir.resolve(a.name());
                try {
                    String hash = download(a, target, null);
                    if (hash.equalsIgnoreCase(expectedSha256)) {
                        return new Lookup(versionRelease, versionRelease, a, target);
                    }
                } catch (IOException e) {
                    downloadFailure = e;
                }
                Files.deleteIfExists(target);
            }
        }
         
         
        if (downloadFailure != null) {
            throw new IOException("Could not download an official jar to complete verification", downloadFailure);
        }
        return new Lookup(versionRelease, null, null, null);
    }

    public static String download(Asset asset, Path target, LongConsumer progress) throws IOException {
        if (!isModJar(asset)) {
            throw new IOException("Refusing to download a non-official asset");
        }
        HttpURLConnection connection = open(asset.downloadUrl());
        connection.setRequestProperty("Accept", "application/octet-stream");
        int code = connection.getResponseCode();
        if (code != 200) {
            connection.disconnect();
            throw new IOException("HTTP " + code);
        }
        long declared = connection.getContentLengthLong();
        if (declared > MAX_JAR_BYTES) {
            connection.disconnect();
            throw new IOException("File too large");
        }
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                    total += read;
                    if (total > MAX_JAR_BYTES) throw new IOException("File too large");
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    if (progress != null) progress.accept(total);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        } catch (Exception e) {
            Files.deleteIfExists(target);
            throw new IOException(e);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URI(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Synchronize/" + ModJar.version());
            return connection;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static String str(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static boolean bool(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsBoolean();
    }
}