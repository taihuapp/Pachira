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

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class DirectConnection {

    public static final String HASHED_MASTER_PASSWORD_NAME = "HASHEDMASTERPASSWORD";

    public static class FIData {
        private int mID;
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

        public FIData(int id, String fiid, String subId, String brokerId, String name, String  org, String url) {
            mID = id;
            mFIIDProperty.set(fiid);
            mSubIDProperty.set(subId);
            mBrokerIDProperty.set(brokerId);
            mNameProperty.set(name);
            mORGProperty.set(org);
            mURLProperty.set(url);
        }

        public int getID() { return mID; }
        void setID(int id) { mID = id; }

        StringProperty getFIIDProperty() { return mFIIDProperty; }
        public String getFIID() { return getFIIDProperty().get(); }

        StringProperty getSubIDProperty() { return mSubIDProperty; }
        public String getSubID() { return getSubIDProperty().get(); }

        StringProperty getBrokerIDProperty() { return mBrokerIDProperty; }
        public String getBrokerID() { return getBrokerIDProperty().get(); }

        StringProperty getNameProperty() { return mNameProperty; }
        public String getName() { return getNameProperty().get(); }

        StringProperty getORGProperty() { return mORGProperty; }
        public String getORG() { return getORGProperty().get(); }

        StringProperty getURLProperty() { return mURLProperty; }
        public String getURL() { return getURLProperty().get(); }
    }

    private int mID;
    private final StringProperty mNameProperty = new SimpleStringProperty("");
    private final IntegerProperty mFIIDProperty = new SimpleIntegerProperty(-1);
    private String mEncryptedUserName;
    private String mEncryptedPassword;

    public int getFIID() { return mFIIDProperty.get(); }
    public int getID() { return mID; }
    void setID(int id) { mID = id; }
    void setFIID(int id) { mFIIDProperty.set(id); }
    StringProperty getNameProperty() { return mNameProperty; }
    public String getName() { return getNameProperty().get(); }
    void setName(String n) { mNameProperty.set(n); }

    public String getEncryptedUserName() { return mEncryptedUserName; }
    public String getEncryptedPassword() { return mEncryptedPassword; }
    void setEncryptedUserName(String eun) { mEncryptedUserName = eun; }
    public void setEncryptedPassword(String encryptedPassword) { mEncryptedPassword = encryptedPassword; }

    // constructor
    // don't use null to initialize eun and encryptedPassword, if necessary, use empty string instead
    public DirectConnection(int id, String name, int fiID, String eun, String encryptedPassword) {
        mID = id;
        mNameProperty.set(name);
        mFIIDProperty.set(fiID);
        mEncryptedUserName = eun;
        mEncryptedPassword = encryptedPassword;
    }
}
