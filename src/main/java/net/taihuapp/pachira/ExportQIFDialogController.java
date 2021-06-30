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

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class ExportQIFDialogController {

    private static final Logger mLogger = Logger.getLogger(ExportQIFDialogController.class);

    private MainModel mainModel;

    @FXML
    private CheckBox mAccountCheckBox;
    @FXML
    private CheckBox mCategoryCheckBox;
    @FXML
    private CheckBox mSecurityCheckBox;
    @FXML
    private CheckBox mTransactionCheckBox;

    @FXML
    private Label mFromLabel;
    @FXML
    private Label mToLabel;
    @FXML
    private DatePicker mFromDatePicker;
    @FXML
    private DatePicker mToDatePicker;

    @FXML
    private Label mAccountLabel;
    @FXML
    private ListView<Account> mAccountListView;
    @FXML
    private Button mSelectAllButton;
    @FXML
    private Button mClearAllButton;

    @FXML
    private Button mExportButton;
    @FXML
    private Button mCancelButton;

    private Stage getStage() { return (Stage) mAccountListView.getScene().getWindow(); }

    @FXML
    private void handleExport() {
        final FileChooser fileChooser = new FileChooser();
        String defaultFileName;
        try {
            defaultFileName = mainModel.getDBFileName() + ".QIF";
        } catch (DaoException e) {
            final String msg = e.getErrorCode() + " exception when calling getDBFileName";
            mLogger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.toString(), e);
            return;
        }
        fileChooser.setTitle("Export to QIF file...");
        fileChooser.setInitialFileName(defaultFileName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("qif files",
                "*.qif", "*.QIF"));
        final File file = fileChooser.showSaveDialog(getStage());
        if (file == null)
            return;

        try {
            final String output = mainModel.exportToQIF(mAccountCheckBox.isSelected(), mCategoryCheckBox.isSelected(),
                    mSecurityCheckBox.isSelected(), mTransactionCheckBox.isSelected(),
                    mFromDatePicker.getValue(), mToDatePicker.getValue(),
                    mAccountListView.getSelectionModel().getSelectedItems());
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(output);
            fileWriter.close();

            getStage().close();
        } catch (IOException | DaoException | ModelException e) {
            final String msg = e.getClass().getName() + " exception when exporting to QIF";
            mLogger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.toString(), e);
        }
    }

    @FXML
    private void handleCancel() {
        getStage().close();
    }

    @FXML
    private void handleSelectAll() {
        mAccountListView.getSelectionModel().selectAll();
        mAccountListView.requestFocus();
    }

    @FXML
    private void handleClearAll() {
        mAccountListView.getSelectionModel().clearSelection();
        mAccountListView.requestFocus();
    }

    @FXML
    private void initialize() {
        mAccountListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Account a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null)
                    setText("");
                else {
                    setText(a.getName());
                }
            }
        });
        mAccountListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        DatePickerUtil.captureEditedDate(mFromDatePicker);
        DatePickerUtil.captureEditedDate(mToDatePicker);

        mFromLabel.visibleProperty().bind(mTransactionCheckBox.selectedProperty());
        mToLabel.visibleProperty().bind(mFromLabel.visibleProperty());
        mFromDatePicker.visibleProperty().bind(mFromLabel.visibleProperty());
        mToDatePicker.visibleProperty().bind(mFromLabel.visibleProperty());

        // selecting accounts only affects exporting transactions.
        mAccountLabel.visibleProperty().bind(mTransactionCheckBox.selectedProperty());
        mAccountListView.visibleProperty().bind(mTransactionCheckBox.selectedProperty());
        mSelectAllButton.visibleProperty().bind(mTransactionCheckBox.selectedProperty());
        mClearAllButton.visibleProperty().bind(mTransactionCheckBox.selectedProperty());
    }

    void setMainModel(MainModel mainModel) {

        this.mainModel = mainModel;

        final ObservableList<Account> accountList = mainModel.getAccountList(a ->
                !a.getName().equals(MainModel.DELETED_ACCOUNT_NAME));
        mAccountListView.setItems(accountList);
        mAccountListView.getSelectionModel().selectAll();

        LocalDate fromDate = LocalDate.MAX;
        LocalDate toDate = LocalDate.MIN;
        for (Account account : accountList) {
            final List<Transaction> transactionList = account.getTransactionList();
            final int s = transactionList.size();
            if (s == 0)
                continue;

            if (transactionList.get(0).getTDate().isBefore(fromDate))
                fromDate = transactionList.get(0).getTDate();
            if (transactionList.get(s-1).getTDate().isAfter(toDate))
                toDate = transactionList.get(s-1).getTDate();
        }
        if (fromDate == LocalDate.MAX) {
            mFromDatePicker.setValue(LocalDate.now());
            mToDatePicker.setValue(LocalDate.now());
        } else {
            mFromDatePicker.setValue(fromDate);
            mToDatePicker.setValue(toDate);
        }
    }
}