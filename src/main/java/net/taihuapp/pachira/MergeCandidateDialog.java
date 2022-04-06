/*
 * Copyright (C) 2018-2022.  Guangliang He.  All Rights Reserved.
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.log4j.Logger;

import java.net.URL;
import java.time.LocalDate;
import java.util.Arrays;

class MergeCandidateDialog {

    private static final Logger logger = Logger.getLogger(MergeCandidateDialog.class);

    private final Stage mDialogStage;
    private Transaction mSelectedTransaction = null;

    static class MergeCandidateTransactionTableView extends TransactionTableView {
        @Override
        final void setColumnVisibility() {
            for (TableColumn<Transaction, ?> tc : Arrays.asList(
                    mTransactionAccountColumn,
                    mTransactionDescriptionColumn,
                    mTransactionBalanceColumn
            )) {
                tc.setVisible(false);
            }
        }

        @Override
        final void setColumnSortability() {} // all columns remain sortable

        MergeCandidateTransactionTableView(MainModel mainModel, ObservableList<Transaction> tList) {
            super(mainModel, tList);}
    }

    private void handleClose() { mDialogStage.close(); }
    Transaction getSelectedTransaction() { return mSelectedTransaction; }

    // constructor
    MergeCandidateDialog(MainModel mainModel, Stage stage, final Transaction downloadedTransaction) {
        mDialogStage = stage;

        MergeCandidateTransactionTableView mergeCandidateTransactionTableView =
                new MergeCandidateTransactionTableView(mainModel,
                        mainModel.getMergeCandidateTransactionList(downloadedTransaction));
        mergeCandidateTransactionTableView.setRowFactory(tv -> {
            TableRow<Transaction> row = new TableRow<>();
            // double click select the merge candidate
            row.setOnMouseClicked(e -> {
                if ((e.getClickCount() == 2) && (!row.isEmpty())) {
                    mSelectedTransaction = row.getItem();
                    handleClose();
                }
            });
            PseudoClass future = PseudoClass.getPseudoClass("future");
            row.itemProperty().addListener((obs, oTransaction, nTransaction) ->
                    row.pseudoClassStateChanged(future, (nTransaction != null)
                    && nTransaction.getTDate().isAfter(LocalDate.now())));
            return row;
        });

        Label infoLabel = new Label();
        infoLabel.setText("Found " + mergeCandidateTransactionTableView.getItems().size()
                + " possible merge candidate transactions");

        Button mergeButton = new Button();
        mergeButton.setText("Merge");
        mergeButton.disableProperty().bind(mergeCandidateTransactionTableView.getSelectionModel()
                .selectedItemProperty().isNull());
        mergeButton.setDefaultButton(true);
        mergeButton.setOnAction(e -> {
            mSelectedTransaction = mergeCandidateTransactionTableView.getSelectionModel().getSelectedItem();
            mDialogStage.close();
        });

        Button cancelButton = new Button();
        cancelButton.setText("Cancel");
        cancelButton.setOnAction(e -> {
            mSelectedTransaction = null;
            mDialogStage.close();
        });

        HBox hBox = new HBox();
        hBox.getChildren().addAll(mergeButton, cancelButton);

        Insets insets = new Insets(5,5,5,5);
        HBox.setMargin(mergeButton, insets);
        HBox.setMargin(cancelButton, insets);

        VBox vBox = new VBox();
        vBox.getChildren().addAll(infoLabel, mergeCandidateTransactionTableView, hBox);
        VBox.setMargin(infoLabel, insets);
        VBox.setMargin(mergeCandidateTransactionTableView, insets);
        VBox.setMargin(hBox, insets);

        VBox.setVgrow(mergeCandidateTransactionTableView, Priority.ALWAYS);

        if (mergeCandidateTransactionTableView.getItems().size() > 0)
            mergeCandidateTransactionTableView.getSelectionModel().select(0);  // select the very first
        mDialogStage.setScene(new Scene(vBox));
        mergeButton.requestFocus();

        final String cssFileName = "/css/TransactionTableView.css";
        final URL cssUrl = getClass().getResource(cssFileName);
        if (cssUrl != null) {
            mergeCandidateTransactionTableView.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            final String msg = getClass() + ".getResource(" + cssFileName + ") returns null";
            NullPointerException npe = new NullPointerException(msg);
            logger.error(msg, npe);
            DialogUtil.showWarningDialog(mDialogStage, "Error", "Unable to get css", msg);
            throw npe;
        }
    }
}
