/*
 * Copyright (C) 2017.  Guangliang He.  All Rights Reserved.
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Account {

    /*
     * Accounts are separated into four groups, and each groups are consisted with several
     * types
     *    Banking
     *        Checking
     *        Saving
     *        credit card
     *        cash
     *    Investing
     *        Brokerage
     *        IRA or Keogh
     *        401(k) or 403(b)
     *        529
     *    Property
     *        House
     *        Vehicle
     *        Other
     *    Debt
     *        loan
     *        Other liability (not a credit card)
     */

    // make sure the name is not longer than 10 characters
    // otherwise database change is needed
    enum Type { SPENDING, INVESTING, PROPERTY, DEBT }

    private final Type mType;

    private final IntegerProperty mID;
    private final StringProperty mName;
    private final StringProperty mDescription;
    private final ObjectProperty<BigDecimal> mCurrentBalance;
    private ObservableList<Transaction> mTransactionList = null;
    private final BooleanProperty mHiddenFlag = new SimpleBooleanProperty(false);
    private final IntegerProperty mDisplayOrder = new SimpleIntegerProperty(Integer.MAX_VALUE);
    private final ObservableList<Security> mCurrentSecurityList;
    // detailed constructor
    public Account(int id, Type type, String name, String description, Boolean hidden, Integer displayOrder,
                   BigDecimal balance) {
        mID = new SimpleIntegerProperty(id);
        mType = type;
        mName = new SimpleStringProperty(name);
        mDescription = new SimpleStringProperty(description);
        mCurrentBalance = new SimpleObjectProperty<>(balance);
        mHiddenFlag.set(hidden);
        mDisplayOrder.set(displayOrder);

        // sorted by Name
        mCurrentSecurityList = FXCollections.observableArrayList();
    }

    // getters and setters
    public Type getType() { return mType; }

    ObservableList<Security> getCurrentSecurityList() { return mCurrentSecurityList; }
    boolean hasSecurity(Security security) {
        for (Security s : getCurrentSecurityList()) {
            // test same ID instead of test s == security
            if (s.getID() == security.getID())
                return true;
        }
        return false;
    }

    private IntegerProperty getIDProperty() { return mID; }
    int getID() { return getIDProperty().get(); }
    void setID(int id) { getIDProperty().set(id); }

    BooleanProperty getHiddenFlagProperty() { return mHiddenFlag; }
    Boolean getHiddenFlag() { return getHiddenFlagProperty().get(); }
    void setHiddenFlag(boolean h) { getHiddenFlagProperty().set(h); }

    IntegerProperty getDisplayOrderProperty() { return mDisplayOrder; }
    Integer getDisplayOrder() { return getDisplayOrderProperty().get(); }
    void setDisplayOrder(int d) { mDisplayOrder.set(d); }

    StringProperty getNameProperty() { return mName; }
    String getName() { return mName.get(); }
    void setName(String name) { mName.set(name); }

    private StringProperty getDescriptionProperty() { return mDescription; }
    String getDescription() { return getDescriptionProperty().get(); }
    void setDescription(String d) { mDescription.set(d); }

    void setTransactionList(ObservableList<Transaction> tList) { mTransactionList = tList; }

    ObservableList<Transaction> getTransactionList() { return mTransactionList; }

    ObjectProperty<BigDecimal> getCurrentBalanceProperty() { return mCurrentBalance; }
    BigDecimal getCurrentBalance() { return getCurrentBalanceProperty().get(); }
    void setCurrentBalance(BigDecimal cb) { mCurrentBalance.set(cb); }

    // update balance field for each transaction for non INVESTING account
    // no-op for INVESTING accounts
    void updateTransactionListBalance() {
        BigDecimal b = new BigDecimal(0);
        boolean accountBalanceIsSet = false;
        for (Transaction t : getTransactionList()) {
            if ((getType() != Type.INVESTING) && !accountBalanceIsSet
                    && t.getTDateProperty().get().isAfter(LocalDate.now())) {
                // this is a future transaction.  if account current balance is not set
                // set it before process this future transaction
                setCurrentBalance(b);
                accountBalanceIsSet = true;
            }
            BigDecimal amount = t.getCashAmountProperty().get();
            if (amount != null) {
                b = b.add(amount);
                t.setBalance(b);
            }
        }
        // at the end of the list, if the account balance still not set, set it now.
        if (!accountBalanceIsSet && (getType() != Type.INVESTING))
            setCurrentBalance(b);
    }

    public String toString() {
        return "mID:" + mID.get() + ";mType:" +
                (mType == null ? "Null Type" : mType.name()) + ";mName:" +
                mName.get() + ";mDescription:" + mDescription.get();
    }
}
