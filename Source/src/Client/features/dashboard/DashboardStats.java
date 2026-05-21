package Client.features.dashboard;

public class DashboardStats {
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
