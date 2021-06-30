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

import net.taihuapp.pachira.DirectConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Optional;

public class DirectConnectionDao extends Dao<DirectConnection, Integer> {

    DirectConnectionDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "DCINFO"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{ "ID" }; }

    @Override
    String[] getColumnNames() { return new String[]{ "NAME", "FIID", "USERNAME", "PASSWORD" }; }

    @Override
    boolean autoGenKey() { return true; }

    @Override
    Integer getKeyValue(DirectConnection directConnection) { return directConnection.getID(); }

    @Override
    DirectConnection fromResultSet(ResultSet resultSet) throws SQLException {
        return new DirectConnection(resultSet.getInt("ID"), resultSet.getString("NAME"),
                resultSet.getInt("FIID"), resultSet.getString("USERNAME"), resultSet.getString("PASSWORD"));
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, DirectConnection directConnection, boolean withKey) throws SQLException {
        preparedStatement.setString(1, directConnection.getName());
        preparedStatement.setInt(2, directConnection.getFIID());
        preparedStatement.setString(3, directConnection.getEncryptedUserName());
        preparedStatement.setString(4, directConnection.getEncryptedPassword());
        if (withKey)
            preparedStatement.setInt(5, directConnection.getID());
    }

    /**
     * get DirectConnection by name
     * @param name - input name
     * @return - Optional of DirectConnection
     * @throws DaoException - from database operations
     */
    public Optional<DirectConnection> get(String name) throws DaoException {
        final String sqlCmd = "SELECT * FROM " + getTableName() + " WHERE NAME = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            preparedStatement.setString(1, name);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next())
                    return Optional.of(fromResultSet(resultSet));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "", e);
        }
    }

    /**
     * merge DirectConnection to database
     * @param directConnection - the input
     * @throws DaoException - from database operation
     */
    public void merge(DirectConnection directConnection) throws DaoException {
        final String sqlCmd = "MERGE INTO " + getTableName()
                + "(" + String.join(", ", getColumnNames()) + ") key(NAME) values("
                + String.join(", ", Collections.nCopies(getColumnNames().length, "?")) + ")";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            setPreparedStatement(preparedStatement, directConnection, false);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_MERGE, "", e);
        }
    }
}
