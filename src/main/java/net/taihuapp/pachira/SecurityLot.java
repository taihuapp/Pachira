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

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class SecurityLot implements LotView {

    private final int decimalScale; // number of decimal places to use
    private final int transactionID;
    private final LocalDate transactionDate;
    private final Transaction.TradeAction tradeAction;
    // this is the traded price
    private final ObjectProperty<BigDecimal> tradedPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    // this is the market price
    private final ObjectProperty<BigDecimal> marketPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> quantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> marketValueProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> pnlProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> rorProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> costBasisProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);

    SecurityLot(Transaction t, int scale) {
        this(t.getID(), t.getTradeAction(), t.getADate() != null ? t.getADate() : t.getTDate(),
                t.getSignedQuantity(), t.getCostBasis(), t.getPrice(), scale);
    }

    // d should be the acquired date
    SecurityLot(int tid, Transaction.TradeAction ta, LocalDate d, BigDecimal q, BigDecimal c, BigDecimal p, int scale) {
        transactionID = tid;
        transactionDate = d;
        tradeAction = ta;
        decimalScale = scale;

        // bind some derived properties
        marketValueProperty.bind(Bindings.createObjectBinding(() ->
                        getMarketPrice().multiply(getQuantity()).setScale(decimalScale, RoundingMode.HALF_UP),
                marketPriceProperty, quantityProperty));
        pnlProperty.bind(Bindings.createObjectBinding(() ->
                getMarketValue().subtract(getCostBasis()), marketValueProperty, costBasisProperty));
        rorProperty.bind(Bindings.createObjectBinding(() -> {
            final BigDecimal costBasis = costBasisProperty.get();
            if (costBasis.compareTo(BigDecimal.ZERO) == 0)
                return null;
            return BigDecimal.valueOf(100).multiply(pnlProperty.get())
                    .divide(costBasis.abs(),2, RoundingMode.HALF_UP);  // always 2 decimal places for RoR
        }, pnlProperty, costBasisProperty));

        tradedPriceProperty.set(p);
        quantityProperty.set(q);
        costBasisProperty.set(c.setScale(decimalScale, RoundingMode.HALF_UP));
    }

    int getScale() { return decimalScale; }
    private ObjectProperty<BigDecimal> getMarketPriceProperty() { return marketPriceProperty; }
    private BigDecimal getMarketPrice() { return getMarketPriceProperty().get(); }
    void setMarketPrice(BigDecimal p) { getMarketPriceProperty().set(p); }

    int getTransactionID() { return transactionID; }
    LocalDate getDate() { return transactionDate; }
    Transaction.TradeAction getTradeAction() { return tradeAction; }

    @Override
    public String getLabel() { return transactionDate.toString(); }

    @Override
    public ObjectProperty<BigDecimal> getPriceProperty() { return tradedPriceProperty; }
    public BigDecimal getPrice() { return getPriceProperty().get(); }
    public void setPrice(BigDecimal p) {
        getPriceProperty().set(p); }

    @Override
    public ObjectProperty<BigDecimal> getQuantityProperty() { return quantityProperty; }
    public BigDecimal getQuantity() { return getQuantityProperty().get(); }
    public void setQuantity(BigDecimal q) { getQuantityProperty().set(q); }

    @Override
    public ObjectProperty<BigDecimal> getMarketValueProperty() { return marketValueProperty; }
    public BigDecimal getMarketValue() { return getMarketValueProperty().get(); }

    @Override
    public ObjectProperty<BigDecimal> getPnLProperty() { return pnlProperty; }
    public BigDecimal getPnL() { return getPnLProperty().get(); }

    @Override
    public ObjectProperty<BigDecimal> getRoRProperty() { return rorProperty; }

    @Override
    public ObjectProperty<BigDecimal> getCostBasisProperty() { return costBasisProperty; }
    public BigDecimal getCostBasis() { return getCostBasisProperty().get(); }
    public void setCostBasis(BigDecimal c) { getCostBasisProperty().set(c); }

    /**
     * Take an existing lot, and a tradedLot, and a match quantity, match the two lots
     * the quantity of these two lot cannot be the same sign
     * the absolute value of the quantity of either lot should be no less than matchQuantity (positive)
     * @param lot: an existing lot
     * @param tradedLot: a traded lot
     * @param matchQuantity: the amount to match
     * @return PnL or null of matchQuantity is null
     */
    static BigDecimal matchLots(SecurityLot lot, SecurityLot tradedLot, BigDecimal matchQuantity) {

        if (matchQuantity == null) {
            // we are not doing match, return null
            return null;
        }

        final BigDecimal lotOldC = lot.getCostBasis();
        final BigDecimal tradedOldC = tradedLot.getCostBasis();
        final BigDecimal lotOldQ = lot.getQuantity();
        final BigDecimal tradedOldQ = tradedLot.getQuantity();

        final int lotOldQSign = lotOldQ.signum();
        if (lotOldQSign == 0) {
            // the lot has a cost basis but zero quantity, we just empty the cost basis
            // for the lot and return.
            lot.setCostBasis(BigDecimal.ZERO);
            return lotOldC.negate();
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

        final BigDecimal lotNewC = scaleCostBasis(lotOldC, lotOldQ, lotNewQ);
        final BigDecimal tradedNewC = scaleCostBasis(tradedOldC, tradedOldQ, tradedNewQ);
        lot.setCostBasis(lotNewC);
        lot.setQuantity(lotNewQ);
        tradedLot.setCostBasis(tradedNewC);
        tradedLot.setQuantity(tradedNewQ);
        return lotOldC.subtract(lotNewC).add(tradedOldC).subtract(tradedNewC).negate();
    }

    /**
     * return oldC*newQ/oldQ.  Obviously oldQ can not be zero.
     * @param oldC - old cost basis
     * @param oldQ - old quantity
     * @param newQ - new quantity
     * @return scaled cost basis
     */
    private static BigDecimal scaleCostBasis(BigDecimal oldC, BigDecimal oldQ, BigDecimal newQ) {
        return oldC.multiply(newQ).divide(oldQ, oldC.scale(), RoundingMode.HALF_UP);
    }
}
