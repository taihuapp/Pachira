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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Security {
    enum Type {
        // make sure the name is not longer than 16 characters
        // or database structure needs to be changed.
        STOCK, BOND, MUTUALFUND, CD, INDEX, OTHER;

        public String toString() {
            switch (this) {
                case STOCK:
                    return "Stock";
                case BOND:
                    return "Bond";
                case MUTUALFUND:
                    return "Mutual Fund";
                case INDEX:
                    return "Market Index";
                case CD:
                    return "CD";
                default:
                    return "Other";
            }
        }

        static Type fromString(String t) {
            switch (t.toUpperCase()) {
                case "MUTUAL FUND":
                    return MUTUALFUND;
                case "MARKET INDEX":
                    return INDEX;
                default:
                    return Type.valueOf(t.toUpperCase());
            }
        }
    }

    // should "property" should be used?
    private int mID;
    private final StringProperty mTickerProperty;
    private final StringProperty mNameProperty;
    private ObjectProperty<Type> mTypeProperty;

    // default constructor
    public Security() {
        // 0 is not a legit security ID in database
        this(0, "", "", Type.STOCK);
    }

    public Security(int id, String ticker, String name, Type type) {
        mID = id;
        mTickerProperty = new SimpleStringProperty(ticker);
        mNameProperty = new SimpleStringProperty(name);
        mTypeProperty = new SimpleObjectProperty<>(type);
    }

    Security(Security s) {
        this(s.getID(), s.getTicker(), s.getName(), s.getType());
    }

    // getters and setters
    int getID() { return mID; }
    void setID(int id) { mID = id; }

    StringProperty getTickerProperty() { return mTickerProperty; }
    String getTicker() { return getTickerProperty().get(); }
    void setTicker(String ticker) { getTickerProperty().set(ticker); }

    StringProperty getNameProperty() { return mNameProperty; }
    String getName() { return getNameProperty().get(); }
    void setName(String name) { getNameProperty().set(name); }

    ObjectProperty<Type> getTypeProperty() { return mTypeProperty; }
    Type getType() { return getTypeProperty().get(); }
    void setType(Type type) { getTypeProperty().set(type); }

    public String toString() {
        return "ID = " + getID() + ", Name = '" + getName() + "', Ticker = '" + getTicker()
                + "', Type = '" + getType() + "'";
    }
}
