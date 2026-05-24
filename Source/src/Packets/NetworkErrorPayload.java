package Packets;

import java.io.Serializable;
import java.util.Date;

/**
 * Structured error payload returned by the network layer.
 */
public class NetworkErrorPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String message;
    private final MessageType requestType;
    private final Date createdAt;

    public NetworkErrorPayload(String code, String message, MessageType requestType) {
        this.code = code;
        this.message = message;
        this.requestType = requestType;
        this.createdAt = new Date();
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public MessageType getRequestType() {
        return requestType;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return code + ": " + message;
    }
}