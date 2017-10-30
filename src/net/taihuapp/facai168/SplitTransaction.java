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

import javafx.beans.property.*;

import java.math.BigDecimal;

/**
 * A class for split transactions
 *
 * Split transactions have a simpler structure than
 * a full blown transactions.  It is essentially a cash transaction
 * But split transactions use different conventions.  So we are
 * using a separate class for it.
 */

class SplitTransaction {
    private int mID;

    // positive for Category ID
    // negative for negative of Transfer Account ID
    private final IntegerProperty mCategoryIDProperty = new SimpleIntegerProperty(0);

    private final StringProperty mPayeeProperty = new SimpleStringProperty();
    private final StringProperty mMemoProperty = new SimpleStringProperty();

    // amount can be positive or negative
    // positive means cash into account, similar to XIN/DEPOSIT in Transaction class
    // negative means cash out of account, XOUT/WITHDRAW
    private final ObjectProperty<BigDecimal> mAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);  // this is amount

    private int mMatchID;  // the id of the transaction is matched up to this split transaction

    SplitTransaction(SplitTransaction st) {
        this(st.getID(), st.getCategoryID(), st.getPayee(), st.getMemo(), st.getAmount(), st.getMatchID());
    }

    SplitTransaction(int id, int cid, String payee, String memo, BigDecimal amount, int matchTid) {
        mID = id;
        mCategoryIDProperty.set(cid);
        mPayeeProperty.set(payee);
        mMemoProperty.set(memo);
        mAmountProperty.set(amount);
        mMatchID = matchTid;
    }

    int getID() { return mID; }
    IntegerProperty getCategoryIDProperty() { return mCategoryIDProperty; }
    Integer getCategoryID() { return getCategoryIDProperty().get(); }
    StringProperty getPayeeProperty() { return mPayeeProperty; }
    String getPayee() { return getPayeeProperty().get(); }
    StringProperty getMemoProperty() { return mMemoProperty; }
    String getMemo() { return getMemoProperty().get(); }
    ObjectProperty<BigDecimal> getAmountProperty() { return mAmountProperty; }
    BigDecimal getAmount() { return getAmountProperty().get(); }
    int getMatchID() { return mMatchID; }

    void setID(int id) { mID = id; }
    void setMatchID(int mid) { mMatchID = mid; }
    void setPayee(String payee) { getPayeeProperty().set(payee); }
    void setMemo(String memo) { getMemoProperty().set(memo); }
    void setAmount(BigDecimal amount) { getAmountProperty().set(amount); }

    boolean isTransfer(int exAid) {
        int cid = getCategoryID();

        return ((-cid > MainApp.MIN_ACCOUNT_ID) && (-cid != exAid));
    }

}
