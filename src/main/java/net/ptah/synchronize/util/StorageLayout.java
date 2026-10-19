package net.ptah.synchronize.util;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class StorageLayout {
    private final String source;
    private int cursor;

    private StorageLayout(String source) { this.source = source; }

    public static Map<String, Object> readJson(Path file) throws IOException {
        check(file);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return new LinkedHashMap<>();
        StorageLayout parser = new StorageLayout(Files.readString(file, StandardCharsets.UTF_8));
        Object value = parser.value(0);
        parser.space();
        if (!(value instanceof Map<?, ?>) || parser.cursor != parser.source.length()) throw new IOException("Invalid JSON object: " + file);
        return object(value);
    }

    public static Map<String, Object> object(Object value) throws IOException {
        if (!(value instanceof Map<?, ?> map)) throw new IOException("Expected JSON object");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new IOException("Invalid JSON key");
            result.put(key, entry.getValue());
        }
        return result;
    }

    public static void writeJson(Path file, Map<String, Object> value) throws IOException {
        check(file);
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        check(temp);
        StringBuilder json = new StringBuilder();
        encode(json, value, 0);
        Files.writeString(temp, json.append('\n'), StandardCharsets.UTF_8);
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static void check(Path file) throws IOException {
        for (Path path = file.toAbsolutePath().normalize(); path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("Linked storage path: " + path);
        }
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file)) throw new IOException("Invalid storage file: " + file);
    }

    public static void migrateSharedDirectories(Path modDirectory) throws IOException {
        Path legacy = modDirectory.resolve("profiles/shared-directories.properties");
        check(legacy);
        if (!Files.exists(legacy, LinkOption.NOFOLLOW_LINKS)) return;
        Properties properties = new Properties();
        try (var in = Files.newInputStream(legacy)) { properties.load(in); }
        Path config = modDirectory.resolve("config.json");
        Map<String, Object> settings = readJson(config);
        Object current = settings.get("additionalSharedDirectories");
        if (current != null && !(current instanceof String)) throw new IOException("Invalid additionalSharedDirectories setting");
        LinkedHashSet<String> directories = new LinkedHashSet<>();
        for (String list : List.of(current == null ? "" : (String) current, properties.getProperty("additionalSharedDirectories", ""))) {
            for (String entry : list.split(",")) if (!entry.trim().isEmpty()) directories.add(entry.trim());
        }
        settings.put("additionalSharedDirectories", String.join(",", directories));
        writeJson(config, settings);
        Files.delete(legacy);
    }

    public static void migrateTemporaryDirectories(Path modDirectory) throws IOException {
        Path temp = modDirectory.resolve("temp");
        moveTree(modDirectory.resolve("temp_verify"), temp.resolve("verify"));
        moveTree(modDirectory.resolve("profiles/downloads"), temp.resolve("downloads"));
        if (!Files.exists(temp, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(temp) || !Files.isDirectory(temp)) throw new IOException("Unsafe temporary directory");
        try (var children = Files.list(temp)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                if (!List.of("verify", "downloads", "transfer").contains(name)) moveTree(child, temp.resolve("transfer").resolve(name));
            }
        }
    }

    private static void moveTree(Path source, Path target) throws IOException {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return;
        for (Path path : List.of(source, target)) {
            for (Path part = path.toAbsolutePath().normalize(); part != null; part = part.getParent()) {
                if (Files.isSymbolicLink(part)) throw new IOException("Linked migration path: " + part);
            }
        }
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(target);
            try (var children = Files.list(source)) {
                for (Path child : children.toList()) moveTree(child, target.resolve(child.getFileName()));
            }
            Files.delete(source);
        } else if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(target.getParent());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.mismatch(source, target) != -1) throw new IOException("Conflicting temporary migration: " + target);
                Files.delete(source);
            } else Files.move(source, target);
        } else throw new IOException("Unsupported migration file: " + source);
    }

    private void space() { while (cursor < source.length() && " \r\n\t".indexOf(source.charAt(cursor)) >= 0) cursor++; }
    private boolean take(char c) { space(); if (cursor < source.length() && source.charAt(cursor) == c) { cursor++; return true; } return false; }
    private IOException invalid() { return new IOException("Invalid JSON at offset " + cursor); }

    private Object value(int depth) throws IOException {
        if (depth > 64) throw invalid();
        space();
        if (cursor >= source.length()) throw invalid();
        char c = source.charAt(cursor);
        if (c == '"') return string();
        if (take('{')) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (take('}')) return map;
            do {
                space();
                String key = string();
                if (!take(':') || map.containsKey(key)) throw invalid();
                map.put(key, value(depth + 1));
                if (take('}')) return map;
            } while (take(','));
            throw invalid();
        }
        if (take('[')) {
            List<Object> list = new ArrayList<>();
            if (take(']')) return list;
            do { list.add(value(depth + 1)); if (take(']')) return list; } while (take(','));
            throw invalid();
        }
        for (String literal : List.of("true", "false", "null")) {
            if (source.startsWith(literal, cursor)) { cursor += literal.length(); return literal.equals("null") ? null : Boolean.valueOf(literal); }
        }
        int from = cursor;
        while (cursor < source.length() && "-+0123456789.eE".indexOf(source.charAt(cursor)) >= 0) cursor++;
        String number = source.substring(from, cursor);
        if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw invalid();
        try { return new BigDecimal(number); } catch (NumberFormatException e) { throw invalid(); }
    }

    private String string() throws IOException {
        if (cursor >= source.length() || source.charAt(cursor++) != '"') throw invalid();
        StringBuilder result = new StringBuilder();
        while (cursor < source.length()) {
            char c = source.charAt(cursor++);
            if (c == '"') return result.toString();
            if (c < 32) throw invalid();
            if (c == '\\') {
                if (cursor >= source.length()) throw invalid();
                c = source.charAt(cursor++);
                switch (c) {
                    case '"', '\\', '/' -> result.append(c);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (cursor + 4 > source.length() || !source.substring(cursor, cursor + 4).matches("[0-9a-fA-F]{4}")) throw invalid();
                        try { result.append((char) Integer.parseInt(source.substring(cursor, cursor + 4), 16)); }
                        catch (NumberFormatException e) { throw invalid(); }
                        cursor += 4;
                    }
                    default -> throw invalid();
                }
            } else result.append(c);
        }
        throw invalid();
    }

    private static void encode(StringBuilder out, Object value, int depth) throws IOException {
        if (depth > 64) throw new IOException("JSON nesting too deep");
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IOException("Invalid JSON key");
                if (!first) out.append(','); first = false;
                out.append('\n').append("  ".repeat(depth + 1));
                quote(out, key); out.append(": "); encode(out, entry.getValue(), depth + 1);
            }
            if (!map.isEmpty()) out.append('\n').append("  ".repeat(depth));
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) { if (i > 0) out.append(", "); encode(out, list.get(i), depth + 1); }
            out.append(']');
        } else if (value instanceof String text) quote(out, text);
        else if (value == null || value instanceof Boolean || value instanceof BigDecimal) out.append(value);
        else throw new IOException("Unsupported JSON value");
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 32 || Character.isSurrogate(c)) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        out.append('"');
    }
}
