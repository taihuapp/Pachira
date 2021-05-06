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
import net.taihuapp.pachira.SplitTransaction;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * PairTidSplitTransactionListDao is a Dao class for the pair of transaction id and the list of split transactions
 * The split transactions are stored in SPLITTRANSACTIONS table, each transaction id can have a) 0 row or b) more
 * than 1 rows.  We will have multiple rows of split transactions make up a list and pair up with the transaction id
 * for the full object.  We need to override default Dao methods to achieve this.
 */
public class PairTidSplitTransactionListDao extends Dao<Pair<Integer, List<SplitTransaction>>, Integer> {

    public PairTidSplitTransactionListDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "SPLITTRANSACTIONS"; }

    @Override
    String getKeyColumnName() { return "TRANSACTIONID"; }

    @Override
    String[] getColumnNames() {
        throw new IllegalArgumentException("getColumnNames() should not be called for " + getClass().getName());
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
     * this only return one splittransaction in a singleton list.
     * need to add a loop to get all in get method.
     * @param resultSet input
     * @return the pair constructed from the resultSet
     * @throws SQLException from resultSet.get...
     */
    @Override
    Pair<Integer, List<SplitTransaction>> fromResultSet(ResultSet resultSet) throws SQLException {
        final int tid = resultSet.getInt("TRANSACTIONID");
        final int id = resultSet.getInt("ID");
        final int cid = resultSet.getInt("CATEGORYID");
        final int tagId = resultSet.getInt("TAGID");
        final String memo = resultSet.getString("MEMO");
        // // TODO: 5/2/21 make AMOUNT column not null
        BigDecimal amount = resultSet.getBigDecimal("AMOUNT");
        final int matchID = resultSet.getInt("MATCHTRANSACTIONID");

        return new Pair<>(tid, Collections.singletonList(new SplitTransaction(id, cid, tagId, memo, amount, matchID)));
    }

    /**
     * this method should not be used for this class
     */
    @Override
    void setPreparedStatement(PreparedStatement preparedStatement,
                              Pair<Integer, List<SplitTransaction>> integerListPair, boolean withKey) {
        throw new IllegalStateException("setPreparedStatement should not be called for " + getClass().getName());
    }

    @Override
    public Optional<Pair<Integer, List<SplitTransaction>>> get(Integer key) throws DaoException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(getSQLString(SQLCommands.GET))) {
            setPreparedStatement(preparedStatement, key);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                List<SplitTransaction> spList = new ArrayList<>();
                while (resultSet.next()) {
                    Pair<Integer, List<SplitTransaction>> integerListPair = fromResultSet(resultSet);
                    spList.add(integerListPair.getValue().get(0));
                }
                if (spList.isEmpty())
                    return Optional.empty();
                return Optional.of(new Pair<>(key, spList));
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed to get SplitTransactions for " + key, e);
        }
    }

    @Override
    public List<Pair<Integer, List<SplitTransaction>>> getAll() throws DaoException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(getSQLString(SQLCommands.GET_ALL))) {
            int tid = -1;
            List<SplitTransaction> spList = null;
            List<Pair<Integer, List<SplitTransaction>>> fullList = new ArrayList<>();
            while (resultSet.next()) {
                Pair<Integer, List<SplitTransaction>> integerListPair = fromResultSet(resultSet);
                int newTid = integerListPair.getKey();
                if (newTid != tid) {
                    tid = newTid;
                    spList = new ArrayList<>();
                    fullList.add(new Pair<>(tid, spList));
                }
                if (spList != null) // this check is not necessary, but stops intellij complaining
                    spList.add(integerListPair.getValue().get(0));
            }
            return fullList;
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed to getAll SplitTransaction.", e);
        }
    }

    @Override
    public int update(Pair<Integer, List<SplitTransaction>> integerListPair) throws DaoException {
        insert(integerListPair);
        return 1;
    }

    @Override
    public Integer insert(Pair<Integer, List<SplitTransaction>> integerListPair) throws DaoException {
        final int tid = integerListPair.getKey();
        final String sqlCmd = "INSERT INTO SPLITTRANSACTIONS "
                + "(TRANSACTIONID, CATEGORYID, MEMO, AMOUNT, MATCHTRANSACTIONID, TAGID) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        DaoManager daoManager = DaoManager.getInstance();
        try {
            daoManager.beginTransaction();
            delete(tid);  // clear everything for tid first
            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
                for (SplitTransaction sp : integerListPair.getValue()) {
                    preparedStatement.setInt(1, tid);
                    preparedStatement.setInt(2, sp.getCategoryID());
                    preparedStatement.setString(3, sp.getMemo());
                    preparedStatement.setBigDecimal(4, sp.getAmount());
                    preparedStatement.setInt(5, sp.getMatchID());
                    preparedStatement.setInt(6, sp.getTagID());

                    preparedStatement.executeUpdate();
                }
            } catch (SQLException e) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_INSERT,
                        "Failed to insert SplitTransaction for " + tid, e);
            }
            daoManager.commit();
            return tid;
        } catch (DaoException e) {
            try {
                // failed insert, rollback
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }

            // re-throw e now
            throw e;
        }
    }
}
