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

    private class CategoryTransferToStringConverter extends StringConverter<Integer> {
        public Integer fromString(String name) {
            return mMainApp.mapCategoryOrAccountNameToID(name);
        }
        public String toString(Integer cid) {
            if (cid >= MainApp.MIN_CATEGORY_ID) {
                Category c = mMainApp.getCategoryByID(cid);
                return c == null ? "" : c.getName();
            }

            if (cid <= -MainApp.MIN_ACCOUNT_ID) {
                Account a = mMainApp.getAccountByID(-cid);
                return a == null ? "" : MainApp.getWrappedAccountName(a);
            }
            return "";
        }
    }

    private MainApp mMainApp;
    private Stage mDialogStage;
    private BigDecimal mNetAmount;

    @FXML
    private TableView<SplitTransaction> mSplitTransactionsTableView;
    @FXML
    private TableColumn<SplitTransaction, Integer> mCategoryTableColumn;
    @FXML
    private TableColumn<SplitTransaction, String> mPayeeTableColumn;
    @FXML
    private TableColumn<SplitTransaction, String> mMemoTableColumn;
    @FXML
    private TableColumn<SplitTransaction, BigDecimal> mAmountTableColumn;
    @FXML
    private ComboBox<Integer> mCategoryIDComboBox;
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
    void setMainApp(MainApp mainApp, Stage stage, List<SplitTransaction> stList, BigDecimal netAmount) {
        mMainApp = mainApp;
        mDialogStage = stage;
        mNetAmount = netAmount;

        mCategoryIDComboBox.setConverter(new CategoryTransferToStringConverter());
        mCategoryIDComboBox.getItems().clear();
        mCategoryIDComboBox.getItems().add(0); // add a blank
        for (Category c : mMainApp.getCategoryList()) {
            mCategoryIDComboBox.getItems().add(c.getID());
        }
        for (Account a : mMainApp.getAccountList(null, null, true)) {
            mCategoryIDComboBox.getItems().add(-a.getID());
        }
        mCategoryIDComboBox.getSelectionModel().selectFirst();

        mSplitTransactionsTableView.setEditable(true);
        mSplitTransactionsTableView.getItems().clear();
        for (SplitTransaction st : stList) {
            mSplitTransactionsTableView.getItems().add(new SplitTransaction(st.getID(), st.getCategoryID(),
                    st.getPayee(), st.getMemo(), st.getAmount(), st.getMatchID()));
        }

        mCategoryTableColumn.setCellValueFactory(cd -> cd.getValue().getCategoryIDProperty().asObject());
        mCategoryTableColumn.setCellFactory(ComboBoxTableCell.forTableColumn(new CategoryTransferToStringConverter(),
                mCategoryIDComboBox.getItems()));

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
        mPayeeTextField.setPrefWidth((mPayeeTableColumn.getWidth()));
        mMemoTextField.setPrefWidth(mMemoTableColumn.getWidth());
        mAmountTextField.setPrefWidth(mAmountTableColumn.getWidth());

        mCategoryTableColumn.widthProperty().addListener((ob, o, n) -> mCategoryIDComboBox.setPrefWidth(n.doubleValue()));
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
        mDialogStage.setUserData(mSplitTransactionsTableView.getItems());
        mDialogStage.close();
    }

    @FXML
    private void handleCancel() {
        mDialogStage.setUserData(null);
        mDialogStage.close();
    }

    @FXML
    private void handleAdd() {
        mSplitTransactionsTableView.getItems().add(new SplitTransaction(-1, mCategoryIDComboBox.getValue(),
                mPayeeTextField.getText(), mMemoTextField.getText(), new BigDecimal(mAmountTextField.getText()), -1));
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
