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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;

import static net.taihuapp.pachira.QIFUtil.EOL;
import static net.taihuapp.pachira.QIFUtil.EOR;

class Tag {
    private int mID;
    private final StringProperty mNameProperty;
    private final StringProperty mDescriptionProperty;

    Tag(int id, String name, String desc) {
        mID = id;
        mNameProperty = new SimpleStringProperty(name);
        mDescriptionProperty = new SimpleStringProperty(desc);
    }

    // default constructor
    Tag() {
        this(-1, "", "");
    }
    // copy constructor
    Tag(Tag tag) {
        this(tag.getID(), tag.getName(), tag.getDescription());
    }

    int getID() { return mID; }
    void setID(int i) { mID = i; }

    StringProperty getNameProperty() { return mNameProperty; }
    String getName() { return getNameProperty().get(); }
    void setName(String n) { getNameProperty().set(n); }

    StringProperty getDescriptionProperty() { return mDescriptionProperty; }
    String getDescription() { return getDescriptionProperty().get(); }
    void setDescription(String desc) { getDescriptionProperty().set(desc); }

    String toQIF() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("N").append(getName()).append(EOL);
        if (!getDescription().isEmpty())
            stringBuilder.append("D").append(getDescription()).append(EOL);
        stringBuilder.append(EOR).append(EOL);
        return stringBuilder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        return Objects.equals(getName(), tag.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }
}

