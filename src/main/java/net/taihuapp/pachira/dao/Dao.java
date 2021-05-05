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

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * An abstract class for DAO of type T with key K.
 * It is assumed that each object of type T is stored as a row in a table, keyed by K.
 * If this assumption is not satisfied, then one need to override corresponding methods.
 *
 * @param <T>
 * @param <K>
 */
abstract class Dao<T, K> {

    /**
     * the jdbc database connection
     */
    Connection connection;

    /** the table name, key column name, and other column names for storing T in database     */
    abstract String getTableName();
    abstract String getKeyColumnName();
    abstract String[] getColumnNames();

    /**
     * construct an object of type T using the information in a resultSet (from a select statement)
     * @param resultSet the resultSet with information
     * @return the constructed object
     * @throws SQLException from resultSet operations
     */
    abstract T fromResultSet(ResultSet resultSet) throws SQLException, DaoException;

    /**
     * properly set the key in a preparedStatement for get/delete command
     * @param preparedStatement empty preparedStatement to be set
     * @param k value of the key
     * @throws SQLException from preparedStatement operations
     */
    abstract void setPreparedStatement(PreparedStatement preparedStatement, K k) throws SQLException;

    /**
     * properly set the preparedStatement for insert/update.  The order of columns should match
     * the order in getColumnNames()
     * @param preparedStatement empty preparedStatement to be set
     * @param t the object used to set preparedStatement
     * @param withKey if true, set key value
     * @throws SQLException from preparedStatement operations
     */
    // set the prepared statement with the value in t, including key if withKey is true.
    // following the column order in getColumnNames(), and then key column if needed.
    abstract void setPreparedStatement(PreparedStatement preparedStatement, T t, boolean withKey)
            throws SQLException;

    /**
     * supported SQL commands
     */
    enum SQLCommands {
        DELETE, GET, GET_ALL, INSERT, UPDATE
    }

    /**
     * get the proper SQL statement for the SQL command
     * @param sqlCommand one of the enum input
     * @return sql command in String
     */
    String getSQLString(SQLCommands sqlCommand) {
        switch (sqlCommand) {
            case DELETE:
                return "DELETE FROM " + getTableName() + " WHERE " + getKeyColumnName() + " = ?";
            case GET:
                return "SELECT * FROM " + getTableName() + " WHERE " + getKeyColumnName() + " = ?";
            case GET_ALL:
                return "SELECT * FROM " + getTableName();
            case INSERT:
                return "INSERT INTO " + getTableName()
                        + " (" + String.join(", ", getColumnNames()) + ") VALUES ("
                        + String.join(", ", Collections.nCopies(getColumnNames().length, "?"))
                        + ")";
            case UPDATE:
                return "UPDATE " + getTableName() + " set " + String.join(" = ?, ", getColumnNames())
                        + " = ? WHERE " + getKeyColumnName() + " = ?";
            default:
                throw new IllegalArgumentException(sqlCommand + " not implemented yet");
        }
    }

    /**
     * get the object matches the key
     * @param key - the search key
     * @return the object in an Optional
     * @throws DaoException from Dao operations
     */
    public Optional<T> get(K key) throws DaoException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(getSQLString(SQLCommands.GET))) {
            setPreparedStatement(preparedStatement, key);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(fromResultSet(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "", e);
        }
    }

    /**
     * delete the object matches the key
     * @param key object matches key will be deleted
     * @return number of objects deleted
     * @throws DaoException from Dao operations
     */
    public int delete(K key) throws DaoException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(getSQLString(SQLCommands.DELETE))) {
            setPreparedStatement(preparedStatement, key);
            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_DELETE, "", e);
        }
    }

    /**
     * get all the objects in a list
     * @return a list of all objects in the database
     * @throws DaoException from Dao operations
     */
    public List<T> getAll() throws DaoException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(getSQLString(SQLCommands.GET_ALL))) {
            List<T> tList = new ArrayList<>();
            while (resultSet.next()) {
                tList.add(fromResultSet(resultSet));
            }
            return tList;
        } catch (SQLException e) {
            // should log error first before re-throw
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "", e);
        }
    }

    /**
     * return the key value in the resultSet.  The resultSet is from
     * preparedStatement.getGeneratedKeys()
     * @param resultSet - the resultSet from preparedStatement.getGeneratedKeys() which contains the key value
     * @return value of the key
     * @throws SQLException from jdbc statements
     */
    protected abstract K getKeyValue(ResultSet resultSet) throws SQLException;

    /**
     *
     * @param t - the object to be inserted
     * @return - the generated key
     * @throws DaoException from Dao operations
     */
    public K insert(T t) throws DaoException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(getSQLString(SQLCommands.INSERT),
                Statement.RETURN_GENERATED_KEYS)) {
            setPreparedStatement(preparedStatement, t, false);

            preparedStatement.executeUpdate();

            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                resultSet.next();
                return getKeyValue(resultSet);
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_INSERT, "", e);
        }
    }

    /**
     * update database for object t.
     * @param t - the object to be updated in the database
     * @return the number of rows updated.
     * @throws DaoException from Dao operations
     */
    public int update(T t) throws DaoException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(getSQLString(SQLCommands.UPDATE))) {
            setPreparedStatement(preparedStatement, t, true);

            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw (new DaoException(DaoException.ErrorCode.FAIL_TO_UPDATE, "", e));
        }
    }
}
