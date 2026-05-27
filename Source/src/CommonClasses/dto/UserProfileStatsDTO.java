package CommonClasses.dto;

import java.io.Serializable;
import java.util.Date;

public class UserProfileStatsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int bidsPlaced;
    private final int auctionsWon;
    private final double winRate;
    private final int itemsBought;
    private final int itemsSold;
    private final int activeParticipations;
    private final long totalSpent;
    private final Date memberSince;

    public UserProfileStatsDTO(int bidsPlaced, int auctionsWon, double winRate, int itemsBought, int itemsSold,
                               int activeParticipations, long totalSpent, Date memberSince) {
        this.bidsPlaced = bidsPlaced;
        this.auctionsWon = auctionsWon;
        this.winRate = winRate;
        this.itemsBought = itemsBought;
        this.itemsSold = itemsSold;
        this.activeParticipations = activeParticipations;
        this.totalSpent = totalSpent;
        this.memberSince = memberSince == null ? null : new Date(memberSince.getTime());
    }

    public int getBidsPlaced() {
        return bidsPlaced;
    }

    public int getAuctionsWon() {
        return auctionsWon;
    }

    public double getWinRate() {
        return winRate;
    }

    public int getItemsBought() {
        return itemsBought;
    }

    public int getItemsSold() {
        return itemsSold;
    }

    public int getActiveParticipations() {
        return activeParticipations;
    }

    public long getTotalSpent() {
        return totalSpent;
    }

    public Date getMemberSince() {
        return memberSince == null ? null : new Date(memberSince.getTime());
    }
}
