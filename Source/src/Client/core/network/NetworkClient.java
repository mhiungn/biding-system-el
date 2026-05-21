package Client.core.network;

import CommonClasses.Auction;
import CommonClasses.Bidder;
import Packets.MessageType;
import Packets.PacketMessage;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Socket client used by JavaFX controllers.
 * Handles connection timeout, read timeout and automatic reconnect.
 */
public class NetworkClient implements Closeable {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 10000;
    private static final int DEFAULT_RECONNECT_DELAY_MS = 2000;
    private static final int DEFAULT_MAX_RECONNECT_ATTEMPTS = 5;

    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private Thread listenerThread;
    private Consumer<PacketMessage> packetListener;
    private Consumer<String> statusListener;

    private String host;
    private int port;
    private int connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
    private int readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
    private int reconnectDelayMs = DEFAULT_RECONNECT_DELAY_MS;
    private int maxReconnectAttempts = DEFAULT_MAX_RECONNECT_ATTEMPTS;

    private String lastUsername;
    private String lastPassword;
    private volatile boolean closed = true;
    private volatile boolean reconnecting;

    public synchronized void connect(String host, int port, Consumer<PacketMessage> packetListener) throws IOException {
        connect(host, port, packetListener, null);
    }

    public synchronized void connect(String host, int port, Consumer<PacketMessage> packetListener,
                                     Consumer<String> statusListener) throws IOException {
        this.host = host;
        this.port = port;
        this.packetListener = packetListener;
        this.statusListener = statusListener;
        this.closed = false;
        openSocket();
        startListener();
    }

    public synchronized void setPacketListener(Consumer<PacketMessage> packetListener) {
        this.packetListener = packetListener;
    }

    public synchronized void setStatusListener(Consumer<String> statusListener) {
        this.statusListener = statusListener;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void login(String username, String password) throws IOException {
        this.lastUsername = username;
        this.lastPassword = password;
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
        if (!isConnected() || outputStream == null) {
            throw new IOException("Client is not connected to server");
        }
        outputStream.writeObject(packet);
        outputStream.flush();
        outputStream.reset();
    }

    private synchronized void openSocket() throws IOException {
        closeSocketOnly();
        Socket newSocket = new Socket();
        newSocket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        newSocket.setSoTimeout(readTimeoutMs);
        this.socket = newSocket;
        this.outputStream = new ObjectOutputStream(newSocket.getOutputStream());
        this.inputStream = new ObjectInputStream(newSocket.getInputStream());
        notifyStatus("CONNECTED");
    }

    private void startListener() {
        listenerThread = new Thread(() -> {
            while (!closed) {
                try {
                    Object received = inputStream.readObject();
                    if (received instanceof PacketMessage && packetListener != null) {
                        PacketMessage packet = (PacketMessage) received;
                        System.out.println("[Network] Received " + packet.getMessageType());
                        packetListener.accept(packet);
                    }
                } catch (SocketTimeoutException e) {
                    notifyStatus("WAITING_FOR_SERVER_DATA");
                } catch (EOFException e) {
                    handleConnectionLost("Server closed the connection");
                    break;
                } catch (Exception e) {
                    handleConnectionLost(e.getMessage());
                    break;
                }
            }
        }, "auction-network-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void handleConnectionLost(String reason) {
        if (closed) {
            return;
        }
        notifyStatus("DISCONNECTED: " + reason);
        reconnectInBackground();
    }

    private void reconnectInBackground() {
        if (reconnecting || closed) {
            return;
        }

        reconnecting = true;
        Thread reconnectThread = new Thread(() -> {
            for (int attempt = 1; attempt <= maxReconnectAttempts && !closed; attempt++) {
                notifyStatus("RECONNECTING " + attempt + "/" + maxReconnectAttempts);
                sleepQuietly(reconnectDelayMs);

                try {
                    openSocket();
                    restoreLoginIfPossible();
                    reconnecting = false;
                    startListener();
                    return;
                } catch (IOException e) {
                    notifyStatus("RECONNECT_FAILED: " + e.getMessage());
                }
            }

            reconnecting = false;
            notifyStatus("RECONNECT_GIVE_UP");
        }, "auction-network-reconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    private void restoreLoginIfPossible() throws IOException {
        if (lastUsername != null && lastPassword != null) {
            send(new PacketMessage(MessageType.LOGIN_REQUEST, new Bidder(lastUsername, lastPassword, null)));
        }
    }

    private void notifyStatus(String status) {
        System.out.println("[Network] " + status);
        if (statusListener != null) {
            statusListener.accept(status);
        }
    }

    private void sleepQuietly(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void closeSocketOnly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        } finally {
            socket = null;
            outputStream = null;
            inputStream = null;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        closeSocketOnly();
        notifyStatus("CLOSED");
    }
}
