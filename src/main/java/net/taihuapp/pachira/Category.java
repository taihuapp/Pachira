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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.math.BigDecimal;
import java.util.Objects;

import static net.taihuapp.pachira.QIFUtil.EOL;
import static net.taihuapp.pachira.QIFUtil.EOR;

public class Category {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        // return true if two string contents equal
        return mNameProperty.get().equals(((Category) o).mNameProperty.get());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mNameProperty.get());
    }

    private int mID;
    private final StringProperty mNameProperty;  // name of the category
    private final StringProperty mDescriptionProperty;  // description
    private final ObjectProperty<Boolean> mIsIncomeProperty; // income category flag
    // mTaxRefNum = -1 for non tax related
    //               0 for tax related but no valid ref num
    //              >0 actual tax ref number
    private final ObjectProperty<Integer> mTaxRefNumProperty;  // Tax reference number (for tax-related items,
    private final ObjectProperty<BigDecimal> mBudgetAmountProperty; // budget amount

    // constructor
    public Category(int id, String name, String description, boolean isIncome, int taxRefNum) {
        mID = id;
        mNameProperty = new SimpleStringProperty(name);
        mDescriptionProperty = new SimpleStringProperty(description);
        mIsIncomeProperty = new SimpleObjectProperty<>(isIncome);
        mTaxRefNumProperty = new SimpleObjectProperty<>(taxRefNum);

        mBudgetAmountProperty = new SimpleObjectProperty<>(null);
    }

    // copy Constructor
    @SuppressWarnings("CopyConstructorMissesField")
    Category(Category c) { this(c.getID(), c.getName(), c.getDescription(), c.getIsIncome(), c.getTaxRefNum()); }

    // default constructor
    public Category() {
        this(0, "", "", true, -1);
    }

    public void copy(Category category) {
        setID(category.getID());
        setName(category.getName());
        setDescription(category.getDescription());
        setIsIncome(category.getIsIncome());
        setTaxRefNum(category.getTaxRefNum());
        setBudgetAmount(category.getBudgetAmount());
    }

    void setID(int id) { mID = id; }
    public int getID() { return mID; }

    StringProperty getNameProperty() { return mNameProperty; }
    public String getName() { return getNameProperty().get(); }
    void setName(String name) { getNameProperty().set(name); }

    StringProperty getDescriptionProperty() { return mDescriptionProperty; }
    public String getDescription() { return getDescriptionProperty().get(); }
    void setDescription(String des) { getDescriptionProperty().set(des); }

    private ObjectProperty<Boolean> getIsIncomeProperty() { return mIsIncomeProperty; }
    public Boolean getIsIncome() { return getIsIncomeProperty().get(); }
    void setIsIncome(boolean isIncome) { getIsIncomeProperty().set(isIncome); }

    @SuppressWarnings("SameParameterValue")
    void setIsTaxRelated(boolean t) {
        if (t && (getTaxRefNum() < 0)) {
            setTaxRefNum(0); // set it to be tax related
        } else if (!t) {
            setTaxRefNum(-1); // set it to be non tax related
        }
    }

    boolean isTaxRelated() { return getTaxRefNum() >= 0; }

    private ObjectProperty<Integer> getTaxRefNumProperty() { return mTaxRefNumProperty; }
    public int getTaxRefNum() { return getTaxRefNumProperty().get(); }
    void setTaxRefNum(int r) { mTaxRefNumProperty.set(r); }

    private ObjectProperty<BigDecimal> getBudgetAmountProperty() { return mBudgetAmountProperty; }
    public BigDecimal getBudgetAmount() { return getBudgetAmountProperty().get(); }
    public void setBudgetAmount(BigDecimal b) { getBudgetAmountProperty().set(b); }

    public String toString() { return "[" + getName() + "," + getDescription() + "]" ;}

    String toQIF() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("N").append(getName()).append(EOL);
        if (!getDescription().isEmpty())
            stringBuilder.append("D").append(getDescription()).append(EOL);
        if (isTaxRelated()) {
            stringBuilder.append("T").append(EOL);
            if (getTaxRefNum() > 0)
                stringBuilder.append("R").append(getTaxRefNum()).append(EOL);
        }
        stringBuilder.append(getIsIncome() ? "I" : "E").append(EOL);
        if (getBudgetAmount() != null) {
            stringBuilder.append("B").append(getBudgetAmount()).append(EOL);
        }
        stringBuilder.append(EOR).append(EOL);

        return stringBuilder.toString();
    }
}
