package net.taihuapp.facai168;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * Created by ghe on 5/30/16.
 *
 */
public class EditSecurityDialogController {

    private MainApp mMainApp;
    private Security mSecurity;
    private Stage mDialogStage;

    @FXML
    private TextField mNameTextField;
    @FXML
    private TextField mTickerTextField;
    @FXML
    private ChoiceBox<Security.Type> mTypeChoiceBox;

    void setMainApp(MainApp mainApp, Security security, Stage stage) {
        mMainApp = mainApp;
        mSecurity = security;
        mDialogStage = stage;

        mNameTextField.textProperty().bindBidirectional(mSecurity.getNameProperty());
        mTickerTextField.textProperty().bindBidirectional(mSecurity.getTickerProperty());

        mTypeChoiceBox.setConverter(new StringConverter<Security.Type>() {
            public Security.Type fromString(String s) { return Security.Type.fromString(s); }
            public String toString(Security.Type type) { return type.toString(); }
        });
        mTypeChoiceBox.getItems().setAll(Security.Type.values());
        mTypeChoiceBox.valueProperty().bindBidirectional(mSecurity.getTypeProperty());
    }

    @FXML
    private void handleSave() {
        if (mMainApp.insertUpdateSecurityToDB(mSecurity)) {
            mMainApp.initializeLists();
            mDialogStage.close();
        }
    }

    @FXML
    private void handleCancel() {
        mDialogStage.close();
    }
}