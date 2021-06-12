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

    private MainModel mainModel;

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

    void setMainModel(MainModel mainModel) {

        this.mainModel = mainModel;

        mDCTableView.setItems(mainModel.getDCInfoList());
        mDCAliasColumn.setCellValueFactory(cd -> cd.getValue().getNameProperty());
        mFINameColumn.setCellValueFactory(cd -> mainModel.getFIData(fid -> fid.getID() == cd.getValue().getFIID())
                .map(DirectConnection.FIData::getNameProperty).orElse(new ReadOnlyStringWrapper("")));
    }

    private void showEditDCInfoDialog(DirectConnection dcInfo) {
        try {
            if (!mainModel.hasMasterPasswordInKeyStore()) {
                try {
                    List<String> passwords = DialogUtil.showPasswordDialog((Stage) mDCTableView.getScene().getWindow(),
                            "Enter Vault Master Password", PasswordDialogController.MODE.ENTER);
                    if (passwords.isEmpty())
                        return; // user cancelled, do nothing
                    if (passwords.size() != 2 || !mainModel.verifyMasterPassword(passwords.get(1))) {
                        // failed to verify master password
                        DialogUtil.showWarningDialog((Stage) mDCTableView.getScene().getWindow(),
                                "Edit Direct Connection","Failed to input correct Master Password",
                                "Direct connection cannot be edited");
                        return;
                    }
                } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException
                        | UnrecoverableKeyException e) {
                    DialogUtil.showExceptionDialog((Stage) mDCTableView.getScene().getWindow(),
                            e.getClass().getName(), "Vault Exception", e.toString(), e);
                    return;
                }
            }

            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditDCInfoDialog.fxml"));

            Stage stage = new Stage();
            stage.setTitle("Edit Direct Connection Information:");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mDCTableView.getScene().getWindow());
            stage.setScene(new Scene(loader.load()));

            EditDCInfoDialogController controller = loader.getController();
            controller.setMainModel(mainModel, dcInfo);
            stage.showAndWait();
        } catch (IOException e) {
            final String msg = "IOException when showEditDCInfoDialog";
            mLogger.error(msg, e);
            MainApp.showExceptionDialog((Stage) mDCTableView.getScene().getWindow(), e.getClass().getName(),
                    msg, e.toString(), e);
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
