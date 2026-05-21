package Client.features.sell;

import Client.core.ui.NavigationController;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * Skeleton Controller for the Sell Item screen.
 * <p>
 * Created purely to resolve FXML loader event-handler dependencies and FXML controller bindings,
 * allowing the UI to be test-run without errors.
 * Full logic will be implemented in future phases.
 * </p>
 */
public class SellItemController extends NavigationController {

    // ========================== FXML Fields ==========================

    @FXML private Button btnBrowseAuctions;
    @FXML private Button btnMyBids;
    @FXML private Button btnSellItem;
    @FXML private Button btnSearch;
    @FXML private Button btnNotifications;
    @FXML private Button btnUserProfile;
    @FXML private Label lblDraftBadge;

    @FXML private StackPane mainDropZone;
    @FXML private HBox thumbnailRow;
    @FXML private StackPane addPhotoBtn;
    @FXML private StackPane thumb1;
    @FXML private StackPane thumb2;
    @FXML private StackPane thumb3;
    @FXML private StackPane thumb4;
    @FXML private StackPane thumb5;

    @FXML private TextField txtItemName;
    @FXML private ComboBox<String> cmbCategory;
    @FXML private ComboBox<String> cmbCondition;
    @FXML private TextField txtStartingPrice;
    @FXML private Label lblCharCount;
    @FXML private TextArea txtDescription;
    @FXML private TextField txtLocation;

    @FXML private DatePicker dpEndDate;
    @FXML private TextField txtBidIncrement;
    @FXML private Button btnAutoExtend;

    @FXML private Button btnReset;
    @FXML private Button btnListItem;

    // ========================== Initialization ==========================

    @FXML
    public void initialize() {
        System.out.println("[SellItemController] SellItem page initialized successfully.");
    }

    // ========================== Event Handlers ==========================

    @FXML
    private void handleMainUpload(MouseEvent event) {
        System.out.println("[SellItemController] handleMainUpload clicked.");
    }

    @FXML
    private void handleAddPhoto(MouseEvent event) {
        System.out.println("[SellItemController] handleAddPhoto clicked.");
    }

    @FXML
    private void toggleAutoExtend(ActionEvent event) {
        System.out.println("[SellItemController] toggleAutoExtend clicked.");
    }

    @FXML
    private void handleReset(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Reset");
        alert.setHeaderText("Reset Item Form");
        alert.setContentText("Are you sure you want to clear all entered details?");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                txtItemName.clear();
                if (cmbCategory != null) cmbCategory.setValue(null);
                if (cmbCondition != null) cmbCondition.setValue(null);
                txtStartingPrice.clear();
                txtDescription.clear();
                txtLocation.clear();
                if (dpEndDate != null) dpEndDate.setValue(null);
                txtBidIncrement.clear();
                System.out.println("[SellItemController] Form has been reset.");
            }
        });
    }

    @FXML
    private void handleListItem(ActionEvent event) {
        String bidIncrement = txtBidIncrement.getText();
        if (bidIncrement == null || bidIncrement.trim().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Validation Error");
            alert.setHeaderText("Validation Failed");
            alert.setContentText("Minimum Bid Increment is a must, can't be left empty.");
            alert.showAndWait();
            return;
        }
        System.out.println("[SellItemController] handleListItem clicked. Validation passed.");
    }
}
