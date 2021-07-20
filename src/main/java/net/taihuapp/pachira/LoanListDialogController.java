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
import org.apache.log4j.Logger;

import java.io.IOException;

public class LoanListDialogController {

    private static final Logger logger = Logger.getLogger(LoanListDialogController.class);

    private MainModel mainModel;

    @FXML
    private TableView<Loan> loanTableView;
    @FXML
    private TableColumn<Loan, String> loanNameColumn;
    @FXML
    private TableColumn<Loan, String> loadDescriptionColumn;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;

    void setMainModel(MainModel mainModel) {
        this.mainModel = mainModel;
        loanTableView.setItems(mainModel.getLoanList());
    }

    void close() { ((Stage) loanTableView.getScene().getWindow()).close(); }

    @FXML
    private void handleNew() { showEditLoanDialog(new Loan()); }

    @FXML
    private void handleEdit() {
        showEditLoanDialog(new Loan(loanTableView.getSelectionModel().getSelectedItem()));
    }

    @FXML
    private void handleDelete() {
        System.out.println("Delete");
    }

    @FXML
    private void handleClose() { close(); }

    @FXML
    private void initialize() {
        loanNameColumn.setCellValueFactory(cd -> cd.getValue().getNameProperty());
        loadDescriptionColumn.setCellValueFactory(cd -> cd.getValue().getDescriptionProperty());

        editButton.disableProperty().bind(loanTableView.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(loanTableView.getSelectionModel().selectedItemProperty().isNull());
    }

    // works after this window properly initialized
    private Stage getStage() { return (Stage) loanTableView.getScene().getWindow(); }

    private void showEditLoanDialog(Loan loan) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditLoanDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Loan");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(getStage());
            dialogStage.setScene(new Scene(loader.load()));

            EditLoanDialogController controller = loader.getController();
            controller.setMainModel(mainModel, loan);
            dialogStage.showAndWait();
        } catch (IOException e) {
            final String msg = "showEditLoadDialogException";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(),
                    "showEditLoanDialog Exception", e.toString(), e);
        }
    }
}
