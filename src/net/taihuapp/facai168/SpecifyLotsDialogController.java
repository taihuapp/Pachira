package net.taihuapp.facai168;

import javafx.fxml.FXML;
import javafx.scene.control.Button;

/**
 * Created by ghe on 12/4/15.
 *
 */
public class SpecifyLotsDialogController {
    MainApp mMainApp;
    Transaction mTransaction;

    @FXML
    Button mResetButton;
    @FXML
    Button mOKButton;
    @FXML
    Button mCancelButton;

    @FXML
    private void handleReset() {
        System.out.println("Reset");
    }

    @FXML
    private void handleOK() {
        System.out.println("OK");
    }

    @FXML
    private void handleCancel() {
        System.out.println("Cancel");
    }

    public void setMainApp(MainApp mainApp, Transaction t) {
        mMainApp = mainApp;
        mTransaction = t;
    }
}
