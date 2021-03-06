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

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import static java.time.temporal.ChronoUnit.DAYS;

public class DateSchedule {
    public enum BaseUnit {
        DAY, WEEK, MONTH, QUARTER, YEAR
    }

    // base unit
    private final ObjectProperty<BaseUnit> mBaseUnitProperty = new SimpleObjectProperty<>();

    // number of unit time (d, m, q, y) of repeating
    private final IntegerProperty mNumPeriodProperty = new SimpleIntegerProperty();

    // mStartDate is the first occurrence of the date
    // mEndDate may not be the last occurrence
    // mEndDateProperty.get may return null, which means no end date
    private final ObjectProperty<LocalDate> mStartDateProperty = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> mEndDateProperty = new SimpleObjectProperty<>();

    // number of days of advance alert
    private final IntegerProperty mAlertDayProperty = new SimpleIntegerProperty();

    // used in MONTH/QUARTER/YEAR schedules
    // if true, count day of the month
    // otherwise, count day of the week
    private final BooleanProperty mIsDOMBasedProperty = new SimpleBooleanProperty();

    // used in MONTH/QUARTER/YEAR schedules
    // if true, count forward from 1st of the month
    // otherwise, count backward from the end of the month
    private final BooleanProperty mIsForwardProperty = new SimpleBooleanProperty();

    private final StringProperty mDescriptionProperty = new SimpleStringProperty();

    LocalDate getNextDueDate(LocalDate from) {
        if (from == null || from.isBefore(getStartDate()))
            return getStartDate();
        if (getEndDate() != null && from.isAfter(getEndDate()))
            return null; // after end date already

        // from is between startDate and endDate
        LocalDate to;
        switch (getBaseUnit()) {
            case DAY:
                to = from.plusDays(getNumPeriod());
                break;
            case WEEK:
                to = from.plusDays(getNumPeriod()*7L);
                break;
            case MONTH:
                // add one more month just to be safe
                to = from.plusDays((1+getNumPeriod())*31L);
                break;
            case QUARTER:
                to = from.plusDays((1+getNumPeriod())*92L);
                break;
            case YEAR:
                to = from.plusDays((1+getNumPeriod())*366L);
                break;
            default:
                to = from; // we shouldn't be here
                break;
        }

        if (getEndDate() != null && to.isAfter(getEndDate()))
            to = getEndDate();

        List<LocalDate> dueDates = getDueDates(from, to);
        if (dueDates.size() == 0)
            return null;
        return dueDates.get(0);
    }

    // return the scheduled dates from d1 (inclusive) to d2 (inclusive)
    // in an ascending sorted list
    private List<LocalDate> getDueDates(LocalDate d1, LocalDate d2) {
        List<LocalDate> dList = new ArrayList<>();
        switch (getBaseUnit()) {
            case DAY:
            case WEEK:
                long s = getStartDate().toEpochDay();
                long e = getEndDate() == null ? java.lang.Long.MAX_VALUE : getEndDate().toEpochDay();
                long l1 = d1.toEpochDay();
                long l2 = d2.toEpochDay();
                if (l1 < s)
                    l1 = s;
                if (e < l2)
                    l2 = e;
                long baseCnt = (getBaseUnit() == BaseUnit.DAY ? 1L : 7L)* getNumPeriod();
                long r = ((l1-s) % baseCnt);
                for (long i = r == 0 ? l1 : l1 + (baseCnt-r); i <= l2; i += baseCnt) {
                    dList.add(LocalDate.ofEpochDay(i));
                }
                break;
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
                    if (d.isAfter(d2) || ((getEndDate() != null) && d.isAfter(getEndDate())))
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

    // return the day/week count of startdate to reference date
    private long getDWCount() {
        long cnt = isForward() ? firstDayOfPeriod(getBaseUnit(), getStartDate()).until(getStartDate(), DAYS)
                : getStartDate().until(lastDayOfPeriod(getBaseUnit(), getStartDate()), DAYS);
        if (isDOMBased())
            return cnt+1;
        return cnt/7+1;
    }

    // getters for properties
    ObjectProperty<BaseUnit> getBaseUnitProperty() { return mBaseUnitProperty; }
    ObjectProperty<LocalDate> getStartDateProperty() { return mStartDateProperty; }
    ObjectProperty<LocalDate> getEndDateProperty() { return mEndDateProperty; }
    IntegerProperty getNumPeriodProperty() { return mNumPeriodProperty; }
    IntegerProperty getAlertDayProperty() { return mAlertDayProperty; }
    BooleanProperty getIsDOMBasedProperty() { return mIsDOMBasedProperty; }
    BooleanProperty getIsForwardProperty() { return mIsForwardProperty; }
    StringProperty getDescriptionProperty() { return mDescriptionProperty; }

    // getters
    public BaseUnit getBaseUnit() { return getBaseUnitProperty().get(); }
    public LocalDate getStartDate() { return getStartDateProperty().get(); }
    public LocalDate getEndDate() { return getEndDateProperty().get(); }
    public Integer getNumPeriod() { return getNumPeriodProperty().get(); }
    public Integer getAlertDay() { return getAlertDayProperty().get(); }
    public Boolean isDOMBased() { return getIsDOMBasedProperty().get(); }
    public Boolean isForward() { return getIsForwardProperty().get(); }

    // setters
    void setStartDate(LocalDate s) { getStartDateProperty().set(s); }

    // constructor
    // np > 0.
    // e should be after s
    // isDOM and isFwd are not used for DAY and WEEK
    public DateSchedule(BaseUnit bu, int np, LocalDate s, LocalDate e, int ad, boolean isDOM, boolean isFwd) {
        mBaseUnitProperty.set(bu);
        mNumPeriodProperty.set(np);
        mStartDateProperty.set(s);
        mEndDateProperty.set(e);
        mAlertDayProperty.set(ad);
        mIsDOMBasedProperty.set(isDOM);
        mIsForwardProperty.set(isFwd);

        bindDescriptionProperty();
    }

    private String nth(long n) {
        if (n > 3 && n < 21)
            return "th";
        switch ((int) n%10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }

    private void bindDescriptionProperty() {
        final Callable<String> converter = () -> {
            int np = getNumPeriod();
            String buLowerCase = getBaseUnit().toString().toLowerCase();
            switch (getBaseUnit()) {
                case DAY:
                case WEEK:
                    return "Every " + np + " " + buLowerCase
                            + (np == 1 ? "" : "s")
                            + (getBaseUnit() == BaseUnit.DAY ? "" :
                            " on " + getStartDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault()));
                case MONTH:
                case QUARTER:
                case YEAR:
                    long dwCount = getDWCount();
                    if (isDOMBased()) {
                        if (isForward())
                            return "Day " + dwCount + " of every " + np + " " + buLowerCase;
                        else {
                            return (dwCount == 1 ? "The last day" : (dwCount + nth(dwCount)) + " last day")
                                    + " of every " + np + " " + buLowerCase + (np > 1 ? "s" : "");
                        }
                    } else {
                        String dow = getStartDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
                        if (isForward()) {
                            return "The " + dwCount + nth(dwCount) + " " + dow + " of every " + np + " " + buLowerCase;
                        } else {
                            return (dwCount == 1 ? "The last " : (dwCount + nth(dwCount))) + dow
                                    + " of every " + np + " " + buLowerCase + (np > 1 ? "s" : "");
                        }
                    }
                default:
                    return "Unknown problem";
            }
        };
        mDescriptionProperty.bind(Bindings.createStringBinding(converter, getBaseUnitProperty(),
                getNumPeriodProperty(), getStartDateProperty(), getEndDateProperty(),
                getIsDOMBasedProperty(), getIsForwardProperty()));
    }
}
