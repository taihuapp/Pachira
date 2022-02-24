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
import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SpecifyLotInfo extends SecurityLot {

    private final ObjectProperty<BigDecimal> mSelectedSharesProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mRealizedPNLProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> proceedsProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);

    // constructor
    SpecifyLotInfo(SecurityLot lot) {
        super(lot.getTransactionID(), lot.getTradeAction(), lot.getDate(),
                lot.getQuantity(), lot.getCostBasis(), lot.getPrice(), lot.getScale());
    }

    // getters
    ObjectProperty<BigDecimal> getSelectedSharesProperty() {
        return mSelectedSharesProperty;
    }

    ObjectProperty<BigDecimal> getRealizedPNLProperty() {
        return mRealizedPNLProperty;
    }

    BigDecimal getRealizedPNL() {
        return getRealizedPNLProperty().get();
    }

    BigDecimal getSelectedShares() {
        return getSelectedSharesProperty().get();
    }

    BigDecimal getProceeds() {
        return proceedsProperty.get();
    }

    boolean isShortTerm(LocalDate coverDate) {
        return coverDate.isBefore(getDate().plusYears(1));
    }

    void updateSelectedShares(BigDecimal s, SecurityLot tradedLot) {
        mSelectedSharesProperty.set(s);
        final BigDecimal tradedCostBasis = tradedLot.getCostBasis();
        getRealizedPNLProperty().set(SecurityLot.matchLots(this, tradedLot, s));
        // sell proceeds prorated to this lot is the difference of traded lot cost basis
        // before and after lot matching.
        proceedsProperty.set(tradedLot.getCostBasis().subtract(tradedCostBasis));
    }
}
