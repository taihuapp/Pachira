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
import net.taihuapp.pachira.ReportDialogController;
import net.taihuapp.pachira.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static net.taihuapp.pachira.ReportDialogController.ItemName.*;

public class ReportSettingDao extends Dao<ReportDialogController.Setting, Integer> {

    private final ReportDetailDao reportDetailDao;

    ReportSettingDao(Connection connection, ReportDetailDao reportDetailDao) {
        this.connection = connection;
        this.reportDetailDao = reportDetailDao;
    }

    @Override
    String getTableName() { return "SAVEDREPORTS"; }

    @Override
    String[] getKeyColumnNames() { return new String[]{ "ID" }; }

    @Override
    String[] getColumnNames() {
        return new String[]{ "NAME", "TYPE", "DATEPERIOD", "SDATE", "EDATE", "FREQUENCY",
                "PAYEECONTAINS", "PAYEEREGEX", "MEMOCONTAINS", "MEMOREGEX" };
    }

    @Override
    boolean autoGenKey() { return true; }

    @Override
    Integer getKeyValue(ReportDialogController.Setting setting) { return setting.getID(); }

    @Override
    ReportDialogController.Setting fromResultSet(ResultSet resultSet) throws SQLException, DaoException {
        final int id = resultSet.getInt("ID");
        final ReportDialogController.ReportType type =
                ReportDialogController.ReportType.valueOf(resultSet.getString("TYPE"));

        final ReportDialogController.Setting setting = new ReportDialogController.Setting(id, type);
        setting.setName(resultSet.getString("NAME"));
        setting.setDatePeriod(ReportDialogController.DatePeriod.valueOf(resultSet.getString("DATEPERIOD")));
        setting.setStartDate(resultSet.getObject("SDATE", LocalDate.class));
        setting.setEndDate(resultSet.getObject("EDATE", LocalDate.class));
        setting.setFrequency(ReportDialogController.Frequency.valueOf(resultSet.getString("FREQUENCY")));
        setting.setPayeeContains(resultSet.getString("PAYEECONTAINS"));
        setting.setPayeeRegEx(resultSet.getBoolean("PAYEEREGEX"));
        setting.setMemoContains(resultSet.getString("MEMOCONTAINS"));
        setting.setMemoRegEx(resultSet.getBoolean("MEMOREGEX"));

        reportDetailDao.get(id).ifPresent(reportDetails -> {
            for (ReportDialogController.ItemName itemName : ReportDialogController.ItemName.values()) {
                List<String> values = reportDetails.getValue().get(itemName.name());
                if (values == null)
                    continue;
                switch (itemName) {
                    case ACCOUNTID:
                        setting.getSelectedAccountIDSet().addAll(values.stream().map(Integer::parseInt)
                                .collect(Collectors.toSet()));
                        break;
                    case CATEGORYID:
                        setting.getSelectedCategoryIDSet().addAll(values.stream().map(Integer::parseInt)
                                .collect(Collectors.toSet()));
                        break;
                    case SECURITYID:
                        setting.getSelectedSecurityIDSet().addAll(values.stream().map(Integer::parseInt)
                                .collect(Collectors.toSet()));
                        break;
                    case TRADEACTION:
                        setting.getSelectedTradeActionSet().addAll(values.stream().map(Transaction.TradeAction::valueOf)
                                .collect(Collectors.toSet()));
                        break;
                }
            }
        });
        return setting;
    }

    @Override
    void setPreparedStatement(PreparedStatement preparedStatement, ReportDialogController.Setting setting,
                              boolean withKey) throws SQLException {
        preparedStatement.setString(1, setting.getName());
        preparedStatement.setString(2, setting.getType().name());
        preparedStatement.setString(3, setting.getDatePeriod().name());
        preparedStatement.setObject(4, setting.getStartDate());
        preparedStatement.setObject(5, setting.getEndDate());
        preparedStatement.setString(6, setting.getFrequency().name());
        preparedStatement.setString(7, setting.getPayeeContains());
        preparedStatement.setBoolean(8, setting.getPayeeRegEx());
        preparedStatement.setString(9, setting.getMemoContains());
        preparedStatement.setBoolean(10, setting.getMemoRegEx());
        if (withKey)
            preparedStatement.setInt(11, setting.getID());
    }

    @Override
    public int delete(Integer reportID) throws DaoException {
        DaoManager daoManager = DaoManager.getInstance();
        try {
            daoManager.beginTransaction();
            final int n = super.delete(reportID);
            reportDetailDao.delete(reportID);
            daoManager.commit();
            return n;
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
     * get details from the setting
     * @param setting the input setting
     * @return a Map of item names and its corresponding values
     */
    private Map<String, List<String>> getReportDetails(ReportDialogController.Setting setting) {
        Map<String, List<String>> itemsMap = new HashMap<>();
        itemsMap.put(ACCOUNTID.name(), setting.getSelectedAccountIDSet().stream()
                .map(String::valueOf).collect(Collectors.toList()));
        itemsMap.put(CATEGORYID.name(), setting.getSelectedCategoryIDSet().stream()
                .map(String::valueOf).collect(Collectors.toList()));
        itemsMap.put(SECURITYID.name(), setting.getSelectedSecurityIDSet().stream()
                .map(String::valueOf).collect(Collectors.toList()));
        itemsMap.put(TRADEACTION.name(), setting.getSelectedTradeActionSet().stream()
                .map(String::valueOf).collect(Collectors.toList()));
        return itemsMap;
    }

    @Override
    public Integer insert(ReportDialogController.Setting setting) throws DaoException {
        DaoManager daoManager = DaoManager.getInstance();
        try {
            daoManager.beginTransaction();
            final int reportID = super.insert(setting);
            Map<String, List<String>> itemsMap = getReportDetails(setting);
            reportDetailDao.insert(new Pair<>(reportID, itemsMap));
            daoManager.commit();
            return reportID;
        } catch (DaoException e) {
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
    }

    @Override
    public int update(ReportDialogController.Setting setting) throws DaoException {
        DaoManager daoManager = DaoManager.getInstance();
        try {
            daoManager.beginTransaction();
            final int n = super.update(setting);
            Map<String, List<String>> itemsMap = getReportDetails(setting);
            reportDetailDao.update(new Pair<>(setting.getID(), itemsMap));
            daoManager.commit();
            return n;
        } catch (DaoException e) {
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
    }
}
