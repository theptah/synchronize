package net.ptah.synchronize.util;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class NetworkUtil {
    private NetworkUtil() {
    }

    public static String getIp(SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            if (inet.getAddress() != null) {
                return inet.getAddress().getHostAddress();
            }
            return inet.getHostString();
        }
        return address != null ? address.toString() : "unknown";
    }
}
