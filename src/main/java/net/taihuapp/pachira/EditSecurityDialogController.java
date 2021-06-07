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
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.log4j.Logger;

public class EditSecurityDialogController {

    private static final Logger logger = Logger.getLogger(EditSecurityDialogController.class);
    private MainModel mainModel;
    private Security mSecurity;

    @FXML
    private TextField mNameTextField;
    @FXML
    private TextField mTickerTextField;
    @FXML
    private ChoiceBox<Security.Type> mTypeChoiceBox;

    void setMainModel(MainModel mainModel, Security security) {

        this.mainModel = mainModel;
        mSecurity = security;

        mNameTextField.textProperty().bindBidirectional(mSecurity.getNameProperty());
        mTickerTextField.textProperty().bindBidirectional(mSecurity.getTickerProperty());

        mTypeChoiceBox.setConverter(new StringConverter<>() {
            public Security.Type fromString(String s) { return Security.Type.fromString(s); }
            public String toString(Security.Type type) { return type.toString(); }
        });
        mTypeChoiceBox.getItems().setAll(Security.Type.values());
        mTypeChoiceBox.valueProperty().bindBidirectional(mSecurity.getTypeProperty());
    }

    @FXML
    private void handleSave() {
        try {
            mainModel.mergeSecurity(mSecurity);
            mainModel.initSecurityList();
            mainModel.initTransactionList();
            mainModel.initAccountList();
            ((Stage) mNameTextField.getScene().getWindow()).close();
        } catch (DaoException e) {
            Stage stage = (Stage) mNameTextField.getScene().getWindow();
            final String msg = e.getErrorCode() + " when save security " + mSecurity;
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(stage, e.getClass().getName(), msg, e.toString(), e);
        }
    }

    @FXML
    private void handleCancel() { ((Stage) mNameTextField.getScene().getWindow()).close(); }
}