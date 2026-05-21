package Client.features.bidding;

import CommonClasses.Bid;
import Server.dao.AuctionDAO;
import Server.dao.DashboardAuctionRow;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Service layer for the Bidding Detail screen.
 * <p>
 * Currently uses direct DAO access. When NetworkClient integration is complete,
 * this class will be refactored to send requests via the network socket instead.
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
        try {
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
        try {
            if (username == null || username.isBlank() || amount <= 0) {
                return false;
            }

            AuctionDAO dao = AuctionDAO.getInstance();
            String id = String.valueOf(auctionId);
            if (!dao.exists(id)) {
                return false;
            }

            DashboardAuctionRow detail = dao.findFullAuctionDetail(auctionId);
            if (detail == null || detail.getItem() == null || isEnded(detail)) {
                return false;
            }

            float currentPrice = detail.getItem().getCurrentHighestPrice();
            if (amount < currentPrice + detail.getMinimumBidIncrement()) {
                return false;
            }

            dao.addBid(id, new Bid(new Date(), amount, username));
            dao.addParticipant(id, username);
            applyAutoExtendIfNeeded(dao, id);
            return true;
        } catch (Exception e) {
            System.err.println("[AuctionDetailService] Error placing bid: " + e.getMessage());
            return false;
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
}
