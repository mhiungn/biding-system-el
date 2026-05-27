package Client.features.notifications;

import Client.core.network.NetworkRequestClient;
import CommonClasses.dto.NotificationDTO;
import Packets.MessageType;
import Packets.PacketMessage;
import Server.service.NotificationApplicationService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NotificationClientService {

    public List<NotificationDTO> listNotifications(String username) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.NOTIFICATION_LIST_REQUEST,
                        null,
                        MessageType.NOTIFICATION_LIST_RESPONSE);
                if (response.getPayload() instanceof List<?>) {
                    return (List<NotificationDTO>) response.getPayload();
                }
            } catch (IOException e) {
                if (NetworkRequestClient.isAuthenticationFailure(e)) {
                    System.err.println("[NotificationClientService] Network list rejected: " + e.getMessage());
                    return new ArrayList<>();
                }
                System.err.println("[NotificationClientService] Network list unavailable, using fallback: "
                        + e.getMessage());
            }
        }
        try {
            return NotificationApplicationService.getInstance().getRecentNotifications(username);
        } catch (Exception e) {
            System.err.println("[NotificationClientService] Cannot load notifications: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public int countUnread(String username) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.NOTIFICATION_COUNT_REQUEST,
                        null,
                        MessageType.NOTIFICATION_COUNT_RESPONSE);
                if (response.getPayload() instanceof Number) {
                    return ((Number) response.getPayload()).intValue();
                }
            } catch (IOException e) {
                if (NetworkRequestClient.isAuthenticationFailure(e)) {
                    System.err.println("[NotificationClientService] Network count rejected: " + e.getMessage());
                    return 0;
                }
                System.err.println("[NotificationClientService] Network count unavailable, using fallback: "
                        + e.getMessage());
            }
        }
        try {
            return NotificationApplicationService.getInstance().getUnreadCount(username);
        } catch (Exception e) {
            System.err.println("[NotificationClientService] Cannot count notifications: " + e.getMessage());
            return 0;
        }
    }

    public void markRead(String username, long notificationId) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                NetworkRequestClient.request(
                        MessageType.NOTIFICATION_MARK_READ_REQUEST,
                        notificationId,
                        MessageType.NOTIFICATION_MARK_READ_RESPONSE);
                return;
            } catch (IOException e) {
                if (NetworkRequestClient.isAuthenticationFailure(e)) {
                    System.err.println("[NotificationClientService] Network mark-read rejected: " + e.getMessage());
                    return;
                }
                System.err.println("[NotificationClientService] Network mark-read unavailable, using fallback: "
                        + e.getMessage());
            }
        }
        NotificationApplicationService.getInstance().markRead(username, notificationId);
    }

    public void markAllRead(String username) {
        if (NetworkRequestClient.isEnabled()) {
            try {
                NetworkRequestClient.request(
                        MessageType.NOTIFICATION_MARK_ALL_READ_REQUEST,
                        null,
                        MessageType.NOTIFICATION_MARK_ALL_READ_RESPONSE);
                return;
            } catch (IOException e) {
                if (NetworkRequestClient.isAuthenticationFailure(e)) {
                    System.err.println("[NotificationClientService] Network mark-all-read rejected: " + e.getMessage());
                    return;
                }
                System.err.println("[NotificationClientService] Network mark-all-read unavailable, using fallback: "
                        + e.getMessage());
            }
        }
        NotificationApplicationService.getInstance().markAllRead(username);
    }
}
