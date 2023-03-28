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
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class SpecifyLotsDialogController {

    private static final Logger mLogger = LogManager.getLogger(SpecifyLotsDialogController.class);

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
            sli.updateSelectedShares(BigDecimal.ZERO, new SecurityLot(mTransaction, sli.getScale()));
        }
        updateSelectedShares();
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

                boolean roundingOK = selectedQ.compareTo(mTransaction.getQuantity()) == 0;

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

        final DecimalFormat pqFormat = ConverterUtil.getPriceQuantityFormatInstance();
        final DecimalFormat dcFormat = ConverterUtil.getDollarCentFormatInstance();
        final BigDecimal totalAmt = mTransaction.getAmount();
        final String amountStr = dcFormat.format(totalAmt);
        final BigDecimal q = mTransaction.getQuantity();
        final String qStr = pqFormat.format(q);
        final BigDecimal costPerShare = totalAmt.divide(q, MainModel.PRICE_QUANTITY_FRACTION_LEN,
                RoundingMode.HALF_UP);
        final String cStr = pqFormat.format(costPerShare);
        final String securityName = mainModel.getSecurity(mTransaction.getSecurityID())
                .map(Security::getName).orElse("");
        if (t.getTradeAction().equals(Transaction.TradeAction.SELL)) {
            mMainLabel0.setText(qStr + " shares of " + securityName + " sold for " + amountStr
                    + " at " + cStr + " per share");
            mMainLabel1.setText("Please select share(s) to be sold");
        } else {
            // should be CVTSHRT
            mMainLabel0.setText(qStr + " shares of " + securityName + " bought for " + amountStr
                    + " at " + cStr + " per share");
            mMainLabel1.setText("Please select share(s) to be covered");
        }
        mTotalSharesLabel.setText(qStr);

        Account account = mainModel.getAccount(a -> a.getID() == t.getAccountID()).orElse(null);
        if (account == null) {
            mLogger.error("Invalid account ID " + t.getAccountID());
            return;
        }
        List<SecurityHolding> shList = mainModel.computeSecurityHoldings(account.getTransactionList(),
                t.getTDate(), t.getID());
        mSpecifyLotInfoList.clear(); // make sure nothing in the list
        for (SecurityHolding s : shList) {
            if (s.getSecurityName().equals(securityName)) {
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
                    sli.updateSelectedShares(mi.getMatchQuantity(), new SecurityLot(mTransaction, sli.getScale()));
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
                    BigDecimal matchQuantity = null;
                    if (sli.getSelectedShares() == null || (sli.getSelectedShares().signum() == 0)) {
                        // not selected, select as much as possible for this lot
                        matchQuantity = mTransaction.getQuantity().subtract(getSelectedShares());
                        if (matchQuantity.abs().compareTo(sli.getQuantity().abs()) > 0)
                            matchQuantity = sli.getQuantity();
                    }
                    sli.updateSelectedShares(matchQuantity, new SecurityLot(mTransaction, sli.getScale()));
                    updateSelectedShares();
                }
            });
            return row;
        });
        mDateColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getDate()));
        mTypeColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTradeAction()));
        mPriceColumn.setCellValueFactory(cellData->cellData.getValue().getPriceProperty());
        mPriceColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty)
                    setText("");
                else {
                    setText(ConverterUtil.getPriceQuantityStringConverterInstance().toString(item));
                }
            }
        });
        mPriceColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        mQuantityColumn.setCellValueFactory(cd -> Bindings.createObjectBinding(() -> {
            final SpecifyLotInfo sli = cd.getValue();
            final BigDecimal quantity = sli.getQuantity() == null ? BigDecimal.ZERO : sli.getQuantity();
            final BigDecimal selected = sli.getSelectedShares() == null ? BigDecimal.ZERO : sli.getSelectedShares();
            // quantity is signed, but selected is always non-negative
            return sli.getTradeAction() == Transaction.TradeAction.SHTSELL ?
                    quantity.subtract(selected).negate() : quantity.add(selected);
        }, cd.getValue().getQuantityProperty(), cd.getValue().getSelectedSharesProperty()));
        mQuantityColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty)
                    setText("");
                else {
                    setText(ConverterUtil.getPriceQuantityStringConverterInstance().toString(item));
                }
            }
        });
        mQuantityColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        mSelectedColumn.setCellValueFactory(cellData->cellData.getValue().getSelectedSharesProperty());
        mPNLColumn.setCellValueFactory(cellData->cellData.getValue().getRealizedPNLProperty());
        mPNLColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        mSelectedColumn.setCellFactory(cell -> new EditableTableCell<>(
                ConverterUtil.getPriceQuantityStringConverterInstance(),
                c -> RegExUtil.getPriceQuantityInputRegEx().matcher(c.getControlNewText()).matches() ? c : null));
        mSelectedColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        mSelectedColumn.setOnEditCommit(
                event -> {
                    BigDecimal ov = event.getOldValue();
                    if (ov == null)
                        ov = BigDecimal.ZERO;
                    BigDecimal nv = event.getNewValue();
                    if (nv == null)
                        nv = BigDecimal.ZERO;
                    SpecifyLotInfo sli = event.getRowValue();
                    final BigDecimal ava = sli.getQuantity().abs();
                    final BigDecimal total = ava.add(ov);
                    if (nv.compareTo(total) > 0)
                        nv = total; // exceeded total
                    if (sli.getTradeAction() == Transaction.TradeAction.SHTSELL)
                        nv = nv.negate(); // short sale carries a negative sign
                    sli.updateSelectedShares(nv, new SecurityLot(mTransaction, sli.getScale()));
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

        final DecimalFormat pqFormat = ConverterUtil.getPriceQuantityFormatInstance();
        final DecimalFormat dollarCentFormat = ConverterUtil.getDollarCentFormatInstance();
        mSelectedSharesLabel.setText(pqFormat.format(selected));
        mPNLLabel.setText(dollarCentFormat.format(realizedPNL));
        if (mTransaction.getQuantity() == null)
            mTransaction.setQuantity(BigDecimal.ZERO);

        mRemainingSharesLabel.setText(pqFormat.format(mTransaction.getQuantity().subtract(selected)));
    }
}
