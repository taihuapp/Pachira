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
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class SecurityListDialogController {

    private MainModel mainModel;

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

    void setMainModel(MainModel mainModel) {

        this.mainModel = mainModel;

        mSecurityTableView.setItems(mainModel.getSecurityList());

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

    void close() { ((Stage) mSecurityTableView.getScene().getWindow()).close(); }

    private void showEditSecurityDialog(Security security) {
        Stage stage = (Stage) mSecurityTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditSecurityDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Security:");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setScene(new Scene(loader.load()));

            EditSecurityDialogController controller = loader.getController();
            controller.setMainModel(mainModel, security);
            dialogStage.showAndWait();
            mSecurityTableView.setItems(mainModel.getSecurityList());
            // need to check selection here and enable/disable edit button
            mEditButton.setDisable(mSecurityTableView.getSelectionModel().getSelectedItem() == null);
            mEditPriceButton.setDisable(mSecurityTableView.getSelectionModel().getSelectedItem() == null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showEditSecurityPriceDialog(Security security) {
        Stage stage = (Stage) mSecurityTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditSecurityPriceDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Security Price:");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setScene(new Scene(loader.load()));

            EditSecurityPriceDialogController controller = loader.getController();
            controller.setMainModel(mainModel, security);
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
        Stage stage = (Stage) mSecurityTableView.getScene().getWindow();
        DialogUtil.showInformationDialog(stage, "Unsupported Operation", "Unsupported Operation",
                "Delete security is not implemented yet");
    }

    @FXML
    private void handleClose() { close(); }

}
