package Client.features.dashboard;

import Client.core.ui.NavigationController;
import Client.features.auth.SessionManager;
import CommonClasses.Items.Item;
import CommonClasses.User;
import Server.dao.DashboardAuctionRow;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public class DashboardController extends NavigationController {

    @FXML private GridPane auctionCardsGrid;
    @FXML private Button btnPrevPage;
    @FXML private Button btnNextPage;
    @FXML private Button btnPage1;
    @FXML private Button btnPage2;
    @FXML private Button btnPage3;
    @FXML private Label lblActiveAuctions;
    @FXML private Label lblEndingToday;
    @FXML private Label lblTotalBids;
    @FXML private Label lblCurrentUserQuickInfo;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private ComboBox<String> sortFilter;
    @FXML private ComboBox<String> priceRangeFilter;

    private static final int PRICE_GAP = 500_000;
    private final DashboardService dashboardService = new DashboardService();
    private List<DashboardAuctionRow> currentPageRows;
    private int currentPage = 0;
    private int totalItems = 0;
    private int totalPages = 0;
    private String selectedCategory = "ALL";
    private boolean endingSoonOnly = false;
    private Float selectedMinPrice = null;
    private Float selectedMaxPrice = null;

    private static final String LABEL_CATEGORY = "CATEGORY";
    private static final String LABEL_PRICE_RANGE = "PRICE RANGE";
    private static final String TIME_ALL = "TIME:ALL";
    private static final String TIME_ENDING_SOON = "TIME: ENDING SOON (<3 DAYS)";

    private boolean suppressFilterReload;

    @FXML
    public void initialize() {
        User currentUser = SessionManager.getCurrentUser();
        if (lblCurrentUserQuickInfo != null) {
            lblCurrentUserQuickInfo.setText(currentUser == null
                    ? "Guest"
                    : currentUser.getUsername() + " | " + currentUser.getEmail());
        }
        setupFilters();
        setupPaginationButtons();
        refreshStats();
        showPage(0);
    }

    private void reloadFromFilters() {
        if (suppressFilterReload) {
            return;
        }
        showPage(0);
    }

    public void showPage(int pageNumber) {
        DashboardPageResult result = dashboardService.loadAuctionPage(
                pageNumber, selectedCategory, endingSoonOnly, selectedMinPrice, selectedMaxPrice
        );
        this.currentPage = Math.max(pageNumber, 0);
        this.currentPageRows = result.getRows();
        this.totalItems = result.getTotalItems();
        this.totalPages = (int) Math.ceil((double) totalItems / DashboardService.PAGE_SIZE);
        if (totalPages == 0) {
            this.currentPage = 0;
        } else if (currentPage >= totalPages) {
            this.currentPage = totalPages - 1;
            result = dashboardService.loadAuctionPage(
                    this.currentPage, selectedCategory, endingSoonOnly, selectedMinPrice, selectedMaxPrice
            );
            this.currentPageRows = result.getRows();
        }
        loadAuctionCards(currentPageRows);
        updatePaginationUi();
    }

    public void loadAuctionCards(List<DashboardAuctionRow> rows) {
        if (auctionCardsGrid == null) {
            return;
        }

        auctionCardsGrid.getChildren().clear();

        int row = 0;
        int col = 0;

        for (DashboardAuctionRow rowData : rows) {
            VBox card = createAuctionCard(rowData);
            auctionCardsGrid.add(card, col, row);

            col++;
            if (col == 4) {
                col = 0;
                row++;
            }
        }
    }

    private VBox createAuctionCard(DashboardAuctionRow rowData) {
        Item item = rowData.getItem();
        VBox card = new VBox();
        card.getStyleClass().add("auction-card");

        Region image = new Region();
        image.getStyleClass().add("card-image");

        VBox content = new VBox(5);
        content.getStyleClass().add("card-content");

        Label title = new Label(item.getName());
        title.getStyleClass().add("card-title");

        Label subtitle = new Label(item.getDescription() == null ? "No description" : item.getDescription());
        subtitle.getStyleClass().add("card-subtitle");

        String formattedPrice = "Current: " + formatCurrency(item.getCurrentHighestPrice());
        Label price = new Label(formattedPrice);
        price.getStyleClass().add("card-price");

        Label startingPrice = new Label("Starting: " + formatCurrency(item.getStartingPrice()));
        startingPrice.getStyleClass().add("card-subtitle");

        HBox metaBox = new HBox(15);
        metaBox.setAlignment(Pos.CENTER_LEFT);

        HBox timeBox = new HBox(5);
        timeBox.setAlignment(Pos.CENTER_LEFT);
        Region timeIcon = new Region();
        timeIcon.getStyleClass().add("time-icon");
        Label timeLabel = new Label(formatEndTime(rowData.getEndTime()));
        timeLabel.getStyleClass().add("card-meta");
        timeBox.getChildren().addAll(timeIcon, timeLabel);

        HBox bidBox = new HBox(5);
        bidBox.setAlignment(Pos.CENTER_LEFT);
        Region bidIcon = new Region();
        bidIcon.getStyleClass().add("bid-icon");
        Label bidLabel = new Label(rowData.getBidCount() + " bids");
        bidLabel.getStyleClass().add("card-meta");
        bidBox.getChildren().addAll(bidIcon, bidLabel);

        metaBox.getChildren().addAll(timeBox, bidBox);

        Button bidButton = new Button("PLACE BID");
        bidButton.getStyleClass().add("bid-button");
        bidButton.setMaxWidth(Double.MAX_VALUE);
        bidButton.setOnAction(e -> {
            try {
                switchToBiddingDetails(e, rowData.getAuctionId());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        content.getChildren().addAll(title, subtitle, price, startingPrice, metaBox, bidButton);
        card.getChildren().addAll(image, content);

        return card;
    }

    private void setupFilters() {
        suppressFilterReload = true;
        try {
            setupCategoryFilter();
            setupTimeFilter();
            setupPriceRangeFilter();
        } finally {
            suppressFilterReload = false;
        }
    }

    private void setupCategoryFilter() {
        if (categoryFilter == null) {
            return;
        }
        categoryFilter.getItems().setAll("All", "ELECTRONICS", "ART", "VEHICLE");
        categoryFilter.setEditable(false);
        categoryFilter.setPromptText(null);
        categoryFilter.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || "All".equalsIgnoreCase(item)) {
                    setText(LABEL_CATEGORY);
                } else {
                    setText(item);
                    //LABEL_CATEGORY + " · " + (display choice)
                }
            }
        });
        categoryFilter.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
            }
        });
        categoryFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedCategory = (newVal == null || "All".equalsIgnoreCase(newVal)) ? "ALL" : newVal;
            reloadFromFilters();
        });
        categoryFilter.getSelectionModel().selectFirst();
    }

    private void setupTimeFilter() {
        if (sortFilter == null) {
            return;
        }
        sortFilter.getItems().setAll(TIME_ALL, TIME_ENDING_SOON);
        sortFilter.setEditable(false);
        sortFilter.setPromptText(null);
        sortFilter.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(TIME_ALL);
                } else {
                    setText(item);
                }
            }
        });
        sortFilter.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
            }
        });
        sortFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            endingSoonOnly = TIME_ENDING_SOON.equals(newVal);
            reloadFromFilters();
        });
        sortFilter.getSelectionModel().select(TIME_ALL);
    }

    private void setupPriceRangeFilter() {
        if (priceRangeFilter == null) {
            return;
        }
        priceRangeFilter.getItems().setAll(
                "Any",
                "0 - 500.000 VND",
                "500.000 - 1.000.000 VND",
                "1.000.000 - 1.500.000 VND",
                ">= 1.500.000 VND"
        );
        priceRangeFilter.setEditable(false);
        priceRangeFilter.setPromptText(null);
        priceRangeFilter.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || "Any".equalsIgnoreCase(item)) {
                    setText(LABEL_PRICE_RANGE);
                } else {
                    setText(LABEL_PRICE_RANGE + " · " + item);
                }
            }
        });
        priceRangeFilter.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
            }
        });
        priceRangeFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            applyPriceRange(newVal);
            reloadFromFilters();
        });
        priceRangeFilter.getSelectionModel().selectFirst();
    }

    private void setupPaginationButtons() {
        if (btnPrevPage != null) {
            btnPrevPage.setOnAction(e -> showPage(currentPage - 1));
        }
        if (btnNextPage != null) {
            btnNextPage.setOnAction(e -> showPage(currentPage + 1));
        }
        if (btnPage1 != null) {
            btnPage1.setOnAction(e -> goToPageFromButton(btnPage1));
        }
        if (btnPage2 != null) {
            btnPage2.setOnAction(e -> goToPageFromButton(btnPage2));
        }
        if (btnPage3 != null) {
            btnPage3.setOnAction(e -> goToPageFromButton(btnPage3));
        }
    }

    private void goToPageFromButton(Button button) {
        if (button == null || button.getText() == null || button.getText().trim().isEmpty()) {
            return;
        }
        int page = Integer.parseInt(button.getText()) - 1;
        showPage(page);
    }

    private void updatePaginationUi() {
        if (btnPrevPage != null) {
            btnPrevPage.setDisable(currentPage <= 0);
        }
        if (btnNextPage != null) {
            btnNextPage.setDisable(totalPages == 0 || currentPage >= totalPages - 1);
        }

        int windowStart = Math.max(0, currentPage - 1);
        if (windowStart + 3 > totalPages) {
            windowStart = Math.max(0, totalPages - 3);
        }
        applyPageButton(btnPage1, windowStart, currentPage);
        applyPageButton(btnPage2, windowStart + 1, currentPage);
        applyPageButton(btnPage3, windowStart + 2, currentPage);
    }

    private void applyPageButton(Button button, int pageNumber, int selectedPage) {
        if (button == null) {
            return;
        }
        boolean visible = pageNumber >= 0 && pageNumber < totalPages;
        button.setVisible(visible);
        button.setManaged(visible);
        if (!visible) {
            return;
        }
        button.setText(String.valueOf(pageNumber + 1));
        boolean isActive = pageNumber == selectedPage;
        if (isActive && !button.getStyleClass().contains("pagination-active")) {
            button.getStyleClass().add("pagination-active");
        } else if (!isActive) {
            button.getStyleClass().remove("pagination-active");
        }
    }

    private void applyPriceRange(String selectedPriceRange) {
        selectedMinPrice = null;
        selectedMaxPrice = null;
        if (selectedPriceRange == null || "Any".equalsIgnoreCase(selectedPriceRange)) {
            return;
        }
        if ("0 - 500.000 VND".equals(selectedPriceRange)) {
            selectedMinPrice = 0f;
            selectedMaxPrice = (float) PRICE_GAP;
            return;
        }
        if ("500.000 - 1.000.000 VND".equals(selectedPriceRange)) {
            selectedMinPrice = (float) PRICE_GAP;
            selectedMaxPrice = (float) (PRICE_GAP * 2);
            return;
        }
        if ("1.000.000 - 1.500.000 VND".equals(selectedPriceRange)) {
            selectedMinPrice = (float) (PRICE_GAP * 2);
            selectedMaxPrice = (float) (PRICE_GAP * 3);
            return;
        }
        if (">= 1.500.000 VND".equals(selectedPriceRange)) {
            selectedMinPrice = (float) (PRICE_GAP * 3);
        }
    }

    private void refreshStats() {
        DashboardStats stats = dashboardService.loadStats();
        if (lblActiveAuctions != null) {
            lblActiveAuctions.setText(String.valueOf(stats.getActiveAuctions()));
        }
        if (lblEndingToday != null) {
            lblEndingToday.setText(String.valueOf(stats.getEndingToday()));
        }
        if (lblTotalBids != null) {
            lblTotalBids.setText(String.valueOf(stats.getTotalBids()));
        }
    }

    private String formatCurrency(float amount) {
        return String.format("%,.0f VND", amount).replace(",", ".");
    }

    private String formatEndTime(Date endTime) {
        if (endTime == null) {
            return "Unknown";
        }
        long remaining = endTime.getTime() - System.currentTimeMillis();
        if (remaining <= 0) {
            return "Ended";
        }

        long totalMinutes = remaining / 60_000;
        if (totalMinutes < 60) {
            return "Less than 1 hour left";
        }

        long hours = totalMinutes / 60;
        return hours == 1 ? "1 hour left" : hours + " hours left";
    }

    @Override
    protected boolean onBeforeClose() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to exit?",
                ButtonType.YES, ButtonType.NO);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }
}
