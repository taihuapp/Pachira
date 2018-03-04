/*
 * Copyright (C) 2018.  Guangliang He.  All Rights Reserved.
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

public class CapitalGainItem {

    // we carry cost basis and proceeds information because possible rounding issues,
    // even though in theory, cost basis and proceeds can be calculated from matching quantity,
    // transaction quantity, and match transaction quantity.
    private Transaction mTransaction;
    private Transaction mMatchTransaction;
    private BigDecimal mQuantity;
    private BigDecimal mCostBasis;
    private BigDecimal mProceeds;

    CapitalGainItem(CapitalGainItem cgi) {
        mTransaction = cgi.mTransaction;
        mMatchTransaction = cgi.mMatchTransaction;
        mQuantity = cgi.mQuantity;
        mCostBasis = cgi.mCostBasis;
        mProceeds = cgi.mProceeds;
    }

    CapitalGainItem(Transaction t, Transaction mt, BigDecimal q, BigDecimal c, BigDecimal p) {
        mTransaction = t;
        mMatchTransaction = mt;
        mQuantity = q;
        mCostBasis = c;
        mProceeds = p;
    }

    public boolean isShortTerm() {
        return mMatchTransaction.getTDate().plusDays(1).plusYears(1).isAfter(mTransaction.getTDate());
    }

    public Transaction getMatchTransaction() {
        return mMatchTransaction;
    }

    public void setMatchTransaction(Transaction matchTransaction) {
        mMatchTransaction = matchTransaction;
    }

    public BigDecimal getQuantity() {
        return mQuantity;
    }

    public void setQuantity(BigDecimal quantity) {
        mQuantity = quantity;
    }

    public BigDecimal getCostBasis() {
        return mCostBasis;
    }

    public void setCostBasis(BigDecimal costBasis) {
        mCostBasis = costBasis;
    }

    public BigDecimal getProceeds() {
        return mProceeds;
    }

    public void setProceeds(BigDecimal proceeds) {
        mProceeds = proceeds;
    }

    public Transaction getTransaction() {
        return mTransaction;
    }

    public void setTransaction(Transaction transaction) {
        mTransaction = transaction;
    }
}
