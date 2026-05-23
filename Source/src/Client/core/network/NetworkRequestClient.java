package Client.core.network;

import Packets.MessageType;
import Packets.PacketMessage;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Small synchronous socket client for service-layer request/response calls.
 */
public final class NetworkRequestClient {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 12345;
    private static final int CONNECT_TIMEOUT_MS = 1200;
    private static final int READ_TIMEOUT_MS = 5000;

    private NetworkRequestClient() {
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty("auction.network.enabled", "true"));
    }

    public static PacketMessage request(MessageType requestType, Serializable payload,
                                        MessageType expectedResponse) throws IOException {
        return request(requestType, payload, EnumSet.of(expectedResponse));
    }

    public static PacketMessage request(MessageType requestType, Serializable payload,
                                        Set<MessageType> expectedResponses) throws IOException {
        String host = System.getProperty("auction.server.host", DEFAULT_HOST);
        int port = Integer.getInteger("auction.server.port", DEFAULT_PORT);

        try (RequestConnection connection = new RequestConnection(host, port)) {
            connection.send(new PacketMessage(requestType, payload));
            while (true) {
                PacketMessage response = connection.read();
                if (expectedResponses.contains(response.getMessageType())) {
                    return response;
                }
                if (response.getMessageType() == MessageType.AUCTION_ACTION_RESPONSE) {
                    return response;
                }
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Timed out waiting for server response", e);
        }
    }

    private static final class RequestConnection implements Closeable {
        private final Socket socket;
        private final ObjectOutputStream outputStream;
        private final ObjectInputStream inputStream;

        private RequestConnection(String host, int port) throws IOException {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());
        }

        private void send(PacketMessage packet) throws IOException {
            outputStream.writeObject(packet);
            outputStream.flush();
            outputStream.reset();
        }

        private PacketMessage read() throws IOException {
            try {
                Object received = inputStream.readObject();
                if (received instanceof PacketMessage) {
                    return (PacketMessage) received;
                }
                throw new IOException("Unexpected response from server: " + received);
            } catch (ClassNotFoundException e) {
                throw new IOException("Cannot deserialize server response", e);
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}