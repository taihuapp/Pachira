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

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

class DirectConnection {
    static class FIData {
        private int mID = -1;
        private final StringProperty mFIIDProperty = new SimpleStringProperty();
        private final StringProperty mSubIDProperty = new SimpleStringProperty();
        private final StringProperty mBrokerIDProperty = new SimpleStringProperty();
        private final StringProperty mNameProperty = new SimpleStringProperty();
        private final StringProperty mORGProperty = new SimpleStringProperty();
        private final StringProperty mURLProperty = new SimpleStringProperty();

        @Override
        public String toString() {
            return getID() + ", " + getFIID() + ", " + getSubID() + ", " + getBrokerID() + ", "
                    + getName() + ", " + getORG() + ", " + getURL();
        }

        FIData() {
            this(-1, "", "", "", "", "", "");
        }

        FIData(FIData fiData) {
            this(fiData.getID(), fiData.getFIID(), fiData.getSubID(), fiData.getBrokerID(), fiData.getName(),
                    fiData.getORG(), fiData.getURL());
        }

        FIData(int id, String fiid, String subId, String brokerId, String name, String  org, String url) {
            mID = id;
            mFIIDProperty.set(fiid);
            mSubIDProperty.set(subId);
            mBrokerIDProperty.set(brokerId);
            mNameProperty.set(name);
            mORGProperty.set(org);
            mURLProperty.set(url);
        }

        int getID() { return mID; }
        void setID(int id) { mID = id; }

        StringProperty getFIIDProperty() { return mFIIDProperty; }
        String getFIID() { return getFIIDProperty().get(); }

        StringProperty getSubIDProperty() { return mSubIDProperty; }
        String getSubID() { return getSubIDProperty().get(); }

        StringProperty getBrokerIDProperty() { return mBrokerIDProperty; }
        String getBrokerID() { return getBrokerIDProperty().get(); }

        StringProperty getNameProperty() { return mNameProperty; }
        String getName() { return getNameProperty().get(); }

        StringProperty getORGProperty() { return mORGProperty; }
        String getORG() { return getORGProperty().get(); }

        StringProperty getURLProperty() { return mURLProperty; }
        String getURL() { return getURLProperty().get(); }
    }

    private int mID = -1;
    private final StringProperty mNameProperty = new SimpleStringProperty("");
    private final IntegerProperty mFIIDProperty = new SimpleIntegerProperty(-1);
    private String mEncryptedUserNameProperty = "";
    private String mEncryptedPasswordProperty = "";

    int getFIID() { return mFIIDProperty.get(); }

    String getName() { return mNameProperty.get(); }
    void setName(String n) { mNameProperty.set(n); }

    // constructor
    DirectConnection(int id, String name, int fiID, String eun, String epwd) {
        mID = id;
        mNameProperty.set(name);
        mFIIDProperty.set(fiID);
        mEncryptedUserNameProperty = eun;
        mEncryptedPasswordProperty = epwd;
    }
}
