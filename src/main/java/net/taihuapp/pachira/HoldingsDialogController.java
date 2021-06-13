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

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Pair;
import javafx.util.StringConverter;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class HoldingsDialogController {

    private static final Logger logger = Logger.getLogger(HoldingsDialogController.class);

    private MainModel mainModel;

    @FXML
    private AnchorPane mMainPane;
    @FXML
    private DatePicker mDatePicker;
    @FXML
    private TreeTableView<LotHolding> mSecurityHoldingTreeTableView;
    @FXML
    private TreeTableColumn<LotHolding, String> mNameColumn;
    @FXML
    private TreeTableColumn<LotHolding, BigDecimal> mPriceColumn;
    @FXML
    private TreeTableColumn<LotHolding, BigDecimal> mQuantityColumn;
    @FXML
    private TreeTableColumn<LotHolding, BigDecimal> mMarketValueColumn;
    @FXML
    private TreeTableColumn<LotHolding, BigDecimal> mCostBasisColumn;
    @FXML
    private TreeTableColumn<LotHolding, BigDecimal> mPNLColumn;
    @FXML
    private TreeTableColumn<LotHolding, BigDecimal> mPctReturnColumn;

    private ListChangeListener<Transaction> mTransactionListChangeListener = null;

    private void populateTreeTable() {
        try {
            mSecurityHoldingTreeTableView.setRoot(new TreeItem<>(new SecurityHolding("Root")));
            for (LotHolding l : mainModel.computeSecurityHoldings(mainModel.getCurrentAccount().getTransactionList(),
                    mDatePicker.getValue(), -1)) {
                TreeItem<LotHolding> t = new TreeItem<>(l);
                mSecurityHoldingTreeTableView.getRoot().getChildren().add(t);
                for (LotHolding l1 : ((SecurityHolding) l).getLotInfoList()) {
                    t.getChildren().add(new TreeItem<>(l1));
                }
            }

            // set initial sort order
            mNameColumn.setSortType(TreeTableColumn.SortType.ASCENDING);
            mSecurityHoldingTreeTableView.getSortOrder().add(mNameColumn);
        } catch (DaoException e) {
            final String msg = e.getErrorCode() + " DaoException";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog((Stage) mMainPane.getScene().getWindow(), e.getClass().getName(),
                    msg, e.toString(), e);
        }
    }

    void setMainModel(MainModel mainModel) {

        this.mainModel = mainModel;

        // javafx DatePicker aren't aware of edited the value in its TextField,
        // this is a work around
        DatePickerUtil.captureEditedDate(mDatePicker);

        mSecurityHoldingTreeTableView.setShowRoot(false);
        mSecurityHoldingTreeTableView.setSortMode(TreeSortMode.ONLY_FIRST_LEVEL);
        mSecurityHoldingTreeTableView.setEditable(true);

        mNameColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, String> p) ->
                new ReadOnlyStringWrapper(p.getValue().getValue().getLabel()));

        mPriceColumn.setCellValueFactory(p -> p.getValue().getValue().getPriceProperty());
        mPriceColumn.setComparator(null);

        mPriceColumn.setCellFactory(new Callback<>() {
            @Override
            public TreeTableCell<LotHolding, BigDecimal> call(TreeTableColumn<LotHolding,
                    BigDecimal> paramTreeTableColumn) {
                return new TextFieldTreeTableCell<>(new StringConverter<BigDecimal>() {
                    @Override
                    public String toString(BigDecimal object) {
                        if (object == null)
                            return null;
                        // format to 6 decimal places
                        DecimalFormat df = new DecimalFormat();
                        df.setMaximumFractionDigits(MainModel.PRICE_FRACTION_DISPLAY_LEN);
                        df.setMinimumFractionDigits(0);
                        return df.format(object);
                    }

                    @Override
                    public BigDecimal fromString(String string) {
                        BigDecimal result;
                        try {
                            result = new BigDecimal(string);
                        } catch (NumberFormatException | NullPointerException e) {
                            result = null;
                        }
                        return result;
                    }
                }) {
                    @Override
                    public void updateItem(BigDecimal item, boolean empty) {
                        TreeTableRow<LotHolding> treeTableRow = getTreeTableRow();
                        boolean isTotalOrCash = false;
                        if (treeTableRow != null) {
                            TreeItem<LotHolding> treeItem = treeTableRow.getTreeItem();
                            if (treeItem != null) {
                                final String label = treeItem.getValue().getLabel();
                                isTotalOrCash = label.equals("TOTAL") || label.equals("CASH");
                                setEditable(mSecurityHoldingTreeTableView.getTreeItemLevel(treeItem) <= 1
                                        && !isTotalOrCash); // it seems the setEditable need to be called again and again
                            }
                        }
                        if (isTotalOrCash)
                            super.updateItem(null, empty); // don't show price for TOTAL or CASH
                        else
                            super.updateItem(item, empty);
                    }
                };
            }
        });
        mPriceColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        mPriceColumn.setOnEditCommit(event -> {
            Security security = mainModel.getSecurity(s -> s.getName()
                    .equals(event.getRowValue().getValue().getSecurityName())).orElse(null);
            if (security == null)
                return;
            LocalDate date = mDatePicker.getValue();
            BigDecimal newPrice = event.getNewValue();
            if (newPrice == null || newPrice.signum() < 0) {
                DialogUtil.showWarningDialog((Stage) mSecurityHoldingTreeTableView.getScene().getWindow(),
                        "Warning!", "Bad input price, change discarded!",
                        ""
                                + "Security Name  : " + security.getName() + System.lineSeparator()
                                + "Security Ticker: " + security.getTicker() + System.lineSeparator()
                                + "Security ID    : " + security.getID() + System.lineSeparator()
                                + "Date           : " + date);
                return; // don't do anything
            }
            if (newPrice.signum() == 0) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirmation Dialog");
                alert.setHeaderText("Do you want to save zero price to database?");
                alert.setContentText(""
                        + "Security Name  : " + security.getName() + "\n"
                        + "Security Ticker: " + security.getTicker() + "\n"
                        + "Security ID    : " + security.getID() + "\n"
                        + "Date           : " + date + "\n"
                        + "Price          : " + newPrice + "?");
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK)
                    return; // don't save, go back
            }

            try {
                mainModel.mergeSecurityPrices(List.of(new Pair<>(security, new Price(date, newPrice))));
                populateTreeTable();
                mainModel.updateAccountBalance(a -> a.hasSecurity(security));
            } catch (DaoException e) {
                final String msg = "Failed to merge price: " + System.lineSeparator()
                        + "Security Name: " + security.getName() + System.lineSeparator()
                        + "Security Ticker: " + security.getTicker() + System.lineSeparator()
                        + "Security ID    : " + security.getID() + System.lineSeparator()
                        + "Date           : " + date + System.lineSeparator()
                        + "Price          : " + newPrice;
                logger.error(msg, e);
                DialogUtil.showExceptionDialog((Stage) mSecurityHoldingTreeTableView.getScene().getWindow(),
                        e.getClass().getName(), msg, e.toString(), e);
            }
        });

        Callback<TreeTableColumn<LotHolding, BigDecimal>, TreeTableCell<LotHolding, BigDecimal>> dollarCentsCF =
                new Callback<>() {
                    @Override
                    public TreeTableCell<LotHolding, BigDecimal> call(TreeTableColumn<LotHolding, BigDecimal> column) {
                        return new TreeTableCell<>() {
                            @Override
                            protected void updateItem(BigDecimal item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item == null || empty) {
                                    setText("");
                                } else {
                                    // format
                                    setText(MainModel.DOLLAR_CENT_FORMAT.format(item));
                                }
                                setStyle("-fx-alignment: CENTER-RIGHT;");
                            }
                        };
                    }
                };

        mQuantityColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getQuantity()));
        mQuantityColumn.setCellFactory(new Callback<>() {
            @Override
            public TreeTableCell<LotHolding, BigDecimal> call(TreeTableColumn<LotHolding, BigDecimal> param) {
                return new TreeTableCell<>() {
                    @Override
                    protected void updateItem(BigDecimal item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item == null || empty) {
                            setText("");
                        } else {
                            // format
                            DecimalFormat df = new DecimalFormat();
                            df.setMaximumFractionDigits(MainModel.QUANTITY_FRACTION_DISPLAY_LEN);
                            df.setMinimumFractionDigits(0);
                            setText(df.format(item));
                        }
                        setStyle("-fx-alignment: CENTER-RIGHT;");
                    }
                };
            }
        });
        mQuantityColumn.setComparator(null);

        mMarketValueColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getMarketValue()));
        mMarketValueColumn.setCellFactory(dollarCentsCF);
        mMarketValueColumn.setComparator(null);

        mCostBasisColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getCostBasis()));
        mCostBasisColumn.setCellFactory(dollarCentsCF);
        mCostBasisColumn.setComparator(null);

        mPNLColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getPNL()));
        mPNLColumn.setCellFactory(dollarCentsCF);
        mPNLColumn.setComparator(null);

        mPctReturnColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getPctRet()));
        mPctReturnColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        mPctReturnColumn.setComparator(null);

        mDatePicker.setOnAction(event -> populateTreeTable());
        mDatePicker.setValue(LocalDate.now());
        populateTreeTable();// setValue doesn't trigger an event, call update manually.

        // set a listener on TransactionList
        mTransactionListChangeListener = c -> populateTreeTable();
        mainModel.getCurrentAccount().getTransactionList().addListener(mTransactionListChangeListener);
    }

    void close() { mainModel.getCurrentAccount().getTransactionList().removeListener(mTransactionListChangeListener); }

    @FXML
    private void handleEnterTransaction() {
        final Stage stage = (Stage) mMainPane.getScene().getWindow();
        final Account account = mainModel.getCurrentAccount();
        List<Transaction.TradeAction> taList = account.getType().isGroup(Account.Type.Group.INVESTING) ?
                List.of(Transaction.TradeAction.values()) :
                List.of(Transaction.TradeAction.WITHDRAW, Transaction.TradeAction.DEPOSIT);
        try {
            DialogUtil.showEditTransactionDialog(mainModel, stage,null, List.of(account), account, taList);
            populateTreeTable();
        } catch (DaoException | IOException e) {
            final String msg = "showEditTransactionDialog throws " + e.getClass().getName();
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(stage, e.getClass().getName(), msg, e.toString(), e);
        }
    }
}
