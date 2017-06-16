package net.taihuapp.facai168;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 *
 * Created by ghe on 6/15/17.
 */
public class EditCategoryDialogController {
    private MainApp mMainApp;
    private Category mCategory;
    private Stage mDialogStage;

    @FXML
    private TextField mNameTextField;
    @FXML
    private TextField mDescriptionTextField;

    void setMainApp(MainApp mainApp, Category category, Stage stage) {
        mMainApp = mainApp;
        mCategory = category;
        mDialogStage = stage;

        mNameTextField.textProperty().bindBidirectional(mCategory.getNameProperty());
        mDescriptionTextField.textProperty().bindBidirectional(mCategory.getDescriptionProperty());
    }

    @FXML
    private void handleSave() {
        if (mMainApp.insertUpdateCategoryToDB(mCategory)) {
            mMainApp.initCategoryList();
            mDialogStage.close();
        }
    }

    @FXML
    private void handleCancel() { mDialogStage.close(); }
}
