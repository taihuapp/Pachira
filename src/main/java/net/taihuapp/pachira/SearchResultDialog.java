/*
 * Copyright (C) 2018-2024.  Guangliang He.  All Rights Reserved.
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

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.util.Arrays;

class SearchResultDialog {

    private final Stage mDialogStage;
    private Transaction mSelectedTransaction = null;

    private void handleClose() { mDialogStage.close(); }
    Transaction getSelectedTransaction() { return mSelectedTransaction; }

    // constructor
    SearchResultDialog(String searchString, MainModel mainModel, Stage stage) throws ModelException {
        mDialogStage = stage;

        TransactionTableView searchTransactionTableView = new TransactionTableView(mainModel,
                mainModel.getStringSearchTransactionList(searchString));
        // hide certain columns
        for (TableColumn<Transaction, ?> tc : Arrays.asList(
                searchTransactionTableView.mTransactionDescriptionColumn,
                searchTransactionTableView.mTransactionInvestAmountColumn,
                searchTransactionTableView.mTransactionCashAmountColumn,
                searchTransactionTableView.mTransactionPaymentColumn,
                searchTransactionTableView.mTransactionDepositColumn,
                searchTransactionTableView.mTransactionBalanceColumn
        )) {
            tc.setVisible(false);
        }

        // make date and account column sortable
        searchTransactionTableView.mTransactionDateColumn.setSortable(true);
        searchTransactionTableView.mTransactionAccountColumn.setSortable(true);

        Callback<TableView<Transaction>, TableRow<Transaction>> callback = searchTransactionTableView.getRowFactory();
        searchTransactionTableView.setRowFactory(tv -> {
            TableRow<Transaction> row = callback.call(tv);

            row.setOnMouseClicked(e -> {
                if ((e.getClickCount() == 2) && (!row.isEmpty())) {
                    mSelectedTransaction = row.getItem();
                    handleClose();
                }
            });
            return row;
        });

        Label resultLabel = new Label();
        resultLabel.setText("Found " + searchTransactionTableView.getItems().size()
                + " transactions match '" + searchString + "'");

        Button closeButton =  new Button();
        closeButton.setText("Close");
        closeButton.setOnAction(e -> mDialogStage.close());

        VBox vBox = new VBox();
        vBox.getChildren().addAll(resultLabel, searchTransactionTableView, closeButton);

        VBox.setMargin(resultLabel, new Insets(5,5,5,5));
        VBox.setMargin(searchTransactionTableView, new Insets(5,5,5,5));
        VBox.setMargin(closeButton, new Insets(5,5,5,5));
        VBox.setVgrow(searchTransactionTableView, Priority.ALWAYS);

        mDialogStage.setScene(new Scene(vBox));
    }
}
