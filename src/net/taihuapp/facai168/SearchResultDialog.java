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

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

class SearchResultDialog {
    private MainApp mMainApp;
    private String mSearchString;
    private Stage mDialogStage;
    private Transaction mSelectedTransaction = null;

    static class SearchTransactionTableView extends TransactionTableView {
        final void setVisibleColumns() {
            mDescriptionColumnVisibility.set(false);
            mInvestmentAmountColumnVisibility.set(false);
            mCashAmountColumnVisibility.set(false);
            mPaymentColumnVisibility.set(false);
            mDepositColumnVisibility.set(false);
            mBalanceColumnVisibility.set(false);
        }

        SearchTransactionTableView(MainApp mainApp) {
            super(mainApp);
        }
    }
    private SearchTransactionTableView mSearchTransactionTableView;

    private VBox mVBox;
    private Label mResultLabel;
    private Button mCloseButton;

    private void handleClose() { mDialogStage.close(); }
    Transaction getSelectedTransaction() { return mSelectedTransaction; }

    // constructor
    SearchResultDialog(String searchString, MainApp mainApp, Stage stage) {
        mSearchString = searchString;
        mMainApp = mainApp;
        mDialogStage = stage;

        mSearchTransactionTableView = new SearchTransactionTableView(mMainApp);
        mSearchTransactionTableView.setItems(mMainApp.getFilteredTransactionList(mSearchString));
        mSearchTransactionTableView.setRowFactory(tv -> {
                    TableRow<Transaction> row = new TableRow<>();
                    row.setOnMouseClicked(e -> {
                        if ((e.getClickCount() == 2) && (!row.isEmpty())) {
                            mSelectedTransaction = row.getItem();
                            handleClose();
                        }
                    });
                    return row;
                });
        mResultLabel = new Label();
        mResultLabel.setText("Found " + mSearchTransactionTableView.getItems().size() + " transactions match '" + mSearchString + "'");

        mCloseButton =  new Button();
        mCloseButton.setText("Close");
        mCloseButton.setOnAction(e -> mDialogStage.close());

        mVBox = new VBox();
        mVBox.setPrefWidth(800);
        mVBox.getChildren().addAll(mResultLabel, mSearchTransactionTableView, mCloseButton);
        VBox.setMargin(mResultLabel, new Insets(5,5,5,5));
        VBox.setMargin(mSearchTransactionTableView, new Insets(5,5,5,5));
        VBox.setMargin(mCloseButton, new Insets(5,5,5,5));

        mDialogStage.setScene(new Scene(mVBox));
    }
}
