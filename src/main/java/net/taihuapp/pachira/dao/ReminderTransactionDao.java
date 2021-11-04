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

import net.taihuapp.pachira.ReminderTransaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class ReminderTransactionDao extends Dao<ReminderTransaction, Integer> {

    ReminderTransactionDao(Connection connection) {
        this.connection = connection;
    }

    @Override
    String getTableName() { return "REMINDERTRANSACTIONS"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{ "REMINDERID" }; }

    @Override
    String[] getColumnNames() { return new String[]{ "DUEDATE", "TRANSACTIONID"}; }

    @Override
    boolean autoGenKey() { return false; }

    @Override
    Integer getKeyValue(ReminderTransaction reminderTransaction) { return reminderTransaction.getReminderId(); }

    @Override
    ReminderTransaction fromResultSet(ResultSet resultSet) throws SQLException, DaoException {
        final int reminderID = resultSet.getInt("REMINDERID");
        final LocalDate dueDate = resultSet.getObject("DUEDATE", LocalDate.class);
        final int transactionID = resultSet.getInt("TRANSACTIONID");

        // these reminder transactions are either entered or skipped, the alert days is not used, enter 0
        return new ReminderTransaction(reminderID, dueDate, transactionID, 0, BigDecimal.ZERO);
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, ReminderTransaction reminderTransaction,
                              boolean withKey) throws SQLException {
        preparedStatement.setObject(1, reminderTransaction.getDueDate());
        preparedStatement.setInt(2, reminderTransaction.getTransactionID());
        if (withKey)
            preparedStatement.setInt(3, reminderTransaction.getReminderId());
    }
}
