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

package net.taihuapp.pachira.net.taihuapp.pachira.dc;

public class AccountDC {
    private int mAccountID;
    private int mDCID;
    private String mRoutingNumber;  // encrypted
    private String mAccountNumber;  // encrypted

    public AccountDC(int accountID, int DCID, String rn, String an) {
        mAccountID = accountID;
        mDCID = DCID;
        mRoutingNumber = rn;
        mAccountNumber = an;
    }

    public int getAccountID() { return mAccountID; }
    public int getDCID() { return mDCID; }
    public String getRoutingNumber() { return mRoutingNumber; }
    public String getAccountNumber() { return mAccountNumber; }

    @Override
    public String toString() {
        return getAccountID() + ", " + getDCID() + ", " + getRoutingNumber() + ", " + getAccountNumber();
    }
}
