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
import net.taihuapp.pachira.SecurityHolding;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * a Dao class for the pair of transaction id and the list of (lot) match info
 */
public class PairTidMatchInfoListDao extends Dao<Pair<Integer, List<SecurityHolding.MatchInfo>>, Integer> {

    public PairTidMatchInfoListDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "LOTMATCH"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{"TRANSID"}; }

    @Override
    String[] getColumnNames() {
        throw new IllegalArgumentException("getColumnNames() should not be called for " + getClass().getName());
    }

    @Override
    boolean autoGenKey() { return true; }

    @Override
    Integer getKeyValue(Pair<Integer, List<SecurityHolding.MatchInfo>> integerListPair) {
        return integerListPair.getKey();
    }

    @Override
    String getSQLString(SQLCommands sqlCommand) {
        switch (sqlCommand) {
            case INSERT:
            case UPDATE:
                throw new IllegalArgumentException(sqlCommand + " should not be called for " + getClass().getName());
            case DELETE:
            case GET:
            case GET_ALL:
            default:
                return super.getSQLString(sqlCommand);
        }
    }

    /**
     * this only return one MatchInfo in a singleton list as the value of a pair
     * @param resultSet the resultSet with information
     * @return the pair constructed from the resultSet
     * @throws SQLException from resultSet.get...
     */
    @Override
    Pair<Integer, List<SecurityHolding.MatchInfo>> fromResultSet(ResultSet resultSet) throws SQLException {
        final int tid = resultSet.getInt("TRANSID");
        final int mid = resultSet.getInt("MATCHID");
        final BigDecimal quantity = resultSet.getBigDecimal("MATCHQUANTITY");

        return new Pair<>(tid, Collections.singletonList(new SecurityHolding.MatchInfo(mid, quantity)));
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement,
                              Pair<Integer, List<SecurityHolding.MatchInfo>> integerListPair, boolean withKey) {
        throw new IllegalStateException("setPreparedStatement should not be called for " + getClass().getName());
    }

    @Override
    public Optional<Pair<Integer, List<SecurityHolding.MatchInfo>>> get(Integer key) throws DaoException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(getSQLString(SQLCommands.GET))) {
            setPreparedStatement(preparedStatement, key);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                List<SecurityHolding.MatchInfo> matchInfoList = new ArrayList<>();
                while (resultSet.next()) {
                    // fromResultSet return a pair of tid and a singleton list of matchInfo item
                    // we just need the match info item from it.
                    matchInfoList.add(fromResultSet(resultSet).getValue().get(0));
                }
                if (matchInfoList.isEmpty())
                    return Optional.empty();
                return Optional.of(new Pair<>(key, matchInfoList));
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed to get Match Info for " + key, e);
        }
    }

    @Override
    public List<Pair<Integer, List<SecurityHolding.MatchInfo>>> getAll() throws DaoException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(getSQLString(SQLCommands.GET_ALL))) {
            int tid = -1;
            List<SecurityHolding.MatchInfo> matchInfoList = null;
            List<Pair<Integer, List<SecurityHolding.MatchInfo>>> fullList = new ArrayList<>();
            while (resultSet.next()) {
                Pair<Integer, List<SecurityHolding.MatchInfo>> integerListPair = fromResultSet(resultSet);
                int newTid = integerListPair.getKey();
                if (newTid != tid) {
                    // we have a new tid here
                    tid = newTid;
                    matchInfoList = new ArrayList<>();
                    fullList.add(new Pair<>(tid, matchInfoList));
                }
                if (matchInfoList != null) {
                    // this check is really unnecessary, but it will stop intellij complaining
                    matchInfoList.add(integerListPair.getValue().get(0));
                }
            }
            return fullList;
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed to getAll MatchInfo.", e);
        }
    }

    @Override
    public int update(Pair<Integer, List<SecurityHolding.MatchInfo>> integerListPair) throws DaoException {
        insert(integerListPair);
        return 1;
    }

    @Override
    public Integer insert(Pair<Integer, List<SecurityHolding.MatchInfo>> integerListPair) throws DaoException {
        final int tid = integerListPair.getKey();
        final String sqlCmd = "INSERT INTO LOTMATCH "
                + "(TRANSID, MATCHID, MATCHQUANTITY) VALUES (?, ?, ?)";

        DaoManager daoManager = DaoManager.getInstance();
        try {
            // we need to delete first before we can add
            // need to be an Dao transaction
            daoManager.beginTransaction();
            delete(tid);
            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
                for (SecurityHolding.MatchInfo matchInfo : integerListPair.getValue()) {
                    preparedStatement.setInt(1, tid);
                    preparedStatement.setInt(2, matchInfo.getMatchTransactionID());
                    preparedStatement.setBigDecimal(3, matchInfo.getMatchQuantity());

                    preparedStatement.executeUpdate();
                }
            } catch (SQLException e) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_INSERT,
                        "Failed to insert MatchInfo for " + tid, e);
            }
            daoManager.commit();
            return tid;
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
