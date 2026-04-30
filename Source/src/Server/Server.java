package Server;

import CommonClasses.Auction;
import Packets.PacketMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Singleton server managing all auctions and connected clients.
 * <p>
 * The {@code Server} maintains maps of active auctions and client handlers,
 * and provides utility methods for broadcasting packets to groups of clients.
 * </p>
 */
public class Server {

    private static Server instance;

    private Map<Integer, Auction> auctions;
    private Map<String, ClientHandler> clientHandlers;

    private Server() {
        auctions = new HashMap<>();
        clientHandlers = new HashMap<>();
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
     * Returns the map of active auctions keyed by auction ID.
     *
     * @return the auctions map
     */
    public Map<Integer, Auction> getAuctions() {
        return auctions;
    }

    /**
     * Adds an auction to the server's active auctions.
     *
     * @param auction the auction to add
     */
    public void addAuction(Auction auction) {
        auctions.put(auction.getId(), auction);
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
            if (clientHandlers.containsKey(client.getUsername())) {
                try {
                    clientHandlers.get(client.getUsername()).sendPacket(packet);
                } catch (IOException e) {
                    e.printStackTrace();
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
                // Chấp nhận kết nối
                java.net.Socket socket = serverSocket.accept();

                // Tạo một cái tên tạm cho Client (vì chưa login nên chưa biết username)
                Client guestClient = new Client("Guest_" + socket.getPort());

                // Tạo Handler để xử lý riêng cho người này
                ClientHandler handler = new ClientHandler(guestClient, socket);

                // Chạy luồng riêng (Multi-thread)
                new Thread(handler).start();
            }
        } catch (java.io.IOException e) {
            System.err.println(" Lỗi khởi động Server: " + e.getMessage());
        }
    }
}
