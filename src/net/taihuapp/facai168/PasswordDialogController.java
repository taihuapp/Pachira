package net.taihuapp.facai168;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ghe on 3/16/15.
 *
 */
public class PasswordDialogController implements ChangeListener<String> {

    enum MODE {ENTER, NEW, CHANGE}

    private Stage mDialogStage;

    private List<String> mPasswords = new ArrayList<>();

    @FXML
    private Label mCurrentPasswordLabel;
    @FXML
    private Label mConfirmPasswordLabel;
    @FXML
    private PasswordField mCurrentPasswordField;
    @FXML
    private PasswordField mPasswordField;
    @FXML
    private PasswordField mConfirmPasswordField;
    @FXML
    private Label mSpaceWarningLabel;
    @FXML
    private Label mLengthWarningLabel;
    @FXML
    private Label mMatchWarningLabel;
    @FXML
    private Button mOKButton;

    void setDialogStage(Stage stage) { mDialogStage = stage; }
    void setMode(MODE mode) {
        switch (mode) {
            case NEW:
            case CHANGE:
                mPasswordField.textProperty().addListener(this);
                mConfirmPasswordField.textProperty().addListener(this);

                mCurrentPasswordLabel.setVisible(mode == MODE.CHANGE);
                mCurrentPasswordField.setVisible(mode == MODE.CHANGE);
                mSpaceWarningLabel.setVisible(false);
                mLengthWarningLabel.setVisible(true);
                mMatchWarningLabel.setVisible(false);
                mOKButton.setVisible(false);
                break;
            case ENTER:
                mCurrentPasswordLabel.setVisible(false);
                mCurrentPasswordField.setVisible(false);
                mSpaceWarningLabel.setVisible(false);
                mConfirmPasswordLabel.setVisible(false);
                mConfirmPasswordField.setVisible(false);
                mLengthWarningLabel.setVisible(false);
                mMatchWarningLabel.setVisible(false);
                mOKButton.setVisible(true);
                break;
            default:
                System.err.println("Unknown mode " + mode.toString());
                break;
        }
    }

    @FXML
    private void handleCancel() {
        mPasswords.clear();
        mDialogStage.close();
    }

    @FXML
    private void handleOK() {
        mPasswords.clear();
        mPasswords.add(mPasswordField.getText());
        if (mCurrentPasswordField.isVisible())
            mPasswords.add(mCurrentPasswordField.getText());
        mDialogStage.close();
    }

    List<String> getPasswords() { return mPasswords; }

    @Override
    public void changed(ObservableValue<? extends String> ov, String t, String t1) {
        String pw0 = mPasswordField.getText();
        String pw1 = mConfirmPasswordField.getText();

        boolean hasSpace = pw0.contains(" ");
        boolean lengthOK = pw0.length() >= 8;
        boolean matchOK = pw0.equals(pw1);

        mSpaceWarningLabel.setVisible(hasSpace);
        mLengthWarningLabel.setVisible(!lengthOK);
        mMatchWarningLabel.setVisible(!matchOK);
        mOKButton.setVisible(lengthOK && matchOK && !hasSpace);
    }
}
