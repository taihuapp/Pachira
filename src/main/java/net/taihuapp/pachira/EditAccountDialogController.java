/*
 * Copyright (C) 2018-2023.  Guangliang He.  All Rights Reserved.
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
import net.taihuapp.pachira.dao.DaoException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;

public class EditAccountDialogController {

    private static final Logger logger = LogManager.getLogger(EditAccountDialogController.class);

    private Account account;
    private MainModel mainModel;

    @FXML
    private ChoiceBox<Account.Type> mTypeChoiceBox;
    @FXML
    private TextField mNameTextField;
    @FXML
    private TextArea mDescriptionTextArea;
    @FXML
    private CheckBox mHiddenFlagCheckBox;

    void setAccount(MainModel mainModel, Account account, Account.Type.Group g) {
        this.mainModel = mainModel;
        this.account = account;

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

        if (account == null) {
            account = new Account(0, mTypeChoiceBox.getValue(), name,
                    mDescriptionTextArea.getText(), mHiddenFlagCheckBox.isSelected(), Integer.MAX_VALUE,
                    null, BigDecimal.ZERO);
        } else {
            account.setName(name);
            account.setType(mTypeChoiceBox.getValue());
            account.setDescription(mDescriptionTextArea.getText());
            account.setHiddenFlag(mHiddenFlagCheckBox.isSelected());
        }

        // insert or update database
        try {
            mainModel.insertUpdateAccount(account);
            close();
        } catch (DaoException e) {
            final String msg = e.getErrorCode() + " DaoException when insertUpdateAccount";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog((Stage) mNameTextField.getScene().getWindow(),
                    "DaoException", msg, e.toString(), e);
        }
    }

    @FXML
    private void handleCancel() {
        close();
    }

    private void close() { ((Stage) mNameTextField.getScene().getWindow()).close(); }
}
