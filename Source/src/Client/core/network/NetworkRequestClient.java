package Client.core.network;

import Client.features.auth.SessionManager;
import Packets.MessageType;
import Packets.NetworkConfig;
import Packets.NetworkErrorPayload;
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

    private NetworkRequestClient() {
    }

    public static boolean isEnabled() {
        return NetworkConfig.networkEnabled();
    }

    public static boolean ping() {
        try {
            PacketMessage response = request(MessageType.PING, null, MessageType.PONG);
            return response.getMessageType() == MessageType.PONG;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isAuthenticationFailure(IOException exception) {
        String message = exception.getMessage();
        return message != null
                && (message.startsWith("AUTH_REQUIRED:") || message.startsWith("AUTH_INVALID:"));
    }

    public static PacketMessage request(MessageType requestType, Serializable payload,
                                        MessageType expectedResponse) throws IOException {
        return request(requestType, payload, EnumSet.of(expectedResponse));
    }

    public static PacketMessage request(MessageType requestType, Serializable payload,
                                        Set<MessageType> expectedResponses) throws IOException {
        try (RequestConnection connection = new RequestConnection(NetworkConfig.host(), NetworkConfig.port())) {
            connection.send(new PacketMessage(requestType, payload, SessionManager.getAuthToken()));
            while (true) {
                PacketMessage response = connection.read();
                if (expectedResponses.contains(response.getMessageType())) {
                    return response;
                }
                if (response.getMessageType() == MessageType.NETWORK_ERROR) {
                    throw new IOException(formatNetworkError(response));
                }
                if (response.getMessageType() == MessageType.AUCTION_ACTION_RESPONSE) {
                    return response;
                }
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Timed out waiting for server response", e);
        }
    }

    public static boolean logout() {
        String token = SessionManager.getAuthToken();
        if (token == null || token.isBlank()) {
            SessionManager.clear();
            return true;
        }

        try {
            request(MessageType.LOGOUT_REQUEST, null, MessageType.LOGOUT_RESPONSE);
            return true;
        } catch (IOException e) {
            System.err.println("[NetworkRequestClient] Network logout failed: " + e.getMessage());
            return false;
        } finally {
            SessionManager.clear();
        }
    }

    private static final class RequestConnection implements Closeable {
        private final Socket socket;
        private final ObjectOutputStream outputStream;
        private final ObjectInputStream inputStream;

        private RequestConnection(String host, int port) throws IOException {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), NetworkConfig.DEFAULT_CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(NetworkConfig.DEFAULT_READ_TIMEOUT_MS);
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

    private static String formatNetworkError(PacketMessage response) {
        if (response.getPayload() instanceof NetworkErrorPayload) {
            NetworkErrorPayload error = (NetworkErrorPayload) response.getPayload();
            return error.getCode() + ": " + error.getMessage();
        }
        return "Network request failed";
    }
}
