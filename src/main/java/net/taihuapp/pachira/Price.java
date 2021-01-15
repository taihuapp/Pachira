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

import java.math.BigDecimal;
import java.time.LocalDate;

import static net.taihuapp.pachira.QIFUtil.*;

class Price {

    private final ObjectProperty<LocalDate> mDateProperty;
    private final ObjectProperty<BigDecimal> mPriceProperty;

    Price(LocalDate d, BigDecimal p) {
        mDateProperty = new SimpleObjectProperty<>(d);
        mPriceProperty = new SimpleObjectProperty<>(p);
    }

    ObjectProperty<LocalDate> getDateProperty() { return mDateProperty; }
    LocalDate getDate() { return getDateProperty().get(); }

    ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }
    BigDecimal getPrice() { return getPriceProperty().get(); }

    String toQIF(String ticker) {
        final String quote = "\"";
        final String comma = ",";
        return "!Type:Prices" + EOL
                + quote + ticker + quote + comma
                + getPrice() + comma
                + quote + formatDate(getDate()) + quote + EOL
                + EOR + EOL;
    }
}
