package Server;

import CommonClasses.Auction;

import java.util.TimerTask;

/**
 * A timer task that terminates a "Time_Fixed" auction when its scheduled time arrives.
 * <p>
 * When executed, this task simply calls {@link Auction#conclude()} to end the auction
 * and determine the winner.
 * </p>
 */
public class AuctionTerminateTask extends TimerTask {

    private Auction auction;

    /**
     * Constructs a new termination task for the given auction.
     *
     * @param auction the auction to terminate
     */
    public AuctionTerminateTask(Auction auction) {
        this.auction = auction;
    }

    @Override
    public void run() {
        auction.conclude();
    }
}
