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
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

public class DirectConnectionListDialogController {

    private static final Logger mLogger = Logger.getLogger(DirectConnectionListDialogController.class);

    private MainApp mMainApp;
    private Stage mDialogStage;

    @FXML
    private TableView<DirectConnection> mDCTableView;
    @FXML
    private TableColumn<DirectConnection, String> mDCAliasColumn;
    @FXML
    private TableColumn<DirectConnection, String> mFINameColumn;

    @FXML
    private Button mEditButton;
    @FXML
    private Button mDeleteButton;

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mDialogStage = stage;

        mDCTableView.setItems(mMainApp.getDCInfoList());
        mDCAliasColumn.setCellValueFactory(cd -> cd.getValue().getNameProperty());
        mFINameColumn.setCellValueFactory(cd -> {
            DirectConnection dc = cd.getValue();
            DirectConnection.FIData fiData = mMainApp.getFIDataByID(dc.getFIID());
            if (fiData == null)
                return new ReadOnlyStringWrapper("");
            return fiData.getNameProperty();
        });
    }

    private void showEditDCInfoDialog(DirectConnection dcInfo) {
        try {
            if (!mMainApp.hasMasterPasswordInKeyStore()) {
                try {
                    List<String> passwords = mMainApp.showPasswordDialog(PasswordDialogController.MODE.ENTER);
                    if (passwords.size() != 2 || !mMainApp.verifyMasterPassword(passwords.get(1))) {
                        // failed to verify master password
                        MainApp.showWarningDialog("Edit Direct Connection",
                                "Failed to input correct Master Password",
                                "Direct connection cannot be edited");
                        return;
                    }
                } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException
                        | UnrecoverableKeyException e) {
                    mMainApp.showExceptionDialog("Exception", "Vault Exception", e.getMessage(), e);
                    return;
                }
            }

            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditDCInfoDialog.fxml"));

            Stage stage = new Stage();
            stage.setTitle("Edit Direct Connection Information:");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mDialogStage);
            stage.setScene(new Scene(loader.load()));

            EditDCInfoDialogController controller = loader.getController();
            controller.setMainApp(mMainApp, dcInfo, stage);
            stage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
            mMainApp.showExceptionDialog("IOException", "showEditDCInfoDialog failed",
                    e.getMessage(), e);
        }
    }

    @FXML
    private void handleNew() {
        showEditDCInfoDialog(new DirectConnection(-1, "", -1, "", ""));
    }

    @FXML
    private void handleDelete() {
        System.out.println("Delete");
    }

    @FXML
    private void handleEdit() {
        showEditDCInfoDialog(mDCTableView.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void initialize() {
        mEditButton.disableProperty().bind(mDCTableView.getSelectionModel().selectedItemProperty().isNull());
        mDeleteButton.disableProperty().bind(mDCTableView.getSelectionModel().selectedItemProperty().isNull());
    }
}
