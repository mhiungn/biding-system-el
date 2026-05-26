package Client.components;

import Client.core.network.NetworkPushManager;
import Client.core.network.PushEventListener;
import Client.features.notifications.NotificationClientService;
import CommonClasses.dto.NotificationDTO;
import CommonClasses.dto.NotificationPushDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.function.Consumer;

public class NotificationPopup implements PushEventListener {
    private final NotificationClientService service;
    private final String username;
    private final Consumer<NotificationDTO> notificationAction;
    private final Runnable afterRead;
    private final Popup popup = new Popup();
    private final VBox list = new VBox(6);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MMM dd, HH:mm");

    public NotificationPopup(NotificationClientService service, String username,
                             Consumer<NotificationDTO> notificationAction, Runnable afterRead) {
        this.service = service;
        this.username = username;
        this.notificationAction = notificationAction;
        this.afterRead = afterRead;
        popup.setAutoHide(true);
        popup.setOnHidden(event -> NetworkPushManager.getInstance().unregister(this));
        popup.getContent().add(createContent());
    }

    public void show(Button anchor) {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }
        NetworkPushManager.getInstance().register(this);
        reload();
        popup.show(anchor, anchor.localToScreen(0, anchor.getHeight()).getX() - 280,
                anchor.localToScreen(0, anchor.getHeight()).getY() + 8);
    }

    public void hide() {
        popup.hide();
    }

    private VBox createContent() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        root.setPrefWidth(340);
        root.setMaxHeight(420);
        root.setStyle("-fx-background-color: #1f1f1f; -fx-border-color: #333333; "
                + "-fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8;");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Notifications");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: 700;");
        Button markAll = new Button("Mark all read");
        markAll.setStyle("-fx-background-color: transparent; -fx-text-fill: #1ed760; -fx-cursor: hand;");
        markAll.setOnAction(event -> {
            service.markAllRead(username);
            reload();
            afterRead.run();
        });
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getChildren().addAll(title, markAll);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(330);
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

        root.getChildren().addAll(header, scroll);
        return root;
    }

    private void reload() {
        list.getChildren().clear();
        List<NotificationDTO> notifications = service.listNotifications(username);
        if (notifications.isEmpty()) {
            Label empty = new Label("No notifications");
            empty.setStyle("-fx-text-fill: #b3b3b3; -fx-padding: 16;");
            list.getChildren().add(empty);
            return;
        }

        for (NotificationDTO notification : notifications) {
            list.getChildren().add(createRow(notification));
        }
    }

    private VBox createRow(NotificationDTO notification) {
        VBox row = new VBox(4);
        row.setPadding(new Insets(10));
        row.setStyle(notification.isRead()
                ? "-fx-background-color: #242424; -fx-background-radius: 6; -fx-cursor: hand;"
                : "-fx-background-color: #173522; -fx-background-radius: 6; -fx-cursor: hand;");

        Label title = new Label(notification.getTitle());
        title.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 700;");
        title.setWrapText(true);

        Label message = new Label(notification.getMessage());
        message.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px;");
        message.setWrapText(true);

        Label time = new Label(notification.getCreatedAt() == null ? "" : timeFormat.format(notification.getCreatedAt()));
        time.setStyle("-fx-text-fill: #8a8a8a; -fx-font-size: 10px;");

        row.getChildren().addAll(title, message, time);
        row.setOnMouseClicked(event -> {
            service.markRead(username, notification.getId());
            afterRead.run();
            hide();
            notificationAction.accept(notification);
        });
        return row;
    }

    @Override
    public void onNotificationPush(NotificationPushDTO payload) {
        if (payload == null || !username.equals(payload.getUsername()) || !popup.isShowing()) {
            return;
        }
        reload();
        afterRead.run();
    }
}
