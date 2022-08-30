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
import java.util.regex.Pattern;

public class RegExUtil {
    // for decimal numbers up to 6 places after the decimal point
    static final Pattern INTEREST_RATE_REG_EX = Pattern.compile("^(0|[1-9]\\d*)?(\\.\\d{0,6})?$");
    // for positive integer or empty
    static final Pattern POSITIVE_INTEGER_REG_EX = Pattern.compile("^([1-9]+\\d*)?$");
    // for non-negative integer
    static final Pattern NON_NEGATIVE_INTEGER_REG_EX = Pattern.compile("^(0|([1-9]+\\d*))?$");

    // this is used for price and quantity
    static Pattern getPriceQuantityInputRegEx() {
        // minus sign is not allowed in price and/or quantity field
        return getDecimalInputRegEx(ConverterUtil.PRICE_QUANTITY_FRACTION_DISPLAY_LEN, false);
    }

    // for currency input under locale
    static Pattern getCurrencyInputRegEx(Currency currency, boolean allowNegative) {
        return getDecimalInputRegEx(currency.getDefaultFractionDigits(), allowNegative);
    }

    private static Pattern getDecimalInputRegEx(int fractionDigits, boolean allowNegative) {
        final String unsignedRegEx = "(0|[1-9][%s\\d]*)?(%s\\d{0,%d})?";  // for unsigned decimal numbers
        final String regEx = allowNegative ? ("-?" + unsignedRegEx) : unsignedRegEx; // possible minus sign
        final DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
        return Pattern.compile(String.format("^" + regEx + "$",
                Pattern.quote(String.valueOf(decimalFormatSymbols.getGroupingSeparator())),
                Pattern.quote(String.valueOf(decimalFormatSymbols.getDecimalSeparator())),
                fractionDigits));
    }
}
