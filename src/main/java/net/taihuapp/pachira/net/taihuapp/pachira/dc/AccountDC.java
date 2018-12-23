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

import java.util.Date;

public class AccountDC {
    private int mAccountID;
    private String mAccountType;
    private int mDCID;
    private String mRoutingNumber;  // encrypted
    private String mAccountNumber;  // encrypted
    private Date mLastDownloadDT;  // UTC Date time for last download

    public AccountDC(int accountID, String accountType, int DCID, String rn, String an, Date lddt) {
        mAccountID = accountID;
        mAccountType = accountType;
        mDCID = DCID;
        mRoutingNumber = rn;
        mAccountNumber = an;
        mLastDownloadDT = lddt;
    }

    public int getAccountID() { return mAccountID; }
    public String getAccountType() { return mAccountType; }
    public int getDCID() { return mDCID; }
    public String getRoutingNumber() { return mRoutingNumber; }
    public String getEncryptedAccountNumber() { return mAccountNumber; }
    public void setEncryptedAccountNumber(String s) { mAccountNumber = s; }
    public Date getLastDownloadDateTime() { return mLastDownloadDT; }

    public void setLastDownloadDateTime(Date ld) { mLastDownloadDT = ld; }

    @Override
    public String toString() {
        return getAccountID() + ", " + getAccountType() + ", " + getDCID() + ", " + getRoutingNumber()
                + ", " + getEncryptedAccountNumber();
    }
}
