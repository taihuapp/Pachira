/*
 * Copyright (C) 2018-2023.  Guangliang He.  All Rights Reserved.
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
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

/**
 * A base class TableView for transactions.
 * This TableView is derived and used in multiple places.
 */

class TransactionTableView extends TableView<Transaction> {

    private static final Logger logger = LogManager.getLogger(TransactionTableView.class);

    static final String CSS_FILE_NAME = "/css/TransactionTableView.css";

    MainModel mainModel;

    protected TableColumn<Transaction, Transaction.Status> mTransactionStatusColumn = new TableColumn<>("Clr");
    protected TableColumn<Transaction, LocalDate> mTransactionDateColumn = new TableColumn<>("Date");
    protected TableColumn<Transaction, String> mTransactionAccountColumn = new TableColumn<>("Account");
    protected TableColumn<Transaction, Transaction.TradeAction> mTransactionTradeActionColumn = new TableColumn<>("Action");
    protected TableColumn<Transaction, String> mTransactionSecurityNameColumn = new TableColumn<>("Security");
    protected TableColumn<Transaction, String> mTransactionReferenceColumn = new TableColumn<>("Ck #");
    protected TableColumn<Transaction, String> mTransactionPayeeColumn = new TableColumn<>("Payee");
    protected TableColumn<Transaction, String> mTransactionMemoColumn = new TableColumn<>("Memo");
    protected TableColumn<Transaction, String> mTransactionCategoryColumn = new TableColumn<>("Category");
    protected TableColumn<Transaction, String> mTransactionDescriptionColumn = new TableColumn<>("Description");
    protected TableColumn<Transaction, String> mTransactionTagColumn = new TableColumn<>("Tag");
    protected TableColumn<Transaction, BigDecimal> mTransactionQuantityColumn = new TableColumn<>("Quantity");
    protected TableColumn<Transaction, BigDecimal> mTransactionInvestAmountColumn = new TableColumn<>("Inv Amt");
    protected TableColumn<Transaction, BigDecimal> mTransactionCashAmountColumn = new TableColumn<>("Cash Amt");
    protected TableColumn<Transaction, BigDecimal> mTransactionPaymentColumn = new TableColumn<>("Payment");
    protected TableColumn<Transaction, BigDecimal> mTransactionDepositColumn = new TableColumn<>("Deposit");
    protected TableColumn<Transaction, BigDecimal> mTransactionBalanceColumn = new TableColumn<>("Balance");
    protected TableColumn<Transaction, BigDecimal> mTransactionAmountColumn = new TableColumn<>("Amount");

    // constructor
    TransactionTableView(MainModel m, ObservableList<Transaction> tList) {
        mainModel = m;

        // add columns to TableView
        //setTableMenuButtonVisible(true);
        getColumns().addAll(Arrays.asList(
                mTransactionStatusColumn,
                mTransactionDateColumn,
                mTransactionAccountColumn,
                mTransactionTradeActionColumn,
                mTransactionSecurityNameColumn,
                mTransactionReferenceColumn,
                mTransactionPayeeColumn,
                mTransactionMemoColumn,
                mTransactionCategoryColumn,
                mTransactionDescriptionColumn,
                mTransactionTagColumn,
                mTransactionQuantityColumn,
                mTransactionInvestAmountColumn,
                mTransactionCashAmountColumn,
                mTransactionPaymentColumn,
                mTransactionDepositColumn,
                mTransactionBalanceColumn,
                mTransactionAmountColumn
        ));

        // set preferred width for each column
        mTransactionStatusColumn.setPrefWidth(20);
        mTransactionDateColumn.setPrefWidth(85);
        mTransactionAccountColumn.setPrefWidth(100);
        mTransactionTradeActionColumn.setPrefWidth(75);
        mTransactionSecurityNameColumn.setPrefWidth(120);
        mTransactionReferenceColumn.setPrefWidth(60);
        mTransactionPayeeColumn.setPrefWidth(150);
        mTransactionMemoColumn.setPrefWidth(150);
        mTransactionCategoryColumn.setPrefWidth(150);
        mTransactionDescriptionColumn.setPrefWidth(150);
        mTransactionTagColumn.setPrefWidth(60);
        mTransactionQuantityColumn.setPrefWidth(100);
        mTransactionInvestAmountColumn.setPrefWidth(100);
        mTransactionCashAmountColumn.setPrefWidth(100);
        mTransactionPaymentColumn.setPrefWidth(100);
        mTransactionDepositColumn.setPrefWidth(100);
        mTransactionBalanceColumn.setPrefWidth(100);
        mTransactionAmountColumn.setPrefWidth(100);

        // binding columns to Transaction members
        mTransactionStatusColumn.setCellValueFactory(cd -> cd.getValue().getStatusProperty());
        mTransactionStatusColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Transaction.Status item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.valueOf(item.toChar()));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        mTransactionDateColumn.setCellValueFactory(cd -> cd.getValue().getTDateProperty());
        mTransactionAccountColumn.setCellValueFactory(cd ->
                mainModel.getAccount(a -> a.getID() == cd.getValue().getAccountID())
                        .map(Account::getNameProperty).orElse(new ReadOnlyStringWrapper("")));
        mTransactionTradeActionColumn.setCellValueFactory(cd -> cd.getValue().getTradeActionProperty());
        mTransactionSecurityNameColumn.setCellValueFactory(cd -> mainModel.getSecurity(cd.getValue().getSecurityID())
                    .map(Security::getNameProperty).orElse(new ReadOnlyStringWrapper("")));
        mTransactionReferenceColumn.setCellValueFactory(cd -> cd.getValue().getReferenceProperty());
        mTransactionPayeeColumn.setCellValueFactory(cd -> cd.getValue().getPayeeProperty());
        mTransactionMemoColumn.setCellValueFactory(cd -> cd.getValue().getMemoProperty());
        mTransactionCategoryColumn.setCellValueFactory(cd -> {
            Transaction t = cd.getValue();
            if (t.getSplitTransactionList().size() > 0)
                return new ReadOnlyStringWrapper("--Split--");
            final int categoryID = t.getCategoryID();
            final Optional<Category> categoryOptional = mainModel.getCategory(c -> c.getID() == categoryID);
            final Optional<Account> accountOptional = mainModel.getAccount(a -> a.getID() == -categoryID);
            if (categoryOptional.isPresent())
                return categoryOptional.get().getNameProperty();

            if (accountOptional.isPresent())
                return Bindings.concat("[", accountOptional.get().getNameProperty(), "]");

            return new ReadOnlyStringWrapper("");
        });
        mTransactionDescriptionColumn.setCellValueFactory(cd -> cd.getValue().getDescriptionProperty());
        mTransactionTagColumn.setCellValueFactory(cd -> mainModel.getTag(t -> t.getID() == cd.getValue().getTagID())
                .map(Tag::getNameProperty).orElse(new ReadOnlyStringWrapper("")));
        mTransactionQuantityColumn.setCellValueFactory(cd -> cd.getValue().getQuantityProperty());
        mTransactionInvestAmountColumn.setCellValueFactory(cd -> cd.getValue().getInvestAmountProperty());
        mTransactionCashAmountColumn.setCellValueFactory(cd -> cd.getValue().getCashAmountProperty());
        mTransactionPaymentColumn.setCellValueFactory(cd -> cd.getValue().getPaymentProperty());
        mTransactionDepositColumn.setCellValueFactory(cd -> cd.getValue().getDepositProperty());
        mTransactionBalanceColumn.setCellValueFactory(cd -> cd.getValue().getBalanceProperty());
        mTransactionAmountColumn.setCellValueFactory(cd -> cd.getValue().getAmountProperty());

        // style adjustment
        mTransactionDateColumn.setStyle("-fx-alignment: CENTER;");
        mTransactionReferenceColumn.setStyle("-fx-alignment: CENTER;");

        mTransactionQuantityColumn.setCellFactory(new Callback<>() {
            @Override
            public TableCell<Transaction, BigDecimal> call(TableColumn<Transaction, BigDecimal> param) {
                return new TableCell<>() {
                    @Override
                    protected void updateItem(BigDecimal item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item == null || empty) {
                            setText("");
                        } else {
                            // format
                            setText(ConverterUtil.getPriceQuantityFormatInstance().format(item));
                        }
                        setStyle("-fx-alignment: CENTER-RIGHT;");
                    }
                };
            }
        });

        final Callback<TableColumn<Transaction, BigDecimal>, TableCell<Transaction, BigDecimal>> dollarCentsCF =
                new Callback<>() {
                    @Override
                    public TableCell<Transaction, BigDecimal> call(TableColumn<Transaction, BigDecimal> param) {
                        return new TableCell<>() {
                            @Override
                            protected void updateItem(BigDecimal item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item == null || empty) {
                                    setText("");
                                } else {
                                    // format
                                    setText(item.signum() == 0 ?
                                            "" : ConverterUtil.getDollarCentFormatInstance().format(item));
                                }
                                setStyle("-fx-alignment: CENTER-RIGHT;");
                            }
                        };
                    }
                };
        mTransactionPaymentColumn.setCellFactory(dollarCentsCF);
        mTransactionDepositColumn.setCellFactory(dollarCentsCF);
        mTransactionInvestAmountColumn.setCellFactory(dollarCentsCF);
        mTransactionCashAmountColumn.setCellFactory(dollarCentsCF);
        mTransactionBalanceColumn.setCellFactory(cell -> new TableCell<>() {
                @Override
                protected void updateItem(BigDecimal item, boolean empty) {
                    super.updateItem(item, empty);

                    if (item == null || empty) {
                        setText("");
                    } else {
                        // format
                        setText(ConverterUtil.getDollarCentFormatInstance().format(item));
                    }
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
            }
        );
        mTransactionAmountColumn.setCellFactory(dollarCentsCF);

        final URL cssUrl = getClass().getResource(CSS_FILE_NAME);
        if (cssUrl != null) {
            getStylesheets().add(cssUrl.toExternalForm());

            // set row style to highlight future transactions and reconciled transactions
            setRowFactory(tv -> {
                final TableRow<Transaction> row = new TableRow<>();

                final PseudoClass reconciled = PseudoClass.getPseudoClass("reconciled");
                final PseudoClass future = PseudoClass.getPseudoClass("future");
                final ChangeListener<Transaction.Status> statusChangeListener = (obs, ov, nv) ->
                        row.pseudoClassStateChanged(reconciled, nv == Transaction.Status.RECONCILED);
                final ChangeListener<LocalDate> dateChangeListener = (obs, ov, nv) ->
                        row.pseudoClassStateChanged(future, nv.isAfter(LocalDate.now()));
                row.itemProperty().addListener((obs, ov, nv) -> {
                    if (nv != null) {
                        // we have a row to display
                        row.pseudoClassStateChanged(reconciled, nv.getStatus() == Transaction.Status.RECONCILED);
                        row.pseudoClassStateChanged(future, nv.getTDate().isAfter(LocalDate.now()));
                        nv.getStatusProperty().addListener(statusChangeListener);
                        nv.getTDateProperty().addListener(dateChangeListener);
                    } else {
                        row.pseudoClassStateChanged(reconciled, false);
                        row.pseudoClassStateChanged(future, false);
                    }
                    if (ov != null) {
                        ov.getStatusProperty().removeListener(statusChangeListener);
                        ov.getTDateProperty().removeListener(dateChangeListener);
                    }
                });
                return row;
            });
        } else {
            // failed to get css url, log and ignore.
            logger.error(getClass() + ".getResource(" + CSS_FILE_NAME + ") returns null");
        }

        SortedList<Transaction> sortedList = new SortedList<>(tList);
        setItems(sortedList);
        sortedList.comparatorProperty().bind(comparatorProperty());
    }

    void updateMainModel(MainModel m) {
        mainModel = m;
        setItems(new SortedList<>(FXCollections.observableArrayList()));
    }
}
