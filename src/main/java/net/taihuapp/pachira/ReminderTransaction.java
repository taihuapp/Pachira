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

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ReminderTransaction {
    static final String OVERDUE = "Over due";
    static final String DUE_SOON = "Due soon";
    static final String COMPLETED = "Completed";
    static final String SKIPPED = "Skipped";

    private final ObjectProperty<Integer> mReminderIdProperty;
    private final ObjectProperty<LocalDate> mDueDateProperty;
    private final IntegerProperty mTransactionIDProperty;
    private final StringProperty mStatusProperty;
    private final ObjectProperty<Integer> alertDaysProperty;
    private final ObjectProperty<BigDecimal> amountProperty;

    // tid > 0, representing the corresponding transaction id.
    // tid 0 is skipped
    // tid < 0 is un-executed, -(tid) is the alert days.
    public ReminderTransaction(int rID, LocalDate d, int tid, int alertDays, BigDecimal amt) {
        mReminderIdProperty = new SimpleObjectProperty<>(rID);
        mDueDateProperty = new SimpleObjectProperty<>(d);
        mStatusProperty = new SimpleStringProperty();
        mTransactionIDProperty = new SimpleIntegerProperty(tid);
        alertDaysProperty = new SimpleObjectProperty<>(alertDays);
        amountProperty = new SimpleObjectProperty<>(amt);

        mStatusProperty.bind(Bindings.createStringBinding(() -> {
            final int id = mTransactionIDProperty.get();
            if (id > 0)
                return COMPLETED;
            if (id == 0)
                return SKIPPED;

            // id < 0, not executed
            LocalDate today = MainApp.CURRENT_DATE_PROPERTY.get();
            LocalDate dueDate = mDueDateProperty.get();
            if (dueDate.isBefore(today))
                return OVERDUE;

            if (!dueDate.isAfter(today.plusDays(alertDaysProperty.get())))
                return DUE_SOON;

            return "";
        }, mTransactionIDProperty, mDueDateProperty, alertDaysProperty, MainApp.CURRENT_DATE_PROPERTY));
    }

    ObjectProperty<LocalDate> getDueDateProperty() { return mDueDateProperty; }
    public LocalDate getDueDate() { return getDueDateProperty().get(); }
    public int getReminderId() { return mReminderIdProperty.get(); }
    StringProperty getStatusProperty() { return mStatusProperty; }
    public String getStatus() { return getStatusProperty().get(); }
    private IntegerProperty getTransactionIDProperty() { return mTransactionIDProperty; }
    public int getTransactionID() { return getTransactionIDProperty().get(); }

    ObjectProperty<BigDecimal> getAmountProperty() { return amountProperty; }

    void setTransactionID(int tid) { getTransactionIDProperty().set(tid); }
}
