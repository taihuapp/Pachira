package net.taihuapp.facai168;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;

/**
 * Created by ghe on 3/19/15.
 *
 */
public class EditAccountDialogController {

    private Stage mDialogStage;
    private MainApp mMainApp;
    private Account mAccount;

    @FXML
    private ChoiceBox<Account.Type> mTypeChoiceBox;
    @FXML
    private TextField mNameTextField;
    @FXML
    private TextArea mDescriptionTextArea;
    @FXML
    private CheckBox mHiddenFlagCheckBox;

    void setAccount(MainApp mainApp, Account account, Account.Type t) {
        mMainApp = mainApp;
        mAccount = account;

        // todo more initialization
        if (account != null) {
            // edit an existing account
            mTypeChoiceBox.getSelectionModel().select(account.getType());
        } else if (t != null) {
            // new accout with a given type
            mTypeChoiceBox.getSelectionModel().select(t);
        } else {
            // new account without a given type, default to first Type
            mTypeChoiceBox.getSelectionModel().select(0);
        }

        // disable if editing an existing account, or an account with given type.
        mTypeChoiceBox.setDisable(account != null || t != null);

        mNameTextField.setText(account == null ? "" : account.getName());
        mDescriptionTextArea.setText(account == null ? "" : account.getDescription());
        mHiddenFlagCheckBox.setSelected(account == null ? false : account.getHiddenFlag());
    }

    void setDialogStage(Stage stage) { mDialogStage = stage; }

    @FXML
    private void handleOK() {
        String name = mNameTextField.getText();
        if (name == null || name.length() == 0) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText("Account name cannot be empty");
            alert.showAndWait();
            return;
        }

        if (mAccount == null) {
            mAccount = new Account(0, mTypeChoiceBox.getValue(), name,
                    mDescriptionTextArea.getText(), mHiddenFlagCheckBox.isSelected(), Integer.MAX_VALUE,
                    BigDecimal.ZERO);
        } else {
            mAccount.setName(name);
            mAccount.setDescription(mDescriptionTextArea.getText());
            mAccount.setHiddenFlag(mHiddenFlagCheckBox.isSelected());
        }

        // insert or update database
        mMainApp.insertUpdateAccountToDB(mAccount);

        mDialogStage.close();
    }

    @FXML
    private void handleCancel() {
        mDialogStage.close();
    }

    @FXML
    private void initialize() { mTypeChoiceBox.getItems().addAll(Account.Type.values()); }
}
