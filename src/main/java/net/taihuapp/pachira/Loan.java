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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Loan {

    static class PaymentItem {
        // sequence id, from 1 to n
        private final ObjectProperty<Integer> sequenceIDProperty = new SimpleObjectProperty<>();
        private final ObjectProperty<LocalDate> dateProperty = new SimpleObjectProperty<>();
        private final ObjectProperty<BigDecimal> principalAmountProperty = new SimpleObjectProperty<>();
        private final ObjectProperty<BigDecimal> interestAmountProperty = new SimpleObjectProperty<>();
        private final ObjectProperty<BigDecimal> balanceAmountProperty = new SimpleObjectProperty<>();
        private final ObjectProperty<Boolean> isPaidProperty = new SimpleObjectProperty<>(false);

        PaymentItem(Integer seq, LocalDate d, BigDecimal p, BigDecimal i, BigDecimal b) {
            sequenceIDProperty.set(seq);
            dateProperty.set(d);
            principalAmountProperty.set(p);
            interestAmountProperty.set(i);
            balanceAmountProperty.set(b);
        }

        public ObjectProperty<Integer> getSequenceIDProperty() { return sequenceIDProperty; }
        ObjectProperty<LocalDate> getDateProperty() { return dateProperty; }
        ObjectProperty<BigDecimal> getPrincipalAmountProperty() { return principalAmountProperty; }
        ObjectProperty<BigDecimal> getInterestAmountProperty() { return interestAmountProperty; }
        public ObjectProperty<BigDecimal> getBalanceAmountProperty() { return balanceAmountProperty; }
        public ObjectProperty<Boolean> getIsPaidProperty() { return isPaidProperty; }

        LocalDate getDate() { return getDateProperty().get(); }
        BigDecimal getPrincipalAmount() { return getPrincipalAmountProperty().get(); }
        BigDecimal getInterestAmount() { return getInterestAmountProperty().get(); }
    }

    private final ObjectProperty<Integer> accountIDProperty = new SimpleObjectProperty<>(-1);
    private final ObjectProperty<BigDecimal> originalAmountProperty = new SimpleObjectProperty<>(null);
    private final ObjectProperty<BigDecimal> interestRateProperty = new SimpleObjectProperty<>(null);
    private final ObjectProperty<DateSchedule.BaseUnit> compoundBaseUnitProperty =
            new SimpleObjectProperty<>(DateSchedule.BaseUnit.MONTH);
    private final ObjectProperty<Integer> compoundBURepeatProperty = new SimpleObjectProperty<>(1);
    private final DateSchedule paymentDateSchedule = new DateSchedule(DateSchedule.BaseUnit.MONTH, 1,
            LocalDate.now().plusMonths(1),null,true, true);
    private final ObjectProperty<LocalDate> loanDateProperty = new SimpleObjectProperty<>(LocalDate.now());
    private final ObjectProperty<Integer> numberOfPaymentsProperty = new SimpleObjectProperty<>(null);
    private final ObjectProperty<BigDecimal> paymentAmountProperty = new SimpleObjectProperty<>(null);
    private final ObservableList<PaymentItem> paymentSchedule = FXCollections.observableArrayList();
    private final BooleanProperty calcPaymentAmountProperty = new SimpleBooleanProperty(true);
    private final ObservableList<LoanTransaction> loanTransactionList = FXCollections.observableArrayList();

    private void setupBindings() {
        // these properties will affect payment amounts but not payment dates
        getOriginalAmountProperty().addListener((obs, o, n) -> updatePaymentSchedule());
        getInterestRateProperty().addListener((obs, o, n) -> updatePaymentSchedule());
        getCompoundBaseUnitProperty().addListener((obs, o, n) -> updatePaymentSchedule());
        getCompoundBURepeatProperty().addListener((obs, o, n) -> updatePaymentSchedule());
        getLoanDateProperty().addListener((obs, o, n) -> updatePaymentSchedule());
        getPaymentAmountProperty().addListener((obs, o, n) -> updatePaymentSchedule());

        // these properties will affect both payment amounts and payment dates
        paymentDateSchedule.getStartDateProperty().addListener((obs, o, n) -> updatePaymentSchedule());
        paymentDateSchedule.getBaseUnitProperty().addListener((obs, o, n) -> updatePaymentSchedule());
        paymentDateSchedule.getNumPeriodProperty().addListener((obs, o, n) -> updatePaymentSchedule());
        getNumberOfPaymentsProperty().addListener((obs, o, n) -> updatePaymentSchedule());
    }

    Loan() { setupBindings(); }

    /**
     *
     * @param accountId: the account id associated with the loan
     * @param compoundBaseUnit: compound base unit
     * @param compoundBURepeat: compound base unit repeat
     *
     * @param originalAmount: original amount
     * @param interestRate: original interest rate, in %, 1% = 1
     * @param loanDate: loan initiation date
     * @param paymentAmount: regular payment amount for principal+interest
     */
    public Loan(int accountId, DateSchedule.BaseUnit compoundBaseUnit, int compoundBURepeat,
                DateSchedule.BaseUnit paymentBaseUnit, int paymentBURepeat, LocalDate firstPaymentDate,
                int numberOfPayments, BigDecimal originalAmount, BigDecimal interestRate, LocalDate loanDate,
                BigDecimal paymentAmount) {

        setupBindings();

        setAccountID(accountId);
        setCompoundBaseUnit(compoundBaseUnit);
        setCompoundBURepeat(compoundBURepeat);

        setOriginalAmount(originalAmount);
        setInterestRate(interestRate);
        setLoanDate(loanDate);
        setPaymentAmount(paymentAmount);
        setCalcPaymentAmount(paymentAmount == null);

        // setup payment DateSchedule
        setPaymentBaseUnit(paymentBaseUnit);
        setPaymentBURepeat(paymentBURepeat);
        setFirstPaymentDate(firstPaymentDate);
        setNumberOfPayments(numberOfPayments);
    }

    /**
     *
     * @param apr annual percentage rate, 1% == BigDecimal.ONE
     * @return effective interest rate in percentage term, 1% is 1
     */
    private BigDecimal getEffectiveInterestRate(BigDecimal apr) {
        double r = apr.doubleValue()/100;
        int n = DateSchedule.numberOfPeriodsPerYear(getCompoundBaseUnit())*getCompoundBURepeat();
        int m = DateSchedule.numberOfPeriodsPerYear(getPaymentDateSchedule().getBaseUnit())
                *getPaymentDateSchedule().getNumPeriod();
        return BigDecimal.valueOf(Math.pow(1+r/n, ((double) n)/((double) m))-1).movePointRight(2);
    }

    /**
     * @return a list of LocalDates (getNumberOfPayments()+1 elements)
     * element 0 is the date exactly one full period before start date.
     * element 1 to getNumberOfPayments() are the due dates.
     */
    private List<LocalDate> getPaymentDates() {
        DateSchedule dateSchedule = getPaymentDateSchedule();
        List<LocalDate> paymentDates = new ArrayList<>();
        LocalDate d = dateSchedule.getStartDate();
        paymentDates.add(dateSchedule.getPrevDueDate(d));
        for (int i = 0; i < getNumberOfPayments(); i++) {
            paymentDates.add(d);
            d = dateSchedule.getNextDueDate(d);
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

    private void updatePaymentStatus() {
        final Set<LocalDate> paidDateSet = loanTransactionList.stream().map(LoanTransaction::getDate)
                .collect(Collectors.toSet());
        for (PaymentItem pi : getPaymentSchedule())
            pi.getIsPaidProperty().set(paidDateSet.contains(pi.getDate()));
    }

    /**
     * Given an input date, remaining balance, apr, and payment amount, compute the remaining payments
     * @param date: the input date
     * @param balance: the remaining balance
     * @param apr: the annual percentage rate
     * @param paymentAmount: the set payment amount
     * @return a list of payment items
     */
    private List<PaymentItem> calcPayments(LocalDate date, BigDecimal balance, BigDecimal apr,
                                           BigDecimal paymentAmount) {
        List<PaymentItem> paymentItems = new ArrayList<>();
        final List<LocalDate> paymentDates = getPaymentDates();
        final int n = paymentDates.size();

        // find i such that paymentDates.get(i) is the first one after date
        int i = 0;
        while (i < n && !paymentDates.get(i).isAfter(date))
            i++;

        // calculate payment break down for the first (possibly partial) period
        final BigDecimal y = getEffectiveInterestRate(apr); // percentage interest rate per period
        final BigDecimal oddDayInterest;
        if (i == 0) {
            // date is before paymentDays.get(0)
            final long daysToD0 = ChronoUnit.DAYS.between(date, paymentDates.get(0));
            // calculate odd day interest
            // odd day interest, use actual/actual to pro-rate in the year
            final long daysInYear = ChronoUnit.DAYS.between(date, date.plusYears(1));
            // a word of caution here:  calcInterest() returns a rounded (to cents) interest
            // should we compound first then round?
            oddDayInterest = BigDecimal.ONE.add(y.movePointLeft(2))
                    .multiply(calcInterest(balance, apr, daysToD0, daysInYear))
                    .setScale(2, RoundingMode.HALF_UP);
            i = 1;
        } else {
            // calculate adjustment to the regular interest
            // date is between paymentDates(i-1) and (i), pro-rate in the period
            oddDayInterest = calcInterest(balance, y, ChronoUnit.DAYS.between(date, paymentDates.get(i-1)),
                    ChronoUnit.DAYS.between(paymentDates.get(i-1), paymentDates.get(i)));
        }
        final long daysInPeriod = ChronoUnit.DAYS.between(paymentDates.get(i - 1), paymentDates.get(i));
        BigDecimal iPayment = calcInterest(balance, y, daysInPeriod, daysInPeriod); // regular full period interest
        BigDecimal pPayment = (i == n-1) ? balance : paymentAmount.subtract(iPayment); // need to pay off at the last
        balance = balance.subtract(pPayment);
        paymentItems.add(new PaymentItem(i, paymentDates.get(i), pPayment.max(BigDecimal.ZERO),
                iPayment.add(oddDayInterest), balance));

        // now finish the remaining paymentItem
        while (++i < n) {
            iPayment = y.multiply(balance).setScale(0, RoundingMode.HALF_UP).movePointLeft(2);
            pPayment = (i == n-1) ? balance : paymentAmount.subtract(iPayment);  // need to pay off at the last
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

        if (paymentDateSchedule.getStartDate() == null || getOriginalAmount() == null
                || getInterestRate() == null || getNumberOfPayments() == null
                || getCompoundBURepeat() == null || getPaymentBURepeat() == null)
            return; // don't have enough input, return now.

        if (getCalcPaymentAmount())
            setPaymentAmount(calcPaymentAmount());

        if (getPaymentAmount() == null)
            return;

        // calculate the regular payments (P+I)
        paymentSchedule.setAll(calcPayments(getLoanDate(), getOriginalAmount(), getInterestRate(), getPaymentAmount()));

        // update paid status
        updatePaymentStatus();
    }

    ObservableList<PaymentItem> getPaymentSchedule() { return paymentSchedule; }

    Optional<PaymentItem> getPaymentItem(LocalDate dueDate) {
        return getPaymentSchedule().stream().filter(paymentItem -> paymentItem.getDate().isEqual(dueDate)).findAny();
    }

    public Integer getAccountID() { return getAccountIDProperty().get(); }
    void setAccountID(Integer i) { getAccountIDProperty().set(i); }

    public BigDecimal getOriginalAmount() { return getOriginalAmountProperty().get(); }
    void setOriginalAmount(BigDecimal amount) { getOriginalAmountProperty().set(amount); }

    public BigDecimal getInterestRate() { return getInterestRateProperty().get(); }
    void setInterestRate(BigDecimal interestRateInPct) { getInterestRateProperty().set(interestRateInPct); }

    public DateSchedule.BaseUnit getCompoundBaseUnit() { return getCompoundBaseUnitProperty().get(); }
    void setCompoundBaseUnit(DateSchedule.BaseUnit bu) { getCompoundBaseUnitProperty().set(bu); }

    public Integer getCompoundBURepeat() { return getCompoundBURepeatProperty().get(); }
    void setCompoundBURepeat(int repeat) { getCompoundBURepeatProperty().set(repeat); }

    private DateSchedule getPaymentDateSchedule() { return paymentDateSchedule; }

    public DateSchedule.BaseUnit getPaymentBaseUnit() { return getPaymentBaseUnitProperty().get(); }
    void setPaymentBaseUnit(DateSchedule.BaseUnit bu) { getPaymentBaseUnitProperty().set(bu); }

    public Integer getPaymentBURepeat() { return getPaymentBURepeatProperty().get(); }
    void setPaymentBURepeat(int repeat) { getPaymentBURepeatProperty().set(repeat); }

    public Integer getNumberOfPayments() { return getNumberOfPaymentsProperty().get(); }
    void setNumberOfPayments(Integer n) { getNumberOfPaymentsProperty().set(n); }

    public LocalDate getLoanDate() { return getLoanDateProperty().get(); }
    void setLoanDate(LocalDate date) { getLoanDateProperty().set(date); }

    public LocalDate getFirstPaymentDate() { return getFirstPaymentDateProperty().get(); }
    void setFirstPaymentDate(LocalDate date) { getFirstPaymentDateProperty().set(date); }

    public BigDecimal getPaymentAmount() { return getPaymentAmountProperty().get(); }
    void setPaymentAmount(BigDecimal paymentAmount) { getPaymentAmountProperty().set(paymentAmount); }

    public Boolean getCalcPaymentAmount() { return getCalcPaymentAmountProperty().get(); }
    void setCalcPaymentAmount(boolean b) { getCalcPaymentAmountProperty().set(b); }

    public void setLoanTransactionList(List<LoanTransaction> list) {
        loanTransactionList.setAll(list);
        updatePaymentStatus();
    }

    public void addLoanTransaction(LoanTransaction loanTransaction) {
        loanTransactionList.add(loanTransaction);
        updatePaymentStatus();
    }

    ObjectProperty<Integer> getAccountIDProperty() { return accountIDProperty; }
    ObjectProperty<BigDecimal> getOriginalAmountProperty() { return originalAmountProperty; }
    ObjectProperty<BigDecimal> getInterestRateProperty() { return interestRateProperty; }
    ObjectProperty<DateSchedule.BaseUnit> getCompoundBaseUnitProperty() { return compoundBaseUnitProperty; }
    ObjectProperty<Integer> getCompoundBURepeatProperty() { return compoundBURepeatProperty; }
    ObjectProperty<DateSchedule.BaseUnit> getPaymentBaseUnitProperty() {
        return getPaymentDateSchedule().getBaseUnitProperty();
    }
    ObjectProperty<Integer> getPaymentBURepeatProperty() { return getPaymentDateSchedule().getNumPeriodProperty(); }
    ObjectProperty<Integer> getNumberOfPaymentsProperty() { return numberOfPaymentsProperty; }
    ObjectProperty<LocalDate> getLoanDateProperty() { return loanDateProperty; }
    ObjectProperty<LocalDate> getFirstPaymentDateProperty() { return getPaymentDateSchedule().getStartDateProperty(); }
    ObjectProperty<BigDecimal> getPaymentAmountProperty() { return paymentAmountProperty; }
    BooleanProperty getCalcPaymentAmountProperty() { return calcPaymentAmountProperty; }
}
