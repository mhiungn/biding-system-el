package Server;

import CommonClasses.Auction;
import CommonClasses.Bid;
import CommonClasses.User;
import Client.features.dashboard.DashboardPageResult;
import Client.features.dashboard.DashboardStats;
import Packets.MessageType;
import Packets.PacketFactory;
import Packets.PacketMessage;
import Payload.AuctionUpdatePayload;
import Server.dao.AuctionDAO;
import Server.dao.DashboardAuctionRow;
import Server.dao.UserDAO;
import Server.service.AuthenticationService;

import java.io.EOFException;
import java.io.IOException;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
/**
 * Handles socket communication for one connected client.
 * The handler reads PacketMessage objects, routes them by MessageType,
 * and sends direct responses or broadcast auction updates.
 */
public class ClientHandler implements Runnable {

    private final Client client;
    private final Socket socket;
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
    public synchronized void sendPacket(PacketMessage packet) throws IOException {
        if (outputStream == null) {
            throw new IOException("Output stream is not ready");
        }
        outputStream.writeObject(packet);
        outputStream.flush();
        outputStream.reset();
        System.out.println("[Network] Sent " + packet.getMessageType() + " to " + client.getUsername());
    }

    @Override
    public void run() {
        try (ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {
            System.out.println(" [Network] Listening to client: " + client.getUsername());

            while (!socket.isClosed()) {
                Object received;
                try {
                    received = inputStream.readObject();
                } catch (SocketTimeoutException e) {
                    continue;
                }
                if (received instanceof PacketMessage) {
                    handlePacket((PacketMessage) received);
                }
            }
        } catch (EOFException e) {
            System.out.println(" [Network] Client disconnected." + client.getUsername());
        } catch (Exception e) {
            System.err.println(" [Network] Connection error: " + e.getMessage());
        } finally {
            cleanupClient();
        }
    }

    private void handlePacket(PacketMessage request) throws IOException {
        try {
            MessageType type = request.getMessageType();
            if (type == null) {
                throw new IllegalArgumentException("Message type is required");
            }

            if (type == MessageType.PING) {
                sendPacket(PacketFactory.of(MessageType.PONG, "OK"));
            } else if (type == MessageType.LOGIN_REQUEST) {
                handleLogin(request);
            }else if (type == MessageType.REGISTER_REQUEST) {
                handleRegister(request);
            } else if (type == MessageType.DASHBOARD_PAGE_REQUEST) {
                handleDashboardPage(request);
            } else if (type == MessageType.DASHBOARD_STATS_REQUEST) {
                handleDashboardStats();
            } else if (type == MessageType.AUCTION_DETAIL_REQUEST) {
                handleAuctionDetail(request);
            } else if (type == MessageType.AUCTION_OWNER_REQUEST) {
                handleAuctionOwner(request);
            } else if (type == MessageType.BID_HISTORY_REQUEST) {
                handleBidHistory(request);
            } else if (type == MessageType.PARTICIPANT_COUNT_REQUEST) {
                handleParticipantCount(request);
            } else if (type == MessageType.HIGHEST_BIDDER_REQUEST) {
                handleHighestBidder(request);
            } else if (type == MessageType.MY_ACTIVE_BIDS_REQUEST) {
                handleMyActiveBids(request);
            } else if (type == MessageType.MY_COMPLETED_BIDS_REQUEST) {
                handleMyCompletedBids(request);
            } else if (type == MessageType.USER_HIGHEST_BID_REQUEST) {
                handleUserHighestBid(request);
            } else if (type == MessageType.LIST_AUCTIONS) {
                requireLogin();
                sendPacket(new PacketMessage(MessageType.LIST_AUCTIONS,
                        new ArrayList<>(CommonClasses.AuctionManager.getInstance().getAllAuctions().values())));
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
                if (request.getPayload() instanceof Map<?, ?>
                        && ((Map<?, ?>) request.getPayload()).containsKey("username")) {
                    handleDatabasePlaceBid(request);
                } else {
                    requireLogin();
                    handlePlaceBid(request);
                }
            } else if (type == MessageType.CANCEL_AUCTION) {
                requireLogin();
                handleCancelAuction(request);
            } else {
                sendErrorResponse("UNSUPPORTED_MESSAGE", "Unsupported message type: " + type, type);
            }
        } catch (Exception e) {
            sendErrorResponse("REQUEST_FAILED", e.getMessage(), request.getMessageType());
        }
    }

    private void handleLogin(PacketMessage request) throws IOException {
        if (!(request.getPayload() instanceof User)) {
            throw new IllegalArgumentException("LOGIN_REQUEST payload must be a User");
        }

        User loginInfo = (User) request.getPayload();
        User userResult = UserDAO.getInstance().authenticate(loginInfo.getUsername(), loginInfo.getPassword());

        if (userResult != null) {
            client.setUsername(userResult.getUsername());
            Server.getInstance().getClientHandlers().put(userResult.getUsername(), this);
            System.out.println(" [Network] User '" + userResult.getUsername() + "' logged in.");
        }else {
            System.out.println("[Network] Login failed for username: " + loginInfo.getUsername());
        }
        sendPacket(new PacketMessage(MessageType.LOGIN_RESPONSE, userResult));
    }

    private void handleRegister(PacketMessage request) throws IOException {
        if (!(request.getPayload() instanceof User)) {
            throw new IllegalArgumentException("REGISTER_REQUEST payload must be a User");
        }

        User user = (User) request.getPayload();
        boolean success = AuthenticationService.getInstance().register(user);
        if (success) {
            client.setUsername(user.getUsername());
            Server.getInstance().getClientHandlers().put(user.getUsername(), this);
            sendPacket(new PacketMessage(MessageType.REGISTER_RESPONSE, user));
        } else {
            sendPacket(new PacketMessage(MessageType.REGISTER_RESPONSE, null));
        }
    }

    private void handleDashboardPage(PacketMessage request) throws IOException {
        Map<?, ?> payload = requireMapPayload(request);
        int page = readInt(payload, "page", 0);
        String category = readString(payload, "category", "ALL");
        boolean endingSoon = readBoolean(payload, "endingSoon", false);
        Float minPrice = readFloatObject(payload, "minPrice");
        Float maxPrice = readFloatObject(payload, "maxPrice");
        int pageSize = readInt(payload, "pageSize", 12);

        AuctionDAO dao = AuctionDAO.getInstance();
        int total = dao.countDashboardAuctions(category, endingSoon, minPrice, maxPrice);
        int safePage = Math.max(page, 0);
        int offset = safePage * pageSize;
        if (offset >= total && total > 0) {
            safePage = (int) Math.ceil((double) total / pageSize) - 1;
            offset = safePage * pageSize;
        }
        List<DashboardAuctionRow> rows = dao.findDashboardAuctions(
                category, endingSoon, minPrice, maxPrice, pageSize, offset);
        sendPacket(new PacketMessage(MessageType.DASHBOARD_PAGE_RESPONSE,
                new DashboardPageResult(rows, total)));
    }

    private void handleDashboardStats() throws IOException {
        AuctionDAO dao = AuctionDAO.getInstance();
        DashboardStats stats = new DashboardStats(
                dao.countActiveAuctions(),
                dao.countEndingTodayAuctions(),
                dao.countTotalBids());
        sendPacket(new PacketMessage(MessageType.DASHBOARD_STATS_RESPONSE, stats));
    }

    private void handleAuctionDetail(PacketMessage request) throws IOException {
        int auctionId = readAuctionId(request.getPayload());
        sendPacket(new PacketMessage(MessageType.AUCTION_DETAIL_RESPONSE,
                (Serializable) AuctionDAO.getInstance().findFullAuctionDetail(auctionId)));
    }

    private void handleAuctionOwner(PacketMessage request) throws IOException {
        int auctionId = readAuctionId(request.getPayload());
        sendPacket(new PacketMessage(MessageType.AUCTION_OWNER_RESPONSE,
                AuctionDAO.getInstance().findAuctionOwner(auctionId)));
    }

    private void handleBidHistory(PacketMessage request) throws IOException {
        int auctionId = readAuctionId(request.getPayload());
        sendPacket(new PacketMessage(MessageType.BID_HISTORY_RESPONSE,
                (Serializable) new ArrayList<>(AuctionDAO.getInstance().getBidHistoryForAuction(auctionId))));
    }

    private void handleParticipantCount(PacketMessage request) throws IOException {
        int auctionId = readAuctionId(request.getPayload());
        var snapshot = AuctionDAO.getInstance().findById(String.valueOf(auctionId));
        int count = snapshot == null ? 0 : snapshot.getRegisteredUsernames().size();
        sendPacket(new PacketMessage(MessageType.PARTICIPANT_COUNT_RESPONSE, count));
    }

    private void handleHighestBidder(PacketMessage request) throws IOException {
        int auctionId = readAuctionId(request.getPayload());
        sendPacket(new PacketMessage(MessageType.HIGHEST_BIDDER_RESPONSE,
                AuctionDAO.getInstance().getHighestBidderUsername(auctionId)));
    }

    private void handleMyActiveBids(PacketMessage request) throws IOException {
        String username = readUsername(request.getPayload());
        sendPacket(new PacketMessage(MessageType.MY_ACTIVE_BIDS_RESPONSE,
                (Serializable) new ArrayList<>(AuctionDAO.getInstance().findActiveAuctionsByParticipant(username))));
    }

    private void handleMyCompletedBids(PacketMessage request) throws IOException {
        String username = readUsername(request.getPayload());
        sendPacket(new PacketMessage(MessageType.MY_COMPLETED_BIDS_RESPONSE,
                (Serializable) new ArrayList<>(AuctionDAO.getInstance().findCompletedAuctionsByBidder(username))));
    }

    private void handleUserHighestBid(PacketMessage request) throws IOException {
        Map<?, ?> payload = requireMapPayload(request);
        int auctionId = readInt(payload, "auctionId", -1);
        String username = readString(payload, "username", null);
        sendPacket(new PacketMessage(MessageType.USER_HIGHEST_BID_RESPONSE,
                AuctionDAO.getInstance().getUserHighestBid(auctionId, username)));
    }

    private void handleCreateAuction(PacketMessage request) throws IOException {
        if (!(request.getPayload() instanceof Auction)) {
            throw new IllegalArgumentException("CREATE_AUCTION payload must be an Auction");
        }

        Auction auction = (Auction) request.getPayload();
        auction.setOwnerUsername(client.getUsername());
        CommonClasses.AuctionManager.getInstance().addAuction(auction);

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
        if (!(request.getPayload() instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("PLACE_BID payload must be a Map");
        }

        Map<?, ?> payload = (Map<?, ?>) request.getPayload();
        Object auctionIdValue = payload.get("auctionId");
        Object bidValue = payload.get("bid");
        if (!(auctionIdValue instanceof Number) || !(bidValue instanceof Number)) {
            throw new IllegalArgumentException("PLACE_BID requires numeric auctionId and bid");
        }
        int auctionId = ((Number) auctionIdValue).intValue();
        float bidAmount = ((Number) bidValue).floatValue();

        Auction auction = getAuctionOrThrow(auctionId);
        String previousHighestBidder = auction.findHighestBid().getBidderUsername();

        Bid bid = new Bid(new Date(), bidAmount, client.getUsername());
        auction.placeBid(bid, client.getUsername());

        AuctionUpdatePayload updatePayload = buildAuctionUpdatePayload(auction);
        notifyPreviousHighestBidder(previousHighestBidder, updatePayload);
        sendPacket(new PacketMessage(MessageType.PLACE_BID, updatePayload));
        broadcastAuctionUpdate(auction);
    }

    private void handleDatabasePlaceBid(PacketMessage request) throws IOException {
        Map<?, ?> payload = requireMapPayload(request);
        int auctionId = readInt(payload, "auctionId", -1);
        float bidAmount = readFloat(payload, "bid", 0);
        String username = readString(payload, "username", client.getUsername());

        AuctionDAO dao = AuctionDAO.getInstance();
        DashboardAuctionRow detail = dao.findFullAuctionDetail(auctionId);
        if (username == null || username.isBlank()) {
            sendPacket(new PacketMessage(MessageType.PLACE_BID, false));
            return;
        }
        if (detail == null || detail.getItem() == null || detail.getEndTime() == null
                || detail.getEndTime().getTime() <= System.currentTimeMillis()
                || bidAmount <= detail.getItem().getCurrentHighestPrice()) {
            sendPacket(new PacketMessage(MessageType.PLACE_BID, false));
            return;
        }

        dao.addParticipant(String.valueOf(auctionId), username);
        dao.addBid(String.valueOf(auctionId), new Bid(new Date(), bidAmount, username));
        sendPacket(new PacketMessage(MessageType.PLACE_BID, true));
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
        Auction auction = CommonClasses.AuctionManager.getInstance().getAuction(auctionId);
        if (auction == null) {
            throw new IllegalArgumentException("Auction not found: " + auctionId);
        }
        return auction;
    }

    private Map<?, ?> requireMapPayload(PacketMessage request) {
        if (request.getPayload() instanceof Map<?, ?>) {
            return (Map<?, ?>) request.getPayload();
        }
        throw new IllegalArgumentException(request.getMessageType() + " payload must be a Map");
    }

    private String readUsername(Object payload) {
        if (payload instanceof String && !((String) payload).isBlank()) {
            return (String) payload;
        }
        throw new IllegalArgumentException("Payload must be a username");
    }

    private int readInt(Map<?, ?> payload, String key, int defaultValue) {
        Object value = payload.get(key);
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private float readFloat(Map<?, ?> payload, String key, float defaultValue) {
        Object value = payload.get(key);
        return value instanceof Number ? ((Number) value).floatValue() : defaultValue;
    }

    private Float readFloatObject(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        return value instanceof Number ? ((Number) value).floatValue() : null;
    }

    private boolean readBoolean(Map<?, ?> payload, String key, boolean defaultValue) {
        Object value = payload.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    private String readString(Map<?, ?> payload, String key, String defaultValue) {
        Object value = payload.get(key);
        return value instanceof String ? (String) value : defaultValue;
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

    private void sendErrorResponse(String code, String message, MessageType requestType) throws IOException {
        sendPacket(PacketFactory.error(code, message, requestType));
    }

    private void cleanupClient() {
        if (client.getUsername() != null) {
            Server.getInstance().getClientHandlers().remove(client.getUsername());
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
