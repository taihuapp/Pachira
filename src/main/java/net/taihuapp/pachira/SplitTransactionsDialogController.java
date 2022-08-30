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

import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.Currency;
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

    private final ObjectProperty<BigDecimal> amountProperty = new SimpleObjectProperty<>(null);

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
        mSplitTransactionsTableView.setItems(FXCollections.observableArrayList(st ->
                new Observable[]{ st.getAmountProperty()})); // expose amount field
        for (SplitTransaction st : stList) {
            mSplitTransactionsTableView.getItems().add(new SplitTransaction(st.getID(), st.getCategoryID(),
                    st.getTagID(), st.getMemo(), st.getAmount(), st.getMatchID()));
        }
        mSplitTransactionsTableView.getItems().addListener((ListChangeListener<SplitTransaction>) change ->
                updateRemainingAmount());

        mCategoryTableColumn.setCellValueFactory(cd -> cd.getValue().getCategoryIDProperty());
        mCategoryTableColumn.setCellFactory(ComboBoxTableCell.forTableColumn(mCategoryIDComboBox.getConverter(),
                mCategoryIDComboBox.getItems()));

        mTagTableColumn.setCellValueFactory(cd -> cd.getValue().getTagIDProperty());
        mTagTableColumn.setCellFactory(ComboBoxTableCell.forTableColumn(mTagIDComboBox.getConverter(),
                mTagIDComboBox.getItems()));

        mMemoTableColumn.setCellValueFactory(cd -> cd.getValue().getMemoProperty());
        mMemoTableColumn.setCellFactory(TextFieldTableCell.forTableColumn());

        final Currency usd = Currency.getInstance("USD");
        mAmountTableColumn.setCellValueFactory(cd->cd.getValue().getAmountProperty());
        mAmountTableColumn.setCellFactory(cell -> new EditableTableCell<>(
                ConverterUtil.getCurrencyAmountStringConverterInstance(usd),
                c -> RegExUtil.getCurrencyInputRegEx(Currency.getInstance("USD"), true)
                        .matcher(c.getControlNewText()).matches() ? c : null));
        mAmountTableColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        // setup amount text field
        final TextFormatter<BigDecimal> textFormatter = new TextFormatter<>(
                ConverterUtil.getCurrencyAmountStringConverterInstance(usd), null,
                c -> RegExUtil.getCurrencyInputRegEx(usd, true).matcher(c.getControlNewText())
                        .matches() ? c : null);
        mAmountTextField.setTextFormatter(textFormatter);
        textFormatter.valueProperty().bindBidirectional(amountProperty);

        if (!message.isBlank()) {
            // added textarea to show message
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
        if (mNetAmount == null) {
            // we are not restricted by total amount
            amountProperty.set(BigDecimal.ZERO);
        } else {
            // remaining amount = -(netAmount + sum(st.Amount))
            amountProperty.set(mSplitTransactionsTableView.getItems().stream()
                    .map(SplitTransaction::getAmount).reduce(mNetAmount, BigDecimal::add).negate());
            mOKButton.setDisable(amountProperty.get().compareTo(BigDecimal.ZERO) != 0);
            mAddButton.setDisable(amountProperty.get().compareTo(BigDecimal.ZERO) == 0);
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
                amountProperty.get(), -1));
        mMemoTextField.setText("");
    }

    @FXML
    private void handleDelete() {
        SplitTransaction st = mSplitTransactionsTableView.getSelectionModel().getSelectedItem();
        mSplitTransactionsTableView.getItems().remove(st);
    }
}
