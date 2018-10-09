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

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.apache.log4j.Logger;

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

    void setMainApp(MainApp mainApp, DirectConnection dcInfo, Stage stage) {
        mMainApp = mainApp;
        mDCInfo = dcInfo;
        mStage = stage;

        mFIComboBox.setConverter(new FIDataConverter());
        mFIComboBox.getItems().clear();
        mFIComboBox.getItems().add(null);
        mFIComboBox.getItems().addAll(mMainApp.getFIDataList());
        mFIComboBox.setValue(mMainApp.getFIDataByID(dcInfo.getFIID()));
        new AutoCompleteComboBoxHelper<>(mFIComboBox);

        mUserNamePasswordField.visibleProperty().bind(mShowUserNameCheckBox.selectedProperty().not());
        mUserNameTextField.visibleProperty().bind(mShowUserNameCheckBox.selectedProperty());
        mUserNamePasswordField.textProperty().bindBidirectional(mUserNameTextField.textProperty());
        mPasswordPasswordField.visibleProperty().bind(mShowPasswordCheckBox.selectedProperty().not());
        mPasswordTextField.visibleProperty().bind(mShowPasswordCheckBox.selectedProperty());
        mPasswordPasswordField.textProperty().bindBidirectional(mPasswordTextField.textProperty());

        mDCNameTextField.setText(dcInfo.getName());
        mFIComboBox.getSelectionModel().select(mMainApp.getFIDataByID(dcInfo.getFIID()));

    }

    void handleSave() {

    }

    void handleClose() {

    }
}
