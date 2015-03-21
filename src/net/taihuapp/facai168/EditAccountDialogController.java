package net.taihuapp.facai168;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.List;

/**
 * Created by ghe on 3/19/15.
 */
public class EditAccountDialogController {

    private Stage mDialogStage;
    private Account mAccount;
    private boolean mIsOK = false;

    @FXML
    private ChoiceBox<AccountType> mTypeChoiceBox;
    @FXML
    private TextField mNameTextField;
    @FXML
    private TextArea mDescriptionTextArea;

    public void setDialogStage(Stage stage, Account account, List<AccountType> atList) {
        mDialogStage = stage;
        mAccount = account;

        mTypeChoiceBox.setConverter(new StringConverter<AccountType>() {
            @Override
            public String toString(AccountType object) { return object.getType(); }

            @Override
            public AccountType fromString(String string) {
                return null;
            }
        });

        mTypeChoiceBox.getItems().addAll(atList);

        int id = account.getID();
        int typeID = account.getTypeID();
        int index = 0;
        if (id >= 0) {
            // this is an existing account
            // set the text fields
            mNameTextField.setText(account.getName());
            mDescriptionTextArea.setText(account.getDescription());

            // find the matching typeID
            for (int i = 0; i < atList.size(); i++) {
                System.out.print("i = " + i + "; atID = " + atList.get(i).getID());
                if (typeID == atList.get(i).getID()) {
                    index = i;
                    break;
                }
            }
        }
        // set the first as the default selection
        mTypeChoiceBox.getSelectionModel().select(index);
        mTypeChoiceBox.setDisable(id >= 0);
    }

    public boolean isOK() { return mIsOK; }

    @FXML
    private void handleOK() {
        mAccount.setTypeID(mTypeChoiceBox.getValue().getID());
        mAccount.setName(mNameTextField.getText());
        mAccount.setDescription(mDescriptionTextArea.getText());
        mIsOK = true;
        mDialogStage.close();
    }

    @FXML
    private void handleCancel() {
        mDialogStage.close();
    }
}
