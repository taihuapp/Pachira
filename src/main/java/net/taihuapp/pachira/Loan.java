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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class Loan {

    public enum Period {
        ANNUALLY(1), SEMI_ANNUALLY(2), QUARTERLY(4), BI_MONTHLY(6), MONTHLY(12),
        SEMI_MONTHLY(24), BI_WEEKLY(26), WEEKLY(52), DAILY(365);

        private final int countsPerYear;

        Period(int n) { countsPerYear = n; }
        int getCountsPerYear() { return countsPerYear; }

        @Override
        public String toString() {
            return (name().charAt(0) + name().substring(1).toLowerCase()).replace("_", "-")
                    + " (" + countsPerYear + "/Yr)";
        }
    }

    static class PaymentItem {
        // sequence id, from 1 to n
        private final ObjectProperty<Integer> sequenceIDProperty = new SimpleObjectProperty<>();
        private final ObjectProperty<LocalDate> dateProperty = new SimpleObjectProperty<>();
        private final ObjectProperty<BigDecimal> principalAmountProperty = new SimpleObjectProperty<>();
        private final ObjectProperty<BigDecimal> interestAmountProperty = new SimpleObjectProperty<>();
        private final ObjectProperty<BigDecimal> balanceAmountProperty = new SimpleObjectProperty<>();

        PaymentItem(Integer seq, LocalDate d, BigDecimal p, BigDecimal i, BigDecimal b) {
            sequenceIDProperty.set(seq);
            dateProperty.set(d);
            principalAmountProperty.set(p);
            interestAmountProperty.set(i);
            balanceAmountProperty.set(b);
        }

        public ObjectProperty<Integer> getSequenceIDProperty() { return sequenceIDProperty; }
        public ObjectProperty<LocalDate> getDateProperty() { return dateProperty; }
        public ObjectProperty<BigDecimal> getPrincipalAmountProperty() { return principalAmountProperty; }
        public ObjectProperty<BigDecimal> getInterestAmountProperty() { return interestAmountProperty; }
        public ObjectProperty<BigDecimal> getBalanceAmountProperty() { return balanceAmountProperty; }
    }

    private int id = -1;
    private final ObjectProperty<Integer> accountIDProperty = new SimpleObjectProperty<>(-1);
    private final ObjectProperty<BigDecimal> originalAmountProperty = new SimpleObjectProperty<>(null);
    private final ObjectProperty<BigDecimal> interestRateProperty = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Period> compoundingPeriodProperty = new SimpleObjectProperty<>(Period.MONTHLY);
    private final ObjectProperty<Period> paymentPeriodProperty = new SimpleObjectProperty<>(Period.MONTHLY);
    private final ObjectProperty<Integer> numberOfPaymentsProperty = new SimpleObjectProperty<>(null);
    private final ObjectProperty<LocalDate> loanDateProperty = new SimpleObjectProperty<>(LocalDate.now());
    private final ObjectProperty<LocalDate> firstPaymentDateProperty = new SimpleObjectProperty<>(LocalDate.now()
            .plusMonths(1));
    private final ObjectProperty<BigDecimal> paymentAmountProperty = new SimpleObjectProperty<>(null);
    private final ObservableList<PaymentItem> paymentSchedule = FXCollections.observableArrayList();

    Loan() {}

    public Loan(int id, int accountId, BigDecimal originalAmount, BigDecimal interestRate, Period compoundingPeriod,
                Period paymentPeriod, int numberOfPayments, LocalDate loanDate, LocalDate firstPaymentDate,
                BigDecimal paymentAmount) {
        setID(id);
        setAccountID(accountId);
        setOriginalAmount(originalAmount);
        setInterestRate(interestRate);
        setCompoundingPeriod(compoundingPeriod);
        setPaymentPeriod(paymentPeriod);
        setNumberOfPayments(numberOfPayments);
        setLoanDate(loanDate);
        setFirstPaymentDate(firstPaymentDate);
        setPaymentAmount(paymentAmount);
    }

    // copy constructor
    Loan(Loan loan) {
        this(loan.getID(), loan.getAccountID(), loan.getOriginalAmount(), loan.getInterestRate(),
                loan.getCompoundingPeriod(), loan.getPaymentPeriod(), loan.getNumberOfPayments(),
                loan.getLoanDate(), loan.getFirstPaymentDate(), loan.getPaymentAmount());
    }

    /**
     *
     * @param apr annual percentage rate, 1% == BigDecimal.ONE
     * @return effective interest rate in percentage term, 1% is 1
     */
    private BigDecimal getEffectiveInterestRate(BigDecimal apr) {
        double r = apr.doubleValue()/100;
        int n = getCompoundingPeriod().getCountsPerYear();
        int m = getPaymentPeriod().getCountsPerYear();
        return BigDecimal.valueOf(Math.pow(1+r/n, ((double) n)/((double) m))-1).movePointRight(2);
    }

    /**
     *
     * @return a list of dates.  The first element is the regular interest accruing date.  The
     *         next is the first payment date, and so on.
     */
    private List<LocalDate> getPaymentDates() {
        final List<LocalDate> paymentDates = new ArrayList<>();
        final LocalDate firstPaymentDate = getFirstPaymentDate();
        paymentDates.add(firstPaymentDate);
        final Period period = getPaymentPeriod();
        final int n = period.getCountsPerYear();

        switch (period) {
            case ANNUALLY:
            case SEMI_ANNUALLY:
            case QUARTERLY:
            case BI_MONTHLY:
            case MONTHLY:
                for (int i = 1; i < getNumberOfPayments(); i++) {
                    paymentDates.add(firstPaymentDate.plusMonths((12L * i / n)));
                }
                paymentDates.add(0, firstPaymentDate.minusMonths(1));
                break;
            case SEMI_MONTHLY:
                final int dom1 = firstPaymentDate.getDayOfMonth();
                final boolean isEOM1 = firstPaymentDate.lengthOfMonth() == dom1;
                final LocalDate d2;
                final LocalDate d0;
                if (isEOM1) {
                    // dom1 is end of month
                    d2 = firstPaymentDate.plusDays(15);
                    d0 = d2.minusMonths(1);
                } else if (dom1 > 15) {
                    d0 = firstPaymentDate.minusDays(15);
                    d2 = d0.plusMonths(1);
                } else if (dom1 == 15) {
                    d0 = firstPaymentDate.minusDays(dom1);
                    d2 = d0.plusMonths(1);
                } else {
                    // dom1 < 15
                    if (dom1 + 15 > firstPaymentDate.lengthOfMonth()) {
                        d0 = firstPaymentDate.minusDays(dom1 + 1);
                        d2 = d0.plusMonths(1);
                    } else {
                        d2 = firstPaymentDate.plusDays(15);
                        d0 = d2.minusMonths(1);
                    }
                }
                paymentDates.add(d2); // paymentDates has the first 2 payment days.
                paymentDates.add(0, d0);

                for (int i = 2; i < getNumberOfPayments(); i++) {
                    if (i % 2 == 0) {  // even
                        paymentDates.add(firstPaymentDate.plusMonths(i/2));
                    } else { // odd, 3, 5, ...
                        LocalDate day_i;
                        if (dom1 <= 13 || dom1 == 15) {
                            day_i = d2.plusMonths(i/2);
                        } else if (isEOM1) { // month end
                            day_i = paymentDates.get(paymentDates.size()-1).plusDays(15);
                        } else { // dom1 is between 16 and EOM-1
                            day_i = paymentDates.get(paymentDates.size()-1).minusDays(15).plusMonths(1);
                        }
                        paymentDates.add(day_i);
                    }
                }
                break;
            case BI_WEEKLY:
            case WEEKLY:
                for (int i = 1; i < getNumberOfPayments(); i++) {
                    paymentDates.add(firstPaymentDate.plusDays(i*7L*52/n));
                }
                paymentDates.add(0, firstPaymentDate.minusDays(7*52/n));
                break;
            case DAILY:
                for (int i = 1; i < getNumberOfPayments(); i++) {
                    paymentDates.add(firstPaymentDate.plusDays(i));
                }
                paymentDates.add(0, firstPaymentDate.minusDays(1));
                break;
        }
        return paymentDates;
    }

    /**
     *
     * @param balance  the balance to calculate interest
     * @param interestRate percentage interest rate for the period
     * @param dayCnt number of days of interest accruing
     * @param daysInPeriod number of days in the period
     * @return dollar amount of interest rounded to cents, $1 = 1.00
     */
    private BigDecimal calcInterest(BigDecimal balance, BigDecimal interestRate, long dayCnt, long daysInPeriod) {
        return balance.multiply(interestRate).multiply(BigDecimal.valueOf(dayCnt))
                .divide(BigDecimal.valueOf(daysInPeriod), 0, RoundingMode.HALF_UP).movePointLeft(2);
    }

    private List<PaymentItem> calcPayments(LocalDate date, BigDecimal balance, BigDecimal apr,
                                           BigDecimal paymentAmount) {
        List<PaymentItem> paymentItems = new ArrayList<>();
        final List<LocalDate> paymentDates = getPaymentDates();
        final int n = getNumberOfPayments();
        int i;
        for (i = 0; i < n; i++) {
            if (paymentDates.get(i).isAfter(date))
                break; // paymentDates.get(i) is the first one after date
        }
        final BigDecimal y = getEffectiveInterestRate(apr); // percentage interest rate per period
        final long daysToNextPayment = ChronoUnit.DAYS.between(date, paymentDates.get(i));
        BigDecimal iPayment;
        if (i == 0) {
            // odd day interest, use actual/actual to pro-rate in the year
            final long daysInYear = ChronoUnit.DAYS.between(date, date.plusYears(1));
            // apr in percentage point, the result dailyInterest is round to cents.
            // 1 dollar is BigDecimal.ONE
            iPayment = calcInterest(balance, apr, daysToNextPayment, daysInYear);
        } else {
            // date is between paymentDates(i-1) and (i), pro-rate in the period
            final long daysInPeriod = ChronoUnit.DAYS.between(paymentDates.get(i-1), paymentDates.get(i));
            iPayment = calcInterest(balance, y, daysToNextPayment, daysInPeriod);
        }
        BigDecimal pPayment = paymentAmount.subtract(iPayment);
        balance = balance.subtract(pPayment);
        paymentItems.add(new PaymentItem(i, paymentDates.get(i), pPayment.max(BigDecimal.ZERO), iPayment, balance));

        // now finish the remaining paymentItem
        while (i < n) {
            i++;
            iPayment = y.multiply(balance).setScale(0, RoundingMode.HALF_UP).movePointLeft(2);
            if (i == n)
                pPayment = balance; // we have to pay everything off
            else
                pPayment = paymentAmount.subtract(iPayment);

            balance = balance.subtract(pPayment).max(BigDecimal.ZERO);
            paymentItems.add(new PaymentItem(i, paymentDates.get(i), pPayment.max(BigDecimal.ZERO), iPayment, balance));
            if (balance.compareTo(BigDecimal.ZERO) == 0)
                break; // we are done
        }
        return paymentItems;
    }

    /**
     * given the term of the loan, compute the regular payment amount
     * @return the amount of payment for each period, rounded to cent (0.01)
     */
    private BigDecimal calcPaymentAmount() {
        final int n = getNumberOfPayments();
        final BigDecimal apr = getInterestRate();
        final BigDecimal balance = getOriginalAmount();

        if (apr.compareTo(BigDecimal.ZERO) == 0) // zero interest rate
            return balance.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);

        final BigDecimal y = getEffectiveInterestRate(apr).movePointLeft(2); // in real term, 1% = 0.01
        final BigDecimal onePlusYRaiseToN = y.add(BigDecimal.ONE).pow(n);
        return y.multiply(balance).multiply(onePlusYRaiseToN)
                .divide(onePlusYRaiseToN.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
    }

    void updatePaymentSchedule() {
        // empty the list first
        paymentSchedule.clear();

        if (getFirstPaymentDate() == null || getOriginalAmount() == null
                || getInterestRate() == null || getNumberOfPayments() == null)
            return; // don't have enough input, return now.

        if (getPaymentAmount() == null)
            setPaymentAmount(calcPaymentAmount());

        final List<LocalDate> paymentDates = getPaymentDates();
        // calculate the regular payments (P+I)
        final BigDecimal balance = getOriginalAmount();
        final BigDecimal apr = getInterestRate();
        final BigDecimal y = getEffectiveInterestRate(apr);
        final LocalDate loanDate = getLoanDate();
        final LocalDate d0 = paymentDates.get(0);
        List<PaymentItem> paymentItems = calcPayments(d0, balance, apr, getPaymentAmount());
        // now handle odd day interest
        final long daysToD0 = ChronoUnit.DAYS.between(getLoanDate(), d0);
        final BigDecimal oddDayInterest;

        if (daysToD0 > 0) {
            // own more interest
            final long daysInYear = ChronoUnit.DAYS.between(loanDate, loanDate.plusYears(1));
            // we not only need to calculate the extra interest before d0,
            // to go along with the 'with first' convention, we also need to compound that.
            oddDayInterest = calcInterest(balance, apr, daysToD0, daysInYear)
                    .multiply(BigDecimal.ONE.add(y.movePointLeft(2)));
        } else if (daysToD0 < 0) {
            // owe credit
            final long daysInPeriod = ChronoUnit.DAYS.between(d0, paymentDates.get(1));
            oddDayInterest = calcInterest(balance, getEffectiveInterestRate(apr), daysToD0, daysInPeriod);
        } else {
            oddDayInterest = BigDecimal.ZERO;
        }

        PaymentItem pi = paymentItems.get(0);
        pi.interestAmountProperty.set(pi.interestAmountProperty.get().add(oddDayInterest));

        paymentSchedule.setAll(paymentItems);
    }

    ObservableList<PaymentItem> getPaymentSchedule() { return paymentSchedule; }

    public int getID() { return id; }
    void setID(int id) { this.id = id; }

    public Integer getAccountID() { return getAccountIDProperty().get(); }
    void setAccountID(Integer i) { getAccountIDProperty().set(i); }

    public BigDecimal getOriginalAmount() { return getOriginalAmountProperty().get(); }
    void setOriginalAmount(BigDecimal amount) { getOriginalAmountProperty().set(amount); }

    public BigDecimal getInterestRate() { return getInterestRateProperty().get(); }
    void setInterestRate(BigDecimal interestRateInPct) { getInterestRateProperty().set(interestRateInPct); }

    public Period getCompoundingPeriod() { return getCompoundingPeriodProperty().get(); }
    void setCompoundingPeriod(Period p) { getCompoundingPeriodProperty().set(p); }

    public Period getPaymentPeriod() { return getPaymentPeriodProperty().get(); }
    void setPaymentPeriod(Period pp) { getPaymentPeriodProperty().set(pp); }

    public Integer getNumberOfPayments() { return getNumberOfPaymentsProperty().get(); }
    void setNumberOfPayments(Integer n) { getNumberOfPaymentsProperty().set(n); }

    public LocalDate getLoanDate() { return getLoanDateProperty().get(); }
    void setLoanDate(LocalDate date) { getLoanDateProperty().set(date); }

    public LocalDate getFirstPaymentDate() { return getFirstPaymentDateProperty().get(); }
    void setFirstPaymentDate(LocalDate date) { getFirstPaymentDateProperty().set(date); }

    public BigDecimal getPaymentAmount() { return paymentAmountProperty.get(); }
    void setPaymentAmount(BigDecimal paymentAmount) { getPaymentAmountProperty().set(paymentAmount); }

    ObjectProperty<Integer> getAccountIDProperty() { return accountIDProperty; }
    ObjectProperty<BigDecimal> getOriginalAmountProperty() { return originalAmountProperty; }
    ObjectProperty<BigDecimal> getInterestRateProperty() { return interestRateProperty; }
    ObjectProperty<Period> getCompoundingPeriodProperty() { return compoundingPeriodProperty; }
    ObjectProperty<Period> getPaymentPeriodProperty() { return paymentPeriodProperty; }
    ObjectProperty<Integer> getNumberOfPaymentsProperty() { return numberOfPaymentsProperty; }
    ObjectProperty<LocalDate> getLoanDateProperty() { return loanDateProperty; }
    ObjectProperty<LocalDate> getFirstPaymentDateProperty() { return firstPaymentDateProperty; }
    ObjectProperty<BigDecimal> getPaymentAmountProperty() { return paymentAmountProperty; }
}
