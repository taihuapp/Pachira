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

import java.sql.*;
import java.util.*;

/**
 * The object is a pair of reportID and a map of item name and item values
 */
public class ReportDetailDao extends Dao<Pair<Integer, Map<String, List<String>>>, Integer> {

    ReportDetailDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "SAVEDREPORTDETAILS"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{ "REPORTID" }; }

    @Override
    String[] getColumnNames() { return new String[]{ "ITEMNAME", "ITEMVALUE"}; }

    @Override
    boolean autoGenKey() { return false; }

    @Override
    Integer getKeyValue(Pair<Integer, Map<String, List<String>>> integerMapPair) { return integerMapPair.getKey(); }

    /**
     * this only return one row of item name/item value, a full object may occupy multiple rows
     * @param resultSet the resultSet with information
     * @return a (possibly) partial object
     * @throws SQLException from database operation
     */
    @Override
    Pair<Integer, Map<String, List<String>>> fromResultSet(ResultSet resultSet) throws SQLException {
        final int reportID = resultSet.getInt("REPORTID");
        final String name = resultSet.getString("ITEMNAME");
        final String value = resultSet.getString("ITEMVALUE");

        Map<String, List<String>> itemMap = new HashMap<>();
        itemMap.put(name, Collections.singletonList(value));
        return new Pair<>(reportID, itemMap);
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, Pair<Integer, Map<String, List<String>>> integerMapPair, boolean withKey) throws SQLException {
        throw new IllegalStateException("setPreparedStatement should not be called for " + getClass().getName());
    }

    @Override
    String getSQLString(SQLCommand sqlCommand) {
        if (sqlCommand == SQLCommand.INSERT || sqlCommand == SQLCommand.UPDATE)
            throw new IllegalArgumentException(sqlCommand + " should not be called for " + getClass().getName());

        return super.getSQLString(sqlCommand);
    }

    @Override
    public Optional<Pair<Integer, Map<String, List<String>>>> get(Integer key) throws DaoException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(getSQLString(SQLCommand.GET))) {
            setPreparedStatement(preparedStatement, key);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                final Map<String, List<String>> itemsMap = new HashMap<>();
                while (resultSet.next()) {
                    Pair<Integer, Map<String, List<String>>> integerMapPair = fromResultSet(resultSet);
                    for (String s : integerMapPair.getValue().keySet())
                        itemsMap.computeIfAbsent(s, k -> new ArrayList<>()).addAll(integerMapPair.getValue().get(s));
                }
                return Optional.of(new Pair<>(key, itemsMap));
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed to get Match Info for " + key, e);
        }
    }

    @Override
    public List<Pair<Integer, Map<String, List<String>>>> getAll() throws DaoException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(getSQLString(SQLCommand.GET_ALL))) {
            int reportID = -1;
            Map<String, List<String>> stringListMap = null;
            List<Pair<Integer, Map<String, List<String>>>> fullList = new ArrayList<>();
            while (resultSet.next()) {
                Pair<Integer, Map<String, List<String>>> integerMapPair = fromResultSet(resultSet);
                int newReportID = integerMapPair.getKey();
                if (newReportID != reportID) {
                    reportID = newReportID;
                    stringListMap = new HashMap<>();
                    fullList.add(new Pair<>(reportID, stringListMap));
                }
                if (stringListMap != null) {
                    for (String s : integerMapPair.getValue().keySet())
                        stringListMap.computeIfAbsent(s, k -> new ArrayList<>())
                                .addAll(integerMapPair.getValue().get(s));
                }
            }
            return fullList;
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed to getAll ReportDetails.", e);
        }
    }

    @Override
    public int update(Pair<Integer, Map<String, List<String>>> integerMapPair) throws DaoException {
        insert(integerMapPair);
        return 1;
    }

    @Override
    public Integer insert(Pair<Integer, Map<String, List<String>>> integerMapPair) throws DaoException {
        final int reportID = integerMapPair.getKey();
        final String sqlCmd = "INSERT INTO SAVEDREPORTDETAILS (REPORTID, ITEMNAME, ITEMVALUE) VALUES (?, ?, ?)";

        DaoManager daoManager = DaoManager.getInstance();
        try {
            daoManager.beginTransaction();
            delete(reportID);
            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
                for (String name : integerMapPair.getValue().keySet()) {
                    for (String value : integerMapPair.getValue().get(name)) {
                        preparedStatement.setInt(1, reportID);
                        preparedStatement.setString(2, name);
                        preparedStatement.setString(3, value);

                        preparedStatement.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_INSERT,
                        "Failed to insert ReportDetails for " + reportID, e);
            }
            daoManager.commit();
            return reportID;
        } catch (DaoException e) {
            // something went wrong, roll back
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }

            // re-throw e
            throw e;
        }
    }
}
