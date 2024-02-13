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
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.Callable;

public class DateSchedule {
    public enum BaseUnit {
        DAY, WEEK, HALF_MONTH, MONTH, QUARTER, YEAR;

        @Override
        public String toString() {
            return (name().charAt(0) + name().substring(1).toLowerCase()).replace("_", " ");
        }
    }

    // base unit
    private final ObjectProperty<BaseUnit> mBaseUnitProperty = new SimpleObjectProperty<>();

    // number of unit time (d, m, q, y) of repeating
    private final ObjectProperty<Integer> mNumPeriodProperty = new SimpleObjectProperty<>();

    // mStartDate is the first occurrence of the date
    // mEndDate may not be the last occurrence
    // mEndDateProperty.get may return null, which means no end date
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

    private final StringProperty mDescriptionProperty = new SimpleStringProperty();

    /**
     * get the day of month for the first two dates of a half month schedule (with num period of 1)
     * an integer value of 31 represents end of month
     * @return int[] of day of the month for the first and the second due date
     */
    private static int[] getHalfMonthDates(LocalDate s) {
        int dom0 = s.getDayOfMonth();
        int dom1;
        final boolean isEOM0 = dom0 == s.lengthOfMonth();

        if (dom0 < 15) {
            dom1 = dom0 + 15;
        } else if (dom0 == 15) {
            dom1 = 31;
        } else if (isEOM0) {
            dom1 = 15;
        } else {
            dom1 = dom0 - 15;
        }
        return new int[]{dom0, dom1};
    }

    static int numberOfPeriodsPerYear(BaseUnit bu) {
        switch (bu) {
            case DAY: return 365;
            case WEEK: return 52;
            case HALF_MONTH: return 24;
            case MONTH: return 12;
            case QUARTER: return 4;
            case YEAR: return 1;
            default: throw new IllegalArgumentException(bu + " not implemented");
        }
    }

    // return the first day of the period containing LocalDate d
    private static LocalDate firstDayOfPeriod(BaseUnit t, LocalDate d) {
        switch (t) {
            case DAY:
                return d;
            case WEEK:
                // week starts on Sunday
                return d.minusDays(d.getDayOfWeek().getValue()%7);
            case HALF_MONTH:
                final int dom = d.getDayOfMonth();
                return (dom <= 15) ? d.minusDays(dom-1) : d.minusDays(dom-16); // 1st or 16th
            case MONTH:
                return LocalDate.of(d.getYear(), d.getMonth(), 1);
            case QUARTER:
                return LocalDate.of(d.getYear(), ((d.getMonthValue()-1)/3)*3+1, 1);
            case YEAR:
                return LocalDate.of(d.getYear(), 1, 1);
            default:
                throw new IllegalArgumentException(t + " not implemented");
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
            case HALF_MONTH:
                final int dom = d.getDayOfMonth();
                return (dom <= 15) ? d.plusDays(15-dom) : d.plusDays(d.lengthOfMonth()-dom); // 15 or eom
            case MONTH:
                return LocalDate.of(d.getYear(), d.getMonth(), d.lengthOfMonth());
            case QUARTER:
                int eoqMonth = ((d.getMonth().getValue()-1)/3)*3+3;
                int eoqDay = (eoqMonth == 6 || eoqMonth == 9) ? 30 : 31;
                return LocalDate.of(d.getYear(), eoqMonth, eoqDay);
            case YEAR:
                return LocalDate.of(d.getYear(),12,31);
            default:
                throw new IllegalArgumentException(t + " not implemented");
        }
    }

    // return the day/week count of startdate to reference date
    private long getDWCount() {
        long cnt = isForward() ? firstDayOfPeriod(getBaseUnit(), getStartDate())
                .until(getStartDate(), ChronoUnit.DAYS)
                : getStartDate().until(lastDayOfPeriod(getBaseUnit(), getStartDate()), ChronoUnit.DAYS);
        if (isDOMBased())
            return cnt+1;
        return cnt/7+1;
    }

    // getters for properties
    ObjectProperty<BaseUnit> getBaseUnitProperty() { return mBaseUnitProperty; }
    ObjectProperty<LocalDate> getStartDateProperty() { return mStartDateProperty; }
    ObjectProperty<LocalDate> getEndDateProperty() { return mEndDateProperty; }
    ObjectProperty<Integer> getNumPeriodProperty() { return mNumPeriodProperty; }
    BooleanProperty getIsDOMBasedProperty() { return mIsDOMBasedProperty; }
    BooleanProperty getIsForwardProperty() { return mIsForwardProperty; }
    StringProperty getDescriptionProperty() { return mDescriptionProperty; }

    // getters
    public BaseUnit getBaseUnit() { return getBaseUnitProperty().get(); }
    public LocalDate getStartDate() { return getStartDateProperty().get(); }
    public LocalDate getEndDate() { return getEndDateProperty().get(); }
    public Integer getNumPeriod() { return getNumPeriodProperty().get(); }
    public Boolean isDOMBased() { return getIsDOMBasedProperty().get(); }
    public Boolean isForward() { return getIsForwardProperty().get(); }

    // setters
    void setStartDate(LocalDate s) { getStartDateProperty().set(s); }

    // constructor
    // np > 0.
    // e should be after s
    // isDOM and isFwd are not used for DAY and WEEK
    public DateSchedule(BaseUnit bu, int np, LocalDate s, LocalDate e, boolean isDOM, boolean isFwd) {
        mBaseUnitProperty.set(bu);
        mNumPeriodProperty.set(np);
        mStartDateProperty.set(s);
        mEndDateProperty.set(e);
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
            String buLowerCase = getBaseUnit().toString().toLowerCase().replace("_", " ");
            switch (getBaseUnit()) {
                case DAY:
                case WEEK:
                    return "Every " + np + " " + buLowerCase
                            + (np == 1 ? "" : "s")
                            + (getBaseUnit() == BaseUnit.DAY ? "" :
                            " on " + getStartDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault()));
                case HALF_MONTH:
                    final int[] dom01 = getHalfMonthDates(getStartDate());
                    return "Every " + ((np == 1) ? buLowerCase : np + " " + buLowerCase + "s") + " on day "
                            + dom01[0] + " and " + dom01[1] + " of the month.";
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
                            return (dwCount == 1 ? "The last " : (dwCount + nth(dwCount))) + " last " + dow
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

    // get a due date in the period containing d
    private LocalDate getDueDateInPeriod(LocalDate d) {
        // the first and the last day of the period
        final LocalDate s = getStartDate();
        final LocalDate fd = firstDayOfPeriod(getBaseUnit(), d);
        switch (getBaseUnit()) {
            case DAY:
                return d;
            case WEEK: // week starts on Sunday (7).
                return fd.plusDays(s.getDayOfWeek().getValue()%7);
            case HALF_MONTH:
                final int[] dom01 = getHalfMonthDates(s);
                if (fd.getDayOfMonth() == 1) { // in the 1st half
                    return fd.withDayOfMonth(Math.min(dom01[0], dom01[1]));
                } else { // in the 2nd half
                    return fd.withDayOfMonth(Math.min(Math.max(dom01[0], dom01[1]), d.lengthOfMonth()));
                }
            case MONTH:
            case QUARTER:
            case YEAR:
                final LocalDate ld = lastDayOfPeriod(getBaseUnit(), d);
                final LocalDate fdS = firstDayOfPeriod(getBaseUnit(), s);
                final LocalDate ldS = lastDayOfPeriod(getBaseUnit(), s);
                final long fdUntilLd = fd.until(ld, ChronoUnit.DAYS);

                if (isForward()) { // counting forward
                    if (isDOMBased()) { // counting day of the period
                        if (s.isEqual(ldS)) {// last day of the period
                            return ld;
                        } else {
                            return fd.plusDays(Math.min(fdUntilLd, fdS.until(s, ChronoUnit.DAYS)));
                        }
                    } else { // counting day of the week
                        int dowS = s.getDayOfWeek().getValue();
                        int dowFd = fd.getDayOfWeek().getValue();
                        return fd.plusDays(Math.min(fdUntilLd,
                                (dowS >= dowFd ? dowS-dowFd : dowS-dowFd+7) + (fdS.until(s, ChronoUnit.DAYS)/7)*7));
                    }
                } else { // counting backward
                    if (isDOMBased()) { // counting day of the period
                        return ld.minusDays(Math.min(fdUntilLd, s.until(ldS, ChronoUnit.DAYS)));
                    } else { // counting day of the week
                        int dowS = s.getDayOfWeek().getValue();
                        int dowLd = ld.getDayOfWeek().getValue();
                        return ld.minusDays(Math.min(fdUntilLd,
                                (dowS <= dowLd ? dowLd-dowS : dowLd-dowS+7) + (s.until(ldS, ChronoUnit.DAYS)/7)*7));
                    }
                }
            default:
                throw new IllegalStateException(getBaseUnit() + " not implemented");
        }
    }

    // return the next due date AFTER due date 'from'
    LocalDate getNextDueDate(LocalDate from) {
        return getNextPrevDueDate(from, true);
    }

    // return the previous due date BEFORE due date 'from'
    LocalDate getPrevDueDate(LocalDate from) {
        return getNextPrevDueDate(from, false);
    }

    // starting from a due date 'from', get either next or previous due date
    private LocalDate getNextPrevDueDate(LocalDate from, boolean isNext) {
        long numPeriod = isNext ? getNumPeriod() : -getNumPeriod();
        switch (getBaseUnit()) {
            case DAY:
            case WEEK:
                return from.plusDays(numPeriod*(getBaseUnit() == BaseUnit.DAY ? 1 : 7));
            case HALF_MONTH:
            case MONTH:
            case QUARTER:
            case YEAR:
                LocalDate d; // one of the days (any day) in the period containing next/prev due date
                if (getBaseUnit() == BaseUnit.HALF_MONTH) {
                    d = from.plusMonths(numPeriod / 2);
                    if (getNumPeriod() % 2 != 0) {
                        d = isNext ? lastDayOfPeriod(BaseUnit.HALF_MONTH, d).plusDays(1)
                                : firstDayOfPeriod(BaseUnit.HALF_MONTH, d).minusDays(1);
                    }
                } else { // MONTH, QUARTER, YEAR
                    final long numMonths = (getBaseUnit() == BaseUnit.MONTH) ?
                            1 : (getBaseUnit() == BaseUnit.QUARTER ? 3 : 12);
                    d = from.plusMonths(numPeriod*numMonths);
                }
                return getDueDateInPeriod(d);
            default:
                throw new IllegalStateException(getBaseUnit() + " not implemented");
        }
    }
}
