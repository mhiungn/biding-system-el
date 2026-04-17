package CommonClasses;

/**
 * Defines the timing behavior of an auction.
 * <dl>
 *   <dt>{@link #TIME_FIXED}</dt>
 *   <dd>The auction ends at a fixed time, regardless of bidding activity.</dd>
 *   <dt>{@link #TIME_WITH_RESET}</dt>
 *   <dd>The auction has a countdown phase that resets when new bids arrive,
 *       giving other participants a chance to outbid.</dd>
 * </dl>
 *
 * @see Auction
 */
public enum AuctionType {
    TIME_FIXED,
    TIME_WITH_RESET
}
