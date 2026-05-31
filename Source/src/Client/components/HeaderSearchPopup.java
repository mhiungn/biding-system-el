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
    private static final String GLOBAL_STYLESHEET = "/Client/views/common/scrollbar.css";
    private static final String COMPONENT_STYLESHEET = "/Client/views/components/components.css";

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
        root.getStyleClass().add("auction-search-panel");
        root.getStylesheets().add(HeaderSearchPopup.class.getResource(GLOBAL_STYLESHEET).toExternalForm());
        root.getStylesheets().add(HeaderSearchPopup.class.getResource(COMPONENT_STYLESHEET).toExternalForm());
        root.setPrefWidth(360);
        root.setMaxHeight(420);
        root.setPadding(new Insets(14));

        searchField = new TextField();
        searchField.getStyleClass().add("auction-search-field");
        searchField.setPromptText("Search auctions");
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
        progressIndicator.getStyleClass().add("auction-search-progress");
        progressIndicator.setMaxSize(22, 22);
        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);

        HBox topRow = new HBox(8, searchField, progressIndicator);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        resultsBox = new VBox(6);
        resultsBox.getStyleClass().add("auction-search-results");
        ScrollPane scrollPane = new ScrollPane(resultsBox);
        scrollPane.getStyleClass().add("auction-search-results-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefHeight(320);

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
        container.getStyleClass().add("auction-search-result-card");
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(10));

        VBox text = new VBox(4);
        HBox.setHgrow(text, Priority.ALWAYS);

        Item item = row.getItem();
        Label title = new Label(item == null ? "Unknown auction" : item.getName());
        title.setMaxWidth(Double.MAX_VALUE);
        title.getStyleClass().add("auction-search-result-title");

        String priceText = item == null ? "" : currencyFormat.format((long) item.getCurrentHighestPrice()) + " VND";
        Label meta = new Label(priceText + " | " + row.getBidCount() + " bids | " + formatTime(row));
        meta.getStyleClass().add("auction-search-result-meta");

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
        label.getStyleClass().add("auction-search-empty");
        label.setPadding(new Insets(12));
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
