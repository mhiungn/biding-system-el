package Client.features.dashboard;

import java.io.Serializable;

public class DashboardStats implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int activeAuctions;
    private final int endingToday;
    private final int totalBids;

    public DashboardStats(int activeAuctions, int endingToday, int totalBids) {
        this.activeAuctions = activeAuctions;
        this.endingToday = endingToday;
        this.totalBids = totalBids;
    }

    public int getActiveAuctions() {
        return activeAuctions;
    }

    public int getEndingToday() {
        return endingToday;
    }

    public int getTotalBids() {
        return totalBids;
    }
}
