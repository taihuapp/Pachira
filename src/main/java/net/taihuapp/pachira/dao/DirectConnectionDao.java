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
}
