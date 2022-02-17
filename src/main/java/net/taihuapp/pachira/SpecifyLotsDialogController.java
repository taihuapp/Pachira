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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.util.converter.BigDecimalStringConverter;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class SpecifyLotsDialogController {

    private static final Logger mLogger = Logger.getLogger(SpecifyLotsDialogController.class);

    private static class SpecifyLotInfo extends SecurityLot {

        private final ObjectProperty<BigDecimal> mSelectedSharesProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
        private final ObjectProperty<BigDecimal> mRealizedPNLProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);

        // constructor
        SpecifyLotInfo(SecurityLot lot) {
            super(lot.getTransactionID(), lot.getTradeAction(), lot.getDate(),
                    lot.getQuantity(), lot.getCostBasis(), lot.getPrice(), lot.getScale());
        }

        // getters
        ObjectProperty<BigDecimal> getSelectedSharesProperty() { return mSelectedSharesProperty; }
        ObjectProperty<BigDecimal> getRealizedPNLProperty() { return mRealizedPNLProperty; }
        BigDecimal getRealizedPNL() { return getRealizedPNLProperty().get(); }
        BigDecimal getSelectedShares() { return getSelectedSharesProperty().get(); }

        // setters
        void setSelectedShares(BigDecimal s) { mSelectedSharesProperty.set(s); }

        // update realized pnl against a trade
        void updateRealizedPNL(Transaction t) {
            if (getSelectedShares() == null) {
                // nothing selected
                getRealizedPNLProperty().set(null);
                return;
            }

            // if t has zero quantity, there shouldn't be any cost basis,
            // simply skip
            if (t.getQuantity().signum() == 0)
                return;

            int scale = getCostBasis().scale();
            if (getQuantity().signum() == 0) {
                // this lot has zero quantity and some cost basis
                // use up all the cost basis
                getRealizedPNLProperty().set(getCostBasis().negate());
                return;
            }

            BigDecimal c0 = getCostBasis().multiply(getSelectedShares())
                    .divide(getQuantity().abs(), scale, RoundingMode.HALF_UP);
            // t.getQuantity() is always positive
            BigDecimal c1 = t.getCostBasis().multiply(getSelectedShares())
                    .divide(t.getQuantity(), scale, RoundingMode.HALF_UP);
            getRealizedPNLProperty().set(c1.add(c0).negate());
        }
    }

    private Transaction mTransaction;
    private List<MatchInfo> mMatchInfoList = null;
    private final ObservableList<SpecifyLotInfo> mSpecifyLotInfoList = FXCollections.observableArrayList();

    @FXML
    private Button mResetButton;
    @FXML
    private Button mOKButton;
    @FXML
    private Button mCancelButton;
    @FXML
    private Label mMainLabel0;
    @FXML
    private Label mMainLabel1;

    @FXML
    private TableView<SpecifyLotInfo> mLotInfoTableView;
    @FXML
    private TableColumn<SpecifyLotInfo, LocalDate> mDateColumn;
    @FXML
    private TableColumn<SpecifyLotInfo, Transaction.TradeAction> mTypeColumn;
    @FXML
    private TableColumn<SpecifyLotInfo, BigDecimal> mPriceColumn;
    @FXML
    private TableColumn<SpecifyLotInfo, BigDecimal> mQuantityColumn;
    @FXML
    private TableColumn<SpecifyLotInfo, BigDecimal> mSelectedColumn;
    @FXML
    private TableColumn<SpecifyLotInfo, BigDecimal> mPNLColumn;

    @FXML
    private Label mTotalSharesLabel;
    @FXML
    private Label mSelectedSharesLabel;
    @FXML
    private Label mRemainingSharesLabel;
    @FXML
    private Label mPNLLabel;

    @FXML
    private void handleReset() {
        for (SpecifyLotInfo sli : mSpecifyLotInfoList) {
            sli.setSelectedShares(BigDecimal.ZERO);
            sli.updateRealizedPNL(mTransaction);
        }
    }

    @FXML
    private void handleOK() {
        BigDecimal selectedQ = BigDecimal.ZERO;
        boolean selected = false;
        for (SpecifyLotInfo sli : mSpecifyLotInfoList) {
            if (sli.getSelectedShares() == null)
                continue; // not set yet, skip
            selected = true;
            if (sli.getSelectedShares().compareTo(BigDecimal.ZERO) != 0) {
                selectedQ = selectedQ.add(sli.getSelectedShares());
            }
        }

        if (selected) {
            if (selectedQ.compareTo(mTransaction.getQuantity()) != 0) {
                // selectedQ not match getQuantity
                // it could due to rounding
                boolean hasResidual = false;
                for (SpecifyLotInfo sli : mSpecifyLotInfoList) {
                    if (sli.getSelectedShares().compareTo(sli.getQuantity()) != 0) {
                        hasResidual = true;
                        break;
                    }
                }

                boolean roundingOK = selectedQ.subtract(mTransaction.getQuantity())
                        .setScale(MainModel.QUANTITY_FRACTION_LEN-1, RoundingMode.HALF_UP)
                        .compareTo(BigDecimal.ZERO) == 0;

                if (hasResidual && !roundingOK) {
                    // show warning dialog and go back
                    String header;
                    if (selectedQ.compareTo(mTransaction.getQuantity()) > 0)
                        header = "Selected too many shares";
                    else
                        header = "Selected too few shares";
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setHeaderText(header);
                    alert.setContentText("Selected number of shares should match traded shares");
                    alert.showAndWait();
                    return;
                }
            }

            mMatchInfoList.clear();
            for (SpecifyLotInfo sli : mSpecifyLotInfoList) {
                if (sli.getSelectedShares() == null || sli.getSelectedShares().compareTo(BigDecimal.ZERO) == 0)
                    continue;
                mMatchInfoList.add(new MatchInfo(sli.getTransactionID(), sli.getSelectedShares()));
            }
        }
        ((Stage) mLotInfoTableView.getScene().getWindow()).close();
    }

    @FXML
    private void handleCancel() { ((Stage) mLotInfoTableView.getScene().getWindow()).close(); }

    void setMainModel(MainModel mainModel, Transaction t, List<MatchInfo> matchInfoList)
            throws DaoException {
        mMatchInfoList = matchInfoList;  // a link point to the input list
        mTransaction = t;

        if (t.getTradeAction().equals(Transaction.TradeAction.SELL)) {
            mMainLabel0.setText("" + mTransaction.getQuantity() + " shares of "
                    + mTransaction.getSecurityName() + " sold at "
                    + mTransaction.getPrice() + " per share");
            mMainLabel1.setText("Please select share(s) to be sold");
        } else {
            // should be CVTSHRT
            mMainLabel0.setText("" + mTransaction.getQuantity() + " shares of "
                    + mTransaction.getSecurityName() + " bought at "
                    + mTransaction.getPrice() + " per share");
            mMainLabel1.setText("Please select share(s) to be covered");
        }
        mTotalSharesLabel.setText(""+mTransaction.getQuantity());

        Account account = mainModel.getAccount(a -> a.getID() == t.getAccountID()).orElse(null);
        if (account == null) {
            mLogger.error("Invalid account ID " + t.getAccountID());
            return;
        }
        List<SecurityHolding> shList = mainModel.computeSecurityHoldings(account.getTransactionList(),
                t.getTDate(), t.getID());
        mSpecifyLotInfoList.clear(); // make sure nothing in the list
        for (SecurityHolding s : shList) {
            if (s.getSecurityName().equals(mTransaction.getSecurityName())) {
                // we found the right security
                for (SecurityLot sl : s.getSecurityLotList()) {
                    mSpecifyLotInfoList.add(new SpecifyLotInfo(sl));
                }
                break;
            }
        }

        // pair off between mSpecifyLotInfoList and mMatchInfoList
        for (MatchInfo mi : mMatchInfoList) {
            for (SpecifyLotInfo sli : mSpecifyLotInfoList) {
                if (sli.getTransactionID() == mi.getMatchTransactionID()) {
                    sli.setSelectedShares(mi.getMatchQuantity());
                    sli.updateRealizedPNL(mTransaction);
                    break;
                }
            }
        }

        mLotInfoTableView.setEditable(true);
        mLotInfoTableView.setItems(mSpecifyLotInfoList);
        mLotInfoTableView.setRowFactory(tv -> {
            // double-click the row will select all available quantity for this row
            TableRow<SpecifyLotInfo> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if ((e.getClickCount() == 2) && (!row.isEmpty())) {
                    SpecifyLotInfo sli = row.getItem();
                    if (sli.getSelectedShares() == null || (sli.getSelectedShares().signum() == 0)) {
                        // not selected, select as much as possible for this lot
                        sli.setSelectedShares(mTransaction.getQuantity().subtract(getSelectedShares())
                                .min(sli.getQuantity()));
                    } else {
                        sli.setSelectedShares(null);
                    }
                    sli.updateRealizedPNL(t);
                    updateSelectedShares();
                }
            });
            return row;
        });
        mDateColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getDate()));
        mTypeColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTradeAction()));
        mPriceColumn.setCellValueFactory(cellData->cellData.getValue().getPriceProperty());
        mQuantityColumn.setCellValueFactory(cellData->new SimpleObjectProperty<>(cellData.getValue().getQuantity().abs()));
        mSelectedColumn.setCellValueFactory(cellData->cellData.getValue().getSelectedSharesProperty());
        mPNLColumn.setCellValueFactory(cellData->cellData.getValue().getRealizedPNLProperty());

        mSelectedColumn.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        mSelectedColumn.setOnEditCommit(
                event -> {
                    BigDecimal nv = event.getNewValue();
                    if (nv == null)
                        nv = BigDecimal.ZERO;
                    SpecifyLotInfo sli = event.getRowValue();
                    BigDecimal ava = sli.getQuantity();
                    if (nv.compareTo(ava) > 0)
                        nv = ava;
                    sli.setSelectedShares(nv);
                    sli.updateRealizedPNL(mTransaction);
                    updateSelectedShares();
                }
        );

        updateSelectedShares();
    }

    private BigDecimal getSelectedShares() {
        // Specify Lot Info quantity = null means not selected.
        return mSpecifyLotInfoList.stream().map(SpecifyLotInfo::getSelectedShares).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getRealizedPnL() {
        // Specify Lot Info quantity = null means not selected.
        return mSpecifyLotInfoList.stream().filter(sli -> sli.getSelectedShares() != null)
                .map(SpecifyLotInfo::getRealizedPNL).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Add up selected shares and P&L, update the display labels
    private void updateSelectedShares() {
        final BigDecimal selected = getSelectedShares();
        final BigDecimal realizedPNL = getRealizedPnL();

        mSelectedSharesLabel.setText("" + selected);
        mPNLLabel.setText("" + realizedPNL);
        if (mTransaction.getQuantity() == null)
            mTransaction.setQuantity(BigDecimal.ZERO);

        mRemainingSharesLabel.setText("" + (mTransaction.getQuantity().subtract(selected)));
    }
}
