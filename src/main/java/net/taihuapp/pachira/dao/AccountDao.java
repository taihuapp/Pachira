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

import net.taihuapp.pachira.Account;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class AccountDao extends Dao<Account, Integer> {

    AccountDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "ACCOUNTS"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{ "ID" }; }

    @Override
    String[] getColumnNames() {
        return new String[]{"TYPE", "NAME", "DESCRIPTION", "HIDDENFLAG", "DISPLAYORDER", "LASTRECONCILEDATE"};
    }

    @Override
    boolean autoGenKey() { return true; }

    @Override
    Integer getKeyValue(Account account) { return account.getID(); }

    @Override
    Account fromResultSet(ResultSet resultSet) throws SQLException {
        return new Account(resultSet.getInt("ID"),
                Account.Type.valueOf(resultSet.getString("TYPE")),
                resultSet.getString("NAME"),
                resultSet.getString("DESCRIPTION"),
                resultSet.getBoolean("HIDDENFLAG"),
                resultSet.getInt("DISPLAYORDER"),
                resultSet.getObject("LASTRECONCILEDATE", LocalDate.class),
                BigDecimal.ZERO);
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, Account account, boolean withKey) throws SQLException {
        preparedStatement.setString(1, account.getType().name());
        preparedStatement.setString(2, account.getName());
        preparedStatement.setString(3, account.getDescription());
        preparedStatement.setBoolean(4, account.getHiddenFlag());
        preparedStatement.setInt(5, account.getDisplayOrder());
        preparedStatement.setObject(6, account.getLastReconcileDate());
        if (withKey)
            preparedStatement.setInt(7, account.getID());
    }
}
