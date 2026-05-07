package Client;

import CommonClasses.Auction;
import CommonClasses.Bidder;
import Packets.MessageType;
import Packets.PacketMessage;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Small socket client used by JavaFX controllers to talk to the auction server.
 */
public class NetworkClient implements Closeable {

    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private Thread listenerThread;
    private Consumer<PacketMessage> packetListener;

    public void connect(String host, int port, Consumer<PacketMessage> packetListener) throws IOException {
        this.socket = new Socket(host, port);
        this.outputStream = new ObjectOutputStream(socket.getOutputStream());
        this.inputStream = new ObjectInputStream(socket.getInputStream());
        this.packetListener = packetListener;
        startListener();
    }

    public void login(String username, String password) throws IOException {
        send(new PacketMessage(MessageType.LOGIN_REQUEST, new Bidder(username, password, null)));
    }

    public void listAuctions() throws IOException {
        send(new PacketMessage(MessageType.LIST_AUCTIONS, null));
    }

    public void createAuction(Auction auction) throws IOException {
        send(new PacketMessage(MessageType.CREATE_AUCTION, auction));
    }

    public void joinAuction(int auctionId) throws IOException {
        send(new PacketMessage(MessageType.JOIN_AUCTION, auctionId));
    }

    public void leaveAuction(int auctionId) throws IOException {
        send(new PacketMessage(MessageType.LEAVE_AUCTION, auctionId));
    }

    public void placeBid(int auctionId, float bidAmount) throws IOException {
        Map<String, Number> payload = new HashMap<>();
        payload.put("auctionId", auctionId);
        payload.put("bid", bidAmount);
        send(new PacketMessage(MessageType.PLACE_BID, (HashMap<String, Number>) payload));
    }

    public void cancelAuction(int auctionId) throws IOException {
        send(new PacketMessage(MessageType.CANCEL_AUCTION, auctionId));
    }

    public synchronized void send(PacketMessage packet) throws IOException {
        if (outputStream == null) {
            throw new IOException("Client is not connected");
        }
        outputStream.writeObject(packet);
        outputStream.flush();
        outputStream.reset();
    }

    private void startListener() {
        listenerThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object received = inputStream.readObject();
                    if (received instanceof PacketMessage && packetListener != null) {
                        packetListener.accept((PacketMessage) received);
                    }
                }
            } catch (Exception e) {
                if (packetListener != null) {
                    packetListener.accept(new PacketMessage(
                            MessageType.AUCTION_ACTION_RESPONSE,
                            "Disconnected from server: " + e.getMessage()));
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    @Override
    public void close() throws IOException {
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        if (socket != null) {
            socket.close();
        }
    }
}