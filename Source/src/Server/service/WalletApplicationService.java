package Server.service;

import CommonClasses.dto.WalletDTO;
import Server.dao.WalletDAO;

import java.sql.Connection;
import java.sql.SQLException;

public class WalletApplicationService {
    public static final long DAILY_DEPOSIT_LIMIT = 10_000_000L;

    private static volatile WalletApplicationService instance;

    public static WalletApplicationService getInstance() {
        if (instance == null) {
            synchronized (WalletApplicationService.class) {
                if (instance == null) {
                    instance = new WalletApplicationService();
                }
            }
        }
        return instance;
    }

    private final WalletDAO walletDAO;

    private WalletApplicationService() {
        this.walletDAO = WalletDAO.getInstance();
    }

    public WalletDTO ensureWallet(String username) {
        walletDAO.createWalletIfMissing(username);
        return getWallet(username);
    }

    public WalletDTO getWallet(String username) {
        walletDAO.createWalletIfMissing(username);
        long balance = walletDAO.getBalance(username);
        long held = walletDAO.getHeldAmount(username);
        long spent = walletDAO.getTotalSpent(username);
        return WalletDTO.success(username, balance, held, spent);
    }

    public WalletDTO deposit(String username, long amount) {
        if (amount <= 0) {
            return WalletDTO.failure(username, "Deposit amount must be positive.");
        }
        if (amount >= DAILY_DEPOSIT_LIMIT) {
            return WalletDTO.failure(username, "Daily deposit limit is below 10,000,000.");
        }

        long todayTotal = walletDAO.getTodayDepositTotal(username);
        if (todayTotal + amount >= DAILY_DEPOSIT_LIMIT) {
            long remaining = Math.max(0, DAILY_DEPOSIT_LIMIT - todayTotal);
            return WalletDTO.failure(username,
                    "You have already deposited " + todayTotal
                            + " today. You can only add less than " + remaining + " more.");
        }

        walletDAO.deposit(username, amount);
        return getWallet(username);
    }

    public boolean canAffordBid(String username, int auctionId, long bidAmount) {
        walletDAO.createWalletIfMissing(username);
        long availableForOtherAuctions = walletDAO.getAvailableBalance(username);
        long existingHoldForAuction = walletDAO.getHoldForAuction(username, auctionId);
        return bidAmount <= availableForOtherAuctions + existingHoldForAuction;
    }

    public boolean canAffordBid(Connection conn, String username, int auctionId, long bidAmount) throws SQLException {
        return walletDAO.canAffordBid(conn, username, auctionId, bidAmount);
    }

    public void reserveBidAmount(String username, int auctionId, long amount) {
        walletDAO.reserveHold(username, auctionId, amount);
    }

    public void reserveBidAmount(Connection conn, String username, int auctionId, long amount) throws SQLException {
        walletDAO.reserveHold(conn, username, auctionId, amount);
    }

    public void releaseBidHold(String username, int auctionId) {
        walletDAO.releaseHold(username, auctionId);
    }

    public void releaseBidHold(Connection conn, String username, int auctionId) throws SQLException {
        walletDAO.releaseHold(conn, username, auctionId);
    }

    public void finalizeWinningPayment(String username, int auctionId, long amount) {
        walletDAO.convertHoldToSpent(username, auctionId, amount);
    }

    public boolean hasSpentForAuction(String username, int auctionId) {
        return walletDAO.hasTransaction(username, "SPENT", auctionId);
    }

    public void creditSellerPayout(String seller, int auctionId, long amount) {
        walletDAO.creditSellerPayout(seller, auctionId, amount);
    }

    public boolean hasEarnedForAuction(String username, int auctionId) {
        return walletDAO.hasTransaction(username, "REVENUE", auctionId);
    }
}
