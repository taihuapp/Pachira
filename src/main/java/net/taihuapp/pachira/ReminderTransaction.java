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

import java.time.LocalDate;

public class ReminderTransaction {

    private final ObjectProperty<Integer> mReminderIdProperty;
    private final ObjectProperty<LocalDate> mDueDateProperty;
    private final ObjectProperty<Integer> mTransactionIDProperty;

    // tid > 0, representing the corresponding transaction id.
    // tid 0 is a skipped reminder transaction
    // tid < 0 is un-executed, -(tid) is the alert days.
    public ReminderTransaction(int rID, LocalDate d, int tid) {
        mReminderIdProperty = new SimpleObjectProperty<>(rID);
        mDueDateProperty = new SimpleObjectProperty<>(d);
        mTransactionIDProperty = new SimpleObjectProperty<>(tid);
    }

    ObjectProperty<LocalDate> getDueDateProperty() { return mDueDateProperty; }
    public LocalDate getDueDate() { return getDueDateProperty().get(); }
    public int getReminderId() { return mReminderIdProperty.get(); }
    public ObjectProperty<Integer> getTransactionIDProperty() { return mTransactionIDProperty; }
    public int getTransactionID() { return getTransactionIDProperty().get(); }
    boolean isCompletedOrSkipped() { return getTransactionID() >= 0; }

    void setTransactionID(int tid) { getTransactionIDProperty().set(tid); }
}
