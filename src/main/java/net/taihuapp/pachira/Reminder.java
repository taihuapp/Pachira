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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Reminder {

    public enum Type { PAYMENT, DEPOSIT, LOAN_PAYMENT }

    private int mID = -1;
    private final ObjectProperty<Type> mTypeProperty = new SimpleObjectProperty<>(Type.PAYMENT);
    private final StringProperty mPayeeProperty = new SimpleStringProperty("");
    private final ObjectProperty<BigDecimal> mAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<Integer> mEstimateCountProperty = new SimpleObjectProperty<>(0);
    private final ObjectProperty<Integer> mAccountIDProperty = new SimpleObjectProperty<>(0);
    private final ObjectProperty<Integer> mCategoryIDProperty = new SimpleObjectProperty<>(0);
    private final ObjectProperty<Integer> mTagIDProperty = new SimpleObjectProperty<>(0);
    private final StringProperty mMemoProperty = new SimpleStringProperty("");
    private final List<SplitTransaction> mSplitTransactionList = new ArrayList<>();
    private final DateSchedule mDateSchedule;
    private final ObjectProperty<Integer> alertDaysProperty = new SimpleObjectProperty<>(3);

    // default constructor
    public Reminder() {

        // default monthly schedule, starting today, no end, counting day of month forward.
        mDateSchedule = new DateSchedule(DateSchedule.BaseUnit.MONTH, 1, LocalDate.now(), null,
                true, true);
    }

    // copy constructor
    Reminder(Reminder r) {
        this(r.getID(), r.getType(), r.getPayee(), r.getAmount(), r.getEstimateCount(), r.getAccountID(),
                r.getCategoryID(), r.getTagID(), r.getMemo(), r.getAlertDays(), r.getDateSchedule(),
                r.getSplitTransactionList());
    }

    public Reminder(int id, Type type, String payee, BigDecimal amount, int estCnt, int accountID, int categoryID,
                    int tagID, String memo, int ad, DateSchedule ds, List<SplitTransaction> stList) {
        mID = id;
        mTypeProperty.set(type);
        mPayeeProperty.set(payee);
        mAmountProperty.set(amount);
        mEstimateCountProperty.set(estCnt);
        mAccountIDProperty.set(accountID);
        mCategoryIDProperty.set(categoryID);
        mTagIDProperty.set(tagID);
        mMemoProperty.set(memo);
        alertDaysProperty.set(ad);
        mDateSchedule = ds;

        for (SplitTransaction st : stList)
            mSplitTransactionList.add(new SplitTransaction(st));
    }

    public DateSchedule getDateSchedule() { return mDateSchedule; }

    public int getID() { return mID; }
    void setID(int id) { mID = id; }

    ObjectProperty<Type> getTypeProperty() { return mTypeProperty; }
    public Type getType() { return getTypeProperty().get(); }

    StringProperty getPayeeProperty() { return mPayeeProperty; }
    public String getPayee() { return getPayeeProperty().get(); }

    ObjectProperty<BigDecimal> getAmountProperty() { return mAmountProperty; }
    public BigDecimal getAmount() { return getAmountProperty().get(); }
    void setAmount(BigDecimal a) { getAmountProperty().set(a); }

    ObjectProperty<Integer> getEstimateCountProperty() { return mEstimateCountProperty; }
    public Integer getEstimateCount() { return getEstimateCountProperty().get(); }
    void setEstimateCount(int c) { getEstimateCountProperty().set(c); }

    ObjectProperty<Integer> getAccountIDProperty() { return mAccountIDProperty; }
    public Integer getAccountID() { return getAccountIDProperty().get(); }

    ObjectProperty<Integer> getCategoryIDProperty() { return mCategoryIDProperty; }
    public Integer getCategoryID() { return getCategoryIDProperty().get(); }

    ObjectProperty<Integer> getTagIDProperty() { return mTagIDProperty; }
    public Integer getTagID() { return getTagIDProperty().get(); }

    StringProperty getMemoProperty() { return mMemoProperty; }
    public String getMemo() { return getMemoProperty().get(); }

    ObjectProperty<Integer> getAlertDaysProperty() { return alertDaysProperty; }
    public Integer getAlertDays() { return getAlertDaysProperty().get(); }

    public List<SplitTransaction> getSplitTransactionList() { return mSplitTransactionList; }
    void setSplitTransactionList(List<SplitTransaction> stList) {
        mSplitTransactionList.clear();
        for (SplitTransaction st : stList)
            mSplitTransactionList.add(new SplitTransaction(st));
    }
}
