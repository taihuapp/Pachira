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
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.apache.log4j.Logger;

import java.sql.SQLException;

public class EditFIDataDialogController {

    private static final Logger mLogger = Logger.getLogger(EditFIDataDialogController.class);

    private MainApp mMainApp;
    private DirectConnection.FIData mFIData;
    private Stage mStage;

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

    void setMainApp(MainApp mainApp, DirectConnection.FIData fiData, Stage stage) {
        mMainApp = mainApp;
        mFIData = fiData;
        mStage = stage;

        mNameTextField.textProperty().bindBidirectional(fiData.getNameProperty());
        mFIIDTextField.textProperty().bindBidirectional(fiData.getFIIDProperty());
        mSubIDTextField.textProperty().bindBidirectional(fiData.getSubIDProperty());
        mOrgTextField.textProperty().bindBidirectional(fiData.getORGProperty());
        mURLTextField.textProperty().bindBidirectional(fiData.getURLProperty());
    }

    @FXML
    private void handleSave() {
        try {
            if (!mMainApp.setDBSavepoint()) {
                mLogger.error("Database savepoint unexpectedly set");
                MainApp.showWarningDialog("Unexpected situation", "Database savepoint already set?",
                        "Please restart application");
            } else {
                mMainApp.insertUpdateFIDataToDB(mFIData);
                mMainApp.commitDB();
                mMainApp.initFIDataList();
            }
            mStage.close();
        } catch (SQLException e) {
            try {
                mLogger.error(MainApp.SQLExceptionToString(e), e);
                mMainApp.showExceptionDialog("Database Error", "insertUpdateFIDataToDB failed",
                        MainApp.SQLExceptionToString(e), e);
                mMainApp.rollbackDB();
            } catch (SQLException e1) {
                mLogger.error(MainApp.SQLExceptionToString(e1), e1);
                mMainApp.showExceptionDialog("Database Error", "Unable to rollback to savepoint",
                        MainApp.SQLExceptionToString(e1), e1);
            }
        } finally {
            try {
                mMainApp.releaseDBSavepoint();
            } catch (SQLException e) {
                mMainApp.showExceptionDialog("Database Error",
                        "Unable to release savepoint and set DB autocommit",
                        MainApp.SQLExceptionToString(e), e);
            }
        }
    }

    @FXML
    private void handleCancel() {
        mStage.close();
    }
}
