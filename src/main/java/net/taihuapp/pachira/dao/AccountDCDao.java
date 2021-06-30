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

import net.taihuapp.pachira.AccountDC;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Dao for AccountDC (Account Direct Connect)
 */
public class AccountDCDao extends Dao<AccountDC, Integer> {

    AccountDCDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "ACCOUNTDCS"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{"ACCOUNTID"}; }

    @Override
    String[] getColumnNames() {
        return new String[]{ "ACCOUNTTYPE", "DCID", "ROUTINGNUMBER", "ACCOUNTNUMBER",
                "LASTDOWNLOADDATE", "LASTDOWNLOADTIME", "LASTDOWNLOADLEDGEBAL" };
    }

    @Override
    boolean autoGenKey() { return false; }

    @Override
    Integer getKeyValue(AccountDC accountDC) {
        return accountDC.getAccountID();
    }

    @Override
    AccountDC fromResultSet(ResultSet resultSet) throws SQLException {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        LocalDate localDate = resultSet.getObject("LASTDOWNLOADDATE", LocalDate.class);
        LocalTime localTime = resultSet.getObject("LASTDOWNLOADTIME", LocalTime.class);
        //noinspection MagicConstant
        calendar.set(localDate.getYear(), localDate.getMonthValue()-1, localDate.getDayOfMonth(),
                localTime.getHour(), localTime.getMinute(), localTime.getSecond());
        Date lastDownloadDate = calendar.getTime();
        return new AccountDC(resultSet.getInt("ACCOUNTID"), resultSet.getString("ACCOUNTTYPE"),
                resultSet.getInt("DCID"), resultSet.getString("ROUTINGNUMBER"),
                resultSet.getString("ACCOUNTNUMBER"), lastDownloadDate,
                resultSet.getBigDecimal("LASTDOWNLOADLEDGEBAL"));
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, AccountDC accountDC, boolean withKey)
            throws SQLException {
        preparedStatement.setString(1, accountDC.getAccountType());
        preparedStatement.setInt(2, accountDC.getDCID());
        preparedStatement.setString(3, accountDC.getRoutingNumber());
        preparedStatement.setString(4, accountDC.getEncryptedAccountNumber());

        // save lastDownloadDateTime as of UTC LocalDateTime
        ZoneId utc = TimeZone.getTimeZone("UTC").toZoneId();
        LocalDateTime localDateTime = Instant.ofEpochMilli(accountDC.getLastDownloadDateTime().getTime())
                .atZone(utc).toLocalDateTime();
        preparedStatement.setObject(5, localDateTime.toLocalDate());
        preparedStatement.setObject(6, localDateTime.toLocalTime());
        preparedStatement.setBigDecimal(7, accountDC.getLastDownloadLedgeBalance());
        preparedStatement.setInt(8, accountDC.getAccountID());
    }

    public void merge(AccountDC accountDC) throws DaoException {
        final String sqlCmd = "MERGE INTO " + getTableName() + " ("
                + String.join(", ", Stream.concat(Arrays.stream(getColumnNames()),
                Arrays.stream(getKeyColumnNames())).toArray(String[]::new)) + ") VALUES ("
                +  String.join(", ",
                Collections.nCopies(getColumnNames().length + getKeyColumnNames().length, "?")) + ")";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            setPreparedStatement(preparedStatement, accountDC, true);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_MERGE, "Merge AccountDC failed", e);
        }
    }
}
