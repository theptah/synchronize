package net.ptah.synchronize.network;

import net.ptah.synchronize.config.FileRegistry.FileEntry;
import net.ptah.synchronize.util.HandshakeCrypto;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.ptah.synchronize.compat.Identifiers;

public final class SyncPackets {
    public static final Identifier CLIENT_REJECTED = Identifiers.mod("client_rejected");
    public static final Identifier MOD_VERSION = Identifiers.mod("mod_version");
    public static final Identifier PROMPT = Identifiers.mod("prompt");
    public static final Identifier EXTERNAL_PROMPT = Identifiers.mod("external_prompt");
    public static final Identifier FILE_CHUNK = Identifiers.mod("file_chunk");
    public static final Identifier FILE_ERROR = Identifiers.mod("file_error");
    public static final Identifier TRANSFER_COMPLETE = Identifiers.mod("transfer_complete");
    public static final Identifier HANDSHAKE = Identifiers.mod("handshake");

    public static final int MAX_SYNC_ENTRIES = 512;
    public static final int MAX_PATH_LENGTH = 512;
    public static final int MAX_HASH_LENGTH = 128;
    public static final int MAX_CHUNK_SIZE = 65536;
    public static final int MAX_TOTAL_CHUNKS = 8192;
    public static final int MAX_SESSION_ID_LENGTH = 128;
    public static final int MAX_VERSION_LENGTH = 64;

    private SyncPackets() {}

    public static void writeFileEntryMap(PacketByteBuf buf, Map<String, FileEntry> map) {
        if (map.size() > MAX_SYNC_ENTRIES) throw new IllegalArgumentException("Too many sync entries");
        buf.writeVarInt(map.size());
        for (Map.Entry<String, FileEntry> e : map.entrySet()) {
            buf.writeString(e.getKey());
            FileEntry entry = e.getValue();
            buf.writeString(entry.hash);
            buf.writeString(entry.destination);
            buf.writeBoolean(entry.optional);
            buf.writeBoolean(entry.restart);
        }
    }

    public static Map<String, FileEntry> readFileEntryMap(PacketByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_SYNC_ENTRIES) throw new IllegalArgumentException("Invalid sync entry count");
        Map<String, FileEntry> result = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = buf.readString(MAX_PATH_LENGTH);
            String hash = buf.readString(MAX_HASH_LENGTH);
            String destination = buf.readString(MAX_PATH_LENGTH);
            boolean optional = buf.readBoolean();
            boolean restart = buf.readBoolean();
            if (result.put(key, new FileEntry(hash, destination, optional, restart)) != null) {
                throw new IllegalArgumentException("Duplicate sync entry");
            }
        }
        return result;
    }

    public static void writeResponse(PacketByteBuf buf, boolean accepted, List<String> requestedFiles) {
        if (requestedFiles.size() > MAX_SYNC_ENTRIES) throw new IllegalArgumentException("Too many requested files");
        buf.writeBoolean(accepted);
        buf.writeVarInt(requestedFiles.size());
        for (String file : requestedFiles) buf.writeString(file);
    }

    public static Response readResponse(PacketByteBuf buf) {
        boolean accepted = buf.readBoolean();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_SYNC_ENTRIES) throw new IllegalArgumentException("Invalid requested file count");
        List<String> requested = new ArrayList<>(size);
        Set<String> unique = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            String file = buf.readString(MAX_PATH_LENGTH);
            if (!unique.add(file)) throw new IllegalArgumentException("Duplicate requested file");
            requested.add(file);
        }
        return new Response(accepted, requested);
    }

    public static void writeGarbageInfo(PacketByteBuf buf, Map<String, Boolean> garbage) {
        if (garbage.size() > MAX_SYNC_ENTRIES) throw new IllegalArgumentException("Too many garbage entries");
        buf.writeVarInt(garbage.size());
        for (Map.Entry<String, Boolean> entry : garbage.entrySet()) {
            buf.writeString(entry.getKey());
            buf.writeBoolean(Boolean.TRUE.equals(entry.getValue()));
        }
    }

    public static GarbageInfo readGarbageInfo(PacketByteBuf buf) {
        if (!buf.isReadable()) return new GarbageInfo(Map.of());
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_SYNC_ENTRIES) throw new IllegalArgumentException("Invalid garbage entry count");
        Map<String, Boolean> garbage = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String path = buf.readString(MAX_PATH_LENGTH);
            boolean restart = buf.readBoolean();
            if (garbage.put(path, restart) != null) throw new IllegalArgumentException("Duplicate garbage entry");
        }
        return new GarbageInfo(garbage);
    }

    public static void writeExternalPrompt(PacketByteBuf buf, String sessionId, byte[] aesKey, int port, Map<String, FileEntry> fileEntries, Map<String, Boolean> garbage) {
        if (sessionId == null || sessionId.length() > MAX_SESSION_ID_LENGTH) throw new IllegalArgumentException("Invalid session id");
        if (aesKey == null || aesKey.length != 32) throw new IllegalArgumentException("Invalid AES key length");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
        buf.writeString(sessionId);
        buf.writeByteArray(aesKey);
        buf.writeVarInt(port);
        writeFileEntryMap(buf, fileEntries);
        writeGarbageInfo(buf, garbage);
    }

    public static ExternalPrompt readExternalPrompt(PacketByteBuf buf) {
        String sessionId = buf.readString(MAX_SESSION_ID_LENGTH);
        byte[] aesKey = buf.readByteArray(32);
        if (aesKey.length != 32) throw new IllegalArgumentException("Invalid AES key length");
        int port = buf.readVarInt();
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
        Map<String, FileEntry> fileEntries = readFileEntryMap(buf);
        GarbageInfo garbageInfo = readGarbageInfo(buf);
        return new ExternalPrompt(sessionId, aesKey, port, fileEntries, garbageInfo.garbage());
    }

    public static void writeFileChunk(PacketByteBuf buf, String filePath, byte[] data, int currentChunk, int totalChunks) {
        if (data.length > MAX_CHUNK_SIZE) throw new IllegalArgumentException("Chunk too large");
        if (currentChunk < 1 || totalChunks < 1 || currentChunk > totalChunks || totalChunks > MAX_TOTAL_CHUNKS) {
            throw new IllegalArgumentException("Invalid chunk sequence");
        }
        buf.writeString(filePath);
        buf.writeByteArray(data);
        buf.writeVarInt(currentChunk);
        buf.writeVarInt(totalChunks);
    }

    public static FileChunk readFileChunk(PacketByteBuf buf) {
        String filePath = buf.readString(MAX_PATH_LENGTH);
        byte[] data = buf.readByteArray(MAX_CHUNK_SIZE);
        int currentChunk = buf.readVarInt();
        int totalChunks = buf.readVarInt();
        if (currentChunk < 1 || totalChunks < 1 || currentChunk > totalChunks || totalChunks > MAX_TOTAL_CHUNKS) {
            throw new IllegalArgumentException("Invalid chunk sequence");
        }
        return new FileChunk(filePath, data, currentChunk, totalChunks);
    }

    public static void writeModVersion(PacketByteBuf buf, String version, String sha256) {
        buf.writeString(clamp(version, MAX_VERSION_LENGTH), MAX_VERSION_LENGTH);
        buf.writeString(sha256 == null ? "" : sha256, MAX_HASH_LENGTH);
    }

    public static ModVersion readModVersion(PacketByteBuf buf) {
        String version = buf.readString(MAX_VERSION_LENGTH);
        String sha256 = buf.readString(MAX_HASH_LENGTH);
        if (!sha256.isEmpty() && !sha256.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("Invalid mod hash");
        return new ModVersion(version, sha256.toLowerCase(java.util.Locale.ROOT));
    }

    public static String wireVersion(String version) {
        return clamp(version, MAX_VERSION_LENGTH);
    }

    public static void writeHandshakeOffer(PacketByteBuf buf, byte[] serverNonce) {
        boolean enabled = serverNonce != null && serverNonce.length == HandshakeCrypto.NONCE_LENGTH;
        buf.writeBoolean(enabled);
        if (enabled) buf.writeByteArray(serverNonce);
    }

    public static byte[] readHandshakeOffer(PacketByteBuf buf) {
        if (!buf.isReadable() || !buf.readBoolean()) return null;
        byte[] nonce = buf.readByteArray(HandshakeCrypto.NONCE_LENGTH);
        if (nonce.length != HandshakeCrypto.NONCE_LENGTH) throw new IllegalArgumentException("Invalid handshake nonce");
        return nonce;
    }

    public static void writeHandshakeAttempt(PacketByteBuf buf, byte[] clientNonce, byte[] clientProof) {
        if (clientNonce == null || clientNonce.length != HandshakeCrypto.NONCE_LENGTH
                || clientProof == null || clientProof.length != HandshakeCrypto.PROOF_LENGTH) {
            throw new IllegalArgumentException("Invalid handshake attempt");
        }
        buf.writeBoolean(true);
        buf.writeByteArray(clientNonce);
        buf.writeByteArray(clientProof);
    }

    public static HandshakeAttempt readHandshakeAttempt(PacketByteBuf buf) {
        if (!buf.isReadable() || !buf.readBoolean()) return null;
        byte[] nonce = buf.readByteArray(HandshakeCrypto.NONCE_LENGTH);
        byte[] proof = buf.readByteArray(HandshakeCrypto.PROOF_LENGTH);
        if (nonce.length != HandshakeCrypto.NONCE_LENGTH || proof.length != HandshakeCrypto.PROOF_LENGTH) {
            throw new IllegalArgumentException("Invalid handshake attempt");
        }
        return new HandshakeAttempt(nonce, proof);
    }

    public static void writeHandshakeProof(PacketByteBuf buf, byte[] serverProof) {
        if (serverProof == null || serverProof.length != HandshakeCrypto.PROOF_LENGTH) throw new IllegalArgumentException("Invalid handshake proof");
        buf.writeByteArray(serverProof);
    }

    public static byte[] readHandshakeProof(PacketByteBuf buf) {
        byte[] proof = buf.readByteArray(HandshakeCrypto.PROOF_LENGTH);
        if (proof.length != HandshakeCrypto.PROOF_LENGTH) throw new IllegalArgumentException("Invalid handshake proof");
        return proof;
    }

    private static String clamp(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }

    public record ModVersion(String version, String sha256) {}
    public record HandshakeAttempt(byte[] clientNonce, byte[] clientProof) {}
    public record Response(boolean accepted, List<String> requestedFiles) {}
    public record ExternalPrompt(String sessionId, byte[] aesKey, int port, Map<String, FileEntry> fileHashes, Map<String, Boolean> garbage) {}
    public record GarbageInfo(Map<String, Boolean> garbage) {}
    public record FileChunk(String filePath, byte[] data, int currentChunk, int totalChunks) {}
}
