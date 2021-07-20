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
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Loan {

    enum Period {
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
        private final ObjectProperty<Double> principalAmountProperty = new SimpleObjectProperty<>();
        private final ObjectProperty<Double> interestAmountProperty = new SimpleObjectProperty<>();
        private final ObjectProperty<Double> balanceAmountProperty = new SimpleObjectProperty<>();

        PaymentItem(Integer seq, LocalDate d, Double p, Double i, Double b) {
            sequenceIDProperty.set(seq);
            dateProperty.set(d);
            principalAmountProperty.set(p);
            interestAmountProperty.set(i);
            balanceAmountProperty.set(b);
        }

        public ObjectProperty<Integer> getSequenceIDProperty() { return sequenceIDProperty; }
        public ObjectProperty<LocalDate> getDateProperty() { return dateProperty; }
        public ObjectProperty<Double> getPrincipalAmountProperty() { return principalAmountProperty; }
        public ObjectProperty<Double> getInterestAmountProperty() { return interestAmountProperty; }
        public ObjectProperty<Double> getBalanceAmountProperty() { return balanceAmountProperty; }
    }

    private int id = -1;
    private final ObjectProperty<Integer> accountIDProperty = new SimpleObjectProperty<>(-1);
    private final StringProperty nameProperty = new SimpleStringProperty();
    private final StringProperty descriptionProperty = new SimpleStringProperty();
    private final ObjectProperty<Double> originalAmountProperty = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Double> interestRateProperty = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Period> compoundingPeriodProperty = new SimpleObjectProperty<>(Period.MONTHLY);
    private final ObjectProperty<Period> paymentPeriodProperty = new SimpleObjectProperty<>(Period.MONTHLY);
    private final ObjectProperty<Integer> numberOfPaymentsProperty = new SimpleObjectProperty<>(null);
    private final ObjectProperty<LocalDate> firstPaymentDateProperty = new SimpleObjectProperty<>(LocalDate.now());
    private final ObservableList<PaymentItem> paymentSchedule = FXCollections.observableArrayList();

    Loan() {}

    // copy constructor
    Loan(Loan loan) {
        setID(loan.getID());
        setAccountID(loan.getAccountID());
        setName(loan.getName());
        setDescription(loan.getDescription());
        setOriginalAmount(loan.getOriginalAmount());
        setInterestRate(loan.getInterestRate());
        setCompoundingPeriod(loan.getCompoundingPeriod());
        setPaymentPeriod(loan.getPaymentPeriod());
        setNumberOfPayments(loan.getNumberOfPayments());
        setFirstPaymentDate(loan.getFirstPaymentDate());
    }

    /**
     *
     * @return the interest rate per payment period
     */
    private double getEffectiveInterestRate() {
        double r = getInterestRate()/100;
        int n = getCompoundingPeriod().getCountsPerYear();
        int m = getPaymentPeriod().getCountsPerYear();
        return (Math.pow(1+r/n, ((double) n)/((double) m))-1);
    }

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
                break;
            case SEMI_MONTHLY:
                final int dom1 = firstPaymentDate.getDayOfMonth();
                final boolean isEOM1 = firstPaymentDate.lengthOfMonth() == dom1;
                final LocalDate d2;
                if (isEOM1) {
                    // dom1 is end of month
                    d2 = firstPaymentDate.plusDays(15);
                } else if (dom1 > 15) {
                    d2 = firstPaymentDate.minusDays(15).plusMonths(1);
                } else if (dom1 == 15) {
                    d2 = firstPaymentDate.minusDays(dom1).plusMonths(1);
                } else {
                    // dom1 < 15
                    if (dom1 + 15 > firstPaymentDate.lengthOfMonth())
                        d2 = firstPaymentDate.minusDays(dom1+1).plusMonths(1);
                    else
                        d2 = firstPaymentDate.plusDays(15);
                }
                paymentDates.add(d2); // paymentDates has the first 2 payment days.

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
                break;
            case DAILY:
                for (int i = 1; i < getNumberOfPayments(); i++) {
                    paymentDates.add(firstPaymentDate.plusDays(i));
                }
                break;
        }
        return paymentDates;
    }

    void updatePaymentSchedule() {
        // empty the list first
        paymentSchedule.clear();

        if (getFirstPaymentDate() == null || getOriginalAmount() == null
                || getInterestRate() == null || getNumberOfPayments() == null)
            return;

        final int n = getNumberOfPayments();
        final double y = getEffectiveInterestRate();
        double balance = getOriginalAmount();
        final double payment;
        if (Math.abs(y) < 1e-10) {
            // zero interest
            payment = balance/n;
        } else {
            final double onePlusYRaiseToN = Math.pow(1 + y, n);
            payment = Math.round(100 * y * balance * onePlusYRaiseToN / (onePlusYRaiseToN - 1)) / 1e2; // round to cent
        }
        List<LocalDate> paymentDates = getPaymentDates();
        // calculate first n-1 payments according to the formula
        for (int i = 0; i < n; i++) {
            double iPayment = Math.round(100*y*balance)/1e2; // round to nearest cent
            double pPayment = (i == n-1) ? balance : Math.round(100*(payment-iPayment))/1e2;
            if (balance > pPayment)
                balance -= pPayment;
            else {
                pPayment = balance;
                balance = 0;
            }
            paymentSchedule.add(new PaymentItem(i+1, paymentDates.get(i), pPayment, iPayment, balance));
            if (balance < 1e-6)
                break;
        }
    }

    ObservableList<PaymentItem> getPaymentSchedule() { return paymentSchedule; }

    int getID() { return id; }
    void setID(int id) { this.id = id; }

    Integer getAccountID() { return getAccountIDProperty().get(); }
    void setAccountID(Integer i) { getAccountIDProperty().set(i); }

    String getName() { return getNameProperty().get(); }
    void setName(String name) { getNameProperty().set(name); }

    String getDescription() { return getDescriptionProperty().get(); }
    void setDescription(String description) { getDescriptionProperty().set(description); }

    Double getOriginalAmount() { return getOriginalAmountProperty().get(); }
    void setOriginalAmount(Double amount) { getOriginalAmountProperty().set(amount); }

    Double getInterestRate() { return getInterestRateProperty().get(); }
    void setInterestRate(Double interestRateInPct) { getInterestRateProperty().set(interestRateInPct); }

    Period getCompoundingPeriod() { return getCompoundingPeriodProperty().get(); }
    void setCompoundingPeriod(Period p) { getCompoundingPeriodProperty().set(p); }

    Period getPaymentPeriod() { return getPaymentPeriodProperty().get(); }
    void setPaymentPeriod(Period pp) { getPaymentPeriodProperty().set(pp); }

    Integer getNumberOfPayments() { return getNumberOfPaymentsProperty().get(); }
    void setNumberOfPayments(Integer n) { getNumberOfPaymentsProperty().set(n); }

    LocalDate getFirstPaymentDate() { return getFirstPaymentDateProperty().get(); }
    void setFirstPaymentDate(LocalDate date) { getFirstPaymentDateProperty().set(date); }

    ObjectProperty<Integer> getAccountIDProperty() { return accountIDProperty; }
    StringProperty getNameProperty() { return nameProperty; }
    StringProperty getDescriptionProperty() { return descriptionProperty; }
    ObjectProperty<Double> getOriginalAmountProperty() { return originalAmountProperty; }
    ObjectProperty<Double> getInterestRateProperty() { return interestRateProperty; }
    ObjectProperty<Period> getCompoundingPeriodProperty() { return compoundingPeriodProperty; }
    ObjectProperty<Period> getPaymentPeriodProperty() { return paymentPeriodProperty; }
    ObjectProperty<Integer> getNumberOfPaymentsProperty() { return numberOfPaymentsProperty; }
    ObjectProperty<LocalDate> getFirstPaymentDateProperty() { return firstPaymentDateProperty; }
}
