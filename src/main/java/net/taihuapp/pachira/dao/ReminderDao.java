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

import javafx.util.Pair;
import net.taihuapp.pachira.DateSchedule;
import net.taihuapp.pachira.Reminder;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;

public class ReminderDao extends Dao<Reminder, Integer> {

    private final PairTidSplitTransactionListDao pairTidSplitTransactionListDao;

    ReminderDao(Connection connection, PairTidSplitTransactionListDao pairTidSplitTransactionListDao) {
        this.connection = connection;
        this.pairTidSplitTransactionListDao = pairTidSplitTransactionListDao;
    }

    @Override
    String getTableName() { return "REMINDERS"; }

    @Override
    String[] getKeyColumnNames() {
        return new String[]{ "ID" };
    }

    @Override
    String[] getColumnNames() {
        return new String[]{ "TYPE", "PAYEE", "AMOUNT", "ESTCOUNT", "ACCOUNTID", "CATEGORYID", "TAGID",
                "MEMO", "STARTDATE", "ENDDATE", "BASEUNIT", "NUMPERIOD", "ALERTDAYS", "ISDOM", "ISFWD" };
    }

    @Override
    boolean autoGenKey() { return true; }

    @Override
    Integer getKeyValue(Reminder reminder) { return reminder.getID(); }

    @Override
    Reminder fromResultSet(ResultSet resultSet) throws SQLException, DaoException {
        final int id = resultSet.getInt("ID");
        final Reminder.Type type = Reminder.Type.valueOf(resultSet.getString("TYPE"));
        final String payee = resultSet.getString("PAYEE");
        final BigDecimal amount = resultSet.getBigDecimal("AMOUNT");
        final int estCount = resultSet.getInt("ESTCOUNT");
        final int accountID = resultSet.getInt("ACCOUNTID");
        final int categoryID = resultSet.getInt("CATEGORYID");
        final int tagID = resultSet.getInt("TAGID");
        final String memo = resultSet.getString("MEMO");
        final LocalDate startDate = resultSet.getObject("STARTDATE", LocalDate.class);
        final LocalDate endDate = resultSet.getObject("ENDDATE", LocalDate.class);
        DateSchedule.BaseUnit baseUnit = DateSchedule.BaseUnit.valueOf(resultSet.getString("BASEUNIT"));
        final int numPeriod = resultSet.getInt("NUMPERIOD");
        final int alertDays = resultSet.getInt("ALERTDAYS");
        final boolean isDOM = resultSet.getBoolean("ISDOM");
        final boolean isFWD = resultSet.getBoolean("ISFWD");

        DateSchedule dateSchedule = new DateSchedule(baseUnit, numPeriod, startDate, endDate, alertDays, isDOM, isFWD);
        return new Reminder(id, type, payee, amount, estCount, accountID, categoryID, tagID, memo, dateSchedule,
                pairTidSplitTransactionListDao.get(-id).map(Pair::getValue).orElse(new ArrayList<>()));
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, Reminder reminder, boolean withKey) throws SQLException {
        preparedStatement.setString(1, reminder.getType().name());
        preparedStatement.setString(2, reminder.getPayee());
        preparedStatement.setBigDecimal(3, reminder.getAmount());
        preparedStatement.setInt(4, reminder.getEstimateCount());
        preparedStatement.setInt(5, reminder.getAccountID());
        preparedStatement.setInt(6, reminder.getCategoryID());
        preparedStatement.setInt(7, reminder.getTagID());
        preparedStatement.setString(8, reminder.getMemo());
        preparedStatement.setObject(9, reminder.getDateSchedule().getStartDate());
        preparedStatement.setObject(10, reminder.getDateSchedule().getEndDate());
        preparedStatement.setString(11, reminder.getDateSchedule().getBaseUnit().name());
        preparedStatement.setInt(12, reminder.getDateSchedule().getNumPeriod());
        preparedStatement.setInt(13, reminder.getDateSchedule().getAlertDay());
        preparedStatement.setBoolean(14, reminder.getDateSchedule().isDOMBased());
        preparedStatement.setBoolean(15, reminder.getDateSchedule().isForward());
        if (withKey)
            preparedStatement.setInt(16, reminder.getID());
    }
}
