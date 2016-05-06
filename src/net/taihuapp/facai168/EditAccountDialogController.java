package net.taihuapp.facai168;

import javafx.fxml.FXML;
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
    private Account mAccount;
    private boolean mIsOK = false;

    @FXML
    private ChoiceBox<Account.Type> mTypeChoiceBox;
    @FXML
    private TextField mNameTextField;
    @FXML
    private TextArea mDescriptionTextArea;

    public void setAccount(Account account) {
        mAccount = account;

        // todo more initialization
        mTypeChoiceBox.getSelectionModel().select(account.getType());
        mTypeChoiceBox.setDisable(account.getID() >= 0); // disable type for existing

        mNameTextField.setText(account.getName());
        mDescriptionTextArea.setText(account.getDescription());
    }

    void setDialogStage(Stage stage) { mDialogStage = stage; }

    boolean isOK() { return mIsOK; }

    @FXML
    private void handleOK() {
        if (mAccount.getID() <= 0) {
            try {
                mAccount.setType(mTypeChoiceBox.getValue());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mAccount.setName(mNameTextField.getText());
        mAccount.setDescription(mDescriptionTextArea.getText());
        mIsOK = true;
        mDialogStage.close();
    }

    @FXML
    private void handleCancel() {
        mDialogStage.close();
    }

    @FXML
    private void initialize() {
        // todo move to initialize
/*       mTypeChoiceBox.setConverter(new StringConverter<Account.Type>() {
            @Override
            public String toString(Account.Type object) { return object.getType(); }

            @Override
            public AccountType fromString(String string) {
                return null;
            }
        });
*/
        mTypeChoiceBox.getItems().addAll(Account.Type.values());

    }
}
