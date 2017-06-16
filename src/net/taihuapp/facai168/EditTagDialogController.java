package net.taihuapp.facai168;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 *
 * Created by ghe on 6/15/17.
 */
public class EditTagDialogController {
    private MainApp mMainApp;
    private Tag mTag;
    private Stage mDialogStage;

    @FXML
    private TextField mNameTextField;
    @FXML
    private TextField mDescriptionTextField;

    void setMainApp(MainApp mainApp, Tag tag, Stage stage) {
        mMainApp = mainApp;
        mTag = tag;
        mDialogStage = stage;

        mNameTextField.textProperty().bindBidirectional(mTag.getNameProperty());
        mDescriptionTextField.textProperty().bindBidirectional(mTag.getDescriptionProperty());
    }

    @FXML
    private void handleSave() {
        if (mMainApp.insertUpdateTagToDB(mTag)) {
            mMainApp.initTagList();
            mDialogStage.close();
        }
    }

    @FXML
    private void handleCancel() { mDialogStage.close(); }
}
