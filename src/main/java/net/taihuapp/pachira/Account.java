/*
 * Copyright (C) 2018-2024.  Guangliang He.  All Rights Reserved.
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
import java.util.Arrays;
import java.util.Optional;

import static net.taihuapp.pachira.QIFUtil.EOL;
import static net.taihuapp.pachira.QIFUtil.EOR;

public class Account {

    /*
     * Accounts are separated into four groups, and each group are consisted with several
     * types
     *    Banking
     *        Checking
     *        Savings
     *        Credit Card
     *        Cash
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
     *        Loan
     *        Other liability (not a credit card)
     */

    // make sure the names are not longer than 16 characters, the type column in account table is varchar(16)
    public enum Type {
        CHECKING(Group.SPENDING), SAVINGS(Group.SPENDING), CREDIT_CARD(Group.SPENDING), CASH(Group.SPENDING),
        BROKERAGE(Group.INVESTING), IRA(Group.INVESTING), PLAN401K(Group.INVESTING), PLAN529(Group.INVESTING),
        HOUSE(Group.PROPERTY), VEHICLE(Group.PROPERTY), OTHER_ASSET(Group.PROPERTY),
        LOAN(Group.DEBT), OTHER_LIABILITY(Group.DEBT);

        public enum Group {
            SPENDING, INVESTING, PROPERTY, DEBT;
            @Override
            public String toString() {
                return name().charAt(0) + name().substring(1).toLowerCase();
            }
        }

        private final Group group;

        Type(Group g) {
            group = g;
        }

        Group getGroup() { return group; }
        public boolean isGroup(Group g) { return group.equals(g); }

        public String toString() {
            switch (this) {
                case IRA:
                    return "IRA/Keogh Plan";
                case PLAN401K:
                    return "401(k)/403(b)";
                case PLAN529:
                    return "529 Plan";
                case CHECKING:
                case SAVINGS:
                case CREDIT_CARD:
                case CASH:
                case BROKERAGE:
                case HOUSE:
                case VEHICLE:
                case OTHER_ASSET:
                case LOAN:
                case OTHER_LIABILITY:
                default:
                    return name().charAt(0) + name().substring(1).replace("_", " ").toLowerCase();
            }
        }

        String toQIF(boolean useAlt) {
            // when using alternative form, all types in INVESTING group output Invst
            if (useAlt && isGroup(Group.INVESTING))
                return "Invst";
            switch (this) {
                case CHECKING:
                case SAVINGS:
                    return "Bank";
                case CREDIT_CARD:
                    return "CCard";
                case CASH:
                    return "Cash";
                case BROKERAGE:
                case IRA:
                case PLAN529:
                    return "Port";
                case PLAN401K:
                    return "401(k)/403(b)";
                case HOUSE:
                case VEHICLE:
                case OTHER_ASSET:
                    return "Oth A";
                case LOAN:
                case OTHER_LIABILITY:
                    return "Oth L";
                default:
                    return toString();
            }
        }

        static Optional<Type> fromQIF(String QIFCode) {
            if (QIFCode.equals("Mutual") || QIFCode.equals("Invst"))
                return fromQIF("Port");
            return Arrays.stream(Type.values()).filter(t -> t.toQIF(false).equals(QIFCode)).findFirst();
        }
    }

    private final ObjectProperty<Type> mTypeProperty = new SimpleObjectProperty<>();

    private int mID;
    private final StringProperty mName;
    private final StringProperty mDescription;
    private final ObjectProperty<BigDecimal> mCurrentBalance;
    private final BooleanProperty mHiddenFlag = new SimpleBooleanProperty(false);
    private final ObjectProperty<Integer> mDisplayOrder = new SimpleObjectProperty<>(Integer.MAX_VALUE);
    private final ObjectProperty<LocalDate> mLastReconcileDateProperty = new SimpleObjectProperty<>(null);

    // detailed constructor
    public Account(int id, Type type, String name, String description, Boolean hidden, Integer displayOrder,
                   LocalDate lrDate, BigDecimal balance) {
        mID = id;
        mTypeProperty.set(type);
        mName = new SimpleStringProperty(name);
        mDescription = new SimpleStringProperty(description);
        mCurrentBalance = new SimpleObjectProperty<>(balance);
        mHiddenFlag.set(hidden);
        mDisplayOrder.set(displayOrder);
        mLastReconcileDateProperty.set(lrDate);
    }

    public String toQIF(boolean useAlt) {
        String qif = "N" + getName() + EOL
                + "T" + getType().toQIF(useAlt) + EOL;
        if (!getDescription().isEmpty())
            qif = qif + "D" + getDescription() + EOL;

        return qif + EOR + EOL;
    }

    // getters and setters
    public ObjectProperty<Type> getTypeProperty() { return mTypeProperty; }
    public Type getType() { return mTypeProperty.get(); }
    public void setType(Type type) { mTypeProperty.set(type); }

    public int getID() { return mID; }
    // AccountDao needs setID
    public void setID(int id) { mID = id; }

    public BooleanProperty getHiddenFlagProperty() { return mHiddenFlag; }
    public Boolean getHiddenFlag() { return getHiddenFlagProperty().get(); }
    void setHiddenFlag(boolean h) { getHiddenFlagProperty().set(h); }

    public ObjectProperty<Integer> getDisplayOrderProperty() { return mDisplayOrder; }
    public Integer getDisplayOrder() { return getDisplayOrderProperty().get(); }
    void setDisplayOrder(int d) { mDisplayOrder.set(d); }

    StringProperty getNameProperty() { return mName; }
    public String getName() { return mName.get(); }
    void setName(String name) { mName.set(name); }

    private StringProperty getDescriptionProperty() { return mDescription; }
    public String getDescription() { return getDescriptionProperty().get(); }
    void setDescription(String d) { mDescription.set(d); }

    public ObjectProperty<BigDecimal> getCurrentBalanceProperty() { return mCurrentBalance; }
    BigDecimal getCurrentBalance() { return getCurrentBalanceProperty().get(); }
    public void setCurrentBalance(BigDecimal cb) { mCurrentBalance.set(cb); }

    private ObjectProperty<LocalDate> getLastReconcileDateProperty() { return mLastReconcileDateProperty; }
    public LocalDate getLastReconcileDate() { return getLastReconcileDateProperty().get();  }
    void setLastReconcileDate(LocalDate d) { getLastReconcileDateProperty().set(d); }

    public String toString() {
        return "mID:" + mID + ";mType:" +
                (getType() == null ? "Null Type" : getType().name()) + ";mName:" +
                mName.get() + ";mDescription:" + mDescription.get();
    }
}
