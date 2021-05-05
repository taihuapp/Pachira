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
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

abstract class LotHolding {

    private final StringProperty mSecurityNameProperty = new SimpleStringProperty("");
    private final ObjectProperty<BigDecimal> mPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mMarketValueProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mCostBasisProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mPNLProperty = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> mPctRetProperty = new SimpleObjectProperty<>();

    // getters
    abstract String getLabel();
    ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }
    ObjectProperty<BigDecimal> getQuantityProperty() { return mQuantityProperty; }
    ObjectProperty<BigDecimal> getMarketValueProperty() { return mMarketValueProperty; }
    ObjectProperty<BigDecimal> getPNLProperty() { return mPNLProperty; }
    private ObjectProperty<BigDecimal> getPctRetProperty() { return mPctRetProperty; }
    public String getSecurityName() { return mSecurityNameProperty.get(); }
    public BigDecimal getPrice() { return mPriceProperty.get(); }
    public BigDecimal getQuantity() { return mQuantityProperty.get(); }
    public BigDecimal getCostBasis() { return mCostBasisProperty.get(); }
    public BigDecimal getMarketValue() { return mMarketValueProperty.get(); }
    public BigDecimal getPNL() { return mPNLProperty.get(); }
    public BigDecimal getPctRet() { return mPctRetProperty.get(); }

    // constructor
    LotHolding(String n) { mSecurityNameProperty.set(n); }

    // setters
    public void setPNL(BigDecimal pnl) { mPNLProperty.set(pnl); } // shouldn't PNL = market value - cost basis???
    public void setMarketValue(BigDecimal m) { mMarketValueProperty.set(m); }
    public void setQuantity(BigDecimal q) { mQuantityProperty.set(q); }
    public void setCostBasis(BigDecimal c) { mCostBasisProperty.set(c); }
    public void setPrice(BigDecimal p) {
        mPriceProperty.set(Objects.requireNonNullElse(p, BigDecimal.ZERO));
    }

    protected abstract void updateMarketValue(BigDecimal p); // update market value for price p

    // update PctRet, assume MarketValue, PNL, and cost basis are updated
    protected void updatePctRet() {
        BigDecimal c = getCostBasis();
        if (c.compareTo(BigDecimal.ZERO) != 0) {
            getPctRetProperty().set((new BigDecimal(100)).multiply(getPNL())
                    .divide(c.abs(), 2, RoundingMode.HALF_UP));
        } else {
            getPNLProperty().set(null);
        }
    }
}
