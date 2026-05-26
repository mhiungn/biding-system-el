package CommonClasses.dto;

import java.io.Serializable;
import java.util.Date;

public class NotificationDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String username;
    private final Integer auctionId;
    private final String type;
    private final String title;
    private final String message;
    private final String actionTarget;
    private final boolean read;
    private final Date createdAt;

    public NotificationDTO(long id, String username, Integer auctionId, String type, String title,
                           String message, String actionTarget, boolean read, Date createdAt) {
        this.id = id;
        this.username = username;
        this.auctionId = auctionId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.actionTarget = actionTarget;
        this.read = read;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
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

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getActionTarget() {
        return actionTarget;
    }

    public boolean isRead() {
        return read;
    }

    public Date getCreatedAt() {
        return createdAt;
    }
}
