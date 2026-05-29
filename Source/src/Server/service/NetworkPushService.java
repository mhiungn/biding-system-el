package Server.service;

import CommonClasses.dto.AuctionUpdatePushDTO;
import CommonClasses.dto.AutoBidNotificationDTO;
import CommonClasses.dto.DashboardAuctionRow;
import CommonClasses.dto.NotificationPushDTO;
import CommonClasses.dto.WalletUpdatePushDTO;
import Packets.MessageType;
import Packets.PacketMessage;
import Server.ClientHandler;
import Server.Server;
import Server.dao.AuctionDAO;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;

public class NetworkPushService {
    private static volatile NetworkPushService instance;

    public static NetworkPushService getInstance() {
        if (instance == null) {
            synchronized (NetworkPushService.class) {
                if (instance == null) {
                    instance = new NetworkPushService();
                }
            }
        }
        return instance;
    }

    private final AuctionDAO auctionDAO;
    private final WalletApplicationService walletService;
    private final NotificationApplicationService notificationService;
    private volatile BiConsumer<String, PacketMessage> deliveryOverride;

    private NetworkPushService() {
        this.auctionDAO = AuctionDAO.getInstance();
        this.walletService = WalletApplicationService.getInstance();
        this.notificationService = NotificationApplicationService.getInstance();
    }

    public void pushBidAccepted(int auctionId, String bidderUsername, String previousHighestBidder,
                                String sellerUsername) {
        pushAuctionUpdate(auctionId, "BID_PLACED");
        pushWalletUpdate(bidderUsername, "BID_HOLD_UPDATED");
        if (previousHighestBidder != null && !previousHighestBidder.equals(bidderUsername)) {
            pushWalletUpdate(previousHighestBidder, "BID_HOLD_RELEASED");
            pushNotificationUpdate(previousHighestBidder, auctionId, "OUTBID");
        }
        if (sellerUsername != null && !sellerUsername.equals(bidderUsername)) {
            pushNotificationUpdate(sellerUsername, auctionId, "NEW_BID_ON_SELLER_ITEM");
        }
    }

    public void pushAuctionUpdate(int auctionId, String reason) {
        DashboardAuctionRow row = auctionDAO.findFullAuctionDetail(auctionId);
        AuctionUpdatePushDTO payload = new AuctionUpdatePushDTO(auctionId, row, reason);
        PacketMessage packet = new PacketMessage(MessageType.AUCTION_UPDATE_PUSH, payload);

        Set<String> recipients = new LinkedHashSet<>(auctionDAO.findParticipantsForAuction(auctionId));
        String owner = auctionDAO.findAuctionOwner(auctionId);
        if (owner != null && !owner.isBlank()) {
            recipients.add(owner);
        }

        if (recipients.isEmpty()) {
            Server.getInstance().getPushClientHandlers().keySet().forEach(username -> sendToUser(username, packet));
            return;
        }
        recipients.forEach(username -> sendToUser(username, packet));
    }

    public void pushWalletUpdate(String username, String reason) {
        if (username == null || username.isBlank()) {
            return;
        }
        WalletUpdatePushDTO payload = new WalletUpdatePushDTO(
                username,
                walletService.getWallet(username),
                reason);
        sendToUser(username, new PacketMessage(MessageType.WALLET_UPDATE_PUSH, payload));
    }

    public void pushNotificationUpdate(String username, Integer auctionId, String type) {
        if (username == null || username.isBlank()) {
            return;
        }
        NotificationPushDTO payload = new NotificationPushDTO(
                username,
                auctionId,
                type,
                notificationService.getUnreadCount(username));
        sendToUser(username, new PacketMessage(MessageType.NOTIFICATION_PUSH, payload));
    }

    public void sendToUser(String username, PacketMessage packet) {
        BiConsumer<String, PacketMessage> override = deliveryOverride;
        if (override != null) {
            override.accept(username, packet);
            return;
        }

        ClientHandler handler = Server.getInstance().getPushClientHandlers().get(username);
        if (handler == null) {
            return;
        }
        try {
            handler.sendPacket(packet);
        } catch (IOException e) {
            System.err.println("[NetworkPushService] Cannot push " + packet.getMessageType()
                    + " to " + username + ": " + e.getMessage());
        }
    }

    public void setDeliveryOverrideForTests(BiConsumer<String, PacketMessage> deliveryOverride) {
        this.deliveryOverride = deliveryOverride;
    }

    public void clearDeliveryOverrideForTests() {
        this.deliveryOverride = null;
    }

    /**
     * Gửi thông báo sự kiện Auto-Bid cho một user cụ thể qua Socket push.
     * <p>
     * Được gọi bởi {@link AutoBidManager} khi:
     * <ul>
     *   <li>{@code AUTO_BID_PLACED} — hệ thống đã tự động đặt giá thay user</li>
     *   <li>{@code AUTO_BID_LIMIT_REACHED} — giá vượt quá maxBid, auto-bid dừng lại</li>
     * </ul>
     * </p>
     *
     * @param username  username nhận thông báo
     * @param auctionId ID phiên đấu giá
     * @param type      loại sự kiện (AUTO_BID_PLACED, AUTO_BID_LIMIT_REACHED, etc.)
     * @param bidAmount số tiền liên quan
     * @param maxBid    giới hạn maxBid của cấu hình auto-bid
     * @param message   thông điệp mô tả chi tiết
     */
    public void pushAutoBidNotification(String username, int auctionId, String type,
                                        float bidAmount, float maxBid, String message) {
        if (username == null || username.isBlank()) {
            return;
        }
        AutoBidNotificationDTO payload = new AutoBidNotificationDTO(
                username, auctionId, type, bidAmount, maxBid, message);
        sendToUser(username, new PacketMessage(MessageType.AUTO_BID_NOTIFICATION, payload));
    }
}
