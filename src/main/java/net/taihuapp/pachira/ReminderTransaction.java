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

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;

import java.time.LocalDate;

class ReminderTransaction {
    static final String OVERDUE = "Over due";
    static final String DUESOON = "Due soon";
    static final String COMPLETED = "Completed";
    static final String SKIPPED = "Skipped";

    private final Reminder mReminder;
    private final ObjectProperty<LocalDate> mDueDateProperty;
    private final IntegerProperty mTransactionIDProperty;
    private final StringProperty mStatusProperty;

    // tid 0 is skipped
    // tid < 0 is un-executed
    ReminderTransaction(Reminder r, LocalDate d, int tid) {
        mReminder = r;
        mDueDateProperty = new SimpleObjectProperty<>(d);
        mStatusProperty = new SimpleStringProperty();
        mTransactionIDProperty = new SimpleIntegerProperty(tid);

        mStatusProperty.bind(Bindings.createStringBinding(() -> {
            final int id = mTransactionIDProperty.get();
            if (id > 0)
                return COMPLETED;
            if (id == 0)
                return SKIPPED;

            // id < 0, not executed
            LocalDate today = MainApp.CURRENTDATEPROPERTY.get();
            LocalDate dueDate = mDueDateProperty.get();
            if (dueDate.isBefore(today))
                return OVERDUE;

            if (!dueDate.isAfter(today.plusDays(mReminder.getDateSchedule().getAlertDay())))
                return DUESOON;

            return "";
        }, mTransactionIDProperty, mDueDateProperty, MainApp.CURRENTDATEPROPERTY));
    }

    ObjectProperty<LocalDate> getDueDateProperty() { return mDueDateProperty; }
    LocalDate getDueDate() { return getDueDateProperty().get(); }
    Reminder getReminder() { return mReminder; }
    StringProperty getStatusProperty() { return mStatusProperty; }
    String getStatus() { return getStatusProperty().get(); }
    private IntegerProperty getTransactionIDProperty() { return mTransactionIDProperty; }
    int getTransactionID() { return getTransactionIDProperty().get(); }

    void setTransactionID(int tid) { getTransactionIDProperty().set(tid); }
}
