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
import net.taihuapp.pachira.Security;
import net.taihuapp.pachira.SplitTransaction;
import net.taihuapp.pachira.Transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TransactionDao extends Dao<Transaction, Integer> {

    private final SecurityDao securityDao;
    private final PairTidSplitTransactionListDao pairTidSplitTransactionListDao;
    private final Map<Integer, List<SplitTransaction>> tidSplitTransactionListMap = new HashMap<>();

    public TransactionDao(Connection connection, SecurityDao securityDao,
                          PairTidSplitTransactionListDao pairTidSplitTransactionListDao) {
        this.connection = connection;
        this.securityDao = securityDao;
        this.pairTidSplitTransactionListDao = pairTidSplitTransactionListDao;

    }

    @Override
    String getTableName() { return "TRANSACTIONS"; }

    @Override
    String getKeyColumnName() { return "ID"; }

    @Override
    String[] getColumnNames() {
        return new String[]{ "ACCOUNTID", "DATE", "AMOUNT", "TRADEACTION", "SECURITYID", "STATUS", "CATEGORYID",
                "TAGID", "MEMO", "PRICE", "QUANTITY", "COMMISSION", "MATCHTRANSACTIONID", "MATCHSPLITTRANSACTIONID",
                "PAYEE", "ADATE", "OLDQUANTITY", "REFERENCE", "SPLITFLAG", "ACCRUEDINTEREST", "FITID" };
    }

    @Override
    Transaction fromResultSet(ResultSet resultSet) throws SQLException, DaoException {
        final int id = resultSet.getInt("ID");
        final int aid = resultSet.getInt("ACCOUNTID");
        final LocalDate tDate = resultSet.getObject("DATE", LocalDate.class);
        final LocalDate aDate = resultSet.getObject("ADATE", LocalDate.class);

        // todo make TRADEACTION not null column
        final Transaction.TradeAction tradeAction =
                Transaction.TradeAction.valueOf(resultSet.getString("TRADEACTION"));
        final Transaction.Status status = Transaction.Status.valueOf(resultSet.getString("STATUS"));

        final String name = securityDao.get(resultSet.getInt("SECURITYID"))
                .map(Security::getName).orElse("");

        final String reference = resultSet.getString("REFERENCE");
        final String payee = resultSet.getString("PAYEE");
        final String memo = resultSet.getString("MEMO");

        final BigDecimal price = resultSet.getBigDecimal("PRICE");
        final BigDecimal quantity = resultSet.getBigDecimal("QUANTITY");
        final BigDecimal oldQuantity = resultSet.getBigDecimal("OLDQUANTITY");
        final BigDecimal commission = resultSet.getBigDecimal("COMMISSION");
        final BigDecimal accruedInterest = resultSet.getBigDecimal("ACCRUEDINTEREST");
        BigDecimal amount = resultSet.getBigDecimal("AMOUNT");
        if (amount == null)
            amount = BigDecimal.ZERO;
        final int cid = resultSet.getInt("CATEGORYID");
        final int tagID = resultSet.getInt("TAGID");
        final int matchID = resultSet.getInt("MATCHTRANSACTIONID");
        final int matchSplitID = resultSet.getInt("MATCHSPLITTRANSACTIONID");

        final boolean splitFlag = resultSet.getBoolean("SPLITFLAG");
        final List<SplitTransaction> stList = splitFlag ? tidSplitTransactionListMap.get(id) : new ArrayList<>();

        final String fitid = resultSet.getString("FITID");

        return new Transaction(id, aid, tDate, aDate, tradeAction, status, name, reference,
                payee, price, quantity, oldQuantity, memo, commission, accruedInterest, amount,
                cid, tagID, matchID, matchSplitID, stList, fitid);
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, Transaction transaction, boolean withKey)
            throws SQLException {
        try {
            preparedStatement.setInt(1, transaction.getAccountID());
            preparedStatement.setObject(2, transaction.getTDate());
            preparedStatement.setBigDecimal(3, transaction.getAmount());
            preparedStatement.setString(4, transaction.getTradeAction().name());
            preparedStatement.setObject(5,
                    securityDao.get(transaction.getSecurityName()).map(Security::getID).orElse(null));
            preparedStatement.setString(6, transaction.getStatus().name());
            preparedStatement.setInt(7, transaction.getCategoryID());
            preparedStatement.setInt(8, transaction.getTagID());
            preparedStatement.setString(9, transaction.getMemo());
            preparedStatement.setBigDecimal(10, transaction.getPrice());
            preparedStatement.setBigDecimal(11, transaction.getQuantity());
            preparedStatement.setBigDecimal(12, transaction.getCommission());
            preparedStatement.setInt(13, transaction.getMatchID());
            preparedStatement.setInt(14, transaction.getMatchSplitID());
            preparedStatement.setString(15, transaction.getPayee());
            preparedStatement.setObject(16, transaction.getADate());
            preparedStatement.setBigDecimal(17, transaction.getOldQuantity());
            preparedStatement.setString(18, transaction.getReference());
            preparedStatement.setBoolean(19, transaction.isSplit());
            preparedStatement.setBigDecimal(20, transaction.getAccruedInterest());
            preparedStatement.setString(21, transaction.getFITID());
            if (withKey)
                preparedStatement.setInt(22, transaction.getID());
        } catch (DaoException e) {
            if (e.getCause() instanceof SQLException)
                throw (SQLException) e.getCause();
            throw new IllegalStateException("unexpected throwable: ", e);
        }
    }

    @Override
    public List<Transaction> getAll() throws DaoException {
        // refresh tidSplitTransactionListMap
        tidSplitTransactionListMap.clear();
        List<Pair<Integer, List<SplitTransaction>>> list = pairTidSplitTransactionListDao.getAll();
        for (Pair<Integer, List<SplitTransaction>> integerListPair : list) {
            tidSplitTransactionListMap.put(integerListPair.getKey(), integerListPair.getValue());
        }

        return super.getAll();
    }
}
