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

package net.taihuapp.pachira.dao;

import net.taihuapp.pachira.Loan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class LoanDao extends Dao<Loan, Integer> {

    LoanDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "LOANS"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{ "ID" }; }

    @Override
    String[] getColumnNames() {
        return new String[]{ "ACCOUNT_ID", "AMOUNT", "INTEREST_RATE", "COMPOUND_PERIOD", "PAYMENT_PERIOD",
                "NUMBER_OF_PAYMENTS", "LOAN_DATE", "FIRST_PAYMENT_DATE", "PAYMENT_AMOUNT" };
    }

    @Override
    boolean autoGenKey() { return true; }

    @Override
    Integer getKeyValue(Loan loan) { return loan.getID(); }

    @Override
    Loan fromResultSet(ResultSet resultSet) throws SQLException, DaoException {
        return new Loan(resultSet.getInt("ID"),
                resultSet.getInt("ACCOUNT_ID"),
                resultSet.getBigDecimal("AMOUNT"),
                resultSet.getBigDecimal("INTEREST_RATE"),
                Loan.Period.valueOf(resultSet.getString("COMPOUND_PERIOD")),
                Loan.Period.valueOf(resultSet.getString("PAYMENT_PERIOD")),
                resultSet.getInt("NUMBER_OF_PAYMENTS"),
                resultSet.getObject("LOAN_DATE", LocalDate.class),
                resultSet.getObject("FIRST_PAYMENT_DATE", LocalDate.class),
                resultSet.getBigDecimal("PAYMENT_AMOUNT"));
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, Loan loan, boolean withKey) throws SQLException {
        preparedStatement.setInt(1, loan.getAccountID());
        preparedStatement.setBigDecimal(2, loan.getOriginalAmount());
        preparedStatement.setBigDecimal(3, loan.getInterestRate());
        preparedStatement.setString(4, loan.getCompoundingPeriod().name());
        preparedStatement.setString(5, loan.getPaymentPeriod().name());
        preparedStatement.setInt(6, loan.getNumberOfPayments());
        preparedStatement.setObject(7, loan.getLoanDate());
        preparedStatement.setObject(8, loan.getFirstPaymentDate());
        preparedStatement.setBigDecimal(9, loan.getPaymentAmount());
        if (withKey)
            preparedStatement.setInt(10, loan.getID());
    }
}
