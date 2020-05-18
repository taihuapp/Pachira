/*
 * Copyright (C) 2018.  Guangliang He.  All Rights Reserved.
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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.util.List;

public class SplitTransactionsDialogController {

    private class TagToStringConverter extends StringConverter<Integer> {
        public Integer fromString(String name) {
            return mMainApp.mapTagNameToID(name);
        }
        public String toString(Integer tagid) {
            Tag tag = mMainApp.getTagByID(tagid);
            return tag == null ? "" : tag.getName();
        }
    }

    private MainApp mMainApp;
    private Stage mDialogStage;
    private BigDecimal mNetAmount;
    private boolean mIsCanceled = true; // default to be true

    @FXML
    private TableView<SplitTransaction> mSplitTransactionsTableView;
    @FXML
    private TableColumn<SplitTransaction, Integer> mCategoryTableColumn;
    @FXML
    private TableColumn<SplitTransaction, Integer> mTagTableColumn;
    @FXML
    private TableColumn<SplitTransaction, String> mPayeeTableColumn;
    @FXML
    private TableColumn<SplitTransaction, String> mMemoTableColumn;
    @FXML
    private TableColumn<SplitTransaction, BigDecimal> mAmountTableColumn;
    @FXML
    private ComboBox<Integer> mCategoryIDComboBox;
    @FXML
    private ComboBox<Integer> mTagIDComboBox;
    @FXML
    private TextField mPayeeTextField;
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
    void setMainApp(MainApp mainApp, int accountID, Stage stage, List<SplitTransaction> stList, BigDecimal netAmount) {
        mMainApp = mainApp;
        mDialogStage = stage;
        mNetAmount = netAmount;

        (new EditTransactionDialogControllerNew.CategoryTransferAccountIDComboBoxWrapper(mCategoryIDComboBox, mainApp))
                .setFilter(false, accountID);
        mCategoryIDComboBox.getSelectionModel().selectFirst();

        mTagIDComboBox.setConverter(new TagToStringConverter());
        mTagIDComboBox.getItems().clear();
        mTagIDComboBox.getItems().add(0);
        for (Tag tag : mMainApp.getTagList())
            mTagIDComboBox.getItems().add(tag.getID());
        mTagIDComboBox.getSelectionModel().selectFirst();

        mSplitTransactionsTableView.setEditable(true);
        mSplitTransactionsTableView.getItems().clear();
        for (SplitTransaction st : stList) {
            mSplitTransactionsTableView.getItems().add(new SplitTransaction(st.getID(), st.getCategoryID(),
                    st.getTagID(), st.getPayee(), st.getMemo(), st.getAmount(), st.getMatchID()));
        }

        mCategoryTableColumn.setCellValueFactory(cd -> cd.getValue().getCategoryIDProperty());
        mCategoryTableColumn.setCellFactory(ComboBoxTableCell.forTableColumn(mCategoryIDComboBox.getConverter(),
                mCategoryIDComboBox.getItems()));

        mTagTableColumn.setCellValueFactory(cd -> cd.getValue().getTagIDProperty());
        mTagTableColumn.setCellFactory(ComboBoxTableCell.forTableColumn(mTagIDComboBox.getConverter(),
                mTagIDComboBox.getItems()));

        mPayeeTableColumn.setCellValueFactory(cd -> cd.getValue().getPayeeProperty());
        mPayeeTableColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        mPayeeTableColumn.setOnEditCommit(e
                -> e.getTableView().getItems().get(e.getTablePosition().getRow()).setPayee(e.getNewValue()));

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

        updateRemainingAmount();
    }

    List<SplitTransaction> getSplitTransactionList() {
        if (mIsCanceled)
            return null;
        return mSplitTransactionsTableView.getItems();
    }

    private void updateRemainingAmount() {
        BigDecimal remainingAmount = mNetAmount;
        for (SplitTransaction st : mSplitTransactionsTableView.getItems()) {
            remainingAmount = remainingAmount.add(st.getAmount());
        }
        remainingAmount = remainingAmount.negate();
        mAmountTextField.setText(remainingAmount.toPlainString());
    }

    @FXML
    private void initialize() {
        mCategoryIDComboBox.setPrefWidth(mCategoryTableColumn.getWidth());
        mTagIDComboBox.setPrefWidth(mTagTableColumn.getWidth());
        mPayeeTextField.setPrefWidth((mPayeeTableColumn.getWidth()));
        mMemoTextField.setPrefWidth(mMemoTableColumn.getWidth());
        mAmountTextField.setPrefWidth(mAmountTableColumn.getWidth());

        mCategoryTableColumn.widthProperty().addListener((ob, o, n) -> mCategoryIDComboBox.setPrefWidth(n.doubleValue()));
        mTagTableColumn.widthProperty().addListener((ob, o, n) -> mTagIDComboBox.setPrefWidth(n.doubleValue()));
        mPayeeTableColumn.widthProperty().addListener((ob, o, n) -> mPayeeTextField.setPrefWidth(n.doubleValue()));
        mMemoTableColumn.widthProperty().addListener((ob, o, n) -> mMemoTextField.setPrefWidth(n.doubleValue()));
        mAmountTableColumn.widthProperty().addListener((ob, o, n) -> mAmountTextField.setPrefWidth(n.doubleValue()));

        BooleanBinding remaining = Bindings.createBooleanBinding(() -> {
            try {
                return (new BigDecimal(mAmountTextField.getText())).compareTo(BigDecimal.ZERO) != 0;
            } catch (NumberFormatException e) {
                return true;
            }
        }, mAmountTextField.textProperty());

        mAddButton.disableProperty().bind(remaining.not());
        mOKButton.disableProperty().bind(remaining);

        mDeleteButton.disableProperty().bind(Bindings.createBooleanBinding(()
                        -> (mSplitTransactionsTableView.getSelectionModel().getSelectedItem() == null),
                mSplitTransactionsTableView.getSelectionModel().getSelectedItems()));
    }

    @FXML
    private void handleOK() {
        mIsCanceled = false;
        mDialogStage.close();
    }

    @FXML
    private void handleCancel() {
        mIsCanceled = true;
        mDialogStage.close();
    }

    @FXML
    private void handleAdd() {
        mSplitTransactionsTableView.getItems().add(new SplitTransaction(-1, mCategoryIDComboBox.getValue(),
                mTagIDComboBox.getValue(), mPayeeTextField.getText(), mMemoTextField.getText(),
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
