package net.taihuapp.facai168;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

/**
 * Created by ghe on 3/19/15.
 */
public class EditAccountDialogController {

    private MainApp mMainApp;

    private Account mAccount;

    @FXML
    private ChoiceBox<AccountType> mTypeChoiceBox;
    @FXML
    private TextField mNameTextField;

    public void setMainApp(MainApp mainApp) {
        mMainApp = mainApp;

        mTypeChoiceBox.setConverter(new StringConverter<AccountType>() {
            @Override
            public String toString(AccountType object) {
                System.err.println(object.getType());
                return object.getType();
            }

            @Override
            public AccountType fromString(String string) {
                return null;
            }
        });

        mTypeChoiceBox.getItems().addAll(mMainApp.getAccountTypeList());
    }

    @FXML
    private void handleOK() {
        System.out.println("OK");
    }

    @FXML
    private void handleCancel() {
        System.out.println("Cancel");
    }
}
