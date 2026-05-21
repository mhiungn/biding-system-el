package Server;

import Packets.PacketMessage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final int PORT = 12345;
    private static final int CLIENT_READ_TIMEOUT_MS = 10000;
    private static Server instance;

    private Map<String, ClientHandler> clientHandlers;

    private Server() {
        clientHandlers = new ConcurrentHashMap<>();
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
        int port = 12345; //
        Server serverInstance = Server.getInstance();

        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(port)) {
            System.out.println(" Server Đấu Giá đã khởi động tại cổng: " + port);
            System.out.println(" Đang đợi các bạn Client kết nối vào...");

            while (true) {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(CLIENT_READ_TIMEOUT_MS);
                System.out.println("[Network] Client connected from " + socket.getRemoteSocketAddress());

                Client guestClient = new Client("Guest_" + socket.getPort());
                ClientHandler handler = new ClientHandler(guestClient, socket);
                new Thread(handler, "client-handler-" + socket.getPort()).start();
            }
        } catch (java.io.IOException e) {
            System.err.println(" Lỗi khởi động Server: " + e.getMessage());
        }
    }
}
