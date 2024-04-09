/*
 * Copyright (C) 2018-2024.  Guangliang He.  All Rights Reserved.
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

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Security prices
 * The prices are keyed by the security id and the date
 */
public class SecurityIDPriceDao extends Dao<Pair<Integer, Price>, Pair<Integer, LocalDate>> {

    SecurityIDPriceDao(Connection connection) { this.connection = connection; }

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
    Pair<Integer, LocalDate> getKeyValue(Pair<Integer, Price> securityIDPricePair) {
        return new Pair<>(securityIDPricePair.getKey(), securityIDPricePair.getValue().getDate());
    }

    @Override
    Pair<Integer, Price> fromResultSet(ResultSet resultSet) throws SQLException {
        Price price = new Price(resultSet.getObject("Date", LocalDate.class), resultSet.getBigDecimal("PRICE"));
        Integer securityID = resultSet.getInt("ID");
        return new Pair<>(securityID, price);
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, Pair<Integer, Price> pricePair, boolean withKey) {
        throw new IllegalArgumentException("setPreparedStatement() should not be called for " + getClass().getName());
    }

    @Override
    String getSQLString(SQLCommand sqlCommands) {
        throw new IllegalArgumentException("getSQLString() should not be called for " + getClass().getName());
    }

    @Override
    public Optional<Pair<Integer, Price>> get(Pair<Integer, LocalDate> securityIDLocalDatePair) throws DaoException {
        final Integer securityID = securityIDLocalDatePair.getKey();
        final LocalDate date = securityIDLocalDatePair.getValue();
        final String sqlCmd = "SELECT * FROM " + getTableName() + " where SECURITYID = ? AND DATE = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            preparedStatement.setInt(1, securityID);
            preparedStatement.setObject(2, date);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    // found it
                    Price p = new Price(date, resultSet.getBigDecimal("PRICE"));
                    return Optional.of(new Pair<>(securityID, p));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET,
                    "Failed to get price for (" + securityID + ") on " + date, e);
        }
    }

    @Override
    public int delete(Pair<Integer, LocalDate> securityIDDatePair) throws DaoException {
        final int id = securityIDDatePair.getKey();
        final LocalDate date = securityIDDatePair.getValue();
        final String sqlCmd = "DELETE FROM " + getTableName() + " WHERE SECURITYID = ? AND DATE = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            preparedStatement.setInt(1, id);
            preparedStatement.setObject(2, date);

            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_DELETE,
                    "Failed to delete prices for (" + id + ") on " + date, e);
        }
    }

    @Override
    public List<Pair<Integer, Price>> getAll() throws DaoException {
        final String sqlCmd = "SELECT * FROM " + getTableName() + " ORDER BY SECURITYID, DATE";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            List<Pair<Integer, Price>> fullList = new ArrayList<>();
            while (resultSet.next()) {
                final int securityID = resultSet.getInt("SECURITYID");
                final LocalDate date = resultSet.getObject("DATE", LocalDate.class);
                final BigDecimal p = resultSet.getBigDecimal("PRICE");
                fullList.add(new Pair<>(securityID, new Price(date, p)));
            }
            return fullList;
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed get all prices", e);
        }
    }

    @Override
    public Pair<Integer, LocalDate> insert(Pair<Integer, Price> securityIDPricePair) throws DaoException {
        mergePricesToDB(Collections.singletonList(securityIDPricePair));
        return new Pair<>(securityIDPricePair.getKey(), securityIDPricePair.getValue().getDate());
    }

    @Override
    public int update(Pair<Integer, Price> securityIDPricePair) throws DaoException {
        mergePricesToDB(Collections.singletonList(securityIDPricePair));
        return 1;
    }

    public void mergePricesToDB(List<Pair<Integer, Price>> pairList) throws DaoException {
        final String sqlCmd = "MERGE INTO " + getTableName()
                + " (SECURITYID, DATE, PRICE) values (?, ?, ?)";

        DaoManager daoManager = DaoManager.getInstance();
        try {
            daoManager.beginTransaction();
            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
                for (Pair<Integer, Price> pair : pairList) {
                    final int id = pair.getKey();
                    final Price price = pair.getValue();
                    preparedStatement.setInt(1, id);
                    preparedStatement.setObject(2, price.getDate());
                    preparedStatement.setBigDecimal(3, price.getPrice());

                    preparedStatement.executeUpdate();
                }
            } catch (SQLException e) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_MERGE, "Merge to prices failed", e);
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
     * @param securityID - id of the given security
     * @return - a list of prices
     * @throws DaoException - database operations
     */
    public List<Price> get(int securityID) throws DaoException {
        final String sqlCmd = "SELECT * FROM " + getTableName() + " WHERE SECURITYID = ? ORDER BY DATE";

        List<Price> priceList = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            preparedStatement.setInt(1, securityID);

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
                    "Failed to get prices for (" + securityID+ ")", e);
        }
    }

    /**
     * get price for the given security on the given date
     * @param securityIDDatePair - input security and date
     * @return - optional price
     * @throws DaoException - from database operations
     */
    public Optional<Pair<Integer, Price>> getLastPrice(Pair<Integer, LocalDate> securityIDDatePair)
            throws DaoException {
        final int id = securityIDDatePair.getKey();
        final LocalDate date = securityIDDatePair.getValue();
        final String sqlCmd = "SELECT TOP 1 PRICE FROM " + getTableName()
                    + " WHERE SECURITYID = ? AND DATE <= ? ORDER BY DATE DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd)) {
            preparedStatement.setInt(1, id);
            preparedStatement.setObject(2, date);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    BigDecimal p = resultSet.getBigDecimal("PRICE");
                    Price price = new Price(date, p);
                    return Optional.of(new Pair<>(id, price));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET,
                    "Failed to get latest price for (" + id + ") no later than " + date, e);
        }
    }
}
