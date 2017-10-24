/*
 * Copyright (C) 2017.  Guangliang He.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of FaCai168.
 *
 * FaCai168 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any
 * later version.
 *
 * FaCai168 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.taihuapp.facai168;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Created by ghe on 6/1/16.
 *
 */
class Price {

    private ObjectProperty<LocalDate> mDateProperty;
    private ObjectProperty<BigDecimal> mPriceProperty;

    Price(LocalDate d, BigDecimal p) {
        mDateProperty = new SimpleObjectProperty<>(d);
        mPriceProperty = new SimpleObjectProperty<>(p);
    }

    ObjectProperty<LocalDate> getDateProperty() { return mDateProperty; }
    LocalDate getDate() { return getDateProperty().get(); }
    void setDate(LocalDate d) { getDateProperty().set(d); }

    ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }
    BigDecimal getPrice() { return getPriceProperty().get(); }
    void setPrice(BigDecimal p) { getPriceProperty().set(p); }
}
