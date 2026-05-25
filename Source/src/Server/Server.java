package Server;

import Packets.NetworkConfig;
import Packets.PacketMessage;
import Server.service.AuctionFinalizationService;

import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Singleton server managing connected clients and network broadcasting.
 * <p>
 * The {@code Server} maintains a map of connected client handlers and provides
 * utility methods for broadcasting packets to groups of clients.
 * </p>
 * <p>
 * <b>Phase 2 refactor:</b> Auction state management has been moved to
 * {@link CommonClasses.AuctionManager} (the single source of truth for all
 * auction data). The server no longer maintains its own auction map.
 * </p>
 */
public class Server {

    private static Server instance;

    private Map<String, ClientHandler> clientHandlers;
    private Map<String, ClientHandler> pushClientHandlers;

    private Server() {
        clientHandlers = new ConcurrentHashMap<>();
        pushClientHandlers = new ConcurrentHashMap<>();
    }

    /**
     * Returns the singleton Server instance.
     *
     * @return the Server instance
     */
    public static synchronized Server getInstance() {
        if (instance == null) {
            instance = new Server();
        }
        return instance;
    }

    /**
     * Returns the map of client handlers keyed by username.
     *
     * @return the client handlers map
     */
    public Map<String, ClientHandler> getClientHandlers() {
        return clientHandlers;
    }

    public Map<String, ClientHandler> getPushClientHandlers() {
        return pushClientHandlers;
    }

    public void registerPushClient(String username, ClientHandler handler) {
        if (username != null && handler != null) {
            pushClientHandlers.put(username, handler);
        }
    }

    public void unregisterPushClient(String username, ClientHandler handler) {
        if (username != null && handler != null) {
            pushClientHandlers.remove(username, handler);
        }
    }

    /**
     * Sends a packet to a list of clients.
     *
     * @param clients the list of clients to send the packet to
     * @param packet  the packet to send
     */
    public void sendPackets(LinkedList<Client> clients, PacketMessage packet) {
        for (Client client : clients) {
            ClientHandler handler = clientHandlers.get(client.getUsername());
            if (handler != null) {
                try {
                    handler.sendPacket(packet);
                } catch (IOException e) {
                    System.err.println("[Network] Cannot send packet to "
                            + client.getUsername() + ": " + e.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) {
        int port = NetworkConfig.port();
        Server serverInstance = Server.getInstance();
        runAuctionFinalizer("server startup");
        startAuctionFinalizationScheduler();

        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(port)) {
            System.out.println(" Server Đấu Giá đã khởi động tại cổng: " + port);
            System.out.println(" Đang đợi các bạn Client kết nối vào...");

            while (true) {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(NetworkConfig.DEFAULT_CLIENT_READ_TIMEOUT_MS);
                System.out.println("[Network] Client connected from " + socket.getRemoteSocketAddress());

                Client guestClient = new Client("Guest_" + socket.getPort());
                ClientHandler handler = new ClientHandler(guestClient, socket);
                new Thread(handler, "client-handler-" + socket.getPort()).start();
            }
        } catch (java.io.IOException e) {
            System.err.println(" Lỗi khởi động Server: " + e.getMessage());
        }
    }
    private static void runAuctionFinalizer(String trigger) {
        AuctionFinalizationService.getInstance().finalizeEndedAuctionsSafely(trigger);
    }

    private static void startAuctionFinalizationScheduler() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "auction-finalization-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(
                () -> runAuctionFinalizer("server scheduler"),
                60,
                60,
                TimeUnit.SECONDS);
    }
}
