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
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

// A class for security holding
public class SecurityHolding implements LotView {

    static final String CASH = "CASH";
    static final String TOTAL = "TOTAL";
    private final int decimalScale; // number of decimal places to use
    private final String securityName;
    private static final Logger logger = Logger.getLogger(SecurityHolding.class);

    private final ObservableList<SecurityLot> securityLotList;
    private final ObjectProperty<BigDecimal> priceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> quantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> rorProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> costBasisProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> marketValueProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> pnlProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);

    /**
     * Constructor for general security holdings
     * @param n  security name
     * @param scale number of decimal places for rounding
     */
    SecurityHolding(final String n, int scale) {
        securityName = n;
        decimalScale = scale;

        if (n.equals(CASH) || n.equals(TOTAL)) {
            securityLotList = FXCollections.emptyObservableList(); // create an immutable empty list
            quantityProperty.set(null); // we don't care about quantity and ror.
            rorProperty.set(null);
        } else {
            securityLotList = FXCollections.observableArrayList(lot ->
                    new Observable[]{lot.getCostBasisProperty(), lot.getQuantityProperty()});

            // bind cost basis property to the sum of cost basis of each lot in the list
            costBasisProperty.bind(Bindings.createObjectBinding(() ->
                    securityLotList.stream().map(SecurityLot::getCostBasis).reduce(BigDecimal.ZERO, BigDecimal::add),
                    securityLotList));

            // bind quantity property to the sum of quantities of each lot in the list
            quantityProperty.bind(Bindings.createObjectBinding(() ->
                    securityLotList.stream().map(SecurityLot::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add),
                    securityLotList));

            // bind market value property
            marketValueProperty.bind(Bindings.createObjectBinding(() ->
                            getQuantity().multiply(getPrice()).setScale(decimalScale, RoundingMode.HALF_UP),
                    getQuantityProperty(), getPriceProperty()));

            // bind rate of return property
            rorProperty.bind(Bindings.createObjectBinding(() -> {
                final BigDecimal costBasis = getCostBasis();
                if (costBasis.signum() == 0)
                    return null;
                return BigDecimal.valueOf(100).multiply(getPnL())
                        .divide(costBasis.abs(), 2, RoundingMode.HALF_UP);
            }, pnlProperty, costBasisProperty));
        }

        // bind pnlProperty, if not cash
        if (n.equals(CASH))
            pnlProperty.set(null);
        else
            pnlProperty.bind(Bindings.createObjectBinding(() -> getMarketValue().subtract(getCostBasis()),
                    getMarketValueProperty(), getCostBasisProperty()));
    }

    /**
     * adjust stock split for each lot, and attribute the rounding error to the lot
     * with most quantity.
     * @param newQ - numerator for the split ratio
     * @param oldQ - denominator for the split ratio
     */
    private void adjustStockSplit(final BigDecimal newQ, final BigDecimal oldQ) {
        final BigDecimal oldHoldingQ = getQuantity();
        final BigDecimal newHoldingQ = oldHoldingQ.multiply(newQ).divide(oldQ, MainModel.PRICE_QUANTITY_FRACTION_LEN,
                RoundingMode.HALF_UP);
        SecurityLot maxLot = null; // we need max lot in case have rounding error to add
        for (SecurityLot lot : securityLotList) {
            final BigDecimal oldLotQ = lot.getQuantity();
            final BigDecimal oldLotP = lot.getPrice();
            lot.setQuantity(oldLotQ.multiply(newQ)
                    .divide(oldQ, MainModel.PRICE_QUANTITY_FRACTION_LEN, RoundingMode.HALF_UP));
            lot.setPrice(oldLotP.multiply(oldQ)
                    .divide(newQ, MainModel.PRICE_QUANTITY_FRACTION_LEN, RoundingMode.HALF_UP));
            if (maxLot == null || maxLot.getQuantity().abs().compareTo(lot.getQuantity().abs()) < 0) {
                // update maxLot if maxLot quantity is smaller than lot quantity
                maxLot = lot;
            }
        }
        BigDecimal diff = newHoldingQ.subtract(getQuantity());
        if ((maxLot != null) && (diff.signum() != 0)) {
            // there is rounding issues
            maxLot.setQuantity(maxLot.getQuantity().add(diff));
        }
    }

    /**
     * add a lot to the list in the order of lot date
     * so the security lot list is sorted in ascending order of lot date
     * @param lot: a lot to be added
     */
    private void addLot(final SecurityLot lot) {
        for (int index = securityLotList.size(); index > 0; index--) {
            if (!securityLotList.get(index-1).getDate().isAfter(lot.getDate())) {
                securityLotList.add(index, lot);
                return;
            }
        }
        securityLotList.add(0, lot);
    }

    // assume the security name of the transaction matches securityName
    // for transaction of trade action being SELL or CVTSHRT, return the matched (either via MatchInfo or FIFO)
    // SpecifyLotInfo list.
    List<SpecifyLotInfo> processTransaction(final Transaction t, final List<MatchInfo> matchInfoList) {
        final List<SpecifyLotInfo> specifyLotInfoList = new ArrayList<>();
        // handle stock split
        if (t.getTradeAction() == Transaction.TradeAction.STKSPLIT) {
            adjustStockSplit(t.getQuantity(), t.getOldQuantity());
            return specifyLotInfoList;
        }

        if (!Transaction.hasQuantity(t.getTradeAction()))
            return specifyLotInfoList;  // non relevant transaction

        final BigDecimal tradedQuantity = t.getSignedQuantity();
        final BigDecimal tradedCostBasis = t.getCostBasis();
        if ((tradedCostBasis.compareTo(BigDecimal.ZERO) == 0) && (tradedQuantity.compareTo(BigDecimal.ZERO) == 0)) {
            // for long trade, zero cost basis can only occur if quantity is zero.
            // but for short sale,  cost basis is quantity*price + commission, the first term is negative
            // if both are zero, do nothing
            return specifyLotInfoList;
        }

        // we process transactions in the order of TDate
        // we mark the trading lot using ADate if it is not null, otherwise, use TDate
        final SecurityLot tradedLot = new SecurityLot(t, decimalScale);
        if (tradedQuantity.signum()*getQuantity().signum() >= 0) {
            // either a new open trade, or adding to the same position
            // securityLotList.add(tradedLot);
            addLot(tradedLot);
            if (!matchInfoList.isEmpty()) {
                // why matchInfoList isn't empty?
                logger.warn("Can't find offsetting lots for " + t.getTradeAction() + " " + tradedQuantity
                        + " shares of " + t.getSecurityName() + " on " + t.getTDate()
                        + " with transaction id = " + t.getID() + ", account id = " + t.getAccountID());
            }
            return specifyLotInfoList;
        }

        // it's a closing trade, need to match lots
        final Map<Integer, MatchInfo> matchMap = new HashMap<>();
        if (!matchInfoList.isEmpty()) {
            // we have a matching info list
            for (MatchInfo mi : matchInfoList) {
                matchMap.put(mi.getMatchTransactionID(), mi);
            }
        }

        // now we can loop through matchLots, even if we have a match info list
        final Iterator<SecurityLot> matchLotIterator = securityLotList.iterator();
        while (matchLotIterator.hasNext()) {
            final SecurityLot securityLot = matchLotIterator.next();
            final BigDecimal matchQuantity;
            if (matchInfoList.isEmpty()) { // FIFO
                matchQuantity = securityLot.getQuantity().abs().min(tradedLot.getQuantity().abs());
            } else { // use MatchInfo
                final MatchInfo mi = matchMap.get(securityLot.getTransactionID());
                if (mi == null)
                    continue; // this one is not in the match
                else
                    matchQuantity = matchMap.get(securityLot.getTransactionID()).getMatchQuantity();
            }

            // we need to match.
            final SpecifyLotInfo specifyLotInfo =
                    new SpecifyLotInfo(securityLot);
            specifyLotInfo.updateSelectedShares(matchQuantity, tradedLot);
            specifyLotInfoList.add(specifyLotInfo);

            // update securityLot cost basis and quantity
            securityLot.setCostBasis(specifyLotInfo.getCostBasis());
            securityLot.setQuantity(specifyLotInfo.getQuantity());


            if (securityLot.getQuantity().compareTo(BigDecimal.ZERO) == 0)
                matchLotIterator.remove();
            if (tradedLot.getQuantity().compareTo(BigDecimal.ZERO) == 0)
                return specifyLotInfoList; // we are done
        }

        securityLotList.removeIf(lot -> (lot.getCostBasis().signum() == 0) && (lot.getQuantity().signum() == 0));

        if (tradedLot.getQuantity().compareTo(BigDecimal.ZERO) != 0) {
            if (tradedLot.getCostBasis().compareTo(BigDecimal.ZERO) != 0) {
                // something is wrong.
                logger.warn("Can't find enough offset for " + t.getTradeAction() + " " + t.getQuantity()
                        + " shares of " + t.getSecurityName() + " on " + t.getTDate()
                        + " with transaction id = " + t.getID() + ", account id = " + t.getAccountID());
            } else {
                logger.warn("Can't find enough offset for " + t.getTradeAction() + " " + t.getQuantity()
                        + " shares of " + t.getSecurityName() + " on " + t.getTDate()
                        + " with transaction id = " + t.getID() + ", account id = " + t.getAccountID()
                        + System.lineSeparator()
                        + "    Remaining quantity:   " + tradedLot.getQuantity() + System.lineSeparator()
                        + "    Remaining cost basis: " + tradedLot.getCostBasis() + System.lineSeparator());
            }
            //securityLotList.add(tradedLot);
            addLot(tradedLot);
        }
        return specifyLotInfoList;
    }

    String getSecurityName() { return securityName; }
    ObservableList<SecurityLot> getSecurityLotList() { return securityLotList; }

    @Override
    public String getLabel() { return getSecurityName(); }

    @Override
    public ObjectProperty<BigDecimal> getPriceProperty() { return priceProperty; }
    public BigDecimal getPrice() { return getPriceProperty().get(); }
    void setPrice(BigDecimal p) {
        getPriceProperty().set(p);
        securityLotList.forEach(l -> l.setMarketPrice(p));
    }

    @Override
    public ObjectProperty<BigDecimal> getQuantityProperty() { return quantityProperty; }
    public BigDecimal getQuantity() { return getQuantityProperty().get(); }

    @Override
    public ObjectProperty<BigDecimal> getMarketValueProperty() { return marketValueProperty; }
    public BigDecimal getMarketValue() { return getMarketValueProperty().get(); }
    void setMarketValue(BigDecimal m) { getMarketValueProperty().set(m); }

    @Override
    public ObjectProperty<BigDecimal> getPnLProperty() { return pnlProperty; }
    public BigDecimal getPnL() { return getPnLProperty().get(); }

    @Override
    public ObjectProperty<BigDecimal> getRoRProperty() { return rorProperty; }

    @Override
    public ObjectProperty<BigDecimal> getCostBasisProperty() { return costBasisProperty; }
    public BigDecimal getCostBasis() { return getCostBasisProperty().get(); }
    void setCostBasis(BigDecimal c) { getCostBasisProperty().set(c); }
}
