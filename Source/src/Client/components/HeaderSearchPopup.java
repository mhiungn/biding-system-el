package Client.components;

import Client.features.search.SearchService;
import CommonClasses.Items.Item;
import CommonClasses.dto.DashboardAuctionRow;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class HeaderSearchPopup {
    private static final int RESULT_LIMIT = 8;

    private final SearchService searchService;
    private final Consumer<DashboardAuctionRow> onResultSelected;
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));

    private Popup popup;
    private TextField searchField;
    private VBox resultsBox;
    private ProgressIndicator progressIndicator;

    public HeaderSearchPopup(SearchService searchService, Consumer<DashboardAuctionRow> onResultSelected) {
        this.searchService = searchService;
        this.onResultSelected = onResultSelected;
    }

    public void show(javafx.scene.control.Button anchor) {
        if (popup == null) {
            buildPopup();
        }
        if (popup.isShowing()) {
            popup.hide();
            return;
        }

        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, bounds.getMaxX() - 360, bounds.getMaxY() + 8);
        Platform.runLater(() -> {
            searchField.requestFocus();
            searchField.selectAll();
        });
    }

    private void buildPopup() {
        popup = new Popup();
        popup.setAutoHide(true);

        VBox root = new VBox(10);
        root.setPrefWidth(360);
        root.setMaxHeight(420);
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: #181818; -fx-background-radius: 8; "
                + "-fx-border-color: #333333; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 18, 0, 0, 8);");

        searchField = new TextField();
        searchField.setPromptText("Search auctions");
        searchField.setStyle("-fx-background-color: #242424; -fx-text-fill: white; -fx-prompt-text-fill: #8d8d8d; "
                + "-fx-background-radius: 6; -fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-padding: 9 10;");
        searchField.setOnAction(event -> runSearch());
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                popup.hide();
            }
        });
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            debounce.stop();
            debounce.setOnFinished(event -> runSearch());
            debounce.playFromStart();
        });

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(22, 22);
        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);

        HBox topRow = new HBox(8, searchField, progressIndicator);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        resultsBox = new VBox(6);
        ScrollPane scrollPane = new ScrollPane(resultsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefHeight(320);
        scrollPane.setStyle("-fx-background-color: transparent;");
        scrollPane.getStyleClass().add("search-results-scroll");

        root.getChildren().addAll(topRow, scrollPane);
        popup.getContent().add(root);
        renderMessage("Type at least 2 characters.");
    }

    private void runSearch() {
        String keyword = searchField.getText();
        if (keyword == null || keyword.trim().length() < 2) {
            renderMessage("Type at least 2 characters.");
            return;
        }

        progressIndicator.setVisible(true);
        progressIndicator.setManaged(true);

        Task<List<DashboardAuctionRow>> task = new Task<>() {
            @Override
            protected List<DashboardAuctionRow> call() {
                return searchService.searchAuctions(keyword, RESULT_LIMIT);
            }
        };
        task.setOnSucceeded(event -> {
            progressIndicator.setVisible(false);
            progressIndicator.setManaged(false);
            renderResults(task.getValue());
        });
        task.setOnFailed(event -> {
            progressIndicator.setVisible(false);
            progressIndicator.setManaged(false);
            renderMessage("Search failed.");
        });

        Thread thread = new Thread(task, "auction-header-search");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderResults(List<DashboardAuctionRow> rows) {
        resultsBox.getChildren().clear();
        if (rows == null || rows.isEmpty()) {
            renderMessage("No auctions found.");
            return;
        }

        for (DashboardAuctionRow row : rows) {
            resultsBox.getChildren().add(createResultRow(row));
        }
    }

    private HBox createResultRow(DashboardAuctionRow row) {
        HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(10));
        container.setStyle("-fx-background-color: #222222; -fx-background-radius: 6; -fx-cursor: hand;");

        VBox text = new VBox(4);
        HBox.setHgrow(text, Priority.ALWAYS);

        Item item = row.getItem();
        Label title = new Label(item == null ? "Unknown auction" : item.getName());
        title.setMaxWidth(Double.MAX_VALUE);
        title.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 700;");

        String priceText = item == null ? "" : currencyFormat.format((long) item.getCurrentHighestPrice()) + " VND";
        Label meta = new Label(priceText + " | " + row.getBidCount() + " bids | " + formatTime(row));
        meta.setStyle("-fx-text-fill: #b3b3b3; -fx-font-size: 10px;");

        text.getChildren().addAll(title, meta);
        container.getChildren().add(text);
        container.setOnMouseClicked(event -> {
            popup.hide();
            onResultSelected.accept(row);
        });
        return container;
    }

    private void renderMessage(String message) {
        resultsBox.getChildren().clear();
        Label label = new Label(message);
        label.setPadding(new Insets(12));
        label.setStyle("-fx-text-fill: #b3b3b3; -fx-font-size: 11px;");
        resultsBox.getChildren().add(label);
    }

    private String formatTime(DashboardAuctionRow row) {
        if (row.getEndTime() == null) {
            return "No end time";
        }
        long remaining = row.getEndTime().getTime() - System.currentTimeMillis();
        if (remaining <= 0) {
            return "Ended";
        }
        long hours = remaining / 3_600_000;
        if (hours >= 24) {
            return (hours / 24) + "d left";
        }
        return Math.max(1, hours) + "h left";
    }
}
