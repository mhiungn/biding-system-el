package Packets;

import java.io.Serializable;

/**
 * Factory methods for common packet shapes.
 */
public final class PacketFactory {
    private PacketFactory() {
    }

    public static PacketMessage of(MessageType type, Serializable payload) {
        return new PacketMessage(type, payload);
    }

    public static PacketMessage error(String code, String message, MessageType requestType) {
        return new PacketMessage(
                MessageType.NETWORK_ERROR,
                new NetworkErrorPayload(code, message, requestType));
    }
}