package Packets;

/**
 * Shared network configuration keys and defaults.
 */
public final class NetworkConfig {
    public static final String HOST_PROPERTY = "auction.server.host";
    public static final String PORT_PROPERTY = "auction.server.port";
    public static final String NETWORK_ENABLED_PROPERTY = "auction.network.enabled";

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 12345;
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 1200;
    public static final int DEFAULT_READ_TIMEOUT_MS = 5000;
    public static final int DEFAULT_CLIENT_READ_TIMEOUT_MS = 10000;

    private NetworkConfig() {
    }

    public static String host() {
        return System.getProperty(HOST_PROPERTY, DEFAULT_HOST);
    }

    public static int port() {
        return Integer.getInteger(PORT_PROPERTY, DEFAULT_PORT);
    }

    public static boolean networkEnabled() {
        return Boolean.parseBoolean(System.getProperty(NETWORK_ENABLED_PROPERTY, "true"));
    }
}