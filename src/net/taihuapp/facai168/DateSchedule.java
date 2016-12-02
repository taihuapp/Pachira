package net.taihuapp.facai168;

import javafx.beans.property.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ghe on 11/27/16.
 *
 */

class DateSchedule {
    enum BaseUnit {
        DAY, WEEK, MONTH, QUARTER, YEAR
    }

    // base unit
    private final ObjectProperty<BaseUnit> mBaseUnitProperty = new SimpleObjectProperty<>();

    // number of unit time (d, m, q, y) of repeating
    private final IntegerProperty mNumPeriodProperty = new SimpleIntegerProperty();

    // mStartDate is the first occurrence of the date
    // mEndDate may not be the last occurrence
    private final ObjectProperty<LocalDate> mStartDateProperty = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> mEndDateProperty = new SimpleObjectProperty<>();

    // used in MONTH/QUARTER/YEAR schedules
    // if true, count day of the month
    // otherwise, count day of the week
    private final BooleanProperty mIsDOMBasedProperty = new SimpleBooleanProperty();

    // used in MONTH/QUARTER/YEAR schedules
    // if true, count forward from 1st of the month
    // otherwise, count backward from the end of the month
    private final BooleanProperty mIsForwardProperty = new SimpleBooleanProperty();

    // return the scheduled dates from d1 (inclusive) to d2 (inclusive)
    // in an ascending sorted list
    List<LocalDate> getDates(LocalDate d1, LocalDate d2) {
        List<LocalDate> dList = new ArrayList<>();
        switch (getBaseUnit()) {
            case DAY:
            case WEEK:
                long s = getStartDate().toEpochDay();
                long e = getEndDate().toEpochDay();
                long l1 = d1.toEpochDay();
                long l2 = d2.toEpochDay();
                if (l1 < s)
                    l1 = s;
                if (e < l2)
                    l2 = e;
                long baseCnt = (getBaseUnit() == BaseUnit.DAY ? 1 : 7)* getNumPeriod();
                long r = ((l1-s) % baseCnt);
                for (long i = r == 0 ? l1 : l1 + (baseCnt-r); i <= l2; i += baseCnt) {
                    dList.add(LocalDate.ofEpochDay(i));
                }
            case MONTH:
            case QUARTER:
            case YEAR:
                int numMonths = getBaseUnit() == BaseUnit.MONTH ? 1 : (getBaseUnit() == BaseUnit.QUARTER ? 3 : 12);
                numMonths *= getNumPeriod();
                LocalDate d = getStartDate();
                LocalDate fd = firstDayOfPeriod(getBaseUnit(), d);
                LocalDate ld = lastDayOfPeriod(getBaseUnit(), d);
                long cntFD2D = d.toEpochDay()-fd.toEpochDay(); // num of days from 1st day of the period
                long cntD2LD = ld.toEpochDay()-d.toEpochDay(); // num of days to end of the period
                int dow = d.getDayOfWeek().getValue(); // monday to sunday, 1 to 7
                long nth = cntFD2D/7+1;   // nth dow from 1st of the period
                long nthReverse = cntD2LD/7+1;
                while (true) {
                    if (d.isAfter(d2) || d.isAfter(getEndDate()))
                        break; // break out while loop
                    if (!d.isBefore(d1))
                        dList.add(d); // add to the list if equal or after d1

                    // now moving forward
                    d = d.plusMonths(numMonths); // move forward number of months
                    fd = firstDayOfPeriod(getBaseUnit(), d);
                    ld = lastDayOfPeriod(getBaseUnit(), d);
                    if (isDOMBased()) {
                        if (isForward()) {
                            // forward counting day of month
                            d = fd.plusDays(cntFD2D);
                            if (d.isAfter(ld))
                                d = ld;
                        } else {
                            // backward counting day of month
                            d = ld.minusDays(cntD2LD);
                            if (d.isBefore(fd))
                                d = fd;
                        }
                    } else {
                        // counting weekdays
                        if (isForward()) {
                            // nth dow of the month
                            int dowFD = fd.getDayOfWeek().getValue(); // day of the week for 1st of the month
                            d = fd.plusDays((dow >= dowFD ? (dow-dowFD) : (dow-dowFD+7)) + 7*(nth-1));
                            if (d.isAfter(ld))
                                d = ld;
                        } else {
                            // reverseNth from the end of the month
                            int dowLD = ld.getDayOfWeek().getValue();
                            d = ld.minusDays(((dow <= dowLD) ? dowLD-dow : dowLD-dow+7) + (nthReverse-1)*7);
                            if (d.isBefore(fd))
                                d = fd;
                        }
                    }
                }
                break;
        }
        return dList;
    }

    // return the first day of the period containing LocalDate d
    private static LocalDate firstDayOfPeriod(BaseUnit t, LocalDate d) {
        switch (t) {
            case DAY:
                return d;
            case WEEK:
                // week starts on Sunday
                return d.minusDays(d.getDayOfWeek().getValue()%7);
            case MONTH:
                return LocalDate.of(d.getYear(), d.getMonth(), 1);
            case QUARTER:
                return LocalDate.of(d.getYear(), ((d.getMonthValue()-1)/3)*3+1, 1);
            case YEAR:
                return LocalDate.of(d.getYear(), 1, 1);
            default:
                return d; // we shouldn't be here
        }
    }

    // return the last day of the period containing LocalDate d
    private static LocalDate lastDayOfPeriod(BaseUnit t, LocalDate d) {
        switch (t) {
            case DAY:
                return d;
            case WEEK:
                // week ends on Saturday
                return d.plusDays(6-(d.getDayOfWeek().getValue()%7));
            case MONTH:
                return LocalDate.of(d.getYear(), d.getMonth(), d.lengthOfMonth());
            case QUARTER:
                int eoqMonth = ((d.getMonth().getValue()-1)/3)*3+3;
                int eoqDay = (eoqMonth == 6 || eoqMonth == 9) ? 30 : 31;
                return LocalDate.of(d.getYear(), eoqMonth, eoqDay);
            case YEAR:
                return LocalDate.of(d.getYear(),12,31);
            default:
                return d; // we shouldn't be here
        }
    }

    // getters for properties
    ObjectProperty<BaseUnit> getBaseUnitProperty() { return mBaseUnitProperty; }
    ObjectProperty<LocalDate> getStartDateProperty() { return mStartDateProperty; }
    ObjectProperty<LocalDate> getEndDateProperty() { return mEndDateProperty; }
    IntegerProperty getNumPeriodProperty() { return mNumPeriodProperty; }
    BooleanProperty getIsDOMBasedProperty() { return mIsDOMBasedProperty; }
    BooleanProperty getIsForwardProperty() { return mIsForwardProperty; }

    // getters
    BaseUnit getBaseUnit() { return getBaseUnitProperty().get(); }
    LocalDate getStartDate() { return getStartDateProperty().get(); }
    LocalDate getEndDate() { return getEndDateProperty().get(); }
    Integer getNumPeriod() { return getNumPeriodProperty().get(); }
    Boolean isDOMBased() { return getIsDOMBasedProperty().get(); }
    Boolean isForward() { return getIsForwardProperty().get(); }

    // setters
    void setBaseUnit(BaseUnit bu) { getBaseUnitProperty().set(bu); }
    void setStartDate(LocalDate s) { getStartDateProperty().set(s); }
    void setEndDate(LocalDate e) { getEndDateProperty().set(e); }
    void setNumPeriod(int n) { getNumPeriodProperty().set(n); }
    void setDOMBased(boolean tf) { getIsDOMBasedProperty().set(tf); }
    void setForward(boolean tf) { getIsForwardProperty().set(tf); }

    // constructor
    // np > 0.
    // e should be after s
    // isDOM and isFwd are not used for DAY and WEEK
    DateSchedule(BaseUnit bu, int np, LocalDate s, LocalDate e, boolean isDOM, boolean isFwd) {
        mBaseUnitProperty.set(bu);
        mNumPeriodProperty.set(np);
        mStartDateProperty.set(s);
        mEndDateProperty.set(e);
        mIsDOMBasedProperty.set(isDOM);
        mIsForwardProperty.set(isFwd);
    }
}
