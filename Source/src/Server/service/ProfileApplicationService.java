package Server.service;

import CommonClasses.dto.UserProfileStatsDTO;
import Server.dao.AuctionDAO;
import Server.dao.UserDAO;

import java.util.Date;

public class ProfileApplicationService {
    private static volatile ProfileApplicationService instance;

    public static ProfileApplicationService getInstance() {
        if (instance == null) {
            synchronized (ProfileApplicationService.class) {
                if (instance == null) {
                    instance = new ProfileApplicationService();
                }
            }
        }
        return instance;
    }

    private final AuctionDAO auctionDAO;
    private final UserDAO userDAO;
    private final WalletApplicationService walletService;

    private ProfileApplicationService() {
        this.auctionDAO = AuctionDAO.getInstance();
        this.userDAO = UserDAO.getInstance();
        this.walletService = WalletApplicationService.getInstance();
    }

    public UserProfileStatsDTO loadStats(String username) {
        int bidsPlaced = auctionDAO.countBidsByUser(username);
        int auctionsWon = auctionDAO.countWonByUser(username);
        int itemsSold = auctionDAO.countSoldByUser(username);
        int activeParticipations = auctionDAO.countActiveParticipations(username);
        long totalSpent = walletService.getWallet(username).getTotalSpent();
        double winRate = bidsPlaced > 0 ? ((double) auctionsWon / bidsPlaced) * 100 : 0;
        Date memberSince = userDAO.getCreatedAt(username);

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
}
