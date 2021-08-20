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

import java.math.BigDecimal;
import java.time.LocalDate;

public class LoanTransaction {
    // three types of loan transactions
    public enum Type {
        REGULAR_PAYMENT, // regular payment
        EXTRA_PAYMENT, // extra payment
        RATE_CHANGE // interest rate change
    }

    private final int id; // loan transaction id
    private final Type type; // the type
    private final int loanAccountId; // the loan account id
    private int transactionId; // the corresponding transaction id in the transaction table, 0 for RATE_CHANGE type.
    private final LocalDate date; // the transaction date for rate change type.
    private final BigDecimal interestRate; // the new rate in percentage for rate change type, null for payment type
    private final BigDecimal amount; // the new payment amount for rate change type, round to cents, $1 = 1.00

    public LoanTransaction(int id, Type type, int loanId, int transactionId, LocalDate date,
                    BigDecimal interestRate, BigDecimal amount) {
        this.id = id;
        this.type = type;
        this.loanAccountId = loanId;
        this.transactionId = transactionId;
        this.date = date;
        this.interestRate = interestRate;
        this.amount = amount;
    }

    public int getId() { return id; }
    public Type getType() { return type; }
    public int getLoanAccountId() { return loanAccountId; }
    public int getTransactionId() { return transactionId; }
    public LocalDate getDate() { return date; }
    public BigDecimal getInterestRate() { return interestRate; }
    public BigDecimal getAmount() { return amount; }

    void setTransactionId(int tid) { transactionId = tid; }
}
