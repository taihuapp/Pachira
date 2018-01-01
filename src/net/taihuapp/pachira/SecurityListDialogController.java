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
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class SecurityListDialogController {
    private MainApp mMainApp = null;
    private Stage mDialogStage = null;

    @FXML
    private TableView<Security> mSecurityTableView;
    @FXML
    private TableColumn<Security, String> mSecurityNameColumn;
    @FXML
    private TableColumn<Security, String> mSecurityTickerColumn;
    @FXML
    private TableColumn<Security, Security.Type> mSecurityTypeColumn;
    @FXML
    private Button mEditButton;
    @FXML
    private Button mEditPriceButton;
    @FXML
    private Button mDeleteButton;

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mDialogStage = stage;

        mSecurityTableView.setItems(mainApp.getSecurityList());

        mSecurityNameColumn.setCellValueFactory(cellData->cellData.getValue().getNameProperty());
        mSecurityTickerColumn.setCellValueFactory(cellData->cellData.getValue().getTickerProperty());
        mSecurityTypeColumn.setCellValueFactory(cellData->cellData.getValue().getTypeProperty());

        mSecurityTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                mEditButton.setDisable(false);
                mEditPriceButton.setDisable(false);
                //mDeleteButton.setDisable(false);  leave it disabled for now.
            }
        });
    }

    void close() { mDialogStage.close(); }

    private void showEditSecurityDialog(Security security) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("EditSecurityDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Security:");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mDialogStage);
            dialogStage.setScene(new Scene(loader.load()));

            EditSecurityDialogController controller = loader.getController();
            controller.setMainApp(mMainApp, security, dialogStage);
            dialogStage.showAndWait();
            // need to check selection here and enable/disable edit button
            mEditButton.setDisable(mSecurityTableView.getSelectionModel().getSelectedItem() == null);
            mEditPriceButton.setDisable(mSecurityTableView.getSelectionModel().getSelectedItem() == null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showEditSecurityPriceDialog(Security security) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("EditSecurityPriceDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Security Price:");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mDialogStage);
            dialogStage.setScene(new Scene(loader.load()));

            EditSecurityPriceDialogController controller = loader.getController();
            controller.setMainApp(mMainApp, security, dialogStage);
            dialogStage.showAndWait();
            // need to check selection here and enable/disable edit button
            mEditButton.setDisable(mSecurityTableView.getSelectionModel().getSelectedItem() == null);
            mEditPriceButton.setDisable(mSecurityTableView.getSelectionModel().getSelectedItem() == null);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @FXML
    private void handleNew() {
        showEditSecurityDialog(new Security());
    }

    @FXML
    private void handleEdit() {
        // edit a copy of selected item
        showEditSecurityDialog(new Security(mSecurityTableView.getSelectionModel().getSelectedItem()));
    }

    @FXML
    private void handleEditPrice() {
        showEditSecurityPriceDialog(mSecurityTableView.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void handleDelete() {
        mMainApp.showExceptionDialog("Exception", "Action Not Implemented",
                "Delete security action not implemented", null);
    }

    @FXML
    private void handleClose() { mDialogStage.close(); }

}
