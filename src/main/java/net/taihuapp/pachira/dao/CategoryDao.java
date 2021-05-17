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

import net.taihuapp.pachira.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CategoryDao extends Dao<Category, Integer> {

    CategoryDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "CATEGORIES"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{ "ID" }; }

    @Override
    String[] getColumnNames() {
        return new String[]{ "NAME", "DESCRIPTION", "INCOMEFLAG", "TAXREFNUM", "BUDGETAMOUNT" };
    }

    @Override
    boolean autoGenKey() { return true; }

    @Override
    Integer getKeyValue(Category category) { return category.getID(); }

    @Override
    Category fromResultSet(ResultSet resultSet) throws SQLException, DaoException {
        return new Category(resultSet.getInt("ID"), resultSet.getString("NAME"),
                resultSet.getString("DESCRIPTION"), resultSet.getBoolean("INCOMEFLAG"),
                resultSet.getInt("TAXREFNUM"));
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, Category category, boolean withKey) throws SQLException {
        preparedStatement.setString(1, category.getName());
        preparedStatement.setString(2, category.getDescription());
        preparedStatement.setBoolean(3, category.getIsIncome());
        preparedStatement.setInt(4, category.getTaxRefNum());
        preparedStatement.setBigDecimal(5, category.getBudgetAmount());
        if (withKey)
            preparedStatement.setInt(6, category.getID());
    }
}
