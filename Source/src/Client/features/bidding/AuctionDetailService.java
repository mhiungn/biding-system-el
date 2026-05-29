package Client.features.bidding;

import Client.core.network.NetworkRequestClient;
import Client.features.auth.SessionManager;
import CommonClasses.AutoBidConfig;
import CommonClasses.Bid;
import CommonClasses.dto.DashboardAuctionRow;
import Packets.MessageType;
import Packets.PacketMessage;
import Server.dao.AuctionDAO;
import Server.service.AuctionFinalizationService;
import Server.service.AutoBidManager;
import Server.service.BiddingApplicationService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service layer for the Bidding Detail screen.
 * <p>
 * Currently uses direct DAO access. When NetworkClient integration is complete,
 * this class will be refactored to send requests via the network socket
 * instead.
 * </p>
 */
public class AuctionDetailService {

    /**
     * Loads full auction detail including item info, bid count, and timing.
     *
     * @param auctionId the auction ID
     * @return DashboardAuctionRow with full detail, or null if not found
     */
    public DashboardAuctionRow loadAuctionDetail(int auctionId) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.AUCTION_DETAIL_REQUEST, auctionId, MessageType.AUCTION_DETAIL_RESPONSE);
                if (response.getPayload() instanceof DashboardAuctionRow) {
                    return (DashboardAuctionRow) response.getPayload();
                }
                return null;
            } catch (IOException e) {
                System.err.println("[AuctionDetailService] Network detail unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            finalizeExpiredAuctions("auction detail load");
            return AuctionDAO.getInstance().findFullAuctionDetail(auctionId);
        } catch (Exception e) {
            System.err.println("[AuctionDetailService] Error loading auction detail: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the seller/owner username for the given auction.
     *
     * @param auctionId the auction ID
     * @return owner username, or "Unknown" on error
     */
    public String getAuctionOwner(int auctionId) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.AUCTION_OWNER_REQUEST, auctionId, MessageType.AUCTION_OWNER_RESPONSE);
                if (response.getPayload() instanceof String) {
                    return (String) response.getPayload();
                }
                return "Unknown";
            } catch (IOException e) {
                System.err.println("[AuctionDetailService] Network owner unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            String owner = AuctionDAO.getInstance().findAuctionOwner(auctionId);
            return owner != null ? owner : "Unknown";
        } catch (Exception e) {
            System.err.println("[AuctionDetailService] Error loading owner: " + e.getMessage());
            return "Unknown";
        }
    }

    /**
     * Returns the bid history for the given auction, ordered highest first.
     *
     * @param auctionId the auction ID
     * @return list of Bid objects
     */
    public List<Bid> loadBidHistory(int auctionId) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.BID_HISTORY_REQUEST, auctionId, MessageType.BID_HISTORY_RESPONSE);
                if (response.getPayload() instanceof List<?>) {
                    return (List<Bid>) response.getPayload();
                }
            } catch (IOException e) {
                System.err.println("[AuctionDetailService] Network bid history unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            return AuctionDAO.getInstance().getBidHistoryForAuction(auctionId);
        } catch (Exception e) {
            System.err.println("[AuctionDetailService] Error loading bid history: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Returns the username of the current highest bidder.
     *
     * @param auctionId the auction ID
     * @return username or null
     */
    public String getHighestBidder(int auctionId) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.HIGHEST_BIDDER_REQUEST, auctionId, MessageType.HIGHEST_BIDDER_RESPONSE);
                return response.getPayload() instanceof String ? (String) response.getPayload() : null;
            } catch (IOException e) {
                System.err.println("[AuctionDetailService] Network highest bidder unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            return AuctionDAO.getInstance().getHighestBidderUsername(auctionId);
        } catch (Exception e) {
            System.err.println("[AuctionDetailService] Error loading highest bidder: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the participant count for the given auction.
     *
     * @param auctionId the auction ID
     * @return number of registered participants
     */
    public int getParticipantCount(int auctionId) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.PARTICIPANT_COUNT_REQUEST, auctionId, MessageType.PARTICIPANT_COUNT_RESPONSE);
                if (response.getPayload() instanceof Number) {
                    return ((Number) response.getPayload()).intValue();
                }
            } catch (IOException e) {
                System.err.println("[AuctionDetailService] Network participant count unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            AuctionDAO dao = AuctionDAO.getInstance();
            var snapshot = dao.findById(String.valueOf(auctionId));
            return snapshot != null ? snapshot.getRegisteredUsernames().size() : 0;
        } catch (Exception e) {
            System.err.println("[AuctionDetailService] Error loading participant count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Validates and persists a bid for an active auction.
     *
     * @param auctionId the auction ID
     * @param username  the bidder username
     * @param amount    the bid amount
     * @return true if the bid was accepted
     */
    public boolean placeBid(int auctionId, String username, float amount) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("auctionId", auctionId);
                payload.put("bid", amount);
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.PLACE_BID,
                        (HashMap<String, Object>) payload,
                        MessageType.PLACE_BID);
                if (response.getPayload() instanceof Boolean) {
                    return (Boolean) response.getPayload();
                }
            } catch (IOException e) {
                if (NetworkRequestClient.isAuthenticationFailure(e)) {
                    System.err.println("[AuctionDetailService] Network bid rejected: " + e.getMessage());
                    return false;
                }
                System.err.println("[AuctionDetailService] Network bid unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            return BiddingApplicationService.getInstance().placeBid(username, auctionId, amount);
        } catch (Exception e) {
            System.err.println("[AuctionDetailService] Error placing bid: " + e.getMessage());
            return false;
        }
    }

    // ========================== Auto-Bid Methods ==========================

    /**
     * Đăng ký cấu hình Đấu giá tự động cho phiên đấu giá.
     *
     * @param auctionId ID phiên đấu giá
     * @param maxBid    giá tối đa sẵn sàng trả
     * @param increment bước giá tự động cộng thêm
     * @return true nếu đăng ký thành công
     */
    public boolean registerAutoBid(int auctionId, float maxBid, float increment) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("auctionId", auctionId);
                payload.put("maxBid", maxBid);
                payload.put("increment", increment);
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.AUTO_BID_REGISTER_REQUEST,
                        (HashMap<String, Object>) payload,
                        MessageType.AUTO_BID_REGISTER_RESPONSE);
                return response.getPayload() != null;
            } catch (IOException e) {
                System.err.println("[AuctionDetailService] Auto-Bid registration failed: " + e.getMessage());
                return false;
            }
        }

        try {
            String username = SessionManager.getCurrentUser().getUsername();
            AutoBidManager.getInstance().registerAutoBid(username, auctionId, maxBid, increment);
            return true;
        } catch (Exception e) {
            System.err.println("[AuctionDetailService] Error registering auto-bid: " + e.getMessage());
            return false;
        }
    }

    /**
     * Hủy cấu hình Auto-Bid hiện tại của người dùng trên phiên đấu giá.
     *
     * @param auctionId ID phiên đấu giá
     * @return true nếu hủy thành công
     */
    public boolean cancelAutoBid(int auctionId) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.AUTO_BID_CANCEL_REQUEST,
                        auctionId,
                        MessageType.AUTO_BID_CANCEL_RESPONSE);
                if (response.getPayload() instanceof Boolean) {
                    return (Boolean) response.getPayload();
                }
                return false;
            } catch (IOException e) {
                System.err.println("[AuctionDetailService] Auto-Bid cancellation failed: " + e.getMessage());
                return false;
            }
        }

        try {
            String username = SessionManager.getCurrentUser().getUsername();
            return AutoBidManager.getInstance().cancelAutoBid(username, auctionId);
        } catch (Exception e) {
            System.err.println("[AuctionDetailService] Error canceling auto-bid: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy trạng thái cấu hình Auto-Bid hiện tại của người dùng.
     *
     * @param auctionId ID phiên đấu giá
     * @return AutoBidConfig nếu có, null nếu chưa đăng ký
     */
    public AutoBidConfig getAutoBidStatus(int auctionId) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.AUTO_BID_STATUS_REQUEST,
                        auctionId,
                        MessageType.AUTO_BID_STATUS_RESPONSE);
                if (response.getPayload() instanceof AutoBidConfig) {
                    return (AutoBidConfig) response.getPayload();
                }
                return null;
            } catch (IOException e) {
                System.err.println("[AuctionDetailService] Auto-Bid status query failed: " + e.getMessage());
                return null;
            }
        }

        try {
            String username = SessionManager.getCurrentUser().getUsername();
            return AutoBidManager.getInstance().getAutoBidConfig(username, auctionId);
        } catch (Exception e) {
            System.err.println("[AuctionDetailService] Error getting auto-bid status: " + e.getMessage());
            return null;
        }
    }

    private void applyAutoExtendIfNeeded(AuctionDAO dao, String auctionId) {
        var snapshot = dao.findById(auctionId);
        if (snapshot == null || !"Time_With_Reset".equals(snapshot.getType()) || snapshot.getTerminateAt() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long remaining = snapshot.getTerminateAt().getTime() - now;
        if (remaining > 0 && remaining <= 120_000L) {
            dao.updateTerminateAt(auctionId, new Date(snapshot.getTerminateAt().getTime() + 300_000L));
        }
    }

    private boolean isEnded(DashboardAuctionRow detail) {
        return detail.getEndTime() == null || detail.getEndTime().getTime() <= System.currentTimeMillis();
    }

    private void finalizeExpiredAuctions(String trigger) {
        AuctionFinalizationService.getInstance().finalizeEndedAuctionsSafely(trigger);
    }
}
