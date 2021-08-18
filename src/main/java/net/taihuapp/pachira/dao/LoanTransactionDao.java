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

import net.taihuapp.pachira.LoanTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class LoanTransactionDao extends Dao<LoanTransaction, Integer> {

    LoanTransactionDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "LOAN_TRANSACTIONS"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{ "ID" }; }

    @Override
    String[] getColumnNames() {
        return new String[]{ "TYPE", "LOAN_ID", "TRANSACTION_ID",  "DATE", "INTEREST_RATE", "AMOUNT" };
    }

    @Override
    boolean autoGenKey() { return true; }

    @Override
    Integer getKeyValue(LoanTransaction loanTransaction) { return loanTransaction.getId(); }

    @Override
    LoanTransaction fromResultSet(ResultSet resultSet) throws SQLException, DaoException {
        return new LoanTransaction(
                resultSet.getInt("ID"),
                LoanTransaction.Type.valueOf(resultSet.getString("TYPE")),
                resultSet.getInt("LOAN_ID"),
                resultSet.getInt("TRANSACTION_ID"),
                resultSet.getObject("DATE", LocalDate.class),
                resultSet.getBigDecimal("INTEREST_RATE"),
                resultSet.getBigDecimal("AMOUNT")
        );
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, LoanTransaction loanTransaction, boolean withKey)
            throws SQLException {
        preparedStatement.setString(1, loanTransaction.getType().name());
        preparedStatement.setInt(2, loanTransaction.getLoanId());
        preparedStatement.setInt(3, loanTransaction.getTransactionId());
        preparedStatement.setObject(4, loanTransaction.getDate());
        preparedStatement.setBigDecimal(5, loanTransaction.getInterestRate());
        preparedStatement.setBigDecimal(6, loanTransaction.getAmount());
        if (withKey)
            preparedStatement.setInt(7, loanTransaction.getId());
    }

    List<LoanTransaction> getByLoanId(int loanId) throws DaoException {
        final String sqlCmd = "select * from " + getTableName() + " where LOAN_ID = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            preparedStatement.setInt(1, loanId);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                final List<LoanTransaction> loanTransactions = new ArrayList<>();
                while (resultSet.next()) {
                    loanTransactions.add(fromResultSet(resultSet));
                }
                return loanTransactions;
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET,
                    "Failed to get loan transactions for loan " + loanId, e);
        }
    }

    int deleteByLoanId(int loanId) throws DaoException {
        final String sqlCmd = "delete from " + getTableName() + " where LOAN_ID = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            preparedStatement.setInt(1, loanId);
            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_DELETE,
                    "Failed to delete loan transactions for loan " + loanId, e);
        }
    }
}
