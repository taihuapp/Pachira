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

import net.taihuapp.pachira.Tag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TagDao extends Dao<Tag, Integer> {

    public TagDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "TAGS"; }

    @Override
    String[] getKeyColumnNames() {
        return new String[]{ "ID" };
    }

    @Override
    String[] getColumnNames() {
        return new String[]{ "NAME", "DESCRIPTION" };
    }

    @Override
    boolean autoGenKey() {
        return true;
    }

    @Override
    Integer getKeyValue(Tag tag) {
        return tag.getID();
    }

    @Override
    Tag fromResultSet(ResultSet resultSet) throws SQLException, DaoException {
        return new Tag(resultSet.getInt("ID"), resultSet.getString("NAME"), resultSet.getString("DESCRIPTION"));
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, Tag tag, boolean withKey) throws SQLException {
        preparedStatement.setString(1, tag.getName());
        preparedStatement.setString(2, tag.getDescription());
        if (withKey)
            preparedStatement.setInt(3, tag.getID());
    }
}
