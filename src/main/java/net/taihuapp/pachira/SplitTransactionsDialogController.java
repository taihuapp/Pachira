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
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.util.List;

public class SplitTransactionsDialogController {

    private BigDecimal mNetAmount;
    private boolean mIsCanceled = true; // default to be true

    @FXML
    BorderPane borderPane;
    @FXML
    private TableView<SplitTransaction> mSplitTransactionsTableView;
    @FXML
    private TableColumn<SplitTransaction, Integer> mCategoryTableColumn;
    @FXML
    private TableColumn<SplitTransaction, Integer> mTagTableColumn;
    @FXML
    private TableColumn<SplitTransaction, String> mMemoTableColumn;
    @FXML
    private TableColumn<SplitTransaction, BigDecimal> mAmountTableColumn;
    @FXML
    private ComboBox<Integer> mCategoryIDComboBox;
    @FXML
    private ComboBox<Integer> mTagIDComboBox;
    @FXML
    private TextField mMemoTextField;
    @FXML
    private TextField mAmountTextField;
    @FXML
    private Button mAddButton;
    @FXML
    private Button mDeleteButton;
    @FXML
    private Button mOKButton;

    // the content of stList is copied, the original content is unchanged.
    void setMainModel(MainModel mainModel, int accountID, List<SplitTransaction> stList, String message,
                      BigDecimal netAmount) {

        mNetAmount = netAmount;

        (new EditTransactionDialogControllerNew.CategoryTransferAccountIDComboBoxWrapper(mCategoryIDComboBox, mainModel))
                .setFilter(false, accountID);
        mCategoryIDComboBox.getSelectionModel().selectFirst();

        mTagIDComboBox.setConverter(new ConverterUtil.TagIDConverter(mainModel));
        mTagIDComboBox.getItems().clear();
        mTagIDComboBox.getItems().add(0);
        for (Tag tag : mainModel.getTagList())
            mTagIDComboBox.getItems().add(tag.getID());
        mTagIDComboBox.getSelectionModel().selectFirst();

        mSplitTransactionsTableView.setEditable(true);
        mSplitTransactionsTableView.getItems().clear();
        for (SplitTransaction st : stList) {
            mSplitTransactionsTableView.getItems().add(new SplitTransaction(st.getID(), st.getCategoryID(),
                    st.getTagID(), st.getMemo(), st.getAmount(), st.getMatchID()));
        }

        mCategoryTableColumn.setCellValueFactory(cd -> cd.getValue().getCategoryIDProperty());
        mCategoryTableColumn.setCellFactory(ComboBoxTableCell.forTableColumn(mCategoryIDComboBox.getConverter(),
                mCategoryIDComboBox.getItems()));

        mTagTableColumn.setCellValueFactory(cd -> cd.getValue().getTagIDProperty());
        mTagTableColumn.setCellFactory(ComboBoxTableCell.forTableColumn(mTagIDComboBox.getConverter(),
                mTagIDComboBox.getItems()));

        mMemoTableColumn.setCellValueFactory(cd -> cd.getValue().getMemoProperty());
        mMemoTableColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        mMemoTableColumn.setOnEditCommit(e
                -> e.getTableView().getItems().get(e.getTablePosition().getRow()).setMemo(e.getNewValue()));

        mAmountTableColumn.setCellValueFactory(cd->cd.getValue().getAmountProperty());
        mAmountTableColumn.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        mAmountTableColumn.setOnEditCommit(e -> {
            e.getTableView().getItems().get(e.getTablePosition().getRow()).setAmount(e.getNewValue());
            updateRemainingAmount();
        });
        mAmountTableColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        if (!message.isBlank()) {
            // added textarea to show message
            System.err.println(message.length());
            TextArea textArea = new TextArea(message);
            textArea.setEditable(false); // no editing
            textArea.setWrapText(true); // wrapping text
            textArea.setPrefRowCount(1+message.length()/100); // roughly 100 char per line.

            borderPane.setTop(textArea);
            BorderPane.setMargin(textArea, new Insets(5,5,5,5));
        }

        // disable delete button if nothing is selected
        mDeleteButton.disableProperty().bind(mSplitTransactionsTableView.getSelectionModel()
                .selectedItemProperty().isNull());

        updateRemainingAmount();
    }

    List<SplitTransaction> getSplitTransactionList() {
        if (mIsCanceled)
            return null;
        return mSplitTransactionsTableView.getItems();
    }

    // update remaining amount and set up the buttons
    private void updateRemainingAmount() {
        final BigDecimal remainingAmount;
        if (mNetAmount == null) {
            // we are not restricted by total amount
            mAmountTextField.setText("0.00");
        } else {
            // remaining amount = -(netAmount + sum(st.Amount))
            remainingAmount = mSplitTransactionsTableView.getItems().stream().map(SplitTransaction::getAmount)
                    .reduce(mNetAmount, BigDecimal::add).negate();
            mAmountTextField.setText(remainingAmount.toPlainString());
            mOKButton.setDisable(remainingAmount.compareTo(BigDecimal.ZERO) != 0);
            mAddButton.setDisable(remainingAmount.compareTo(BigDecimal.ZERO) == 0);
        }
    }

    @FXML
    private void initialize() {
        mCategoryIDComboBox.setPrefWidth(mCategoryTableColumn.getWidth());
        mTagIDComboBox.setPrefWidth(mTagTableColumn.getWidth());
        mMemoTextField.setPrefWidth(mMemoTableColumn.getWidth());
        mAmountTextField.setPrefWidth(mAmountTableColumn.getWidth());

        mCategoryTableColumn.widthProperty().addListener((ob, o, n) -> mCategoryIDComboBox.setPrefWidth(n.doubleValue()));
        mTagTableColumn.widthProperty().addListener((ob, o, n) -> mTagIDComboBox.setPrefWidth(n.doubleValue()));
        mMemoTableColumn.widthProperty().addListener((ob, o, n) -> mMemoTextField.setPrefWidth(n.doubleValue()));
        mAmountTableColumn.widthProperty().addListener((ob, o, n) -> mAmountTextField.setPrefWidth(n.doubleValue()));
    }

    @FXML
    private void handleOK() {
        mIsCanceled = false;
        ((Stage) mSplitTransactionsTableView.getScene().getWindow()).close();
    }

    @FXML
    private void handleCancel() {
        mIsCanceled = true;
        ((Stage) mSplitTransactionsTableView.getScene().getWindow()).close();
    }

    @FXML
    private void handleAdd() {
        mSplitTransactionsTableView.getItems().add(new SplitTransaction(-1, mCategoryIDComboBox.getValue(),
                mTagIDComboBox.getValue(), mMemoTextField.getText(),
                new BigDecimal(mAmountTextField.getText()), -1));
        mMemoTextField.setText("");
        updateRemainingAmount();
    }

    @FXML
    private void handleDelete() {
        SplitTransaction st = mSplitTransactionsTableView.getSelectionModel().getSelectedItem();
        mSplitTransactionsTableView.getItems().remove(st);
        updateRemainingAmount();
    }
}
