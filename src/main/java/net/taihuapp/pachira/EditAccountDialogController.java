/*
 * Copyright (C) 2018-2021.  Guangliang He.  All Rights Reserved.
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
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;

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

    void setAccount(MainApp mainApp, Account account, Account.Type.Group g) {
        mMainApp = mainApp;
        mAccount = account;

        // todo more initialization
        if (account != null) {
            // edit an existing account
            mTypeChoiceBox.getItems().clear();
            for (Account.Type t : Account.Type.values())
                if (t.isGroup(account.getType().getGroup()))
                    mTypeChoiceBox.getItems().add(t);
            mTypeChoiceBox.getSelectionModel().select(account.getType());
        } else {
            // new account without a given type, default to first Type
            mTypeChoiceBox.getItems().clear();
            for (Account.Type t : Account.Type.values())
                if (g == null || t.isGroup(g))
                    mTypeChoiceBox.getItems().add(t);
            mTypeChoiceBox.getSelectionModel().select(0);
        }

        mNameTextField.setText(account == null ? "" : account.getName());
        mDescriptionTextArea.setText(account == null ? "" : account.getDescription());
        mHiddenFlagCheckBox.setSelected(account != null && account.getHiddenFlag());
    }

    void setDialogStage(Stage stage) { mDialogStage = stage; }

    @FXML
    private void handleOK() {
        String name = mNameTextField.getText();
        if (name == null || name.length() == 0 || MainApp.hasBannedCharacter(name)) {
            // we need to throw up a warning sign and go back
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            if (name == null || name.length() == 0)
                alert.setHeaderText("Account name cannot be empty");
            else {
                String bcs = MainApp.BANNED_CHARACTER_SET.toString();
                bcs = bcs.substring(1, bcs.length() - 1).replaceAll(",", ""); // take out [] and ,
                alert.setHeaderText("Account name cannot contain any of:  " + bcs);
            }
            alert.showAndWait();
            return;
        }

        if (mAccount == null) {
            mAccount = new Account(0, mTypeChoiceBox.getValue(), name,
                    mDescriptionTextArea.getText(), mHiddenFlagCheckBox.isSelected(), Integer.MAX_VALUE,
                    null, BigDecimal.ZERO);
        } else {
            mAccount.setName(name);
            mAccount.setType(mTypeChoiceBox.getValue());
            mAccount.setDescription(mDescriptionTextArea.getText());
            mAccount.setHiddenFlag(mHiddenFlagCheckBox.isSelected());
        }

        // insert or update database
        mMainApp.insertUpdateAccountToDB(mAccount);

        mDialogStage.close();
    }

    @FXML
    private void handleCancel() {
        mDialogStage.close();
    }
}
