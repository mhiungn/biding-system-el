package Server;

import CommonClasses.Auction;

import java.util.TimerTask;

/**
 * A timer task that manages the countdown phase for "Time_With_Reset" auctions.
 * <p>
 * Implements the "going once… going twice… sold!" sequence. If a new bid arrives
 * during the countdown, the task can be cancelled and a fresh one scheduled,
 * effectively resetting the countdown.
 * </p>
 */
public class AuctionCountdownTask extends TimerTask {

    private Auction auction;
    private boolean canConclude;
    private int countdownStep;

    /**
     * Constructs a new countdown task for the given auction.
     *
     * @param auction the auction this countdown is for
     */
    public AuctionCountdownTask(Auction auction) {
        this.auction = auction;
        this.canConclude = true;
        this.countdownStep = 0;
    }

    /**
     * Returns whether this task is allowed to conclude the auction.
     *
     * @return {@code true} if this task can conclude the auction
     */
    public boolean isCanConclude() {
        return canConclude;
    }

    /**
     * Sets whether this task is allowed to conclude the auction.
     * Set to {@code false} before cancelling to prevent a race condition where
     * the task concludes the auction after being "cancelled" but before the
     * cancellation takes effect.
     *
     * @param canConclude {@code true} to allow conclusion, {@code false} to prevent it
     */
    public void setCanConclude(boolean canConclude) {
        this.canConclude = canConclude;
    }

    @Override
    public void run() {
        auction.setInCountDown(true);

        // Countdown sequence: going once → going twice → sold
        try {
            for (countdownStep = 0; countdownStep < 3; countdownStep++) {
                if (!canConclude) {
                    return; // Task was cancelled due to a new bid
                }
                Thread.sleep(5000); // 5 seconds between each step
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (canConclude) {
            auction.conclude();
        }
    }
}
