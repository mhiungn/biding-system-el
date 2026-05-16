package Server;

import CommonClasses.Auction;
import CommonClasses.Bid;
import CommonClasses.User;
import Packets.MessageType;
import Packets.PacketMessage;
import Payload.AuctionUpdatePayload;
import Server.dao.UserDAO;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
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
    public synchronized  void sendPacket(PacketMessage packet) throws IOException {
        if (outputStream != null) {
            outputStream.writeObject(packet);
            outputStream.flush();
            outputStream.reset();
        }
    }

    @Override
    public void run() {
        try (ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {
            System.out.println(" [Network] Listening to client: " + client.getUsername());

            while (true) {
                Object received = inputStream.readObject();
                if (received instanceof PacketMessage) {
                    handlePacket((PacketMessage) received);
                }
            }
        } catch (EOFException e) {
            System.out.println(" [Network] Client disconnected.");
        } catch (Exception e) {
            System.err.println(" [Network] Connection error: " + e.getMessage());
        } finally {
            if (client != null && client.getUsername() != null) {
                Server.getInstance().getClientHandlers().remove(client.getUsername());
            }
        }
    }

    private void handlePacket(PacketMessage request) throws IOException {
        try {
            MessageType type = request.getMessageType();

            if (type == MessageType.LOGIN_REQUEST) {
                handleLogin(request);
            } else if (type == MessageType.LIST_AUCTIONS) {
                requireLogin();
                sendPacket(new PacketMessage(MessageType.LIST_AUCTIONS,
                        (java.io.Serializable) auctionService.getAllAuctions()));
            } else if (type == MessageType.CREATE_AUCTION) {
                requireLogin();
                handleCreateAuction(request);
            } else if (type == MessageType.JOIN_AUCTION) {
                requireLogin();
                handleJoinAuction(request);
            } else if (type == MessageType.LEAVE_AUCTION) {
                requireLogin();
                handleLeaveAuction(request);
            } else if (type == MessageType.PLACE_BID) {
                requireLogin();
                handlePlaceBid(request);
            } else if (type == MessageType.CANCEL_AUCTION) {
                requireLogin();
                handleCancelAuction(request);
            } else {
                sendTextResponse("Unsupported message type: " + type);
            }
        } catch (Exception e) {
            sendTextResponse("ERROR: " + e.getMessage());
        }
    }

    private void handleLogin(PacketMessage request) throws IOException {
        User loginInfo = (User) request.getPayload();
        User userResult = UserDAO.getInstance().authenticate(loginInfo.getUsername(), loginInfo.getPassword());

        if (userResult != null) {
            client.setUsername(userResult.getUsername());
            Server.getInstance().getClientHandlers().put(userResult.getUsername(), this);
            System.out.println(" [Network] User '" + userResult.getUsername() + "' logged in.");
        }

        sendPacket(new PacketMessage(MessageType.LOGIN_RESPONSE, userResult));
    }

    private void handleCreateAuction(PacketMessage request) throws IOException {
        Auction auction = (Auction) request.getPayload();
        auction.setOwnerUsername(client.getUsername());
        Server.getInstance().addAuction(auction);

        sendPacket(new PacketMessage(MessageType.CREATE_AUCTION, auction));
        broadcastAuctionUpdate(auction);
    }

    private void handleJoinAuction(PacketMessage request) throws Exception {
        Auction auction = getAuctionOrThrow(readAuctionId(request.getPayload()));
        auction.addParticipant(client.getUsername());
        if (!client.getRegisteredAuctions().contains(auction.getId())) {
            client.getRegisteredAuctions().add(auction.getId());
        }

        sendPacket(new PacketMessage(MessageType.JOIN_AUCTION, buildAuctionUpdatePayload(auction)));
        broadcastAuctionUpdate(auction);
    }

    private void handleLeaveAuction(PacketMessage request) throws Exception {
        Auction auction = getAuctionOrThrow(readAuctionId(request.getPayload()));
        auction.removeParticipant(client.getUsername());
        client.getRegisteredAuctions().remove(Integer.valueOf(auction.getId()));

        sendPacket(new PacketMessage(MessageType.LEAVE_AUCTION, buildAuctionUpdatePayload(auction)));
        broadcastAuctionUpdate(auction);
    }

    private void handlePlaceBid(PacketMessage request) throws Exception {
        Map<?, ?> payload = (Map<?, ?>) request.getPayload();
        int auctionId = ((Number) payload.get("auctionId")).intValue();
        float bidAmount = ((Number) payload.get("bid")).floatValue();

        Auction auction = getAuctionOrThrow(auctionId);
        String previousHighestBidder = auction.findHighestBid().getBidderUsername();

        Bid bid = new Bid(new Date(), bidAmount, client.getUsername());
        auction.placeBid(bid, client.getUsername());

        AuctionUpdatePayload updatePayload = buildAuctionUpdatePayload(auction);
        notifyPreviousHighestBidder(previousHighestBidder, updatePayload);
        sendPacket(new PacketMessage(MessageType.PLACE_BID, updatePayload));
        broadcastAuctionUpdate(auction);
    }

    private void handleCancelAuction(PacketMessage request) throws Exception {
        Auction auction = getAuctionOrThrow(readAuctionId(request.getPayload()));
        auction.cancel(client.getUsername());

        AuctionUpdatePayload updatePayload = buildAuctionUpdatePayload(auction);
        sendPacket(new PacketMessage(MessageType.CANCEL_AUCTION, updatePayload));
        broadcastToParticipants(auction, new PacketMessage(MessageType.AUCTION_CANCELLED, updatePayload));
    }

    private void requireLogin() {
        String username = client != null ? client.getUsername() : null;
        if (username == null || username.startsWith("Guest_")) {
            throw new IllegalStateException("Client must login before using auction actions");
        }
    }

    private int readAuctionId(Object payload) {
        if (payload instanceof Number) {
            return ((Number) payload).intValue();
        }
        throw new IllegalArgumentException("Payload must be an auction id");
    }

    private Auction getAuctionOrThrow(int auctionId) {
        Auction auction = Server.getInstance().getAuctions().get(auctionId);
        if (auction == null) {
            throw new IllegalArgumentException("Auction not found: " + auctionId);
        }
        return auction;
    }

    private AuctionUpdatePayload buildAuctionUpdatePayload(Auction auction) {
        Bid highestBid = auction.findHighestBid();
        float highestAmount = highestBid.getBid() > 0
                ? highestBid.getBid()
                : auction.getItem().getStartingPrice();

        return new AuctionUpdatePayload(
                auction.getId(),
                highestBid.getCreatedAt(),
                highestAmount,
                auction.getItem().getName(),
                highestBid.getBidderUsername(),
                auction.getItem().getDescription()
        );
    }

    private void broadcastAuctionUpdate(Auction auction) {
        broadcastToParticipants(auction,
                new PacketMessage(MessageType.AUCTION_UPDATE, buildAuctionUpdatePayload(auction)));
    }

    private void broadcastToParticipants(Auction auction, PacketMessage packet) {
        for (String username : auction.getParticipants()) {
            ClientHandler handler = Server.getInstance().getClientHandlers().get(username);
            if (handler != null) {
                try {
                    handler.sendPacket(packet);
                } catch (IOException e) {
                    System.err.println(" [Network] Cannot send packet to " + username + ": " + e.getMessage());
                }
            }
        }
    }

    private void notifyPreviousHighestBidder(String username, AuctionUpdatePayload payload) {
        if (username == null || username.equals(client.getUsername())) {
            return;
        }

        ClientHandler handler = Server.getInstance().getClientHandlers().get(username);
        if (handler != null) {
            try {
                handler.sendPacket(new PacketMessage(MessageType.HIGHEST_BID_OWNER_LOST, payload));
            } catch (IOException e) {
                System.err.println(" [Network] Cannot notify previous highest bidder: " + e.getMessage());
            }
        }
    }

    private void sendTextResponse(String message) throws IOException {
        sendPacket(new PacketMessage(MessageType.AUCTION_ACTION_RESPONSE, message));
    }
}
