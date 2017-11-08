/*
 * Copyright (C) 2017.  Guangliang He.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of FaCai168.
 *
 * FaCai168 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any
 * later version.
 *
 * FaCai168 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.taihuapp.facai168;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Arrays;

class SearchResultDialog {
    private Stage mDialogStage;
    private Transaction mSelectedTransaction = null;

    static class SearchTransactionTableView extends TransactionTableView {
        @Override
        final void setColumnVisibility() {
            for (TableColumn tc : Arrays.asList(
                    mTransactionDescriptionColumn,
                    mTransactionInvestAmountColumn,
                    mTransactionCashAmountColumn,
                    mTransactionPaymentColumn,
                    mTransactionDepositColumn,
                    mTransactionBalanceColumn
            )) {
                tc.setVisible(false);
            }
        }

        @Override
        final void setColumnSortability() {}  // all columns remains sortable

        SearchTransactionTableView(MainApp mainApp, ObservableList<Transaction> tList) {
            super(mainApp, tList);
        }
    }

    private void handleClose() { mDialogStage.close(); }
    Transaction getSelectedTransaction() { return mSelectedTransaction; }

    // constructor
    SearchResultDialog(String searchString, MainApp mainApp, Stage stage) {
        mDialogStage = stage;

        SearchTransactionTableView searchTransactionTableView = new SearchTransactionTableView(mainApp,
                mainApp.getFilteredTransactionList(searchString));
        searchTransactionTableView.setRowFactory(tv -> {
                    TableRow<Transaction> row = new TableRow<>();
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
