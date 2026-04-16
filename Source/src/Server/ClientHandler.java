package Server;

import Packets.PacketMessage;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Handles the communication with a single connected client.
 * <p>
 * Each {@code ClientHandler} runs on its own thread and manages sending/receiving
 * packets to/from the associated {@link Client}.
 * </p>
 */
public class ClientHandler implements Runnable {

    private Client client;
    private Socket socket;
    private ObjectOutputStream outputStream;

    /**
     * Constructs a ClientHandler for the given client and socket.
     *
     * @param client the client this handler manages
     * @param socket the socket connection to the client
     */
    public ClientHandler(Client client, Socket socket) {
        this.client = client;
        this.socket = socket;
        try {
            this.outputStream = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the client managed by this handler.
     *
     * @return the {@link Client}
     */
    public Client getClient() {
        return client;
    }

    /**
     * Sends a packet to the client.
     *
     * @param packet the packet to send
     * @throws IOException if an I/O error occurs
     */
    public void sendPacket(PacketMessage packet) throws IOException {
        if (outputStream != null) {
            outputStream.writeObject(packet);
            outputStream.flush();
        }
    }

    @Override
    public void run() {
        // Main client handler loop — reads incoming packets from the client
        // Implementation depends on the full networking stack
    }
}
