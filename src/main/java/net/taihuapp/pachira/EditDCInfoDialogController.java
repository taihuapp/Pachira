/*
 * Copyright (C) 2018-2020.  Guangliang He.  All Rights Reserved.
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
import org.apache.log4j.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.sql.SQLException;
import java.util.Arrays;


public class EditDCInfoDialogController {

    private class FIDataConverter extends StringConverter<DirectConnection.FIData> {
        public DirectConnection.FIData fromString(String s) {
            return s.length() == 0 ? null : mMainApp.getFIDataByName(s);
        }
        public String toString(DirectConnection.FIData fiData) {
            return fiData == null ? "" : fiData.getName();
        }
    }
    private static final Logger mLogger = Logger.getLogger(EditDCInfoDialogController.class);

    private MainApp mMainApp;
    private DirectConnection mDCInfo;
    private Stage mStage;
    private final BooleanProperty mChangedProperty = new SimpleBooleanProperty(false);
    private void setChanged() { mChangedProperty.set(true); }
    private boolean isChanged() { return mChangedProperty.get(); }

    @FXML
    private TextField mDCNameTextField;
    @FXML
    private ComboBox<DirectConnection.FIData> mFIComboBox;

    // some financial institution ssn as user name, so there is a need to hide it.
    @FXML
    private PasswordField mUserNamePasswordField;
    @FXML
    private PasswordField mPasswordPasswordField;
    @FXML
    private TextField mUserNameTextField;
    @FXML
    private TextField mPasswordTextField;
    @FXML
    private CheckBox mShowUserNameCheckBox;
    @FXML
    private CheckBox mShowPasswordCheckBox;
    @FXML
    private Button mSaveButton;

    void setMainApp(MainApp mainApp, DirectConnection dcInfo, Stage stage) {
        mMainApp = mainApp;
        mDCInfo = dcInfo;
        mStage = stage;

        mFIComboBox.setConverter(new FIDataConverter());
        mFIComboBox.getItems().clear();
        mFIComboBox.getItems().addAll(mMainApp.getFIDataList());
        mFIComboBox.getSelectionModel().select(mMainApp.getFIDataByID(dcInfo.getFIID()));

        mDCNameTextField.setText(dcInfo.getName());
        mFIComboBox.getSelectionModel().select(mMainApp.getFIDataByID(dcInfo.getFIID()));
        char[] clearUserName = null;
        char[] clearPassword = null;
        try {
            if (dcInfo.getEncryptedUserName().isEmpty())
                clearUserName = new char[0];
            else
                clearUserName = mMainApp.decrypt(dcInfo.getEncryptedUserName());
            if (dcInfo.getEncryptedPassword().isEmpty())
                clearPassword = new char[0];
            else
                clearPassword = mMainApp.decrypt(dcInfo.getEncryptedPassword());
            mUserNamePasswordField.setText(new String(clearUserName));
            mPasswordPasswordField.setText(new String(clearPassword));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException | UnrecoverableKeyException
                | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException
                | InvalidAlgorithmParameterException | BadPaddingException e) {
            mLogger.error("Unable decrypt user name and password for " + dcInfo.getName(), e);
            MainApp.showExceptionDialog(mStage, "Exception", "Decryption Exception",
                    "Failed to decrypt DCInfo for " + dcInfo.getName(), e);
        } finally {
            if (clearUserName != null)
                Arrays.fill(clearUserName, ' ');
            if (clearPassword != null)
                Arrays.fill(clearPassword, ' ');
        }
        // add change listeners
        ChangeListener<String> textChangeListener = (observable, oldValue, newValue) -> setChanged();
        mDCNameTextField.textProperty().addListener(textChangeListener);
        mUserNameTextField.textProperty().addListener(textChangeListener);
        mPasswordTextField.textProperty().addListener(textChangeListener);
        mFIComboBox.valueProperty().addListener((observable, oldValue, newValue) -> setChanged());
    }

    @FXML
    private void handleSave() {
        try {
            String encryptedUserName = mMainApp.encrypt(mUserNamePasswordField.getText().toCharArray());
            String encryptedPassword = mMainApp.encrypt(mPasswordPasswordField.getText().toCharArray());
            mDCInfo.setName(mDCNameTextField.getText());
            mDCInfo.setFIID(mFIComboBox.getSelectionModel().getSelectedItem().getID());
            mDCInfo.setEncryptedUserName(encryptedUserName);
            mDCInfo.setEncryptedPassword(encryptedPassword);
            mMainApp.insertUpdateDCToDB(mDCInfo);
            mMainApp.initDCInfoList();
            mStage.close();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException | UnrecoverableKeyException
                | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException e) {
            mLogger.error("Vault Exception", e);
            MainApp.showExceptionDialog(mStage, "Exception", "Vault Exception", e.getMessage(), e);
        } catch (SQLException e) {
            mLogger.error(MainApp.SQLExceptionToString(e), e);
            MainApp.showExceptionDialog(mStage,"Exception","Database Exception",
                    MainApp.SQLExceptionToString(e), e);
        }
    }

    @FXML
    private void handleCancel() {
        if (isChanged() && !MainApp.showConfirmationDialog("Confirmation", "Content has been changed",
                "Do you want to discard changes?")) {
            // content changed and not confirmed OK to discard, go back
            return;
        }

        // close
        mStage.close();
    }

    @FXML
    private void initialize() {
        mUserNamePasswordField.visibleProperty().bind(mShowUserNameCheckBox.selectedProperty().not());
        mUserNameTextField.visibleProperty().bind(mShowUserNameCheckBox.selectedProperty());
        mUserNamePasswordField.textProperty().bindBidirectional(mUserNameTextField.textProperty());
        mPasswordPasswordField.visibleProperty().bind(mShowPasswordCheckBox.selectedProperty().not());
        mPasswordTextField.visibleProperty().bind(mShowPasswordCheckBox.selectedProperty());
        mPasswordPasswordField.textProperty().bindBidirectional(mPasswordTextField.textProperty());

        mShowUserNameCheckBox.setSelected(false);
        mShowPasswordCheckBox.setSelected(false);

        mSaveButton.disableProperty().bind(mChangedProperty.not());
    }
}
