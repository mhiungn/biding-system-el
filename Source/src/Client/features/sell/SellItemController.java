package Client.features.sell;

import Client.core.ui.NavigationController;
import Client.components.AppHeader;
import Client.components.LoadingOverlay;
import Client.features.auth.SessionManager;
import CommonClasses.User;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class SellItemController extends NavigationController {
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_GALLERY_IMAGES = 3;

    @FXML private StackPane mainDropZone;
    @FXML private HBox thumbnailRow;
    @FXML private StackPane addPhotoBtn;
    @FXML private StackPane thumb1;
    @FXML private StackPane thumb2;
    @FXML private StackPane thumb3;

    @FXML private TextField txtItemName;
    @FXML private ComboBox<String> cmbCategory;
    @FXML private TextField txtCondition;
    @FXML private TextField txtStartingPrice;
    @FXML private Label lblCharCount;
    @FXML private TextArea txtDescription;
    @FXML private TextField txtLocation;
    @FXML private DatePicker dpEndDate;
    @FXML private TextField txtBidIncrement;
    @FXML private Button btnAutoExtend;
    @FXML private Button btnReset;
    @FXML private Button btnListItem;
    @FXML private AppHeader appHeader;

    private final SellItemService sellItemService = new SellItemService();
    private final LoadingOverlay loadingOverlay = new LoadingOverlay();
    private final List<StackPane> thumbnailSlots = new ArrayList<>();
    private final List<File> galleryImages = new ArrayList<>();
    private File mainImage;
    private boolean autoExtend;

    @FXML
    public void initialize() {
        appHeader.configure(this);

        cmbCategory.getItems().setAll("ELECTRONICS", "ART", "VEHICLE");
        thumbnailSlots.clear();
        thumbnailSlots.addAll(Arrays.asList(thumb1, thumb2, thumb3));

        for (int i = 0; i < thumbnailSlots.size(); i++) {
            final int index = i;
            thumbnailSlots.get(i).setOnMouseClicked(event -> handleThumbnailUpload(index));
        }

        setupDescriptionCounter();
        setupDragAndDrop();
        renderMainDropZone();
        renderThumbnails();
        updateAutoExtendButton();
    }

    @FXML
    private void handleMainUpload(MouseEvent event) {
        File file = chooseSingleImage("Choose main product picture");
        if (file == null) {
            return;
        }
        if (!isValidImage(file)) {
            return;
        }
        mainImage = file;
        removeDuplicateGalleryImages();
        renderMainDropZone();
        renderThumbnails();
    }

    @FXML
    private void handleAddPhoto(MouseEvent event) {
        int remaining = MAX_GALLERY_IMAGES - galleryImages.size();
        if (remaining <= 0) {
            showInfo("Photo limit reached", "You can add up to three additional pictures.");
            return;
        }

        List<File> selected = chooseMultipleImages("Choose additional product pictures");
        if (selected == null || selected.isEmpty()) {
            return;
        }

        for (File file : selected) {
            if (galleryImages.size() >= MAX_GALLERY_IMAGES) {
                break;
            }
            if (isValidImage(file)) {
                addGalleryImage(file);
            }
        }
        renderThumbnails();
    }

    @FXML
    private void toggleAutoExtend(ActionEvent event) {
        autoExtend = !autoExtend;
        updateAutoExtendButton();
    }

    @FXML
    private void handleReset(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Reset");
        alert.setHeaderText("Reset Item Form");
        alert.setContentText("Clear all entered details and selected pictures?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                clearForm();
            }
        });
    }

    @FXML
    private void handleListItem(ActionEvent event) {
        SellItemRequest request;
        try {
            request = buildRequest();
        } catch (IllegalArgumentException e) {
            showError("Validation Error", e.getMessage());
            return;
        }

        btnListItem.setDisable(true);
        Task<SellItemResult> task = new Task<>() {
            @Override
            protected SellItemResult call() {
                return sellItemService.listItem(request);
            }
        };
        task.setOnSucceeded(taskEvent -> {
            loadingOverlay.hide();
            btnListItem.setDisable(false);
            SellItemResult result = task.getValue();
            showInfo("Item listed", "Auction AUC-" + result.getAuctionId() + " has been added to the dashboard.");
            clearForm();
            try {
                switchToDashboard(event);
            } catch (IOException e) {
                showError("Navigation Error", "The item was listed, but the dashboard could not be opened.");
            }
        });
        task.setOnFailed(taskEvent -> {
            loadingOverlay.hide();
            btnListItem.setDisable(false);
            Throwable error = task.getException();
            showError("Listing Failed", error == null ? "Could not list item." : error.getMessage());
        });

        loadingOverlay.show(btnListItem, "Listing item...");
        Thread thread = new Thread(task, "sell-item-submit");
        thread.setDaemon(true);
        thread.start();
    }

    private SellItemRequest buildRequest() {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            throw new IllegalArgumentException("Please log in before listing an item.");
        }
        String role = user.getRole();
        if (role != null && !"USER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            throw new IllegalArgumentException("Only user accounts can list items.");
        }

        SellItemRequest request = new SellItemRequest();
        request.setSellerUsername(user.getUsername());
        request.setItemName(required(txtItemName, "Item name is required."));
        request.setCategory(requiredCombo(cmbCategory, "Category is required."));
        request.setCondition(required(txtCondition, "Condition is required."));
        request.setStartingPrice(parseMoney(txtStartingPrice.getText(), "Starting price must be a valid number."));
        request.setDescription(required(txtDescription, "Description is required."));
        request.setLocation(required(txtLocation, "Location is required."));
        request.setAuctionEndTime(parseEndDate());
        request.setMinimumBidIncrement(parseMoney(txtBidIncrement.getText(), "Minimum bid increment must be a valid number."));
        request.setAutoExtend(autoExtend);
        if (mainImage == null) {
            throw new IllegalArgumentException("Please select a main picture for this item.");
        }
        request.setMainImage(mainImage);
        request.getGalleryImages().addAll(galleryImages.subList(0, Math.min(MAX_GALLERY_IMAGES, galleryImages.size())));
        return request;
    }

    private Date parseEndDate() {
        LocalDate date = dpEndDate.getValue();
        if (date == null) {
            throw new IllegalArgumentException("End date is required.");
        }
        LocalDateTime endOfDay = LocalDateTime.of(date, LocalTime.of(23, 59, 59));
        Date endDate = Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant());
        if (!endDate.after(new Date())) {
            throw new IllegalArgumentException("End date must be in the future.");
        }
        return endDate;
    }

    private String required(TextField field, String message) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String required(TextArea field, String message) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String requiredCombo(ComboBox<String> comboBox, String message) {
        String value = comboBox.getValue();
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private float parseMoney(String value, String message) {
        String normalized = value == null ? "" : value.replace(",", "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        try {
            float parsed = Float.parseFloat(normalized);
            if (parsed <= 0) {
                throw new IllegalArgumentException(message);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private void handleThumbnailUpload(int index) {
        File file = chooseSingleImage("Choose additional product picture");
        if (file == null || !isValidImage(file)) {
            return;
        }
        while (galleryImages.size() <= index) {
            galleryImages.add(null);
        }
        galleryImages.set(index, file);
        galleryImages.removeIf(item -> item == null);
        removeDuplicateGalleryImages();
        renderThumbnails();
    }

    private File chooseSingleImage(String title) {
        FileChooser chooser = createImageChooser(title);
        return chooser.showOpenDialog(getWindow());
    }

    private List<File> chooseMultipleImages(String title) {
        FileChooser chooser = createImageChooser(title);
        return chooser.showOpenMultipleDialog(getWindow());
    }

    private FileChooser createImageChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image files", "*.jpg", "*.jpeg", "*.png")
        );
        return chooser;
    }

    private Window getWindow() {
        if (mainDropZone == null || mainDropZone.getScene() == null) {
            return null;
        }
        return mainDropZone.getScene().getWindow();
    }

    private boolean isValidImage(File file) {
        if (file == null || !file.isFile()) {
            showError("Invalid Picture", "Selected picture is not valid.");
            return false;
        }
        String lowerName = file.getName().toLowerCase();
        if (!lowerName.endsWith(".jpg") && !lowerName.endsWith(".jpeg") && !lowerName.endsWith(".png")) {
            showError("Invalid Picture", "Only JPG and PNG pictures are supported.");
            return false;
        }
        if (file.length() > 5L * 1024L * 1024L) {
            showError("Invalid Picture", "Each picture must be 5 MB or smaller.");
            return false;
        }
        return true;
    }

    private void setupDescriptionCounter() {
        lblCharCount.setText("0 / " + MAX_DESCRIPTION_LENGTH);
        txtDescription.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.length() > MAX_DESCRIPTION_LENGTH) {
                txtDescription.setText(newValue.substring(0, MAX_DESCRIPTION_LENGTH));
                return;
            }
            int length = newValue == null ? 0 : newValue.length();
            lblCharCount.setText(length + " / " + MAX_DESCRIPTION_LENGTH);
        });
    }

    private void setupDragAndDrop() {
        mainDropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        mainDropZone.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean success = false;
            if (dragboard.hasFiles() && !dragboard.getFiles().isEmpty()) {
                File file = dragboard.getFiles().get(0);
                if (isValidImage(file)) {
                    mainImage = file;
                    removeDuplicateGalleryImages();
                    renderMainDropZone();
                    renderThumbnails();
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        thumbnailRow.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        thumbnailRow.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean success = false;
            if (dragboard.hasFiles()) {
                for (File file : dragboard.getFiles()) {
                    if (galleryImages.size() >= MAX_GALLERY_IMAGES) {
                        break;
                    }
                    if (isValidImage(file)) {
                        addGalleryImage(file);
                        success = true;
                    }
                }
                renderThumbnails();
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void renderMainDropZone() {
        mainDropZone.getChildren().clear();
        if (mainImage == null) {
            VBox placeholder = new VBox(10);
            placeholder.setAlignment(javafx.geometry.Pos.CENTER);
            Label title = new Label("Drag & Drop or Click to Upload");
            title.getStyleClass().add("drop-zone-title");
            Label hint = new Label("JPG, PNG - Max 5 MB");
            hint.getStyleClass().add("drop-zone-hint");
            placeholder.getChildren().addAll(title, hint);
            mainDropZone.getChildren().add(placeholder);
            return;
        }

        ImageView imageView = new ImageView(new Image(mainImage.toURI().toString()));
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(400);
        imageView.setFitHeight(200);
        mainDropZone.getChildren().add(imageView);
    }

    private void addGalleryImage(File file) {
        if (galleryImages.size() >= MAX_GALLERY_IMAGES || isDuplicateImage(file)) {
            return;
        }
        galleryImages.add(file);
    }

    private void removeDuplicateGalleryImages() {
        List<File> uniqueImages = new ArrayList<>();
        for (File file : galleryImages) {
            if (file != null && uniqueImages.stream().noneMatch(existing -> sameFile(existing, file))
                    && !sameFile(mainImage, file)) {
                uniqueImages.add(file);
            }
            if (uniqueImages.size() >= MAX_GALLERY_IMAGES) {
                break;
            }
        }
        galleryImages.clear();
        galleryImages.addAll(uniqueImages);
    }

    private boolean isDuplicateImage(File file) {
        return sameFile(mainImage, file) || galleryImages.stream().anyMatch(existing -> sameFile(existing, file));
    }

    private boolean sameFile(File first, File second) {
        if (first == null || second == null) {
            return false;
        }
        try {
            return first.getCanonicalFile().equals(second.getCanonicalFile());
        } catch (IOException e) {
            return first.getAbsolutePath().equalsIgnoreCase(second.getAbsolutePath());
        }
    }

    private void renderThumbnails() {
        for (int i = 0; i < thumbnailSlots.size(); i++) {
            StackPane slot = thumbnailSlots.get(i);
            slot.getChildren().clear();
            if (i < galleryImages.size() && galleryImages.get(i) != null) {
                ImageView imageView = new ImageView(new Image(galleryImages.get(i).toURI().toString()));
                imageView.setPreserveRatio(true);
                imageView.setFitWidth(64);
                imageView.setFitHeight(64);
                slot.getChildren().add(imageView);
            } else {
                Region placeholder = new Region();
                placeholder.getStyleClass().add("thumb-placeholder");
                slot.getChildren().add(placeholder);
            }
        }
    }

    private void updateAutoExtendButton() {
        btnAutoExtend.getStyleClass().removeAll("toggle-btn-on", "toggle-btn-off");
        btnAutoExtend.getStyleClass().add(autoExtend ? "toggle-btn-on" : "toggle-btn-off");
        btnAutoExtend.setText(autoExtend ? "ON" : "OFF");
    }

    private void clearForm() {
        txtItemName.clear();
        cmbCategory.setValue(null);
        txtCondition.clear();
        txtStartingPrice.clear();
        txtDescription.clear();
        txtLocation.clear();
        dpEndDate.setValue(null);
        txtBidIncrement.clear();
        mainImage = null;
        galleryImages.clear();
        autoExtend = false;
        renderMainDropZone();
        renderThumbnails();
        updateAutoExtendButton();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    @Override
    protected boolean onBeforeClose() {
        boolean dirty = mainImage != null || !galleryImages.isEmpty()
                || !safeText(txtItemName).isEmpty()
                || !safeText(txtDescription).isEmpty();
        if (!dirty) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Discard the current listing and exit?",
                ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    private String safeText(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String safeText(TextArea field) {
        return field.getText() == null ? "" : field.getText().trim();
    }
}
