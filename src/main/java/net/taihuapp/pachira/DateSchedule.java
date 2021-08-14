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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

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

    /**
     * get all the due dates from getStartDate to end (inclusive, ignoring getEndDate)
     * end can not be null, end may not be a due date
     */
    List<LocalDate> getDueDates(LocalDate end) {
        List<LocalDate> dueDates;
        final long sLong = getStartDate().toEpochDay();
        final long eLong = end.toEpochDay();
        switch (getBaseUnit()) {
            case DAY:
            case WEEK:
                // spacing between due dates
                final long baseCnt = getNumPeriod() * (getBaseUnit() == BaseUnit.DAY ? 1L : 7L);
                // due dates in epoch
                LongStream epochDayStream = LongStream.iterate(sLong, l -> l <= eLong, l -> l + baseCnt);
                // convert to list of LocalDate and return.
                return epochDayStream.mapToObj(LocalDate::ofEpochDay).collect(Collectors.toList());
            case HALF_MONTH:
                final int[] dom01 = getHalfMonthDates(getStartDate());
                final int min;
                final int max;
                if (dom01[0] < dom01[1]) {
                    min = dom01[0];
                    max = dom01[1];
                } else {
                    min = dom01[1];
                    max = dom01[0];
                }
                dueDates = new ArrayList<>();
                for (LocalDate d0 = getStartDate().minusDays(dom01[0] - 1); !d0.isAfter(end); d0 = d0.plusMonths(1)) {
                    // first day of same month as getStartDate
                    dueDates.add(d0.withDayOfMonth(min));
                    dueDates.add(d0.withDayOfMonth(Math.min(max, d0.lengthOfMonth())));
                }
                // remove the ones outside getStartDate and e
                dueDates = dueDates.stream().filter(d -> !d.isBefore(getStartDate()) && !d.isAfter(end))
                        .collect(Collectors.toList());
                // skip numOfPeriod
                return IntStream.range(0, dueDates.size()).filter(n -> n % getNumPeriod() == 0)
                        .mapToObj(dueDates::get).collect(Collectors.toList());
            case MONTH:
            case QUARTER:
            case YEAR:
                // num of months for each period
                final int numMonths = getNumPeriod() * (getBaseUnit() == BaseUnit.MONTH ?
                        1 : (getBaseUnit() == BaseUnit.QUARTER ? 3 : 12));
                // from first day of the period to getStartDate
                long fd2s = firstDayOfPeriod(getBaseUnit(), getStartDate()).until(getStartDate(), ChronoUnit.DAYS);
                // from getStartDate to last day of the period
                long s2ld = getStartDate().until(lastDayOfPeriod(getBaseUnit(), getStartDate()), ChronoUnit.DAYS);
                // day of the week for getStartDate
                int dow = getStartDate().getDayOfWeek().getValue(); // Monday to Sunday, 1 to 7
                long nthFwd = fd2s/7 + 1;
                long nthRev = s2ld/7 + 1;
                dueDates = new ArrayList<>();
                for (LocalDate fdOfPeriod = firstDayOfPeriod(getBaseUnit(), getStartDate());
                     !fdOfPeriod.isAfter(end); fdOfPeriod = fdOfPeriod.plusMonths(numMonths)) {
                    LocalDate ldOfPeriod = lastDayOfPeriod(getBaseUnit(), fdOfPeriod);
                    long daysInPeriod = fdOfPeriod.until(ldOfPeriod, ChronoUnit.DAYS)+1;
                    final LocalDate d;
                    if (isDOMBased()) { // count day of the month
                        if (isForward()) // count forward
                            d = fdOfPeriod.plusDays(Math.min(daysInPeriod-1, fd2s));
                        else // count backward
                            d = ldOfPeriod.minusDays(Math.min(daysInPeriod-1, s2ld));
                    } else { // count day of the week
                        if (isForward()) { // count forward
                            int dowFd = fdOfPeriod.getDayOfWeek().getValue();
                            long daysToAdd = (dow >= dowFd ? (dow-dowFd) : (dow-dowFd+7)) + 7*(nthFwd-1);
                            if (daysToAdd > daysInPeriod-1)
                                daysToAdd = daysInPeriod-1;  // can't find that day in period, choose end of period
                            d = fdOfPeriod.plusDays(daysToAdd);
                        } else { // count backward
                            int dowLd = ldOfPeriod.getDayOfWeek().getValue();
                            long daysToSubtract = ((dow <= dowLd) ? dowLd-dow : dowLd-dow+7) + (nthRev-1)*7;
                            if (daysToSubtract > daysInPeriod-1)
                                daysToSubtract = daysInPeriod-1;
                            d = ldOfPeriod.minusDays(daysToSubtract);
                        }
                    }
                    dueDates.add(d);
                }
                return dueDates;
            default:
                throw new IllegalStateException(getBaseUnit() + " not implemented");
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

    public static void main(String[] args) {
        LocalDate s = LocalDate.of(2021, 1, 1);
        LocalDate e = LocalDate.of(2035, 12, 31);
        Boolean[] tfArray = new Boolean[]{ true, false };

        for (BaseUnit bu : BaseUnit.values()) {
            for (int np = 1; np < 4; np++) {
                for (int d = 0; d < 60; d++) {
                    for (boolean isDOM : tfArray) {
                        for (boolean isFwd : tfArray) {
                            DateSchedule dateSchedule = new DateSchedule(bu, np, s.plusDays(d), e, 3, isDOM, isFwd);

                            List<LocalDate> dueDates = dateSchedule.getDueDates(e);
                            for (int i = 1; i < dueDates.size(); i++) {
                                if (!dateSchedule.getNextDueDate(dueDates.get(i - 1)).isEqual(dueDates.get(i))) {
                                    System.out.println(np + " " + dateSchedule.getBaseUnit() + " " + dateSchedule.getStartDate() + " " + e + " " + isDOM + " " + isFwd);
                                    System.out.println("Next of " + dueDates.get(i - 1) + " is " + dateSchedule.getNextDueDate(dueDates.get(i - 1))
                                            + " expecting " + dueDates.get(i));
                                }
                                if (!dateSchedule.getPrevDueDate(dueDates.get(i)).isEqual(dueDates.get(i - 1))) {
                                    System.out.println(np + " " + dateSchedule.getBaseUnit() + " " + dateSchedule.getStartDate() + " " + e + " " + isDOM + " " + isFwd);
                                    System.out.println("Prev of " + dueDates.get(i) + " is " + dateSchedule.getPrevDueDate(dueDates.get(i))
                                            + " expecting " + dueDates.get(i - 1));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
