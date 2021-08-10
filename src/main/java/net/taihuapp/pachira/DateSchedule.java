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

    /**
     * get the day of month for the first two dates of a half month schedule (with num period of 1)
     * an integer value of 31 represents end of month
     * @return int[] of day of the month for the first and the second due date
     */
    private int[] getHalfMonthDates() {
        int dom0 = getStartDate().getDayOfMonth();
        int dom1;
        final boolean isEOM0 = dom0 == getStartDate().lengthOfMonth();

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

    /**
     * the next due date AFTER due date 'from'
     * @param from is a due date
     * @return the next due date or null
     */
    LocalDate getNextDueDate(LocalDate from) {
        long nDaysPerPeriod; // max number of days per period
        switch (getBaseUnit()) {
            case DAY:
                nDaysPerPeriod = 1;
                break;
            case WEEK:
                nDaysPerPeriod = 7;
                break;
            case HALF_MONTH:
                nDaysPerPeriod = 16;
                break;
            case MONTH:
                nDaysPerPeriod = 31;
                break;
            case QUARTER:
                nDaysPerPeriod = 91;
                break;
            case YEAR:
                nDaysPerPeriod = 366;
                break;
            default:
                throw new IllegalStateException(getBaseUnit() + " not implemented");
        }

        List<LocalDate> dueDates = getDueDates(from.plusDays(2*getNumPeriod()*nDaysPerPeriod))
                .stream().filter(d -> d.isAfter(from)).collect(Collectors.toList());
        if (dueDates.isEmpty())
            return null;
        return dueDates.get(0);
    }

    /**
     * get all the due dates from getStartDate to min(end, getEndDate) (inclusive)
     * end can not be null, end may not be a due date
     */
    private List<LocalDate> getDueDates(LocalDate end) {
        List<LocalDate> dueDates;
        final LocalDate e = getEndDate() == null ? end : (end.isBefore(getEndDate()) ? end : getEndDate());
        final long sLong = getStartDate().toEpochDay();
        final long eLong = e.toEpochDay();
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
                final int[] dom01 = getHalfMonthDates();
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
                for (LocalDate d0 = getStartDate().minusDays(dom01[0] - 1); !d0.isAfter(e); d0 = d0.plusMonths(1)) {
                    // first day of same month as getStartDate
                    dueDates.add(d0.plusDays(min - 1));
                    dueDates.add(d0.plusDays(Math.min(max, d0.lengthOfMonth())));
                }
                // remove the ones outside getStartDate and e
                dueDates = dueDates.stream().filter(d -> !d.isBefore(getStartDate()) && !d.isAfter(e))
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
                     !fdOfPeriod.isAfter(e); fdOfPeriod = fdOfPeriod.plusMonths(numMonths)) {
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
                return d; // we shouldn't be here
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
            String buLowerCase = getBaseUnit().toString().toLowerCase().replace("_", " ");
            switch (getBaseUnit()) {
                case DAY:
                case WEEK:
                    return "Every " + np + " " + buLowerCase
                            + (np == 1 ? "" : "s")
                            + (getBaseUnit() == BaseUnit.DAY ? "" :
                            " on " + getStartDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault()));
                case HALF_MONTH:
                    final int[] dom01 = getHalfMonthDates();
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
}
