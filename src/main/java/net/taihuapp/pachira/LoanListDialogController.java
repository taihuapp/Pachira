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
import net.taihuapp.pachira.dao.DaoException;
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
    private Button showButton;
    @FXML
    private Button deleteButton;

    void setMainModel(MainModel mainModel) {
        this.mainModel = mainModel;
        try {
            loanTableView.getItems().setAll(mainModel.getLoanList());
        } catch (DaoException e) {
            final String msg = "DaoException in getLoanList()";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.toString(), e);
        }
    }

    void close() { ((Stage) loanTableView.getScene().getWindow()).close(); }

    @FXML
    private void handleNew() {
        showEditLoanDialog(new Loan());
    }

    @FXML
    private void handleShow() {
        // for existing loan, the 'edit' dialog is read only, no need to make a copy here
        showEditLoanDialog(loanTableView.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void handleDelete() {
        final Loan loan = loanTableView.getSelectionModel().getSelectedItem();
        try {
            mainModel.deleteLoan(loan.getID());
            loanTableView.getItems().remove(loan);
        } catch (DaoException e) {
            final String msg = "DaoException when delete a loan " + loan.getID();
            logger.error(msg, e);
            DialogUtil.showExceptionDialog((Stage) loanTableView.getScene().getWindow(), "DaoException",
                    msg, e.toString(), e);
        }
    }

    @FXML
    private void handleClose() { close(); }

    @FXML
    private void initialize() {
        loanNameColumn.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(mainModel.getAccount(a -> a.getID() == cd.getValue().getAccountID())
                        .map(Account::getName).orElse("Deleted Account")));

        loadDescriptionColumn.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(mainModel.getAccount(a -> a.getID() == cd.getValue().getAccountID())
                        .map(Account::getDescription).orElse("")));

        showButton.disableProperty().bind(loanTableView.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(loanTableView.getSelectionModel().selectedItemProperty().isNull());
    }

    // works after this window properly initialized
    private Stage getStage() { return (Stage) loanTableView.getScene().getWindow(); }

    private void showEditLoanDialog(Loan loan) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditLoanDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Loan Details");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(getStage());
            dialogStage.setScene(new Scene(loader.load()));

            EditLoanDialogController controller = loader.getController();
            controller.setMainModel(mainModel, loan, loanTableView.getItems());
            dialogStage.showAndWait();
        } catch (IOException e) {
            final String msg = "showEditLoadDialogException";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.toString(), e);
        }
    }
}
