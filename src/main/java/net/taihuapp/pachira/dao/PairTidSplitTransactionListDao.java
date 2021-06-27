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
import java.util.*;

/**
 * PairTidSplitTransactionListDao is a Dao class for the pair of transaction id and the list of split transactions
 * The split transactions are stored in SPLITTRANSACTIONS table, each transaction id can have a) 0 row or b) more
 * than 1 rows.  We will have multiple rows of split transactions make up a list and pair up with the transaction id
 * for the full object.  We need to override default Dao methods to achieve this.
 */
public class PairTidSplitTransactionListDao extends Dao<Pair<Integer, List<SplitTransaction>>, Integer> {

    PairTidSplitTransactionListDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "SPLITTRANSACTIONS"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{"TRANSACTIONID"}; }

    @Override
    String[] getColumnNames() {
        throw new IllegalArgumentException("getColumnNames() should not be called for " + getClass().getName());
    }

    @Override
    boolean autoGenKey() { return false; }

    @Override
    Integer getKeyValue(Pair<Integer, List<SplitTransaction>> integerListPair) {
        return integerListPair.getKey();
    }

    @Override
    String getSQLString(SQLCommand sqlCommand) {
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
        try (PreparedStatement preparedStatement = connection.prepareStatement(getSQLString(SQLCommand.GET))) {
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
             ResultSet resultSet = statement.executeQuery(getSQLString(SQLCommand.GET_ALL))) {
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

    /**
     * first delete all rows with matching tid, then insert.
     * @param integerListPair - the pair of tid and the list of splitTransaction
     * @return - tid
     * @throws DaoException - from database operations
     */
    @Override
    public Integer insert(Pair<Integer, List<SplitTransaction>> integerListPair) throws DaoException {
        final int tid = integerListPair.getKey();
        final List<SplitTransaction> splitTransactionList = integerListPair.getValue();
        final List<SplitTransaction> updateList = new ArrayList<>();
        final List<SplitTransaction> insertList = new ArrayList<>();
        for (SplitTransaction splitTransaction : splitTransactionList) {
            if (splitTransaction.getID() > 0) {
                updateList.add(splitTransaction);
            } else {
                insertList.add(splitTransaction);
            }
        }

        Set<Integer> oldIDSet = new HashSet<>();

        DaoManager daoManager = DaoManager.getInstance();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT ID FROM "
                + "SPLITTRANSACTIONS WHERE TRANSACTIONID = ?")) {
            preparedStatement.setInt(1, tid);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next())
                    oldIDSet.add(resultSet.getInt(1));
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed to select from SPLITTRANSACTIONS", e);
        }

        for (SplitTransaction splitTransaction : integerListPair.getValue()) {
            final int id = splitTransaction.getID();
            if (id <= 0)
                continue;
            if (!oldIDSet.remove(id)) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_UPDATE,
                        "SplitTransaction " + id + " for " + tid + " does not exist", null);
            }
        }

        daoManager.beginTransaction();
        try {
            final String deleteCmd = "DELETE FROM SPLITTRANSACTIONS WHERE ID = ?";
            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteCmd)) {
                for (int id : oldIDSet) {
                    deleteStatement.setInt(1, id);

                    deleteStatement.executeUpdate();
                }
            } catch (SQLException e) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_DELETE, "Delete SplitTransactions failed", e);
            }

            final String updateCmd = "UPDATE SPLITTRANSACTIONS SET "
                    + "TRANSACTIONID = ?, CATEGORYID = ?, MEMO = ?, AMOUNT = ?, MATCHTRANSACTIONID = ?, TAGID = ? "
                    + "WHERE ID = ?";
            try (PreparedStatement updateStatement = connection.prepareStatement(updateCmd)) {
                for (SplitTransaction splitTransaction : updateList) {
                    updateStatement.setInt(1, tid);
                    updateStatement.setInt(2, splitTransaction.getCategoryID());
                    updateStatement.setString(3, splitTransaction.getMemo());
                    updateStatement.setBigDecimal(4, splitTransaction.getAmount());
                    updateStatement.setInt(5, splitTransaction.getMatchID());
                    updateStatement.setInt(6, splitTransaction.getTagID());
                    updateStatement.setInt(7, splitTransaction.getID());

                    updateStatement.executeUpdate();
                }
            } catch (SQLException e) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_UPDATE, "Update SplitTransactions failed", e);
            }

            final String insertCmd = "INSERT INTO SPLITTRANSACTIONS "
                    + "(TRANSACTIONID, CATEGORYID, MEMO, AMOUNT, MATCHTRANSACTIONID, TAGID) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement insertStatement =
                         connection.prepareStatement(insertCmd, Statement.RETURN_GENERATED_KEYS)) {
                for (SplitTransaction splitTransaction : insertList) {
                    insertStatement.setInt(1, tid);
                    insertStatement.setInt(2, splitTransaction.getCategoryID());
                    insertStatement.setString(3, splitTransaction.getMemo());
                    insertStatement.setBigDecimal(4, splitTransaction.getAmount());
                    insertStatement.setInt(5, splitTransaction.getMatchID());
                    insertStatement.setInt(6, splitTransaction.getTagID());

                    insertStatement.executeUpdate();

                    try (ResultSet resultSet = insertStatement.getGeneratedKeys()) {
                        if (resultSet.next())
                            splitTransaction.setID(resultSet.getInt(1));
                    }
                }
            } catch (SQLException e) {
                for (SplitTransaction splitTransaction : insertList)
                    splitTransaction.setID(0); // put back the old id
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_INSERT, "Insert to SplitTransactions failed", e);
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
