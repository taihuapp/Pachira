package net.taihuapp.facai168;

import javafx.util.converter.DateStringConverter;

import java.time.LocalDate;

/**
 * Created by ghe on 11/29/16.
 *
 */
class Reminder {
    private DateSchedule mDateSchedule;

    // default constructor
    Reminder() {
        // default monthly schedule, starting today, no end, counting day of month forward.
        mDateSchedule = new DateSchedule(DateSchedule.BaseUnit.MONTH, 1, LocalDate.now(), null,
                true, true);
    }

    DateSchedule getDateSchedule() { return mDateSchedule; }
}
