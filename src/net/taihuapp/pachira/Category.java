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

import java.math.BigDecimal;
import java.util.List;

public class Category {

    private int mID;
    private final StringProperty mNameProperty;  // name of the category
    private final StringProperty mDescriptionProperty;  // description
    private final ObjectProperty<Boolean> mIsIncomeProperty; // income category flag
    // mTaxRefNum = -1 for non tax related
    //               0 for tax related but no valid ref num
    //              >0 actual tax ref number
    private final IntegerProperty mTaxRefNumProperty;  // Tax reference number (for tax-related items,
    private ObjectProperty<BigDecimal> mBudgetAmountProperty; // budget amount

    // constructor
    Category(int id, String name, String description, boolean isIncome, int taxRefNum) {
        mID = id;
        mNameProperty = new SimpleStringProperty(name);
        mDescriptionProperty = new SimpleStringProperty(description);
        mIsIncomeProperty = new SimpleObjectProperty<>(isIncome);
        mTaxRefNumProperty = new SimpleIntegerProperty(taxRefNum);

        mBudgetAmountProperty = new SimpleObjectProperty<>(null);
    }

    // copy Constructor
    Category(Category c) { this(c.getID(), c.getName(), c.getDescription(), c.getIsIncome(), c.getTaxRefNum()); }

    // default constructor
    public Category() {
        this(0, "", "", true, -1);
    }

    void setID(int id) { mID = id; }
    int getID() { return mID; }

    StringProperty getNameProperty() { return mNameProperty; }
    String getName() { return getNameProperty().get(); }
    void setName(String name) { getNameProperty().set(name); }

    StringProperty getDescriptionProperty() { return mDescriptionProperty; }
    String getDescription() { return getDescriptionProperty().get(); }
    void setDescription(String des) { getDescriptionProperty().set(des); }

    private ObjectProperty<Boolean> getIsIncomeProperty() { return mIsIncomeProperty; }
    Boolean getIsIncome() { return getIsIncomeProperty().get(); }
    void setIsIncome(boolean isIncome) { getIsIncomeProperty().set(isIncome); }

    private void setIsTaxRelated(boolean t) {
        if (t && (getTaxRefNum() < 0)) {
            setTaxRefNum(0); // set it to be tax related
        } else if (!t) {
            setTaxRefNum(-1); // set it to be non tax related
        }
    }
    boolean isTaxRelated() { return getTaxRefNum() >= 0; }

    private IntegerProperty getTaxRefNumProperty() { return mTaxRefNumProperty; }
    int getTaxRefNum() { return getTaxRefNumProperty().get(); }
    void setTaxRefNum(int r) { mTaxRefNumProperty.set(r); }

    private ObjectProperty<BigDecimal> getBudgetAmountProperty() { return mBudgetAmountProperty; }
    BigDecimal getBudgetAmount() { return getBudgetAmountProperty().get(); }
    void setBudgetAmount(BigDecimal b) { getBudgetAmountProperty().set(b); }

    static Category fromQIFLines(List<String> lines)  {
        Category category = new Category();
        for (String l : lines) {
            switch (l.charAt(0)) {
                case 'N':
                    category.setName(l.substring(1));
                    break;
                case 'D':
                    category.setDescription(l.substring(1));
                    break;
                case 'T':
                    category.setIsTaxRelated(true);
                    break;
                case 'R':
                    category.setTaxRefNum(Integer.parseInt(l.substring(1)));
                    break;
                case 'I':
                    category.setIsIncome(true);
                    break;
                case 'E':
                    category.setIsIncome(false);
                    break;
                case 'B':
                    category.setBudgetAmount(new BigDecimal(l.substring(1).replace(",","")));
                    break;
                default:
                    return null;
            }
        }
        return category;
    }

    public String toString() { return "[" + getName() + "," + getDescription() + "]" ;}
}
