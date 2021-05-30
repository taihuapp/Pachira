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
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.Arrays;

class SearchResultDialog {
    private final Stage mDialogStage;
    private Transaction mSelectedTransaction = null;

    static class SearchTransactionTableView extends TransactionTableView {
        @Override
        final void setColumnVisibility() {
            for (TableColumn<Transaction, ?> tc : Arrays.asList(
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

        SearchTransactionTableView(MainModel mainModel, ObservableList<Transaction> tList) {
            super(mainModel, tList);
        }
    }

    private void handleClose() { mDialogStage.close(); }
    Transaction getSelectedTransaction() { return mSelectedTransaction; }

    // constructor
    SearchResultDialog(String searchString, MainModel mainModel, Stage stage) {
        mDialogStage = stage;

        SearchTransactionTableView searchTransactionTableView = new SearchTransactionTableView(mainModel,
                mainModel.getStringSearchTransactionList(searchString));

        searchTransactionTableView.setRowFactory(tv -> {
            TableRow<Transaction> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if ((e.getClickCount() == 2) && (!row.isEmpty())) {
                    mSelectedTransaction = row.getItem();
                    handleClose();
                }
            });

            // high light future transactions
            PseudoClass future = PseudoClass.getPseudoClass("future");
            row.itemProperty().addListener((obs, oTransaction, nTransaction) ->
                    row.pseudoClassStateChanged(future, (nTransaction != null)
                    && nTransaction.getTDate().isAfter(LocalDate.now())));
            return row;
        });
        searchTransactionTableView.getStylesheets().add(getClass()
                .getResource("/css/TransactionTableView.css").toExternalForm());

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
