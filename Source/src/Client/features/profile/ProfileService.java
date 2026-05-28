package Client.features.profile;

import Client.core.network.NetworkRequestClient;
import CommonClasses.User;
import CommonClasses.dto.UserProfileStatsDTO;
import CommonClasses.dto.WalletDTO;
import Packets.MessageType;
import Packets.PacketMessage;
import Server.dao.AuctionDAO;
import Server.dao.UserDAO;
import Server.service.WalletApplicationService;

import java.io.IOException;
import java.util.Date;

/**
 * Service layer for the User Profile screen.
 * <p>
 * Currently uses direct DAO access. When NetworkClient integration is complete,
 * this class will be refactored to send requests via the network socket instead.
 * </p>
 */
public class ProfileService {

    /**
     * Loads a user by username.
     *
     * @param username the username
     * @return User object or null
     */
    public User loadUser(String username) {
        try {
            return UserDAO.getInstance().findById(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error loading user: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the total number of bids placed by this user.
     *
     * @param username the username
     * @return bid count
     */
    public int getBidsPlaced(String username) {
        try {
            return AuctionDAO.getInstance().countBidsByUser(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error counting bids: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the number of auctions won by this user.
     *
     * @param username the username
     * @return auctions won count
     */
    public int getAuctionsWon(String username) {
        try {
            return AuctionDAO.getInstance().countWonByUser(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error counting wins: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the number of auctions created/sold by this user.
     *
     * @param username the username
     * @return auctions created count
     */
    public int getAuctionsCreated(String username) {
        try {
            return AuctionDAO.getInstance().countCreatedByUser(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error counting created: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the number of active auctions the user is participating in.
     *
     * @param username the username
     * @return active participation count
     */
    public int getActiveParticipations(String username) {
        try {
            return AuctionDAO.getInstance().countActiveParticipations(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error counting active participations: " + e.getMessage());
            return 0;
        }
    }

    public UserProfileStatsDTO loadStats(String username) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.PROFILE_STATS_REQUEST,
                        null,
                        MessageType.PROFILE_STATS_RESPONSE);
                if (response.getPayload() instanceof UserProfileStatsDTO) {
                    return (UserProfileStatsDTO) response.getPayload();
                }
            } catch (IOException e) {
                if (NetworkRequestClient.isAuthenticationFailure(e)) {
                    System.err.println("[ProfileService] Network stats rejected: " + e.getMessage());
                    return emptyStats();
                }
                System.err.println("[ProfileService] Network stats unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        int bidsPlaced = getBidsPlaced(username);
        int auctionsWon = getAuctionsWon(username);
        int itemsSold = getItemsSold(username);
        int activeParticipations = getActiveParticipations(username);
        long totalSpent = getWallet(username).getTotalSpent();
        double winRate = bidsPlaced > 0 ? ((double) auctionsWon / bidsPlaced) * 100 : 0;
        Date memberSince = getMemberSince(username);

        return new UserProfileStatsDTO(
                bidsPlaced,
                auctionsWon,
                winRate,
                auctionsWon,
                itemsSold,
                activeParticipations,
                totalSpent,
                memberSince);
    }

    public int getItemsSold(String username) {
        try {
            return AuctionDAO.getInstance().countSoldByUser(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error counting sold items: " + e.getMessage());
            return 0;
        }
    }

    public Date getMemberSince(String username) {
        try {
            return UserDAO.getInstance().getCreatedAt(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error loading member since: " + e.getMessage());
            return null;
        }
    }

    public WalletDTO getWallet(String username) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.WALLET_BALANCE_REQUEST,
                        null,
                        MessageType.WALLET_BALANCE_RESPONSE);
                if (response.getPayload() instanceof WalletDTO) {
                    return (WalletDTO) response.getPayload();
                }
            } catch (IOException e) {
                if (NetworkRequestClient.isAuthenticationFailure(e)) {
                    System.err.println("[ProfileService] Network wallet rejected: " + e.getMessage());
                    return WalletDTO.failure(username, "Please log in again.");
                }
                System.err.println("[ProfileService] Network wallet unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            return WalletApplicationService.getInstance().getWallet(username);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error loading wallet: " + e.getMessage());
            return WalletDTO.failure(username, "Could not load wallet.");
        }
    }

    public WalletDTO deposit(String username, long amount) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.WALLET_DEPOSIT_REQUEST,
                        amount,
                        MessageType.WALLET_DEPOSIT_RESPONSE);
                if (response.getPayload() instanceof WalletDTO) {
                    return (WalletDTO) response.getPayload();
                }
            } catch (IOException e) {
                if (NetworkRequestClient.isAuthenticationFailure(e)) {
                    System.err.println("[ProfileService] Network deposit rejected: " + e.getMessage());
                    return WalletDTO.failure(username, "Please log in again.");
                }
                System.err.println("[ProfileService] Network deposit unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            return WalletApplicationService.getInstance().deposit(username, amount);
        } catch (Exception e) {
            System.err.println("[ProfileService] Error depositing money: " + e.getMessage());
            return WalletDTO.failure(username, "Could not deposit money.");
        }
    }

    public User updateEmail(String username, String email) {
        try {
            String normalizedEmail = email == null ? null : email.trim();
            if (normalizedEmail == null || normalizedEmail.isBlank() || !normalizedEmail.contains("@")) {
                return null;
            }

            UserDAO userDAO = UserDAO.getInstance();
            User existingWithEmail = userDAO.findByEmail(normalizedEmail);
            if (existingWithEmail != null && !existingWithEmail.getUsername().equals(username)) {
                return null;
            }

            User current = userDAO.findById(username);
            if (current == null) {
                return null;
            }

            User updated = new User(username, current.getPassword(), normalizedEmail, current.getRole());
            return userDAO.update(username, updated) ? userDAO.findById(username) : null;
        } catch (Exception e) {
            System.err.println("[ProfileService] Error updating email: " + e.getMessage());
            return null;
        }
    }

    public User updatePassword(String username, String password) {
        try {
            if (password == null || password.length() < 4) {
                return null;
            }

            UserDAO userDAO = UserDAO.getInstance();
            User current = userDAO.findById(username);
            if (current == null) {
                return null;
            }

            User updated = new User(username, password, current.getEmail(), current.getRole());
            return userDAO.update(username, updated) ? userDAO.findById(username) : null;
        } catch (Exception e) {
            System.err.println("[ProfileService] Error updating password: " + e.getMessage());
            return null;
        }
    }

    private UserProfileStatsDTO emptyStats() {
        return new UserProfileStatsDTO(0, 0, 0, 0, 0, 0, 0, null);
    }
}
