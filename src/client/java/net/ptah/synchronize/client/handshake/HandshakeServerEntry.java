package net.ptah.synchronize.client.handshake;

public interface HandshakeServerEntry {
    String synchronize$getHandshakeCode();

    void synchronize$setHandshakeCode(String code);
}
