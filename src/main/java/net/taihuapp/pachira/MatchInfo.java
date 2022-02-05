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

import java.math.BigDecimal;

/**
 * a little help class for lot matching process
 */
public class MatchInfo {
    private final int matchTransactionID;
    private final BigDecimal matchQuantity;  // always positive

    public MatchInfo(int matchTid, BigDecimal q) {
        matchTransactionID = matchTid;
        matchQuantity = q;
    }

    // getters
    public int getMatchTransactionID() {
        return matchTransactionID;
    }

    public BigDecimal getMatchQuantity() {
        return matchQuantity;
    }
}
