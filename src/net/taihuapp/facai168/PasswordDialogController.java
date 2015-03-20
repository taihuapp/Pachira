package net.taihuapp.facai168;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Created by ghe on 3/16/15.
 */
public class PasswordDialogController implements ChangeListener {

    public enum MODE {ENTER, NEW, CHANGE}

    private Stage mDialogStage;
    private String mPassword = null;

    @FXML
    private Label mConfirmPasswordLabel;
    @FXML
    private PasswordField mPasswordField;
    @FXML
    private PasswordField mConfirmPasswordField;
    @FXML
    private Label mLengthWarningLabel;
    @FXML
    private Label mMatchWarningLabel;
    @FXML
    private Button mOKButton;

    public void setDialogStage(Stage stage) { mDialogStage = stage; }
    public void setMode(MODE mode) {
        switch (mode) {
            case NEW:
                mPasswordField.textProperty().addListener(this);
                mConfirmPasswordField.textProperty().addListener(this);
                mLengthWarningLabel.setVisible(true);
                mMatchWarningLabel.setVisible(false);
                mOKButton.setVisible(false);
                break;
            case ENTER:
                mConfirmPasswordLabel.setVisible(false);
                mConfirmPasswordField.setVisible(false);
                mLengthWarningLabel.setVisible(false);
                mMatchWarningLabel.setVisible(false);
                mOKButton.setVisible(true);
                break;
            case CHANGE:
                System.err.println("CHANGE PASSWORD Mode not implemented yet");
                break;
            default:
                System.err.println("Unknown mode " + mode.toString());
                break;
        }
    }

    @FXML
    private void handleCancel() {
        mPassword = null;
        mDialogStage.close();
    }

    @FXML
    private void handleOK() {
        mPassword = mPasswordField.getText();
        mDialogStage.close();
    }

    String getPassword() { return mPassword; }

    @Override
    public void changed(ObservableValue observable, Object oldValue, Object newValue) {
        String pw0 = mPasswordField.getText();
        String pw1 = mConfirmPasswordField.getText();

        boolean lengthOK = pw0.length() >= 8;
        boolean matchOK = pw0.equals(pw1);

        mLengthWarningLabel.setVisible(!lengthOK);
        mMatchWarningLabel.setVisible(!matchOK);
        mOKButton.setVisible(lengthOK && matchOK);
    }

    @FXML
    private void initialize() {
    }
}
