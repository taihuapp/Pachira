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

import javafx.beans.property.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

class Reminder {

    enum Type { PAYMENT, DEPOSIT, TRANSFER }

    private int mID = -1;
    private final ObjectProperty<Type> mTypeProperty = new SimpleObjectProperty<>(Type.PAYMENT);
    private final StringProperty mPayeeProperty = new SimpleStringProperty("");
    private final ObjectProperty<BigDecimal> mAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final IntegerProperty mEstimateCountProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty mAccountIDProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty mCategoryIDProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty mTransferAccountIDProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty mTagIDProperty = new SimpleIntegerProperty(0);
    private final StringProperty mMemoProperty = new SimpleStringProperty("");
    private final List<SplitTransaction> mSplitTransactionList = new ArrayList<>();
    private DateSchedule mDateSchedule;

    // default constructor
    Reminder() {

        // default monthly schedule, starting today, no end, counting day of month forward.
        mDateSchedule = new DateSchedule(DateSchedule.BaseUnit.MONTH, 1, LocalDate.now(), null,
                3, true, true);
    }

    // copy constructor
    Reminder(Reminder r) {
        this(r.getID(), r.getType(), r.getPayee(), r.getAmount(), r.getEstimateCount(), r.getAccountID(),
                r.getCategoryID(), r.getTransferAccountID(), r.getTagID(), r.getMemo(), r.getDateSchedule(),
                r.getSplitTransactionList());
    }

    Reminder(int id, Type type, String payee, BigDecimal amount, int estCnt, int accountID, int categoryID,
             int transferAccountID, int tagID, String memo, DateSchedule ds,
             List<SplitTransaction> stList) {
        mID = id;
        mTypeProperty.set(type);
        mPayeeProperty.set(payee);
        mAmountProperty.set(amount);
        mEstimateCountProperty.set(estCnt);
        mAccountIDProperty.set(accountID);
        mCategoryIDProperty.set(categoryID);
        mTransferAccountIDProperty.set(transferAccountID);
        mTagIDProperty.set(tagID);
        mMemoProperty.set(memo);
        mDateSchedule = ds;

        for (SplitTransaction st : stList)
            mSplitTransactionList.add(new SplitTransaction(st));
    }

    DateSchedule getDateSchedule() { return mDateSchedule; }
    void setDateSchedule(DateSchedule ds) { mDateSchedule = ds; }

    int getID() { return mID; }
    void setID(int id) { mID = id; }

    ObjectProperty<Type> getTypeProperty() { return mTypeProperty; }
    Type getType() { return getTypeProperty().get(); }
    void setTyp(Type t) { getTypeProperty().set(t); }

    StringProperty getPayeeProperty() { return mPayeeProperty; }
    String getPayee() { return getPayeeProperty().get(); }
    void setPayee(String p) { getPayeeProperty().set(p); }

    ObjectProperty<BigDecimal> getAmountProperty() { return mAmountProperty; }
    BigDecimal getAmount() { return getAmountProperty().get(); }
    void setAmount(BigDecimal a) { getAmountProperty().set(a); }

    IntegerProperty getEstimateCountProperty() { return mEstimateCountProperty; }
    Integer getEstimateCount() { return getEstimateCountProperty().get(); }
    void setEstimateCount(int c) { getEstimateCountProperty().set(c); }

    IntegerProperty getAccountIDProperty() { return mAccountIDProperty; }
    Integer getAccountID() { return getAccountIDProperty().get(); }
    void setAccountID(int i) { getAccountIDProperty().set(i); }

    IntegerProperty getCategoryIDProperty() { return mCategoryIDProperty; }
    Integer getCategoryID() { return getCategoryIDProperty().get(); }
    void setCategoryID(int i) { getCategoryIDProperty().set(i); }

    IntegerProperty getTransferAccountIDProperty() { return mTransferAccountIDProperty; }
    Integer getTransferAccountID() { return getTransferAccountIDProperty().get(); }
    void setTransferAccountID(int i) { getTransferAccountIDProperty().set(i); }

    IntegerProperty getTagIDProperty() { return mTagIDProperty; }
    Integer getTagID() { return getTagIDProperty().get(); }
    void setTagID(int i) { getTagIDProperty().set(i); }

    StringProperty getMemoProperty() { return mMemoProperty; }
    String getMemo() { return getMemoProperty().get(); }
    void setMemo(String m) { getMemoProperty().set(m); }

    List<SplitTransaction> getSplitTransactionList() { return mSplitTransactionList; }
    void setSplitTransactionList(List<SplitTransaction> stList) {
        mSplitTransactionList.clear();
        for (SplitTransaction st : stList)
            mSplitTransactionList.add(new SplitTransaction(st));
    }
}
