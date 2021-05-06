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

import net.taihuapp.pachira.Security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class SecurityDao extends Dao<Security, Integer> {

    public SecurityDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "SECURITIES"; }

    @Override
    String getKeyColumnName() { return "ID"; }

    @Override
    String[] getColumnNames() { return new String[]{"TICKER", "NAME", "TYPE"}; }

    @Override
    Security fromResultSet(ResultSet resultSet) throws SQLException {
        return new Security(resultSet.getInt("ID"), resultSet.getString("TICKER"),
                resultSet.getString("NAME"), Security.Type.valueOf(resultSet.getString("TYPE")));
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, Security security, boolean withKey) throws SQLException {
        preparedStatement.setString(1, security.getTicker());
        preparedStatement.setString(2, security.getName());
        preparedStatement.setString(3, security.getType().name());
        if (withKey)
            preparedStatement.setInt(4, security.getID());
    }

    public Optional<Security> get(String name) throws DaoException {
        final String sqlCmd = "SELECT * FROM " + getTableName() + " WHERE NAME = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            preparedStatement.setString(1, name);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next())
                    return Optional.of(fromResultSet(resultSet));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed get security '" + name + "'", e);
        }
    }
}
