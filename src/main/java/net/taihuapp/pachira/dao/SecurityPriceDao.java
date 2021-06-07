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
import net.taihuapp.pachira.Price;
import net.taihuapp.pachira.Security;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Security prices
 * For securities with an nonempty Ticker, the security id column is set to 0 and
 * the prices are key by the ticker and the date
 *
 * For securities with empty ticker, the prices are key by the security id and the date
 */
public class SecurityPriceDao extends Dao<Pair<Security, Price>, Pair<Security, LocalDate>> {

    SecurityPriceDao(Connection connection) { this.connection = connection; }

    @Override
    String getTableName() { return "PRICES"; }

    @Override
    String[] getKeyColumnNames() {
        throw new IllegalArgumentException("getKeyColumnNames() should not be called for " + getClass().getName());
    }

    @Override
    String[] getColumnNames() {
        throw new IllegalArgumentException("getColumnNames() should not be called for " + getClass().getName());
    }

    @Override
    boolean autoGenKey() { return true; }

    @Override
    Pair<Security, LocalDate> getKeyValue(Pair<Security, Price> securityPricePair) {
        return new Pair<>(securityPricePair.getKey(), securityPricePair.getValue().getDate());
    }

    @Override
    Pair<Security, Price> fromResultSet(ResultSet resultSet) throws SQLException {
        Price price = new Price(resultSet.getObject("Date", LocalDate.class), resultSet.getBigDecimal("PRICE"));
        Security security = new Security(resultSet.getInt("ID"), resultSet.getString("TICKER"), "",
                null);
        return new Pair<>(security, price);
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, Pair<Security, Price> pricePair, boolean withKey) {
        throw new IllegalArgumentException("setPreparedStatement() should not be called for " + getClass().getName());
    }

    @Override
    String getSQLString(SQLCommands sqlCommands) {
        throw new IllegalArgumentException("getSQLString() should not be called for " + getClass().getName());
    }

    @Override
    public Optional<Pair<Security, Price>> get(Pair<Security, LocalDate> securityLocalDatePair) throws DaoException {
        final Security security = securityLocalDatePair.getKey();
        final LocalDate date = securityLocalDatePair.getValue();
        final String ticker = security.getTicker();
        final int securityID = security.getID();
        final String sqlCmd;
        if (ticker.isEmpty())
            sqlCmd = "SELECT * FROM " + getTableName() + " where SECURITYID = ? AND DATE = ?";
        else
            sqlCmd = "SELECT * FROM " + getTableName() + " where TICKER = ? AND DATE = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            if (ticker.isEmpty())
                preparedStatement.setInt(1, securityID);
            else
                preparedStatement.setString(1, ticker);
            preparedStatement.setObject(2, date);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    // found it
                    Price p = new Price(date, resultSet.getBigDecimal("PRICE"));
                    return Optional.of(new Pair<>(security, p));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET,
                    "Failed to get price for '" + ticker + "'(" + securityID + ") on " + date, e);
        }
    }

    @Override
    public int delete(Pair<Security, LocalDate> securityDatePair) throws DaoException {
        Security security = securityDatePair.getKey();
        final String ticker = security.getTicker();
        final int id = security.getID();
        final LocalDate date = securityDatePair.getValue();
        final String sqlCmd;
        if (ticker.isEmpty()) {
            sqlCmd = "DELETE FROM " + getTableName() + " WHERE SECURITYID = ? AND DATE = ?";
        } else {
            sqlCmd = "DELETE FROM " + getTableName() + " WHERE TICKER = ? AND DATE = ?";
        }
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            if (ticker.isEmpty())
                preparedStatement.setInt(1, id);
            else
                preparedStatement.setString(1, ticker);
            preparedStatement.setObject(2, date);

            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_DELETE,
                    "Failed to delete prices for '" + ticker + "'(" + id + ") on " + date, e);
        }
    }

    @Override
    public List<Pair<Security, Price>> getAll() throws DaoException {
        final String sqlCmd = "SELECT * FROM " + getTableName() + " ORDER BY TICKER, SECURITYID, DATE";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            List<Pair<Security, Price>> fullList = new ArrayList<>();
            while (resultSet.next()) {
                final int securityID = resultSet.getInt("SECURITYID");
                final String ticker = resultSet.getString("TICKER");
                final LocalDate date = resultSet.getObject("DATE", LocalDate.class);
                final BigDecimal p = resultSet.getBigDecimal("PRICE");
                fullList.add(new Pair<>(new Security(securityID, ticker, "", null), new Price(date, p)));
            }
            return fullList;
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed get all prices", e);
        }
    }

    @Override
    public Pair<Security, LocalDate> insert(Pair<Security, Price> securityPricePair) throws DaoException {
        mergePricesToDB(Collections.singletonList(securityPricePair));
        return new Pair<>(securityPricePair.getKey(), securityPricePair.getValue().getDate());
    }

    @Override
    public int update(Pair<Security, Price> securityPricePair) throws DaoException {
        mergePricesToDB(Collections.singletonList(securityPricePair));
        return 1;
    }

    public void mergePricesToDB(List<Pair<Security, Price>> pairList) throws DaoException {
        final String sqlCmd = "MERGE INTO " + getTableName()
                + " (SECURITYID, TICKER, DATE, PRICE) values (?, ?, ?, ?)";

        DaoManager daoManager = DaoManager.getInstance();
        try {
            daoManager.beginTransaction();
            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
                for (Pair<Security, Price> pair : pairList) {
                    final Security security = pair.getKey();
                    final String ticker = security.getTicker();
                    final int id = ticker.isEmpty() ? security.getID() : 0;
                    final Price price = pair.getValue();
                    preparedStatement.setInt(1, id);
                    preparedStatement.setString(2, ticker);
                    preparedStatement.setObject(3, price.getDate());
                    preparedStatement.setBigDecimal(4, price.getPrice());

                    preparedStatement.executeUpdate();
                }
            } catch (SQLException e) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_INSERT, "Merge to prices failed", e);
            }
            daoManager.commit();
        } catch (DaoException e) {
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }

            throw e;
        }
    }

    /**
     * get prices for the given security in a list ordered by date
     * @param security - the given security
     * @return - a list of prices
     * @throws DaoException - database operations
     */
    public List<Price> get(Security security) throws DaoException {
        final String ticker = security.getTicker();
        final int id = security.getID();
        final String sqlCmd = ticker.isEmpty() ?
                "SELECT * FROM " + getTableName() + " WHERE SECURITYID = ? ORDER BY DATE" :
                "SELECT * FROM " + getTableName() + " WHERE TICKER = ? ORDER BY DATE";
        List<Price> priceList = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            if (ticker.isEmpty())
                preparedStatement.setInt(1, id);
            else
                preparedStatement.setString(1, ticker);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final LocalDate date = resultSet.getObject("DATE", LocalDate.class);
                    final BigDecimal p = resultSet.getBigDecimal("PRICE");
                    priceList.add(new Price(date, p));
                }
                return priceList;
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET,
                    "Failed to get prices for '" + ticker + "'(" + id + ")", e);
        }
    }

    /**
     * get price for the given security on the given date
     * @param securityDatePair - input security and date
     * @return - optional price
     * @throws DaoException - from database operations
     */
    public Optional<Pair<Security, Price>> getLastPrice(Pair<Security, LocalDate> securityDatePair)
            throws DaoException {
        final Security security = securityDatePair.getKey();
        final String ticker = security.getTicker();
        final int id = security.getID();
        final LocalDate date = securityDatePair.getValue();
        final String sqlCmd;
        if (ticker.isEmpty())
            sqlCmd = "SELECT TOP 1 PRICE FROM " + getTableName()
                    + " WHERE SECURITYID = ? AND DATE <= ? ORDER BY DATE DESC";
        else
            sqlCmd = "SELECT TOP 1 PRICE FROM " + getTableName()
                    + " WHERE TICKER = ? AND DATE <= ? ORDER BY DATE DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            if (ticker.isEmpty())
                preparedStatement.setInt(1, id);
            else
                preparedStatement.setString(1, ticker);
            preparedStatement.setObject(2, date);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    BigDecimal p = resultSet.getBigDecimal("PRICE");
                    Price price = new Price(date, p);
                    return Optional.of(new Pair<>(security, price));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET,
                    "Failed to get latest price for '" + ticker + "'(" + id + ") no later than " + date, e);
        }
    }
}
