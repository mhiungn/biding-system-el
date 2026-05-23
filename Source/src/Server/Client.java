package Server;

import Packets.PacketMessage;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.LinkedList;

/**
 * Represents a connected client on the server side.
 * <p>
 * Each {@code Client} holds the client's identity (username), their connection
 * socket, and tracks which auctions they are registered in, as well as how many
 * "high bids" they currently hold across all active auctions.
 * </p>
 */
public class Client implements Serializable {

    private String username;
    private LinkedList<Integer> registeredAuctions;
    private int highBidCount;
    private transient Socket socket;
    private transient ObjectOutputStream outputStream;

    /**
     * Constructs a new Client with the given username.
     *
     * @param username the client's username
     */
    public Client(String username) {
        this.username = username;
        this.registeredAuctions = new LinkedList<>();
        this.highBidCount = 0;
    }

    /**
     * Constructs a new Client with a username and socket connection.
     *
     * @param username the client's username
     * @param socket   the client's connected socket
     */
    public Client(String username, Socket socket) {
        this.username = username;
        this.registeredAuctions = new LinkedList<>();
        this.highBidCount = 0;
        this.socket = socket;
        try {
            if (socket != null) {
                this.outputStream = new ObjectOutputStream(socket.getOutputStream());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LinkedList<Integer> getRegisteredAuctions() {
        return registeredAuctions;
    }

    public void setRegisteredAuctions(LinkedList<Integer> registeredAuctions) {
        this.registeredAuctions = registeredAuctions;
    }

    public int getHighBidCount() {
        return highBidCount;
    }

    /**
     * Increments the count of auctions where this client holds the highest bid.
     */
    public void madeHighBid() {
        highBidCount++;
    }

    /**
     * Decrements the count of auctions where this client holds the highest bid.
     */
    public void lostHighBid() {
        if (highBidCount > 0) {
            highBidCount--;
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Client client = (Client) o;
        return username != null && username.equals(client.username);
    }

    @Override
    public int hashCode() {
        return username != null ? username.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Client{username='" + username + "', highBidCount=" + highBidCount + '}';
    }
}
