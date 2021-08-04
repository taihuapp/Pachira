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
 * SplitTransactionListDao is a Dao class for a pair of (type, typ_id) and a list of split transactions
 * The split transactions are stored in SPLITTRANSACTIONS table, each transaction id can have (a) 0 row or (b) more
 * than 1 rows.  We will have multiple rows of split transactions make up a list and pair up with the transaction id
 * for the full object.  We need to override default Dao methods to achieve this.
 */
public class SplitTransactionListDao extends Dao<Pair<Pair<SplitTransaction.Type, Integer>,
        List<SplitTransaction>>, Pair<SplitTransaction.Type, Integer>> {

    SplitTransactionListDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "SPLITTRANSACTIONS"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{"TYPE", "TYPE_ID"}; }

    @Override
    String[] getColumnNames() {
        throw new IllegalArgumentException("getColumnNames() should not be called for " + getClass().getName());
    }

    @Override
    boolean autoGenKey() { return false; }

    @Override
    Pair<SplitTransaction.Type, Integer> getKeyValue(Pair<Pair<SplitTransaction.Type, Integer>,
            List<SplitTransaction>> pair) {
        return pair.getKey();
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
    Pair<Pair<SplitTransaction.Type, Integer>, List<SplitTransaction>> fromResultSet(ResultSet resultSet)
            throws SQLException {

        final SplitTransaction.Type type = SplitTransaction.Type.valueOf(resultSet.getString("TYPE"));
        final int tid = resultSet.getInt("TYPE_ID");
        final int id = resultSet.getInt("ID");
        final int cid = resultSet.getInt("CATEGORYID");
        final int tagId = resultSet.getInt("TAGID");
        final String memo = resultSet.getString("MEMO");
        BigDecimal amount = resultSet.getBigDecimal("AMOUNT");
        final int matchID = resultSet.getInt("MATCHTRANSACTIONID");

        return new Pair<>(new Pair<>(type, tid), Collections.singletonList(new SplitTransaction(id, cid,
                tagId, memo, amount, matchID)));
    }

    /**
     * properly set the preparedStatement for the given key.
     * used in get/delete methods
     * @param preparedStatement empty preparedStatement to be set
     * @param pair a pair of type and id.
     * @throws SQLException from database operations
     */
    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, Pair<SplitTransaction.Type, Integer> pair)
            throws SQLException {
        preparedStatement.setString(1, pair.getKey().name());
        preparedStatement.setInt(2, pair.getValue());
    }

    /**
     * this method should not be used for this class
     */
    @Override
    void setPreparedStatement(PreparedStatement preparedStatement,
                              Pair<Pair<SplitTransaction.Type, Integer>, List<SplitTransaction>> integerListPair,
                              boolean withKey) {
        throw new IllegalStateException("setPreparedStatement should not be called for " + getClass().getName());
    }

    @Override
    public Optional<Pair<Pair<SplitTransaction.Type, Integer>, List<SplitTransaction>>>
    get(Pair<SplitTransaction.Type, Integer> key) throws DaoException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(getSQLString(SQLCommand.GET))) {
            setPreparedStatement(preparedStatement, key);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                List<SplitTransaction> spList = new ArrayList<>();
                while (resultSet.next()) {
                    spList.add(fromResultSet(resultSet).getValue().get(0));
                }
                if (spList.isEmpty())
                    return Optional.empty();
                return Optional.of(new Pair<>(key, spList));
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed to get SplitTransactions for " + key, e);
        }
    }

    /**
     * get all records for the input type, get all records if type is null
     * @param type the type of split transactions to get
     * @return a list
     * @throws DaoException from database operations
     */
    public List<Pair<Pair<SplitTransaction.Type, Integer>, List<SplitTransaction>>> getAll(SplitTransaction.Type type)
        throws DaoException {

        String sqlCmd = getSQLString(SQLCommand.GET_ALL);
        if (type != null) {
            sqlCmd += " where TYPE = ?";
        }
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            if (type != null) {
                preparedStatement.setString(1, type.name());
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                int tid = -1;
                SplitTransaction.Type t = null;
                List<SplitTransaction> spList = null;
                List<Pair<Pair<SplitTransaction.Type, Integer>, List<SplitTransaction>>> fullList = new ArrayList<>();
                while (resultSet.next()) {
                    Pair<Pair<SplitTransaction.Type, Integer>, List<SplitTransaction>> pair = fromResultSet(resultSet);
                    Pair<SplitTransaction.Type, Integer> key = pair.getKey();
                    SplitTransaction.Type newType = key.getKey();
                    int newTid = key.getValue();
                    if (newTid != tid || newType != t) {
                        if ((type != null) && (newType != type))
                            continue; // we only care about type
                        tid = newTid;
                        t = newType;
                        spList = new ArrayList<>();
                        fullList.add(new Pair<>(new Pair<>(t, newTid), spList));
                    }
                    spList.add(pair.getValue().get(0));
                }
                return fullList;
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed to getAll SplitTransaction.", e);
        }
    }

    /**
     * delete records match type, delete all records if type is null
     * @param type input
     * @throws DaoException from database operations
     */
    public void deleteAll(SplitTransaction.Type type) throws DaoException {

        String sqlCmd = "DELETE FROM " + getTableName();
        if (type != null) {
            sqlCmd += " where TYPE = ?";
        }
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            if (type != null)
                preparedStatement.setString(1, type.name());

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_DELETE, "Delete all failed", e);
        }
    }

    @Override
    public List<Pair<Pair<SplitTransaction.Type, Integer>, List<SplitTransaction>>> getAll() throws DaoException {
        return getAll(null);
    }

    @Override
    public int update(Pair<Pair<SplitTransaction.Type, Integer>, List<SplitTransaction>> integerListPair)
            throws DaoException {
        insert(integerListPair);
        return 1;
    }

    /**
     * first delete all rows with matching tid, then insert.
     * @param pair - the pair of tid and the list of splitTransaction
     * @return - tid
     * @throws DaoException - from database operations
     */
    @Override
    public Pair<SplitTransaction.Type, Integer> insert(Pair<Pair<SplitTransaction.Type, Integer>,
            List<SplitTransaction>> pair) throws DaoException {

        final SplitTransaction.Type type = pair.getKey().getKey();
        final int tid = pair.getKey().getValue();
        final List<SplitTransaction> splitTransactionList = pair.getValue();
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
                + "SPLITTRANSACTIONS WHERE TYPE = ? and TYPE_ID = ?")) {
            preparedStatement.setString(1, type.name());
            preparedStatement.setInt(2, tid);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next())
                    oldIDSet.add(resultSet.getInt(1));
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed to select from SPLITTRANSACTIONS", e);
        }

        for (SplitTransaction splitTransaction : pair.getValue()) {
            final int id = splitTransaction.getID();
            if (id <= 0)
                continue;
            // this split transaction has a valid id, it should exist in the old id set.
            if (!oldIDSet.remove(id)) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_UPDATE,
                        type + " SplitTransaction " + id + " for " + tid + " does not exist", null);
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
                    + "TYPE = ?, TYPE_ID = ?, CATEGORYID = ?, MEMO = ?, AMOUNT = ?, MATCHTRANSACTIONID = ?, TAGID = ? "
                    + "WHERE ID = ?";
            try (PreparedStatement updateStatement = connection.prepareStatement(updateCmd)) {
                for (SplitTransaction splitTransaction : updateList) {
                    updateStatement.setString(1, type.name());
                    updateStatement.setInt(2, tid);
                    updateStatement.setInt(3, splitTransaction.getCategoryID());
                    updateStatement.setString(4, splitTransaction.getMemo());
                    updateStatement.setBigDecimal(5, splitTransaction.getAmount());
                    updateStatement.setInt(6, splitTransaction.getMatchID());
                    updateStatement.setInt(7, splitTransaction.getTagID());
                    updateStatement.setInt(8, splitTransaction.getID());

                    updateStatement.executeUpdate();
                }
            } catch (SQLException e) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_UPDATE, "Update SplitTransactions failed", e);
            }

            final String insertCmd = "INSERT INTO SPLITTRANSACTIONS "
                    + "(TYPE, TYPE_ID, CATEGORYID, MEMO, AMOUNT, MATCHTRANSACTIONID, TAGID) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement insertStatement =
                         connection.prepareStatement(insertCmd, Statement.RETURN_GENERATED_KEYS)) {
                for (SplitTransaction splitTransaction : insertList) {
                    insertStatement.setString(1, type.name());
                    insertStatement.setInt(2, tid);
                    insertStatement.setInt(3, splitTransaction.getCategoryID());
                    insertStatement.setString(4, splitTransaction.getMemo());
                    insertStatement.setBigDecimal(5, splitTransaction.getAmount());
                    insertStatement.setInt(6, splitTransaction.getMatchID());
                    insertStatement.setInt(7, splitTransaction.getTagID());

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
            return new Pair<>(type, tid);
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
