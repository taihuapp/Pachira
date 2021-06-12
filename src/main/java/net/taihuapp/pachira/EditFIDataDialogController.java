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
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.log4j.Logger;

public class EditFIDataDialogController {

    private static final Logger mLogger = Logger.getLogger(EditFIDataDialogController.class);

    private MainModel mainModel;
    private DirectConnection.FIData mFIData;

    @FXML
    private TextField mNameTextField;
    @FXML
    private TextField mFIIDTextField;
    @FXML
    private TextField mSubIDTextField;
    @FXML
    private TextField mOrgTextField;
    @FXML
    private TextField mURLTextField;

    void setMainModel(MainModel mainModel, DirectConnection.FIData fiData) {

        this.mainModel = mainModel;
        mFIData = fiData;

        mNameTextField.textProperty().bindBidirectional(fiData.getNameProperty());
        mFIIDTextField.textProperty().bindBidirectional(fiData.getFIIDProperty());
        mSubIDTextField.textProperty().bindBidirectional(fiData.getSubIDProperty());
        mOrgTextField.textProperty().bindBidirectional(fiData.getORGProperty());
        mURLTextField.textProperty().bindBidirectional(fiData.getURLProperty());
    }

    @FXML
    private void handleSave() {
        Stage stage = (Stage) mNameTextField.getScene().getWindow();
        try {
            mainModel.insertUpdateFIData(mFIData);
            stage.close();
        } catch (DaoException e) {
            final String msg = "insertUpdateFIData failed";
            mLogger.error(msg, e);
            DialogUtil.showExceptionDialog(stage, e.getClass().getName(), msg, e.toString(), e);
        }
    }

    @FXML
    private void handleCancel() { ((Stage) mNameTextField.getScene().getWindow()).close(); }
}
