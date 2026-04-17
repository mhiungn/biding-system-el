package Packets;

/**
 * Defines the types of messages exchanged between client and server.
 */
public enum MessageType {
    AUCTION_UPDATE,
    HIGHEST_BID_OWNER_LOST,
    AUCTION_CANCELLED,
    AUCTION_CONCLUDED,
    NOTIFY_AUCTION_WINNER,
    NOTIFY_NO_AUCTION_WINNER,
    LOGIN_REQUEST,
    LOGIN_RESPONSE,
    REGISTER_REQUEST,
    REGISTER_RESPONSE,
    CREATE_AUCTION,
    PLACE_BID,
    CANCEL_AUCTION,
    JOIN_AUCTION,
    LEAVE_AUCTION,
    LIST_AUCTIONS
}
