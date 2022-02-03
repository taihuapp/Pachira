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
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

// A class for security holding
public class SecurityHoldingNew implements LotView {

    static final String CASH = "CASH";
    static final String TOTAL = "TOTAL";
    private final int decimalScale; // number of decimal places to use
    private final String securityName;
    private static final Logger logger = Logger.getLogger(SecurityHoldingNew.class);

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
    SecurityHoldingNew(final String n, int scale) {
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
        }

        // bind pnlProperty
        pnlProperty.bind(Bindings.createObjectBinding(() -> getMarketValue().subtract(getCostBasis()),
                getMarketValueProperty(), getCostBasisProperty()));
    }

    private void adjustStockSplit(final BigDecimal newQ, final BigDecimal oldQ) {
        for (SecurityLot lot : securityLotList) {
            final BigDecimal oldLotQ = lot.getQuantity();
            final BigDecimal oldLotP = lot.getPrice();
            lot.setQuantity(oldLotQ.multiply(newQ).divide(oldQ, MainModel.QUANTITY_FRACTION_LEN, RoundingMode.HALF_UP));
            lot.setPrice(oldLotP.multiply(oldQ).divide(newQ, MainModel.QUANTITY_FRACTION_LEN, RoundingMode.HALF_UP));
        }
    }

    /**
     * Take an existing lot, and a tradedLot, match off
     * the quantity of these two lots can not be the same sign
     * the quantity of both lot is reduced by min(abs(lot.quantity), abs(tradedLot.quantity))
     * @param lot: an existing lot
     * @param tradedLot: a traded lot
     */
    private void matchLots(SecurityLot lot, SecurityLot tradedLot) {
        matchLots(lot, tradedLot, lot.getQuantity().abs().min(tradedLot.getQuantity().abs()));
    }

    /**
     * Take an existing lot, and a tradedLot, and a match quantity, match the two lots
     * the quantity of these two lot cannot be the same sign
     * the absolute value of the quantity of either lot should be no less than matchQuantity (positive)
     * @param lot: an existing lot
     * @param tradedLot: a traded lot
     * @param matchQuantity: the amount to match
     */
    private void matchLots(SecurityLot lot, SecurityLot tradedLot, BigDecimal matchQuantity) {
        // scale cost basis for lot
        final BigDecimal lotOldC = lot.getCostBasis();
        final BigDecimal tradedOldC = tradedLot.getCostBasis();
        final BigDecimal lotOldQ = lot.getQuantity();
        final BigDecimal tradedOldQ = tradedLot.getQuantity();

        final int lotOldQSign = lotOldQ.signum();
        if (lotOldQSign == 0) {
            // the lot has a cost basis but zero quantity, we just empty the cost basis
            // for the lot and return.
            lot.setCostBasis(BigDecimal.ZERO);
            return;
        }
        final BigDecimal lotNewQ;
        final BigDecimal tradedNewQ;

        if (lotOldQSign > 0) {
            lotNewQ = lotOldQ.subtract(matchQuantity);
            tradedNewQ = tradedOldQ.add(matchQuantity);
        } else {
            // lotOldSign < 0
            lotNewQ = lotOldQ.add(matchQuantity);
            tradedNewQ = tradedOldQ.subtract(matchQuantity);
        }

        lot.setCostBasis(scaleCostBasis(lotOldC, lotOldQ, lotNewQ));
        lot.setQuantity(lotNewQ);
        tradedLot.setCostBasis(scaleCostBasis(tradedOldC, tradedOldQ, tradedNewQ));
        tradedLot.setQuantity(tradedNewQ);
    }

    // assume the security name of the transaction matches securityName
    void processTransaction(final Transaction t, final List<SecurityHolding.MatchInfo> matchInfoList) {
        // handle stock split
        if (t.getTradeAction() == Transaction.TradeAction.STKSPLIT) {
            adjustStockSplit(t.getQuantity(), t.getOldQuantity());
            return;
        }

        if (!Transaction.hasQuantity(t.getTradeAction()))
            return;  // non relevant transaction

        final BigDecimal tradedQuantity = t.getSignedQuantity();
        final BigDecimal tradedCostBasis = t.getCostBasis();
        if ((tradedCostBasis.compareTo(BigDecimal.ZERO) == 0) && (tradedQuantity.compareTo(BigDecimal.ZERO) == 0)) {
            // for long trade, zero cost basis can only occur if quantity is zero.
            // but for short sale,  cost basis is quantity*price + commission, the first term is negative
            // if both are zero, do nothing
            return;
        }

        // we process transactions in the order of TDate
        // we mark the trading lot using ADate if it is not null, otherwise, use TDate
        final SecurityLot tradedLot = new SecurityLot(t.getID(), t.getADate() != null ? t.getADate() : t.getTDate(),
                tradedQuantity, tradedCostBasis, t.getPrice(), decimalScale);

        if (tradedQuantity.signum()*getQuantity().signum() >= 0) {
            // either a new open trade, or adding to the same position
            securityLotList.add(tradedLot);
            if (!matchInfoList.isEmpty()) {
                // why matchInfoList isn't empty?
                logger.warn("Can't find offsetting lots for " + t.getTradeAction() + " " + tradedQuantity
                        + " shares of " + t.getSecurityName() + " on " + t.getTDate()
                        + " with transaction id = " + t.getID() + ", account id = " + t.getAccountID());
            }
            return;
        }

        // it's a closing trade, need to match lots
        if (matchInfoList.isEmpty()) {
            // empty matchInfoList, FIFO rule
            final Iterator<SecurityLot> securityLotIterator = securityLotList.iterator();
            while (securityLotIterator.hasNext()) {
                final SecurityLot securityLot = securityLotIterator.next();
                matchLots(securityLot, tradedLot);
                if (securityLot.getQuantity().compareTo(BigDecimal.ZERO) == 0)
                    securityLotIterator.remove();
                if (tradedLot.getQuantity().compareTo(BigDecimal.ZERO) == 0)
                    return; // we are done
            }
        } else {
            // use matchInfoList
            for (SecurityHolding.MatchInfo mi : matchInfoList) {
                Optional<SecurityLot> optionalSecurityLot = securityLotList.stream()
                        .filter(lot -> lot.getTransactionID() == mi.getMatchTransactionID()).findAny();
                if (optionalSecurityLot.isPresent()) {
                    matchLots(optionalSecurityLot.get(), tradedLot, mi.getMatchQuantity());
                } else {
                    logger.error("Missing matching transaction id = " + mi.getMatchTransactionID()
                            + " for transaction of " +  t.getTradeAction() + " " + tradedQuantity
                            + " shares of " + t.getSecurityName() + " on " + t.getTDate()
                            + " with transaction id = " + t.getID() + ", account id = " + t.getAccountID());
                }
            }
        }

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
            securityLotList.add(tradedLot);
        }
    }

    /**
     * return oldC*newQ/oldQ.  Obviously oldQ can not be zero.
     * @param oldC - old cost basis
     * @param oldQ - old quantity
     * @param newQ - new quantity
     * @return scaled cost basis
     */
    private BigDecimal scaleCostBasis(BigDecimal oldC, BigDecimal oldQ, BigDecimal newQ) {
        return oldC.multiply(newQ).divide(oldQ, decimalScale, RoundingMode.HALF_UP);
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

    @Override
    public ObjectProperty<BigDecimal> getRoRProperty() { return rorProperty; }

    @Override
    public ObjectProperty<BigDecimal> getCostBasisProperty() { return costBasisProperty; }
    public BigDecimal getCostBasis() { return getCostBasisProperty().get(); }
    void setCostBasis(BigDecimal c) { getCostBasisProperty().set(c); }
}
