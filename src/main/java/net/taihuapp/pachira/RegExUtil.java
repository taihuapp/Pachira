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

import java.text.DecimalFormatSymbols;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Pattern;

public class RegExUtil {
    // for decimal numbers up to 6 places after the decimal point
    static final Pattern INTEREST_RATE_REG_EX = Pattern.compile("^(0|[1-9]\\d*)?(\\.\\d{0,6})?$");
    // for positive integer or empty
    static final Pattern POSITIVE_INTEGER_REG_EX = Pattern.compile("^([1-9]+\\d*)?$");

    // for currency input under locale
    static Pattern getCurrencyInputRegEx(Currency currency, Locale locale) {
        final DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance(locale);
        final String pattern = String.format("^(0|[1-9][%s\\d]*)?(%s\\d{0,%d})?$",
                Pattern.quote(String.valueOf(decimalFormatSymbols.getGroupingSeparator())),
                Pattern.quote(String.valueOf(decimalFormatSymbols.getDecimalSeparator())),
                currency.getDefaultFractionDigits());
        return Pattern.compile(pattern);
    }
}
