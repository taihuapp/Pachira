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
    // this is the traded price
    private final ObjectProperty<BigDecimal> tradedPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    // this is the market price
    private final ObjectProperty<BigDecimal> marketPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> quantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> marketValueProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> pnlProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> rorProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> costBasisProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);

    // d should be the acquired date
    SecurityLot(int tid, LocalDate d, BigDecimal q, BigDecimal c, BigDecimal p, int scale) {
        transactionID = tid;
        transactionDate = d;
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
        costBasisProperty.set(c);
    }

    private ObjectProperty<BigDecimal> getMarketPriceProperty() { return marketPriceProperty; }
    private BigDecimal getMarketPrice() { return getMarketPriceProperty().get(); }
    void setMarketPrice(BigDecimal p) { getMarketPriceProperty().set(p); }

    int getTransactionID() { return transactionID; }

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
    private BigDecimal getMarketValue() { return getMarketValueProperty().get(); }

    @Override
    public ObjectProperty<BigDecimal> getPnLProperty() { return pnlProperty; }

    @Override
    public ObjectProperty<BigDecimal> getRoRProperty() { return rorProperty; }

    @Override
    public ObjectProperty<BigDecimal> getCostBasisProperty() { return costBasisProperty; }
    public BigDecimal getCostBasis() { return getCostBasisProperty().get(); }
    public void setCostBasis(BigDecimal c) { getCostBasisProperty().set(c); }
}
