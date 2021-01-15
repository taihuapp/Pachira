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

import java.time.LocalDate;

public class QIFUtil {
    // end of line
    public static final String EOL = "\r\n";

    // end of record
    public static final String EOR = "^";

    // format date for QIF
    public static String formatDate(LocalDate date) {
        final int y = date.getYear();
        final int m = date.getMonthValue();
        final int d = date.getDayOfMonth();

        final String format = y >= 2000 ? "%1d/%2d'%2d" : "%1d/%2d/%2d";

        return String.format(format, m, d, y%100);
    }

    // no one should ever call this private constructor
    private QIFUtil() {
        throw new AssertionError();
    }
}
