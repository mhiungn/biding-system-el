package Client.core.network;

import CommonClasses.dto.AuctionUpdatePushDTO;
import CommonClasses.dto.AutoBidNotificationDTO;
import CommonClasses.dto.NotificationPushDTO;
import CommonClasses.dto.WalletUpdatePushDTO;

public interface PushEventListener {
    default void onAuctionUpdatePush(AuctionUpdatePushDTO payload) {
    }

    default void onNotificationPush(NotificationPushDTO payload) {
    }

    default void onWalletUpdatePush(WalletUpdatePushDTO payload) {
    }

    default void onAutoBidNotificationPush(AutoBidNotificationDTO payload) {
    }
}

