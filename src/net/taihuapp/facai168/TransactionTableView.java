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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A base class TableView for transactions.
 * This TableView is derived and used in multiple places.
 */

abstract class TransactionTableView extends TableView<Transaction> {

    protected static MainApp mMainApp;

    private TableColumn<Transaction, LocalDate> mTransactionDateColumn = new TableColumn<>("Date");
    private TableColumn<Transaction, String> mTransactionAccountColumn = new TableColumn<>("Account");
    private TableColumn<Transaction, Transaction.TradeAction> mTransactionTradeActionColumn = new TableColumn<>("Action");
    private TableColumn<Transaction, String> mTransactionSecurityNameColumn = new TableColumn<>("Security");
    private TableColumn<Transaction, String> mTransactionReferenceColumn = new TableColumn<>("Ck #");
    private TableColumn<Transaction, String> mTransactionPayeeColumn = new TableColumn<>("Payee");
    private TableColumn<Transaction, String> mTransactionMemoColumn = new TableColumn<>("Memo");
    private TableColumn<Transaction, String> mTransactionCategoryColumn = new TableColumn<>("Category");
    private TableColumn<Transaction, String> mTransactionDescriptionColumn = new TableColumn<>("Description");
    private TableColumn<Transaction, String> mTransactionTagColumn = new TableColumn<>("Tag");
    private TableColumn<Transaction, BigDecimal> mTransactionInvestAmountColumn = new TableColumn<>("Inv Amt");
    private TableColumn<Transaction, BigDecimal> mTransactionCashAmountColumn = new TableColumn<>("Cash Amt");
    private TableColumn<Transaction, BigDecimal> mTransactionPaymentColumn = new TableColumn<>("Payment");
    private TableColumn<Transaction, BigDecimal> mTransactionDepositColumn = new TableColumn<>("Deposit");
    private TableColumn<Transaction, BigDecimal> mTransactionBalanceColumn = new TableColumn<>("Balance");
    private TableColumn<Transaction, BigDecimal> mTransactionAmountColumn = new TableColumn<>("Amount");

    protected BooleanProperty mDateColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mAccountColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mTradeActionColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mSecurityNameColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mReferenceColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mPayeeColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mMemoColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mCategoryColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mDescriptionColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mTagColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mInvestmentAmountColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mCashAmountColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mPaymentColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mDepositColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mBalanceColumnVisibility = new SimpleBooleanProperty(true);
    protected BooleanProperty mAmountColumnVisibility = new SimpleBooleanProperty(true);

    // constructor
    TransactionTableView(MainApp mainApp) {
        mMainApp = mainApp;

        // add columns to TableView
        //setTableMenuButtonVisible(true);
        getColumns().addAll(
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
                mTransactionInvestAmountColumn,
                mTransactionCashAmountColumn,
                mTransactionPaymentColumn,
                mTransactionDepositColumn,
                mTransactionBalanceColumn,
                mTransactionAmountColumn
        );

        mDateColumnVisibility.bindBidirectional(mTransactionDateColumn.visibleProperty());
        mAccountColumnVisibility.bindBidirectional(mTransactionAccountColumn.visibleProperty());
        mTradeActionColumnVisibility.bindBidirectional(mTransactionTradeActionColumn.visibleProperty());
        mSecurityNameColumnVisibility.bindBidirectional(mTransactionSecurityNameColumn.visibleProperty());
        mReferenceColumnVisibility.bindBidirectional(mTransactionReferenceColumn.visibleProperty());
        mPayeeColumnVisibility.bindBidirectional(mTransactionPayeeColumn.visibleProperty());
        mMemoColumnVisibility.bindBidirectional(mTransactionMemoColumn.visibleProperty());
        mCategoryColumnVisibility.bindBidirectional(mTransactionCategoryColumn.visibleProperty());
        mDescriptionColumnVisibility.bindBidirectional(mTransactionDescriptionColumn.visibleProperty());
        mTagColumnVisibility.bindBidirectional(mTransactionTagColumn.visibleProperty());
        mInvestmentAmountColumnVisibility.bindBidirectional(mTransactionInvestAmountColumn.visibleProperty());
        mCashAmountColumnVisibility.bindBidirectional(mTransactionCashAmountColumn.visibleProperty());
        mPaymentColumnVisibility.bindBidirectional(mTransactionPaymentColumn.visibleProperty());
        mDepositColumnVisibility.bindBidirectional(mTransactionDepositColumn.visibleProperty());
        mBalanceColumnVisibility.bindBidirectional(mTransactionBalanceColumn.visibleProperty());
        mAmountColumnVisibility.bindBidirectional(mTransactionAmountColumn.visibleProperty());

        // set preferred width for each column
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
        mTransactionInvestAmountColumn.setPrefWidth(100);
        mTransactionCashAmountColumn.setPrefWidth(100);
        mTransactionPaymentColumn.setPrefWidth(100);
        mTransactionDepositColumn.setPrefWidth(100);
        mTransactionBalanceColumn.setPrefWidth(100);
        mTransactionAmountColumn.setPrefWidth(100);

        // binding columns to Transaction members
        mTransactionDateColumn.setCellValueFactory(cd -> cd.getValue().getTDateProperty());
        mTransactionAccountColumn.setCellValueFactory(cd -> {
            Account a = mMainApp.getAccountByID(cd.getValue().getAccountID());
            if (a == null)
                return new ReadOnlyStringWrapper(""); // no account found, blank it
            return a.getNameProperty();
        });
        mTransactionTradeActionColumn.setCellValueFactory(cd -> cd.getValue().getTradeActionProperty());
        mTransactionSecurityNameColumn.setCellValueFactory(cd -> cd.getValue().getSecurityNameProperty());
        mTransactionReferenceColumn.setCellValueFactory(cd -> cd.getValue().getReferenceProperty());
        mTransactionPayeeColumn.setCellValueFactory(cd -> cd.getValue().getPayeeProperty());
        mTransactionMemoColumn.setCellValueFactory(cd -> cd.getValue().getMemoProperty());
        mTransactionCategoryColumn.setCellValueFactory(cd -> {
            Transaction t = cd.getValue();
            if (t.getSplitTransactionList().size() > 0)
                return new ReadOnlyStringWrapper("--Split--");
            return new ReadOnlyStringWrapper(mMainApp.mapCategoryOrAccountIDToName(t.getCategoryID()));
        });
        mTransactionDescriptionColumn.setCellValueFactory(cd -> cd.getValue().getDescriptionProperty());
        mTransactionTagColumn.setCellValueFactory(cd -> {
            Tag tag = mMainApp.getTagByID(cd.getValue().getTagID());
            if (tag == null)
                return new ReadOnlyStringWrapper("");
            return tag.getNameProperty();
        });
        mTransactionInvestAmountColumn.setCellValueFactory(cd -> cd.getValue().getInvestAmountProperty());
        mTransactionCashAmountColumn.setCellValueFactory(cd -> cd.getValue().getCashAmountProperty());
        mTransactionPaymentColumn.setCellValueFactory(cd -> cd.getValue().getPaymentProperty());
        mTransactionDepositColumn.setCellValueFactory(cd -> cd.getValue().getDepositeProperty());
        mTransactionBalanceColumn.setCellValueFactory(cd -> cd.getValue().getBalanceProperty());
        mTransactionAmountColumn.setCellValueFactory(cd -> cd.getValue().getAmountProperty());

        // style adjustment
        mTransactionDateColumn.setStyle("-fx-alignment: CENTER;");
        mTransactionReferenceColumn.setStyle("-fx-alignment: CENTER;");

        Callback<TableColumn<Transaction, BigDecimal>, TableCell<Transaction, BigDecimal>> dollarCentsCF =
                new Callback<TableColumn<Transaction, BigDecimal>, TableCell<Transaction, BigDecimal>>() {
                    @Override
                    public TableCell<Transaction, BigDecimal> call(TableColumn<Transaction, BigDecimal> param) {
                        return new TableCell<Transaction, BigDecimal>() {
                            @Override
                            protected void updateItem(BigDecimal item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item == null || empty) {
                                    setText("");
                                } else {
                                    // format
                                    setText(item.signum() == 0 ? "" : MainApp.DOLLAR_CENT_FORMAT.format(item));
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
        mTransactionBalanceColumn.setCellFactory(dollarCentsCF);
        mTransactionAmountColumn.setCellFactory(dollarCentsCF);

        setVisibleColumns();
    }

    abstract void setVisibleColumns();
}
