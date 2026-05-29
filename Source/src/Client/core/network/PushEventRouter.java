package Client.core.network;

import CommonClasses.dto.AuctionUpdatePushDTO;
import CommonClasses.dto.AutoBidNotificationDTO;
import CommonClasses.dto.NotificationPushDTO;
import CommonClasses.dto.WalletUpdatePushDTO;
import Packets.MessageType;
import Packets.PacketMessage;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

public class PushEventRouter {
    private final Set<PushEventListener> listeners = new CopyOnWriteArraySet<>();

    public void register(PushEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void unregister(PushEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void route(PacketMessage packet, Consumer<Runnable> uiExecutor) {
        if (packet == null || packet.getMessageType() == null) {
            return;
        }
        Consumer<Runnable> executor = uiExecutor == null ? Runnable::run : uiExecutor;

        if (packet.getMessageType() == MessageType.AUCTION_UPDATE_PUSH
                && packet.getPayload() instanceof AuctionUpdatePushDTO) {
            AuctionUpdatePushDTO payload = (AuctionUpdatePushDTO) packet.getPayload();
            executor.accept(() -> listeners.forEach(listener -> listener.onAuctionUpdatePush(payload)));
            return;
        }

        if (packet.getMessageType() == MessageType.NOTIFICATION_PUSH
                && packet.getPayload() instanceof NotificationPushDTO) {
            NotificationPushDTO payload = (NotificationPushDTO) packet.getPayload();
            executor.accept(() -> listeners.forEach(listener -> listener.onNotificationPush(payload)));
            return;
        }

        if (packet.getMessageType() == MessageType.WALLET_UPDATE_PUSH
                && packet.getPayload() instanceof WalletUpdatePushDTO) {
            WalletUpdatePushDTO payload = (WalletUpdatePushDTO) packet.getPayload();
            executor.accept(() -> listeners.forEach(listener -> listener.onWalletUpdatePush(payload)));
            return;
        }

        if (packet.getMessageType() == MessageType.AUTO_BID_NOTIFICATION
                && packet.getPayload() instanceof AutoBidNotificationDTO) {
            AutoBidNotificationDTO payload = (AutoBidNotificationDTO) packet.getPayload();
            executor.accept(() -> listeners.forEach(listener -> listener.onAutoBidNotificationPush(payload)));
        }
    }
}

