package Client.core.network;

import Client.features.auth.SessionManager;
import Packets.NetworkConfig;
import Packets.PacketMessage;
import javafx.application.Platform;

import java.io.IOException;

public class NetworkPushManager {
    private static final NetworkPushManager INSTANCE = new NetworkPushManager();

    public static NetworkPushManager getInstance() {
        return INSTANCE;
    }

    private final PushEventRouter router = new PushEventRouter();
    private NetworkClient client;

    private NetworkPushManager() {
    }

    public synchronized void register(PushEventListener listener) {
        router.register(listener);
        startIfPossible();
    }

    public synchronized void unregister(PushEventListener listener) {
        router.unregister(listener);
    }

    public synchronized void startIfPossible() {
        if (!NetworkConfig.networkEnabled()) {
            return;
        }
        String token = SessionManager.getAuthToken();
        if (token == null || token.isBlank()) {
            return;
        }
        if (client != null && client.isConnected()) {
            return;
        }

        NetworkClient newClient = new NetworkClient();
        try {
            newClient.connectForPush(
                    NetworkConfig.host(),
                    NetworkConfig.port(),
                    token,
                    this::handlePacket,
                    status -> System.out.println("[NetworkPushManager] " + status));
            client = newClient;
        } catch (IOException e) {
            System.err.println("[NetworkPushManager] Push connection unavailable: " + e.getMessage());
            newClient.close();
        }
    }

    public synchronized void stop() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private void handlePacket(PacketMessage packet) {
        router.route(packet, Platform::runLater);
    }
}
