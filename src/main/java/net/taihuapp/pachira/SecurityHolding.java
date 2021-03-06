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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

import static net.taihuapp.pachira.Transaction.TradeAction.CVTSHRT;
import static net.taihuapp.pachira.Transaction.TradeAction.SELL;

public class SecurityHolding extends LotHolding {

    private static final Logger mLogger = Logger.getLogger(SecurityHolding.class);

    public final static int CURRENCYDECIMALLEN = 2;  // two place for cents

    public static class LotInfo extends LotHolding implements Comparable<LotInfo> {

        private final int mTransactionID;
        private final ObjectProperty<LocalDate> mDateProperty = new SimpleObjectProperty<>();
        private final ObjectProperty<Transaction.TradeAction> mTradeActionProperty = new SimpleObjectProperty<>();

        // copy constructor
        // how to chain constructor???
        public LotInfo(LotInfo li0) {
            super(li0.getSecurityName());
            mTransactionID = li0.getTransactionID();
            mDateProperty.set(li0.getDate());
            mTradeActionProperty.set(li0.getTradeAction());

            setQuantity(li0.getQuantity());
            setCostBasis(li0.getCostBasis());
            setPrice(li0.getPrice());
        }

        // constructor
        public LotInfo(int id, String n, Transaction.TradeAction ta, LocalDate date,
                       BigDecimal price, BigDecimal quantity, BigDecimal costBasis) {
            super(n);
            mTransactionID = id;
            mDateProperty.set(date);
            mTradeActionProperty.set(ta);

            setQuantity(quantity);
            setCostBasis(costBasis.setScale(CURRENCYDECIMALLEN, RoundingMode.HALF_UP));
            setPrice(price);  // this is the trade price
        }

        ObjectProperty<LocalDate> getDateProperty() { return mDateProperty; }
        LocalDate getDate() { return mDateProperty.get(); }
        int getTransactionID() { return mTransactionID; }
        ObjectProperty<Transaction.TradeAction> getTradeActionProperty() { return mTradeActionProperty; }
        Transaction.TradeAction getTradeAction() { return getTradeActionProperty().get(); }

        // compute market value and pnl
        @Override
        protected void updateMarketValue(BigDecimal p) {
            BigDecimal m = p.multiply(getQuantity()).setScale(CURRENCYDECIMALLEN, RoundingMode.HALF_UP);
            getMarketValueProperty().set(m);
            getPNLProperty().set(m.subtract(getCostBasis()));
        }

        @Override
        public String getLabel() { return getDate().toString(); }

        @Override
        public int compareTo(LotInfo l) {
            // a bit optimization here, if IDs are equal, then must be equal
            int idCompare = getTransactionID()-l.getTransactionID();
            if (idCompare == 0)
                return 0;

            // otherwise, order by date first, then by id within the same date
            int dateCompare = getDate().compareTo(l.getDate());
            if (dateCompare == 0)
                return idCompare;
            return dateCompare;
        }
    }

    public static class MatchInfo {
        private final int mMatchTransactionID;
        private final BigDecimal mMatchQuantity;  // always positive

        public MatchInfo(int mid, BigDecimal q) {
            mMatchTransactionID = mid;
            mMatchQuantity = q;
        }

        // getters
        public int getMatchTransactionID() { return mMatchTransactionID; }
        public BigDecimal getMatchQuantity() { return mMatchQuantity; }
    }

    private final ObservableList<LotInfo> mLotInfoList = FXCollections.observableArrayList();

    public SecurityHolding(String n) {
        super(n);
        //this(n, null);
    }

    // getters
    @Override
    public String getLabel() { return getSecurityName(); }

    private int getLotIndex(int tid) {
        for (int idx = 0; idx < mLotInfoList.size(); idx++) {
            if (mLotInfoList.get(idx).getTransactionID() == tid)
                return idx;
        }
        return -1;
    }

    ObservableList<LotInfo> getLotInfoList() { return mLotInfoList; }

    private BigDecimal scaleCostBasis(BigDecimal oldC, BigDecimal oldQ, BigDecimal newQ) {
        return oldC.multiply(newQ).divide(oldQ, CURRENCYDECIMALLEN, RoundingMode.HALF_UP);
    }

    // update market value and PNL
    @Override
    public void updateMarketValue(BigDecimal p) {
        setPrice(p);
        BigDecimal q = BigDecimal.ZERO;
        for (LotInfo li : getLotInfoList()) {
            li.updateMarketValue(p);
            q = q.add(li.getQuantity());
        }
        BigDecimal m = q.multiply(p).setScale(CURRENCYDECIMALLEN, RoundingMode.HALF_UP);
        getMarketValueProperty().set(m);
        getPNLProperty().set(m.subtract(getCostBasis()));
    }

    @Override
    public void updatePctRet() {
        super.updatePctRet();
        getLotInfoList().forEach(LotInfo::updatePctRet);
    }

    // add the lot and match off if necessary
    // if the lots are added with wrong order, the cost basis
    // calculation will be wrong
    public void addLot(LotInfo lotInfo, List<MatchInfo> matchInfoList) {
        BigDecimal oldQuantity = getQuantity();

        // update total quantity here
        BigDecimal lotInfoQuantity = lotInfo.getQuantity();
        if (lotInfoQuantity == null || lotInfoQuantity.signum() == 0) {
            // no change in quantity, check cost basis
            setCostBasis(getCostBasis().add(lotInfo.getCostBasis()));
            return;
        }

        setQuantity(oldQuantity.add(lotInfoQuantity));

        if (oldQuantity.signum() == 0 && (lotInfo.getTradeAction() == SELL || lotInfo.getTradeAction() == CVTSHRT)) {
            // this is a closing trade
            String indent = "    ";
            mLogger.error("*******\n"
                    + indent + "Transaction Date: " + lotInfo.getDate().toString() + "\n"
                    + indent + "Security Name: " + lotInfo.getSecurityName() + "\n"
                    + indent + "Transaction ID: " + lotInfo.getTransactionID() + "\n"
                    + indent + "Transaction Type: " + lotInfo.getTradeAction() + "\n"
                    + indent + "Quantity: " + lotInfoQuantity + "\n"
                    + indent + "Existing Quantity: " + oldQuantity + "\n"
                    + indent + "can't find enough lots to offset.  Something might be wrong, proceed with caution" + "\n"
                    + indent + "*******");
            getLotInfoList().add(lotInfo);
            setCostBasis(getCostBasis().add(lotInfo.getCostBasis()));
            return;
        }

        if ((oldQuantity.signum() >= 0) && (lotInfoQuantity.signum() > 0)
            || ((oldQuantity.signum() <= 0) && (lotInfo.getQuantity().signum() < 0))) {
            // same sign, nothing of offset
            if (matchInfoList.size() > 0) {
                mLogger.error("" + lotInfo.getTransactionID() + " can't find offset lots" );
            }
            getLotInfoList().add(lotInfo);
            setCostBasis(getCostBasis().add(lotInfo.getCostBasis()));
            return;
        }

        // lotInfo quantity has opposite sign as current holding, need to match lots
        if (lotInfoQuantity.abs().compareTo(oldQuantity.abs()) > 0) {
            // more than offset the current?  we shouldn't be here
            // probably something went wrong.  Issue a error message
            // and then match offset anyway
            String indent = "    ";
            mLogger.error("*******\n"
                    + indent + "Transaction Date: " + lotInfo.getDate().toString() + "\n"
                    + indent + "Security Name: " + lotInfo.getSecurityName() + "\n"
                    + indent + "Transaction ID: " + lotInfo.getTransactionID() + "\n"
                    + indent + "Transaction Type: " + lotInfo.getTradeAction() + "\n"
                    + indent + "Quantity: " + lotInfoQuantity + "\n"
                    + indent + "Existing Quantity: " + oldQuantity + "\n"
                    + indent + "can't find enough lots to offset.  Something might be wrong, proceed with caution" + "\n"
                    + indent + "*******");
        }

        if (matchInfoList.size() == 0) {
            // offset with fifo rule
            Iterator<LotInfo> iterator = getLotInfoList().iterator();
            while (iterator.hasNext()) {
                LotInfo li = iterator.next();
                if (li.getQuantity().abs().compareTo(lotInfo.getQuantity().abs()) > 0) {
                    // this lot in the list has more to offset the lotInfo
                    BigDecimal oldQ = li.getQuantity();
                    BigDecimal newQ = oldQ.add(lotInfo.getQuantity());
                    BigDecimal oldC = li.getCostBasis();
                    BigDecimal newC = scaleCostBasis(oldC, oldQ, newQ);
                    li.setCostBasis(newC);
                    li.setQuantity(newQ);
                    setCostBasis(getCostBasis().add(newC).subtract(oldC));
                    return;
                }

                // lotInfo may has more than li can offset
                BigDecimal oldC = lotInfo.getCostBasis();
                BigDecimal oldQ = lotInfo.getQuantity();
                BigDecimal newQ = oldQ.add(li.getQuantity());
                BigDecimal newC = scaleCostBasis(oldC, oldQ, newQ);

                lotInfo.setQuantity(newQ);
                lotInfo.setCostBasis(newC);

                setCostBasis(getCostBasis().subtract(li.getCostBasis()));

                iterator.remove();  // li has been offset, remove

                if (lotInfo.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    // nothing left to offset
                    return;
                }
            }
        }

        // offset against matchInfo
        for (MatchInfo matchInfo : matchInfoList) {
            int matchTID = matchInfo.getMatchTransactionID();
            int matchIndex =  getLotIndex(matchTID);
            if (matchIndex < 0) {
                mLogger.error("Missing matching transaction " + matchInfo.getMatchTransactionID());
                continue;
            }

            LotInfo matchLot = getLotInfoList().get(matchIndex);
            // getMatchQuantity() always none negative
            BigDecimal matchQ = matchInfo.getMatchQuantity();
            if (matchLot.getQuantity().abs().compareTo(matchQ) < 0 ) {
                // something is wrong here
                mLogger.error("Match lot " + matchTID + " q = " + matchLot.getQuantity()
                        + ", needed " + matchQ + " to match. Reset match Q.");
                matchQ = matchLot.getQuantity().abs();
            }

            BigDecimal oldQ = matchLot.getQuantity();
            BigDecimal newQ = oldQ.compareTo(BigDecimal.ZERO) > 0 ? oldQ.subtract(matchQ) : oldQ.add(matchQ);
            BigDecimal oldC = matchLot.getCostBasis();
            BigDecimal newC = scaleCostBasis(oldC, oldQ, newQ);

            if (newQ.compareTo(BigDecimal.ZERO) == 0) {
                getLotInfoList().remove(matchLot);
            } else {
                matchLot.setQuantity(newQ);
                matchLot.setCostBasis(newC);
            }

            setCostBasis(getCostBasis().add(newC).subtract(oldC));

            // update lotInfo Q and C
            oldQ = lotInfo.getQuantity();
            newQ = oldQ.compareTo(BigDecimal.ZERO) > 0 ? oldQ.subtract(matchQ) : oldQ.add(matchQ);
            oldC = lotInfo.getCostBasis();
            newC = scaleCostBasis(oldC, oldQ, newQ);
            lotInfo.setQuantity(newQ);
            lotInfo.setCostBasis(newC);
        }
    }

    public void adjustStockSplit(BigDecimal newQuantity, BigDecimal oldQuantity) {
        BigDecimal oldQTotal = BigDecimal.ZERO;
        for (LotInfo li : getLotInfoList()) {
            BigDecimal oldQ = li.getQuantity();
            BigDecimal oldP = li.getPrice();
            BigDecimal newQ = oldQ.multiply(newQuantity).divide(oldQuantity, MainApp.QUANTITY_FRACTION_LEN,
                    RoundingMode.HALF_UP);
            oldQTotal = oldQTotal.add(oldQ);
            li.setQuantity(newQ);
            li.setPrice(oldP.multiply(oldQuantity).divide(newQuantity, MainApp.QUANTITY_FRACTION_LEN,
                    RoundingMode.HALF_UP));
        }
        setQuantity(oldQTotal.multiply(newQuantity).divide(oldQuantity, MainApp.QUANTITY_FRACTION_LEN,
                RoundingMode.HALF_UP));
    }
}