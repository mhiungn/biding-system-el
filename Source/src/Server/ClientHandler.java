package Server;

import CommonClasses.Auction;
import CommonClasses.Bid;
import CommonClasses.User;
import CommonClasses.dto.AuthResponse;
import CommonClasses.dto.DashboardAuctionRow;
import CommonClasses.dto.DashboardPageResult;
import CommonClasses.dto.DashboardStats;
import CommonClasses.dto.UserProfileStatsDTO;
import CommonClasses.dto.WalletDTO;
import Packets.MessageType;
import Packets.PacketFactory;
import Packets.PacketMessage;
import Payload.AuctionUpdatePayload;
import Server.dao.AuctionDAO;
import Server.dao.UserDAO;
import Server.service.AuthenticationService;
import Server.service.AuctionFinalizationService;
import Server.service.BiddingApplicationService;
import Server.service.NotificationApplicationService;
import Server.service.ProfileApplicationService;
import Server.service.NetworkPushService;
import Server.service.SessionRegistry;
import Server.service.WalletApplicationService;

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
    private SessionRegistry.AuthenticatedSession authenticatedSession;

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
            } else if (type == MessageType.LOGOUT_REQUEST) {
                handleLogout(request);
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
                handleMyActiveBids(requireLogin(request));
            } else if (type == MessageType.MY_COMPLETED_BIDS_REQUEST) {
                handleMyCompletedBids(requireLogin(request));
            } else if (type == MessageType.MY_SELLING_ITEMS_REQUEST) {
                handleMySellingItems(requireLogin(request));
            } else if (type == MessageType.USER_HIGHEST_BID_REQUEST) {
                handleUserHighestBid(request, requireLogin(request));
            } else if (type == MessageType.WALLET_BALANCE_REQUEST) {
                handleWalletBalance(requireLogin(request));
            } else if (type == MessageType.WALLET_DEPOSIT_REQUEST) {
                handleWalletDeposit(request, requireLogin(request));
            } else if (type == MessageType.PROFILE_STATS_REQUEST) {
                handleProfileStats(requireLogin(request));
            } else if (type == MessageType.NOTIFICATION_LIST_REQUEST) {
                handleNotificationList(requireLogin(request));
            } else if (type == MessageType.NOTIFICATION_COUNT_REQUEST) {
                handleNotificationCount(requireLogin(request));
            } else if (type == MessageType.NOTIFICATION_MARK_READ_REQUEST) {
                handleNotificationMarkRead(request, requireLogin(request));
            } else if (type == MessageType.NOTIFICATION_MARK_ALL_READ_REQUEST) {
                handleNotificationMarkAllRead(requireLogin(request));
            } else if (type == MessageType.PUSH_SUBSCRIBE_REQUEST) {
                handlePushSubscribe(request);
            } else if (type == MessageType.SEARCH_AUCTIONS_REQUEST) {
                handleSearchAuctions(request);
            } else if (type == MessageType.LIST_AUCTIONS) {
                requireLogin(request);
                sendPacket(new PacketMessage(MessageType.LIST_AUCTIONS,
                        new ArrayList<>(CommonClasses.AuctionManager.getInstance().getAllAuctions().values())));
            } else if (type == MessageType.CREATE_AUCTION) {
                handleCreateAuction(request, requireLogin(request));
            } else if (type == MessageType.JOIN_AUCTION) {
                handleJoinAuction(request, requireLogin(request));
            } else if (type == MessageType.LEAVE_AUCTION) {
                handleLeaveAuction(request, requireLogin(request));
            } else if (type == MessageType.PLACE_BID) {
                handleDatabasePlaceBid(request, requireLogin(request));
            } else if (type == MessageType.CANCEL_AUCTION) {
                handleCancelAuction(request, requireLogin(request));
            } else {
                sendErrorResponse("UNSUPPORTED_MESSAGE", "Unsupported message type: " + type, type);
            }
        } catch (AuthenticationRequiredException e) {
            sendErrorResponse(e.getCode(), e.getMessage(), request.getMessageType());
        } catch (Exception e) {
            sendErrorResponse("REQUEST_FAILED", e.getMessage(), request.getMessageType());
        }
    }

    private void handleLogin(PacketMessage request) throws IOException {
        if (!(request.getPayload() instanceof User)) {
            throw new IllegalArgumentException("LOGIN_REQUEST payload must be a User");
        }

        User loginInfo = (User) request.getPayload();
        User userResult = AuthenticationService.getInstance().login(loginInfo.getUsername(), loginInfo.getPassword());

        if (userResult != null) {
            SessionRegistry.AuthenticatedSession session = SessionRegistry.getInstance().createSession(userResult);
            bindAuthenticatedSession(session);
            System.out.println(" [Network] User '" + userResult.getUsername() + "' logged in.");
            sendPacket(new PacketMessage(MessageType.LOGIN_RESPONSE,
                    AuthResponse.success(session.toSafeUser(), session.getToken(), session.getExpiresAt())));
            return;
        }else {
            System.out.println("[Network] Login failed for username: " + loginInfo.getUsername());
        }
        sendPacket(new PacketMessage(MessageType.LOGIN_RESPONSE,
                AuthResponse.failure("Invalid username or password.")));
    }

    private void handleRegister(PacketMessage request) throws IOException {
        if (!(request.getPayload() instanceof User)) {
            throw new IllegalArgumentException("REGISTER_REQUEST payload must be a User");
        }

        User user = (User) request.getPayload();
        boolean success = AuthenticationService.getInstance().register(user);
        if (success) {
            User savedUser = UserDAO.getInstance().findById(user.getUsername());
            SessionRegistry.AuthenticatedSession session = SessionRegistry.getInstance().createSession(savedUser);
            bindAuthenticatedSession(session);
            sendPacket(new PacketMessage(MessageType.REGISTER_RESPONSE,
                    AuthResponse.success(session.toSafeUser(), session.getToken(), session.getExpiresAt())));
        } else {
            sendPacket(new PacketMessage(MessageType.REGISTER_RESPONSE,
                    AuthResponse.failure("Registration failed.")));
        }
    }

    private void handleLogout(PacketMessage request) throws IOException {
        String token = request.getAuthToken();
        SessionRegistry.getInstance().invalidate(token);
        authenticatedSession = null;
        if (client.getUsername() != null) {
            Server.getInstance().getClientHandlers().remove(client.getUsername());
        }
        client.setUsername(null);
        sendPacket(new PacketMessage(MessageType.LOGOUT_RESPONSE, true));
    }

    private void bindAuthenticatedSession(SessionRegistry.AuthenticatedSession session) {
        authenticatedSession = session;
        client.setUsername(session.getUsername());
        Server.getInstance().getClientHandlers().put(session.getUsername(), this);
    }

    private void bindAuthenticatedSessionWithoutLegacyRegistry(SessionRegistry.AuthenticatedSession session) {
        authenticatedSession = session;
        client.setUsername(session.getUsername());
    }

    private void handleDashboardPage(PacketMessage request) throws IOException {
        finalizeExpiredAuctions("network dashboard page load");
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
        finalizeExpiredAuctions("network dashboard stats load");
        AuctionDAO dao = AuctionDAO.getInstance();
        DashboardStats stats = new DashboardStats(
                dao.countActiveAuctions(),
                dao.countEndingTodayAuctions(),
                dao.countTotalBids());
        sendPacket(new PacketMessage(MessageType.DASHBOARD_STATS_RESPONSE, stats));
    }

    private void handleAuctionDetail(PacketMessage request) throws IOException {
        finalizeExpiredAuctions("network auction detail load");
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

    private void handleMyActiveBids(SessionRegistry.AuthenticatedSession session) throws IOException {
        String username = session.getUsername();
        sendPacket(new PacketMessage(MessageType.MY_ACTIVE_BIDS_RESPONSE,
                (Serializable) new ArrayList<>(AuctionDAO.getInstance().findActiveAuctionsByParticipant(username))));
    }

    private void handleMyCompletedBids(SessionRegistry.AuthenticatedSession session) throws IOException {
        String username = session.getUsername();
        sendPacket(new PacketMessage(MessageType.MY_COMPLETED_BIDS_RESPONSE,
                (Serializable) new ArrayList<>(AuctionDAO.getInstance().findCompletedAuctionsByBidder(username))));
    }

    private void handleMySellingItems(SessionRegistry.AuthenticatedSession session) throws IOException {
        String username = session.getUsername();
        sendPacket(new PacketMessage(MessageType.MY_SELLING_ITEMS_RESPONSE,
                (Serializable) new ArrayList<>(AuctionDAO.getInstance().findSellerAuctionRows(username))));
    }

    private void handleUserHighestBid(PacketMessage request, SessionRegistry.AuthenticatedSession session)
            throws IOException {
        int auctionId = readAuctionIdFromMapOrNumber(request.getPayload());
        sendPacket(new PacketMessage(MessageType.USER_HIGHEST_BID_RESPONSE,
                AuctionDAO.getInstance().getUserHighestBid(auctionId, session.getUsername())));
    }

    private void handleWalletBalance(SessionRegistry.AuthenticatedSession session) throws IOException {
        WalletDTO wallet = WalletApplicationService.getInstance().getWallet(session.getUsername());
        sendPacket(new PacketMessage(MessageType.WALLET_BALANCE_RESPONSE, wallet));
    }

    private void handleWalletDeposit(PacketMessage request, SessionRegistry.AuthenticatedSession session)
            throws IOException {
        long amount = readLongAmount(request.getPayload());
        WalletDTO wallet = WalletApplicationService.getInstance().deposit(session.getUsername(), amount);
        if (wallet.isSuccess()) {
            NetworkPushService.getInstance().pushWalletUpdate(session.getUsername(), "DEPOSIT");
        }
        sendPacket(new PacketMessage(MessageType.WALLET_DEPOSIT_RESPONSE, wallet));
    }

    private void handleProfileStats(SessionRegistry.AuthenticatedSession session) throws IOException {
        UserProfileStatsDTO stats = ProfileApplicationService.getInstance().loadStats(session.getUsername());
        sendPacket(new PacketMessage(MessageType.PROFILE_STATS_RESPONSE, stats));
    }

    private void handleNotificationList(SessionRegistry.AuthenticatedSession session) throws IOException {
        sendPacket(new PacketMessage(
                MessageType.NOTIFICATION_LIST_RESPONSE,
                (Serializable) new ArrayList<>(NotificationApplicationService.getInstance()
                        .getRecentNotifications(session.getUsername()))));
    }

    private void handleNotificationCount(SessionRegistry.AuthenticatedSession session) throws IOException {
        sendPacket(new PacketMessage(
                MessageType.NOTIFICATION_COUNT_RESPONSE,
                NotificationApplicationService.getInstance().getUnreadCount(session.getUsername())));
    }

    private void handleNotificationMarkRead(PacketMessage request, SessionRegistry.AuthenticatedSession session)
            throws IOException {
        if (!(request.getPayload() instanceof Number)) {
            throw new IllegalArgumentException("Notification id is required");
        }
        long id = ((Number) request.getPayload()).longValue();
        NotificationApplicationService.getInstance().markRead(session.getUsername(), id);
        sendPacket(new PacketMessage(MessageType.NOTIFICATION_MARK_READ_RESPONSE, true));
    }

    private void handleNotificationMarkAllRead(SessionRegistry.AuthenticatedSession session) throws IOException {
        NotificationApplicationService.getInstance().markAllRead(session.getUsername());
        sendPacket(new PacketMessage(MessageType.NOTIFICATION_MARK_ALL_READ_RESPONSE, true));
    }

    private void handlePushSubscribe(PacketMessage request) throws IOException {
        SessionRegistry.AuthenticatedSession session = requireLogin(request);
        Server.getInstance().registerPushClient(session.getUsername(), this);
        sendPacket(new PacketMessage(MessageType.PUSH_SUBSCRIBE_RESPONSE, true));
    }

    private void handleSearchAuctions(PacketMessage request) throws IOException {
        Map<?, ?> payload = requireMapPayload(request);
        String keyword = readString(payload, "keyword", "");
        int limit = readInt(payload, "limit", 8);
        sendPacket(new PacketMessage(MessageType.SEARCH_AUCTIONS_RESPONSE,
                (Serializable) new ArrayList<>(AuctionDAO.getInstance().searchAuctionsByName(keyword, limit))));
    }

    private void handleCreateAuction(PacketMessage request, SessionRegistry.AuthenticatedSession session)
            throws IOException {
        if (!(request.getPayload() instanceof Auction)) {
            throw new IllegalArgumentException("CREATE_AUCTION payload must be an Auction");
        }

        Auction auction = (Auction) request.getPayload();
        auction.setOwnerUsername(session.getUsername());
        CommonClasses.AuctionManager.getInstance().addAuction(auction);

        sendPacket(new PacketMessage(MessageType.CREATE_AUCTION, auction));
        broadcastAuctionUpdate(auction);
    }

    private void handleJoinAuction(PacketMessage request, SessionRegistry.AuthenticatedSession session)
            throws Exception {
        Auction auction = getAuctionOrThrow(readAuctionId(request.getPayload()));
        auction.addParticipant(session.getUsername());
        if (!client.getRegisteredAuctions().contains(auction.getId())) {
            client.getRegisteredAuctions().add(auction.getId());
        }

        sendPacket(new PacketMessage(MessageType.JOIN_AUCTION, buildAuctionUpdatePayload(auction)));
        broadcastAuctionUpdate(auction);
    }

    private void handleLeaveAuction(PacketMessage request, SessionRegistry.AuthenticatedSession session)
            throws Exception {
        Auction auction = getAuctionOrThrow(readAuctionId(request.getPayload()));
        auction.removeParticipant(session.getUsername());
        client.getRegisteredAuctions().remove(Integer.valueOf(auction.getId()));

        sendPacket(new PacketMessage(MessageType.LEAVE_AUCTION, buildAuctionUpdatePayload(auction)));
        broadcastAuctionUpdate(auction);
    }

    private void handleDatabasePlaceBid(PacketMessage request, SessionRegistry.AuthenticatedSession session)
            throws IOException {
        Map<?, ?> payload = requireMapPayload(request);
        int auctionId = readInt(payload, "auctionId", -1);
        float bidAmount = readFloat(payload, "bid", 0);

        boolean accepted = BiddingApplicationService.getInstance().placeBid(session.getUsername(), auctionId, bidAmount);
        sendPacket(new PacketMessage(MessageType.PLACE_BID, accepted));
    }

    private void handleCancelAuction(PacketMessage request, SessionRegistry.AuthenticatedSession session)
            throws Exception {
        Auction auction = getAuctionOrThrow(readAuctionId(request.getPayload()));
        auction.cancel(session.getUsername());

        AuctionUpdatePayload updatePayload = buildAuctionUpdatePayload(auction);
        sendPacket(new PacketMessage(MessageType.CANCEL_AUCTION, updatePayload));
        broadcastToParticipants(auction, new PacketMessage(MessageType.AUCTION_CANCELLED, updatePayload));
    }

    private SessionRegistry.AuthenticatedSession requireLogin(PacketMessage request) {
        String token = request.getAuthToken();
        if (token == null || token.isBlank()) {
            throw new AuthenticationRequiredException(
                    "AUTH_REQUIRED",
                    "Authentication token is required. Please log in again.");
        }

        SessionRegistry.AuthenticatedSession session = SessionRegistry.getInstance().authenticate(token);
        if (session == null) {
            throw new AuthenticationRequiredException(
                    "AUTH_INVALID",
                    "Authentication token is invalid or expired. Please log in again.");
        }

        bindAuthenticatedSessionWithoutLegacyRegistry(session);
        return session;
    }

    private int readAuctionId(Object payload) {
        if (payload instanceof Number) {
            return ((Number) payload).intValue();
        }
        throw new IllegalArgumentException("Payload must be an auction id");
    }

    private int readAuctionIdFromMapOrNumber(Object payload) {
        if (payload instanceof Number) {
            return ((Number) payload).intValue();
        }
        if (payload instanceof Map<?, ?>) {
            return readInt((Map<?, ?>) payload, "auctionId", -1);
        }
        throw new IllegalArgumentException("Payload must include an auction id");
    }

    private long readLongAmount(Object payload) {
        if (payload instanceof Number) {
            return ((Number) payload).longValue();
        }
        if (payload instanceof Map<?, ?>) {
            Object value = ((Map<?, ?>) payload).get("amount");
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        }
        throw new IllegalArgumentException("Payload must include a numeric amount");
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

    private void finalizeExpiredAuctions(String trigger) {
        AuctionFinalizationService.getInstance().finalizeEndedAuctionsSafely(trigger);
    }

    private void sendErrorResponse(String code, String message, MessageType requestType) throws IOException {
        sendPacket(PacketFactory.error(code, message, requestType));
    }

    private static final class AuthenticationRequiredException extends RuntimeException {
        private final String code;

        private AuthenticationRequiredException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String getCode() {
            return code;
        }
    }

    private void cleanupClient() {
        if (client.getUsername() != null) {
            Server.getInstance().getClientHandlers().remove(client.getUsername(), this);
            Server.getInstance().unregisterPushClient(client.getUsername(), this);
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
