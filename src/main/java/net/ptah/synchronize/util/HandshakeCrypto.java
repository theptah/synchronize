package net.ptah.synchronize.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HandshakeCrypto {
    public static final int NONCE_LENGTH = 32;
    public static final int PROOF_LENGTH = 32;
    public static final int MAX_CODE_LENGTH = 128;
    private static final String CLIENT_LABEL = "synchronize-handshake-client-v1";
    private static final String SERVER_LABEL = "synchronize-handshake-server-v1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private HandshakeCrypto() {
    }

    public static boolean isUsableCode(String code) {
        return code != null && !code.trim().isEmpty();
    }

    public static String normalizeCode(String code) {
        return code == null ? "" : code.trim();
    }

    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_GROUPS = 5;
    private static final int CODE_GROUP_LENGTH = 4;

    public static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_GROUPS * (CODE_GROUP_LENGTH + 1));
        for (int g = 0; g < CODE_GROUPS; g++) {
            if (g > 0) sb.append('-');
            for (int i = 0; i < CODE_GROUP_LENGTH; i++) {
                sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
            }
        }
        return sb.toString();
    }

    public static byte[] newNonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        return nonce;
    }

    public static byte[] clientProof(String code, byte[] serverNonce, byte[] clientNonce, String serverVersion, String serverHash,
            String clientVersion, String clientHash) {
        return proof(CLIENT_LABEL, code, serverNonce, clientNonce, serverVersion, serverHash, clientVersion, clientHash);
    }

    public static byte[] serverProof(String code, byte[] serverNonce, byte[] clientNonce, String serverVersion, String serverHash,
            String clientVersion, String clientHash) {
        return proof(SERVER_LABEL, code, serverNonce, clientNonce, serverVersion, serverHash, clientVersion, clientHash);
    }

    public static boolean matches(byte[] expected, byte[] actual) {
        return expected != null && actual != null && MessageDigest.isEqual(expected, actual);
    }

    private static byte[] proof(String label, String code, byte[] serverNonce, byte[] clientNonce, String serverVersion,
            String serverHash, String clientVersion, String clientHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(normalizeCode(code).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            update(mac, label.getBytes(StandardCharsets.UTF_8));
            update(mac, serverNonce);
            update(mac, clientNonce);
            update(mac, text(serverVersion));
            update(mac, text(serverHash).toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            update(mac, text(clientVersion));
            update(mac, text(clientHash).toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return mac.doFinal();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static void update(Mac mac, String value) {
        update(mac, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void update(Mac mac, byte[] data) {
        byte[] bytes = data == null ? new byte[0] : data;
        mac.update(new byte[] {(byte) (bytes.length >>> 24), (byte) (bytes.length >>> 16), (byte) (bytes.length >>> 8), (byte) bytes.length});
        mac.update(bytes);
    }
}
