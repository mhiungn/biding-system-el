package Client.features.bidding;

import Client.core.network.NetworkRequestClient;
import CommonClasses.dto.DashboardAuctionRow;
import CommonClasses.dto.SellerAuctionRowDTO;
import Packets.MessageType;
import Packets.PacketMessage;
import Server.dao.AuctionDAO;

import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service layer for the My Bids screen.
 * <p>
 * Currently uses direct DAO access. When NetworkClient integration is complete,
 * this class will be refactored to send requests via the network socket instead.
 * </p>
 */
public class MyBidsService {

    /**
     * Returns all active auctions the user is participating in.
     *
     * @param username the logged-in user's username
     * @return list of active auction rows
     */
    public List<DashboardAuctionRow> loadActiveBids(String username) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.MY_ACTIVE_BIDS_REQUEST, null, MessageType.MY_ACTIVE_BIDS_RESPONSE);
                if (response.getPayload() instanceof List<?>) {
                    return (List<DashboardAuctionRow>) response.getPayload();
                }
            } catch (IOException e) {
                if (NetworkRequestClient.isAuthenticationFailure(e)) {
                    System.err.println("[MyBidsService] Network active bids rejected: " + e.getMessage());
                    return new ArrayList<>();
                }
                System.err.println("[MyBidsService] Network active bids unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            return AuctionDAO.getInstance().findActiveAuctionsByParticipant(username);
        } catch (Exception e) {
            System.err.println("[MyBidsService] Error loading active bids: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Returns all completed/cancelled auctions where the user placed bids.
     *
     * @param username the logged-in user's username
     * @return list of completed auction rows
     */
    public List<DashboardAuctionRow> loadCompletedBids(String username) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.MY_COMPLETED_BIDS_REQUEST, null, MessageType.MY_COMPLETED_BIDS_RESPONSE);
                if (response.getPayload() instanceof List<?>) {
                    return (List<DashboardAuctionRow>) response.getPayload();
                }
            } catch (IOException e) {
                if (NetworkRequestClient.isAuthenticationFailure(e)) {
                    System.err.println("[MyBidsService] Network completed bids rejected: " + e.getMessage());
                    return new ArrayList<>();
                }
                System.err.println("[MyBidsService] Network completed bids unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            return AuctionDAO.getInstance().findCompletedAuctionsByBidder(username);
        } catch (Exception e) {
            System.err.println("[MyBidsService] Error loading completed bids: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Returns auctions created by the current user for the selling/sold section.
     *
     * @param username the logged-in user's username
     * @return list of seller auction rows
     */
    public List<SellerAuctionRowDTO> loadSellingItems(String username) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.MY_SELLING_ITEMS_REQUEST, null, MessageType.MY_SELLING_ITEMS_RESPONSE);
                if (response.getPayload() instanceof List<?>) {
                    return (List<SellerAuctionRowDTO>) response.getPayload();
                }
            } catch (IOException e) {
                if (NetworkRequestClient.isAuthenticationFailure(e)) {
                    System.err.println("[MyBidsService] Network seller items rejected: " + e.getMessage());
                    return new ArrayList<>();
                }
                System.err.println("[MyBidsService] Network seller items unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            return AuctionDAO.getInstance().findSellerAuctionRows(username);
        } catch (Exception e) {
            System.err.println("[MyBidsService] Error loading seller items: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Returns the highest bid the user placed on a specific auction.
     *
     * @param auctionId the auction ID
     * @param username  the user's username
     * @return the user's highest bid amount, or 0
     */
    public float getUserHighestBid(int auctionId, String username) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("auctionId", auctionId);
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.USER_HIGHEST_BID_REQUEST,
                        (HashMap<String, Object>) payload,
                        MessageType.USER_HIGHEST_BID_RESPONSE);
                if (response.getPayload() instanceof Number) {
                    return ((Number) response.getPayload()).floatValue();
                }
            } catch (IOException e) {
                if (NetworkRequestClient.isAuthenticationFailure(e)) {
                    System.err.println("[MyBidsService] Network user highest bid rejected: " + e.getMessage());
                    return 0;
                }
                System.err.println("[MyBidsService] Network user highest bid unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            return AuctionDAO.getInstance().getUserHighestBid(auctionId, username);
        } catch (Exception e) {
            System.err.println("[MyBidsService] Error loading user highest bid: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the username of the current highest bidder for an auction.
     *
     * @param auctionId the auction ID
     * @return highest bidder username, or null
     */
    public String getHighestBidder(int auctionId) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.HIGHEST_BIDDER_REQUEST, auctionId, MessageType.HIGHEST_BIDDER_RESPONSE);
                return response.getPayload() instanceof String ? (String) response.getPayload() : null;
            } catch (IOException e) {
                System.err.println("[MyBidsService] Network highest bidder unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            return AuctionDAO.getInstance().getHighestBidderUsername(auctionId);
        } catch (Exception e) {
            System.err.println("[MyBidsService] Error loading highest bidder: " + e.getMessage());
            return null;
        }
    }
}
