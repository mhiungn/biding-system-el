package CommonClasses.dto;

import java.io.Serializable;

public class NotificationPushDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final Integer auctionId;
    private final String type;
    private final int unreadCount;

    public NotificationPushDTO(String username, Integer auctionId, String type, int unreadCount) {
        this.username = username;
        this.auctionId = auctionId;
        this.type = type;
        this.unreadCount = unreadCount;
    }

    public String getUsername() {
        return username;
    }

    public Integer getAuctionId() {
        return auctionId;
    }

    public String getType() {
        return type;
    }

    public int getUnreadCount() {
        return unreadCount;
    }
}
