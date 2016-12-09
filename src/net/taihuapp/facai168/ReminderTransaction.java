package net.taihuapp.facai168;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.time.LocalDate;

/**
 * Created by ghe on 12/7/16.
 *
 */


class ReminderTransaction {
    private final Reminder mReminder;
    private final ObjectProperty<LocalDate> mDueDateProperty;
    private final Transaction mTransaction;

    ReminderTransaction(Reminder r, LocalDate d, Transaction t) {
        mReminder = r;
        mDueDateProperty = new SimpleObjectProperty<>(d);
        mTransaction = t;
    }

    ObjectProperty<LocalDate> getDueDateProperty() { return mDueDateProperty; }
    LocalDate getDueDate() { return getDueDateProperty().get(); }
    Reminder getReminder() { return mReminder; }
    Transaction getTransaction() { return mTransaction; }
}
