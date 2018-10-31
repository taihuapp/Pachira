/*
 * Copyright (C) 2018.  Guangliang He.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of Pachira.
 *
 * Pachira is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any
 * later version.
 *
 * Pachira is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.taihuapp.pachira;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import net.taihuapp.pachira.net.taihuapp.pachira.dc.AccountDC;
import org.apache.log4j.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.sql.SQLException;
import java.util.Arrays;


public class EditAccountDirectConnectionDialogController {

    private static final Logger mLogger = Logger.getLogger(EditAccountDirectConnectionDialogController.class);

    private class DCInfoConverter extends StringConverter<DirectConnection> {
        public DirectConnection fromString(String s) { return mMainApp.getDCInfoByName(s); }
        public String toString(DirectConnection dc) { return dc == null ? "" : dc.getName(); }
    }

    private Stage mStage;
    private MainApp mMainApp;
    private BooleanProperty mChangedProperty = new SimpleBooleanProperty(false);
    private void setChanged() { mChangedProperty.set(true); }
    private boolean isChanged() { return mChangedProperty.get(); }

    @FXML
    private Label mAccountNameLabel;
    @FXML
    private ComboBox<DirectConnection> mDCComboBox;
    @FXML
    private TextField mRoutingNumberTextField;
    @FXML
    private TextField mAccountNumberTextField;
    @FXML
    private PasswordField mAccountNumberPasswordField;
    @FXML
    private CheckBox mShowAccountNumberCheckBox;
    @FXML
    private Button mSaveButton;

    void setMainApp(MainApp mainApp, Stage stage, AccountDC adc) {
        mStage = stage;
        mMainApp = mainApp;

        mDCComboBox.setConverter(new DCInfoConverter());
        mDCComboBox.getItems().clear();
        mDCComboBox.getItems().add(new DirectConnection(0, "", 0, "", ""));  // add a blank one
        mDCComboBox.getItems().addAll(mMainApp.getDCInfoList());
        mDCComboBox.getSelectionModel().select(mMainApp.getDCInfoByID(adc.getDCID()));

        mAccountNameLabel.setText(mMainApp.getCurrentAccount().getName());
        mRoutingNumberTextField.setText(adc.getRoutingNumber());
        char[] clearAccountNumber = null;
        try {
            if (adc.getEncryptedAccountNumber().isEmpty())
                clearAccountNumber = new char[0];
            else
                clearAccountNumber = mMainApp.decrypt(adc.getEncryptedAccountNumber());
            mAccountNumberPasswordField.setText(new String(clearAccountNumber));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException | UnrecoverableKeyException
                | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException
                | InvalidAlgorithmParameterException | BadPaddingException e) {
            mLogger.error("Unable to decrypt account number for " + mAccountNameLabel.getText(), e);
        } finally {
            if (clearAccountNumber != null)
                Arrays.fill(clearAccountNumber, ' ');
        }

        // add change listeners
        ChangeListener<String> textChangeListener = (obs, o, n) -> setChanged();
        mRoutingNumberTextField.textProperty().addListener(textChangeListener);
        mAccountNumberPasswordField.textProperty().addListener(textChangeListener);
        mDCComboBox.valueProperty().addListener((obs, o, n) -> setChanged());
    }

    @FXML
    private void handleSave() {
        int dcID = mDCComboBox.getValue().getID();
        int aid = mMainApp.getCurrentAccount().getID();

        char[] clearAccountNumber = null;
        try {
            if (dcID <= 0) {
                if (MainApp.showConfirmationDialog("Confirmation", "Direct connection is empty",
                        "entry will be deleted"))
                    mMainApp.deleteAccountDCFromDB(aid);
            } else {
                clearAccountNumber = mAccountNumberPasswordField.getText().toCharArray();
                String ean = "";
                if (clearAccountNumber.length > 0)
                    ean = mMainApp.encrypt(clearAccountNumber);
                mMainApp.mergeAccountDCToDB(new AccountDC(aid, dcID, mRoutingNumberTextField.getText(), ean));
            }
            mMainApp.initAccountDCList();
            mStage.close();
        } catch (SQLException e) {
            mLogger.error("SQLException", e);
            mMainApp.showExceptionDialog("Exception", "Database Exception",
                    MainApp.SQLExceptionToString(e), e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException | UnrecoverableKeyException
                | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException e) {
            mLogger.error("Encrypt throws " + e.getClass().getName(), e);
            mMainApp.showExceptionDialog("Exception", "Encryption Exception", e.getMessage(), e);
        } finally {
            if (clearAccountNumber != null)
                Arrays.fill(clearAccountNumber, ' ');
        }
    }

    @FXML
    private void handleCancel() {
        if (isChanged() && !MainApp.showConfirmationDialog("Confirmation", "Content has been changed",
                "Do you want to discard changes?")) {
            return;
        }
        mStage.close();
    }

    @FXML
    private void initialize() {
        mAccountNumberPasswordField.visibleProperty().bind(mShowAccountNumberCheckBox.selectedProperty().not());
        mAccountNumberTextField.visibleProperty().bind(mShowAccountNumberCheckBox.selectedProperty());
        mAccountNumberTextField.textProperty().bindBidirectional(mAccountNumberPasswordField.textProperty());

        mShowAccountNumberCheckBox.setSelected(false);

        mSaveButton.disableProperty().bind(mChangedProperty.not());
    }
}
