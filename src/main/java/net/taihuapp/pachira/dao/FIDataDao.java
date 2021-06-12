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

public class FIDataDao extends Dao<DirectConnection.FIData, Integer> {

    FIDataDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "FIDATA"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{ "ID" }; }

    @Override
    String[] getColumnNames() { return new String[]{ "FIID", "SUBID", "NAME", "ORG", "BROKERID", "URL" }; }

    @Override
    boolean autoGenKey() { return true; }

    @Override
    Integer getKeyValue(DirectConnection.FIData fiData) { return fiData.getID(); }

    @Override
    DirectConnection.FIData fromResultSet(ResultSet resultSet) throws SQLException, DaoException {
        return new DirectConnection.FIData(
                resultSet.getInt("ID"),
                resultSet.getString("FIID"),
                resultSet.getString("SUBID"),
                resultSet.getString("BROKERID"),
                resultSet.getString("NAME"),
                resultSet.getString("ORG"),
                resultSet.getString("URL")
        );
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, DirectConnection.FIData fiData, boolean withKey) throws SQLException {
        preparedStatement.setString(1, fiData.getFIID());
        preparedStatement.setString(2, fiData.getSubID());
        preparedStatement.setString(3, fiData.getName());
        preparedStatement.setString(4, fiData.getORG());
        preparedStatement.setString(5, fiData.getBrokerID());
        preparedStatement.setString(6, fiData.getURL());
        if (withKey)
            preparedStatement.setInt(7, fiData.getID());
    }
}
