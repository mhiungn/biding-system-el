package Packets;

import java.io.Serializable;

/**
 * Represents a network message packet containing a message type and a payload.
 * <p>
 * Used for all client-server communication. The payload is a generic
 * {@link Serializable} object whose concrete type depends on the
 * {@link MessageType}.
 * </p>
 */
public class PacketMessage implements Serializable {

    private MessageType messageType;
    private Serializable payload;

    /**
     * Constructs a new PacketMessage with the given type and payload.
     *
     * @param messageType the type of this message
     * @param payload     the data payload (must be Serializable)
     */
    public PacketMessage(MessageType messageType, Serializable payload) {
        this.messageType = messageType;
        this.payload = payload;
    }

    /**
     * Returns the message type.
     *
     * @return the {@link MessageType}
     */
    public MessageType getMessageType() {
        return messageType;
    }

    /**
     * Sets the message type.
     *
     * @param messageType the new message type
     */
    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    /**
     * Returns the payload of this message.
     *
     * @return the payload object
     */
    public Serializable getPayload() {
        return payload;
    }

    /**
     * Sets the payload of this message.
     *
     * @param payload the new payload
     */
    public void setPayload(Serializable payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "PacketMessage{" +
                "messageType=" + messageType +
                ", payload=" + payload +
                '}';
    }
}
