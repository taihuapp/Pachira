package net.taihuapp.facai168;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

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

    void setAccount(boolean lockAccountType, Account account, MainApp mainApp) {
        mMainApp = mainApp;
        mAccount = account;

        // todo more initialization
        mTypeChoiceBox.getSelectionModel().select(account.getType());

        // if lockAccountType is true, or if account ID > 0, then don't allow type change
        mTypeChoiceBox.setDisable(lockAccountType || account.getID() > 0);

        mNameTextField.setText(account.getName());
        mDescriptionTextArea.setText(account.getDescription());
        mHiddenFlagCheckBox.setSelected(account.getHiddenFlag());
    }

    void setDialogStage(Stage stage) { mDialogStage = stage; }

    @FXML
    private void handleOK() {
        boolean isNew = false;
        if (mAccount.getID() <= 0) {
            isNew = true;
            try {
                mAccount.setType(mTypeChoiceBox.getValue());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mAccount.setName(mNameTextField.getText());
        mAccount.setDescription(mDescriptionTextArea.getText());
        mAccount.setHiddenFlag(mHiddenFlagCheckBox.isSelected());

        // for new account, we need to update accountList in mainApp
        mMainApp.insertUpdateAccountToDB(mAccount, isNew);

        mDialogStage.close();
    }

    @FXML
    private void handleCancel() {
        mDialogStage.close();
    }

    @FXML
    private void initialize() { mTypeChoiceBox.getItems().addAll(Account.Type.values()); }
}
