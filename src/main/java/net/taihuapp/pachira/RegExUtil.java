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

import java.util.regex.Pattern;

public class RegExUtil {
    // for decimal numbers with up to 2 places after the decimal point, also with ',' for 000 group
    static final Pattern DOLLAR_CENT_REG_EX = Pattern.compile("^(0|[1-9][,\\d]*)?(\\.\\d{0,2})?$");
    // for decimal numbers up to 6 places after the decimal point
    static final Pattern INTEREST_RATE_REG_EX = Pattern.compile("^(0|[1-9]\\d*)?(\\.\\d{0,6})?$");
    // for positive integer or empty
    static final Pattern POSITIVE_INTEGER_REG_EX = Pattern.compile("^([1-9]+\\d*)?$");
}
