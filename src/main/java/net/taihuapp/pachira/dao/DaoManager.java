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

import net.taihuapp.pachira.Account;
import net.taihuapp.pachira.MainModel;
import net.taihuapp.pachira.Transaction;
import org.h2.api.ErrorCode;
import org.h2.tools.ChangeFileEncryption;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DaoManager class for file based H2 databases
 */
public class DaoManager {

    // constants
    private static final String DB_VERSION_NAME = "DBVERSION";
    private static final int DB_VERSION_VALUE = 10; // required DB_VERSION
    private static final String DB_OWNER = "ADMPACHIRA";
    private static final String DB_POSTFIX = ".mv.db";
    private static final String URL_PREFIX = "jdbc:h2:";
    private static final String CIPHER_CLAUSE="CIPHER=AES;";
    private static final String IF_EXIST_CLAUSE="IFEXISTS=TRUE;";

    private static final String CLIENT_UID_NAME = "ClientUID";

    private static final int ACCOUNT_NAME_LEN = 40;
    private static final int ACCOUNT_DESC_LEN = 256;
    private static final int MIN_ACCOUNT_ID = 10;

    private static final int SECURITY_TICKER_LEN = 16;
    private static final int SECURITY_NAME_LEN = 64;

    private static final int PRICE_TOTAL_LEN = 20;
    public static final int PRICE_FRACTION_LEN = 8;

    private static final int MIN_CATEGORY_ID = 10;
    private static final int CATEGORY_NAME_LEN = 40;
    private static final int CATEGORY_DESC_LEN = 256;

    private static final int AMOUNT_TOTAL_LEN = 20;
    public static final int AMOUNT_FRACTION_LEN = 4;

    private static final int ADDRESS_LINE_LEN = 32;

    private static final int AMORTIZATION_LINE_LEN = 32;

    private static final int TRANSACTION_STATUS_LEN = 16;
    private static final int TRANSACTION_MEMO_LEN = 255;
    private static final int TRANSACTION_REF_LEN = 16;
    private static final int TRANSACTION_PAYEE_LEN = 64;
    private static final int TRANSACTION_TRADEACTION_LEN = 16;
    private static final int TRANSACTION_TRANSFER_REMINDER_LEN = 40;
    private static final int TRANSACTION_FITID_LEN = 256;

    private static final int QUANTITY_FRACTION_LEN = 8;
    private static final int QUANTITY_TOTAL_LEN = 20;
    public static final int QUANTITY_FRACTION_DISPLAY_LEN = 6;

    // the connection to the database
    private Connection connection = null;

    // nested transaction levels
    private int transactionLevel = 0;

    // private constructor
    private DaoManager() {}

    public static DaoManager getInstance() { return DaoManagerSingleton.INSTANCE; }

    private static class DaoManagerSingleton {
        public static final DaoManager INSTANCE = new DaoManager();
    }

    // postfix for H2 database file
    public static String getDBPostfix() { return DB_POSTFIX; }

    /**
     * begin a jdbc transaction (transactions can be nested)
     * @throws DaoException - from jdbc operations
     */
    public void beginTransaction() throws DaoException {
        if (transactionLevel++ == 0) {
            try {
                connection.setAutoCommit(false);
            } catch (SQLException e) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_SET_AUTOCOMMIT, "setAutoCommit failure", e);
            }
        }
    }

    /**
     * commit a jdbc transaction.
     * @throws DaoException - jdbc operations
     */
    public void commit() throws DaoException {
        if (transactionLevel <= 0)
            throw new IllegalStateException("commit expects positive transactionLevel, got " + transactionLevel);

        if (transactionLevel == 1) {
            // setAutoCommit(true) includes commit operation
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_SET_AUTOCOMMIT, "setAutoCommit failure", e);
            }
        }

        // decrement transaction level, if we didn't experience an exception during commit
        transactionLevel--;
    }

    /**
     * roll back a jdbc transaction
     * @throws DaoException - from jdbc operations
     */
    public void rollback() throws DaoException {
        if (transactionLevel <= 0)
            throw new IllegalStateException("rollback expects positive transactionLevel, got " + transactionLevel);

        // if we're here, that means some sql before commit failed, or commit failed,
        // decrement transactionLevel first, then rollback
        if (--transactionLevel == 0) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_ROLLBACK, "rollback failure", e);
            }
        }
    }

    /**
     * @return file name for the connection
     * @throws DaoException - database operation
     */
    public String getDBFileName() throws DaoException {
        try {
            String url = connection.getMetaData().getURL();
            int index = url.indexOf(';');
            if (index > 0)
                url = url.substring(0, index); // throw away everything after first ';'
            if (!url.startsWith(URL_PREFIX))
                throw new IllegalArgumentException("Bad formatted url: '" + url
                        + "'. Url should start with '" + URL_PREFIX + "'");
            return url.substring(URL_PREFIX.length());
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET_CONNECTION_METADATA, "", e);
        }
    }

    /**
     * backup the current connection
     * @return the backup file name
     * @throws DaoException from database operations
     */
    public String backup() throws DaoException {
        try (PreparedStatement preparedStatement = connection.prepareStatement("Backup to ?")) {
            // dbFileName doesn't have postfix
            final String dbFileName = getDBFileName();
            final String backupDBFileName = dbFileName + "Backup"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + ".zip";
            preparedStatement.setString(1, backupDBFileName);
            preparedStatement.execute();
            return backupDBFileName;
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_BACKUP, "", e);
        }
    }

    /**
     *
     * open an database connection
     * @param fileName the file name for the database WITHOUT postfix
     * @param password the password to the database
     * @param isNew if true, open a connection to the newly created database,
     *              otherwise, open the existing database
     * @throws DaoException - from DB operations
     */
    public void openConnection(String fileName, String password, boolean isNew) throws DaoException {
        try {
            Class.forName("org.h2.Driver");
            String url = URL_PREFIX + fileName + ";" + CIPHER_CLAUSE;
            if (!isNew)
                url += IF_EXIST_CLAUSE;
            Connection newConnection = DriverManager.getConnection(url, DB_OWNER, password + " " + password);

            // once connection is open, we can close the old connection
            closeConnection();

            connection = newConnection;

            if (isNew) {
                // new database, init
                initDB();
            } else {
                // existing database, bring it up to date
                DaoManager daoManager = DaoManager.getInstance();
                try {
                    daoManager.beginTransaction();

                    if (!hasSettingsTable()) {
                        createSettingsTable();
                        putSetting(DB_VERSION_NAME, "0");
                    }

                    final int dbVersion = getSetting(DB_VERSION_NAME).map(Integer::valueOf)
                            .orElseThrow(() -> new DaoException(DaoException.ErrorCode.MISSING_DB_VERSION,
                                    "Failed to get DB VERSION"));
                    updateDB(dbVersion, DB_VERSION_VALUE);

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
        } catch (ClassNotFoundException e) {
            throw new DaoException(DaoException.ErrorCode.DB_DRIVER_NOT_FOUND, "Can't find h2 driver", e);
        } catch (SQLException e) {
            final DaoException.ErrorCode errorCode;
            final String errMsg;
            switch (e.getErrorCode()) {
                case ErrorCode.DATABASE_NOT_FOUND_1: //
                case ErrorCode.DATABASE_NOT_FOUND_2:
                    errorCode = DaoException.ErrorCode.DB_FILE_NOT_FOUND;
                    errMsg = "Database file " + fileName + " not found";
                    break;
                case ErrorCode.FILE_ENCRYPTION_ERROR_1:
                case ErrorCode.WRONG_PASSWORD_FORMAT:
                case ErrorCode.WRONG_USER_OR_PASSWORD:
                    errorCode = DaoException.ErrorCode.BAD_PASSWORD;
                    errMsg = "Wrong password";
                    break;
                case ErrorCode.DATABASE_ALREADY_OPEN_1: // 90020
                    errorCode = DaoException.ErrorCode.FAIL_TO_OPEN_CONNECTION;
                    errMsg = "Database is opened by other process.  Please close the other process";
                    break;
                default:
                    errorCode = DaoException.ErrorCode.FAIL_TO_OPEN_CONNECTION;
                    errMsg = "Failed to open " + fileName;
                    break;
            }
            throw new DaoException(errorCode, errMsg, e);
        }
    }

    /**
     * close the connection and clear daoMap
     * @throws DaoException - db operations
     */
    public void closeConnection() throws DaoException {
        if (connection != null) {
            try {
                connection.close();
                daoMap.clear();
            } catch (SQLException e) {
                throw new DaoException(DaoException.ErrorCode.FAIL_TO_CLOSE_CONNECTION, "close connection failure", e);
            }
        }
    }

    // returns true if SETTINGS table exists, false otherwise
    private boolean hasSettingsTable() throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, "SETTINGS",
                new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    /**
     * put a name/value strings pair into Settings table
     * @param name - the name
     * @param value - the value
     * @throws SQLException - from jdbc operation
     */
    private void putSetting(String name, String value) throws SQLException {
        executeUpdateQuery("merge into SETTINGS (NAME, VALUE) values ('" + name + "', '" + value + "')");
    }

    /**
     * get the value corresponding to the name from the Settings table
     * @param name - the name to look for
     * @return - String Optional
     * @throws SQLException - jdbc operation
     */
    private Optional<String> getSetting(String name) throws SQLException {
        // earlier version of SETTINGS table has int as column type for VALUE
        // later it was changed to varchar(255).
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select VALUE from SETTINGS where NAME = '" + name + "'")) {
            if (resultSet.next()) {
                // found one or more rows
                ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
                if (resultSetMetaData.getColumnType(1) == Types.INTEGER) {
                    return Optional.of(String.valueOf(resultSet.getInt("VALUE")));
                } else {
                    return Optional.of(resultSet.getString("VALUE"));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<UUID> getClientUID() throws DaoException {
        try {
            return getSetting(CLIENT_UID_NAME).map(UUID::fromString);
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_GET, "Failed to get Client UID", e);
        }
    }

    public void putClientUID(UUID uuid) throws DaoException {
        try {
            putSetting(CLIENT_UID_NAME, uuid.toString());
        } catch (SQLException e) {
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_INSERT, "Failed to put Client UID", e);
        }
    }

    /**
     * initialize the database, it's the best for the caller to wrap it in a transaction
     * @throws SQLException - from direct database operations
     * @throws DaoException - from Dao operations
     */
    private void initDB() throws SQLException, DaoException {
        if (connection == null)
            return;

        // SETTINGS table
        createSettingsTable();
        putSetting(DB_VERSION_NAME, String.valueOf(DB_VERSION_VALUE));

        // direct connect tables
        createDirectConnectTables();
        alterAccountDCSTable();

        // Accounts table
        String sqlCmd = "create table ACCOUNTS ("
                // make sure to start from MIN_ACCOUNT_ID
                + "ID integer NOT NULL AUTO_INCREMENT (" + MIN_ACCOUNT_ID + "), "
                + "TYPE varchar (16) NOT NULL, "
                + "NAME varchar(" + ACCOUNT_NAME_LEN + ") UNIQUE NOT NULL, "
                + "DESCRIPTION varchar(" + ACCOUNT_DESC_LEN + ") NOT NULL, "
                + "HIDDENFLAG boolean NOT NULL, "
                + "DISPLAYORDER integer NOT NULL, "
                + "LASTRECONCILEDATE date, "
                + "primary key (ID));";
        executeUpdateQuery(sqlCmd);
        AccountDao accountDao = (AccountDao) getDao(DaoType.ACCOUNT);
        accountDao.insert(new Account(-1, Account.Type.CHECKING, MainModel.DELETED_ACCOUNT_NAME,
                "Placeholder for the Deleted Account", true, Integer.MAX_VALUE,
                null, BigDecimal.ZERO));

        // securities table
        //  starts from 1
        sqlCmd = "create table SECURITIES ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), "  // make sure starts with 1
                + "TICKER varchar(" + SECURITY_TICKER_LEN + ") NOT NULL, "
                + "NAME varchar(" + SECURITY_NAME_LEN + ") UNIQUE NOT NULL, "
                + "TYPE varchar(16) NOT NULL, "
                + "primary key (ID));";
        executeUpdateQuery(sqlCmd);

        // Price Table
        // a price can be keyed by its ticker or its security id.
        // On insert, if the ticker is not empty, then security id is set to 0, otherwise, a real security id is used.
        // On retrieve, return either ticker match or security id match.
        sqlCmd = "create table PRICES ("
                + "SECURITYID integer NOT NULL, "
                + "TICKER varchar(" + SECURITY_TICKER_LEN + ") NOT NULL, "
                + "DATE date NOT NULL, "
                + "PRICE decimal(" + PRICE_TOTAL_LEN + "," + PRICE_FRACTION_LEN + "),"
                + "PRIMARY KEY (SECURITYID, TICKER, DATE));";
        executeUpdateQuery(sqlCmd);

        // Category Table
        // ID starts from 1
        sqlCmd = "create table CATEGORIES ("
                // make sure to start from MIN_CATEGORY_ID
                + "ID integer NOT NULL AUTO_INCREMENT (" + MIN_CATEGORY_ID + "), "
                + "NAME varchar(" + CATEGORY_NAME_LEN + ") UNIQUE NOT NULL, "
                + "DESCRIPTION varchar(" + CATEGORY_DESC_LEN + ") NOT NULL, "
                + "INCOMEFLAG boolean, "
                + "TAXREFNUM integer, "
                + "BUDGETAMOUNT decimal(20,4), "
                + "primary key (ID))";
        executeUpdateQuery(sqlCmd);

        // SplitTransaction
        // ID starts from 1
        sqlCmd = "create table SPLITTRANSACTIONS ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), "
                + "TRANSACTIONID integer NOT NULL, "
                + "CATEGORYID integer, "
                + "TAGID integer, "
                + "MEMO varchar (" + TRANSACTION_MEMO_LEN + "), "
                + "AMOUNT decimal(" + AMOUNT_TOTAL_LEN + "," + AMOUNT_FRACTION_LEN + "), "
                + "PERCENTAGE decimal(20,4), "
                + "MATCHTRANSACTIONID integer, "
                + "MATCHSPLITTRANSACTIONID integer, "
                + "primary key (ID));";
        executeUpdateQuery(sqlCmd);

        // Addresses table
        // ID starts from 1
        sqlCmd = "create table ADDRESSES ("
                + "ID integer not null auto_increment (1), "
                + "LINE0 varchar(" + ADDRESS_LINE_LEN + "), "
                + "LINE1 varchar(" + ADDRESS_LINE_LEN + "), "
                + "LINE2 varchar(" + ADDRESS_LINE_LEN + "), "
                + "LINE3 varchar(" + ADDRESS_LINE_LEN + "), "
                + "LINE4 varchar(" + ADDRESS_LINE_LEN + "), "
                + "LINE5 varchar(" + ADDRESS_LINE_LEN + "), "
                + "primary key (ID));";
        executeUpdateQuery(sqlCmd);

        // amortization lines table
        // ID starts from 1
        sqlCmd = "create table AMORTIZATIONLINES ("
                + "ID integer not null auto_increment (1), "
                + "LINE0 varchar(" + AMORTIZATION_LINE_LEN + "), "
                + "LINE1 varchar(" + AMORTIZATION_LINE_LEN + "), "
                + "LINE2 varchar(" + AMORTIZATION_LINE_LEN + "), "
                + "LINE3 varchar(" + AMORTIZATION_LINE_LEN + "), "
                + "LINE4 varchar(" + AMORTIZATION_LINE_LEN + "), "
                + "LINE5 varchar(" + AMORTIZATION_LINE_LEN + "), "
                + "LINE6 varchar(" + AMORTIZATION_LINE_LEN + "), "
                + "primary key (ID)); ";
        executeUpdateQuery(sqlCmd);

        // Transactions
        // ID starts from 1
        sqlCmd = "create table TRANSACTIONS ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), " // make sure to start with 1
                + "ACCOUNTID integer NOT NULL, "
                + "DATE date NOT NULL, "
                + "ADATE date, "
                + "AMOUNT decimal(20,4), "
                + "STATUS varchar(" + TRANSACTION_STATUS_LEN + ") not null, " // status
                + "CATEGORYID integer, "   // positive for category ID, negative for transfer account id
                + "TAGID integer, "
                + "MEMO varchar(" + TRANSACTION_MEMO_LEN + ") not null, "
                + "REFERENCE varchar (" + TRANSACTION_REF_LEN + ") not null, "  // reference or check number as string
                + "PAYEE varchar (" + TRANSACTION_PAYEE_LEN + ") not null, "
                + "SPLITFLAG boolean, "
                + "ADDRESSID integer, "
                + "AMORTIZATIONID integer, "
                + "TRADEACTION varchar(" + TRANSACTION_TRADEACTION_LEN + "), "
                + "SECURITYID integer, "
                + "PRICE decimal(" + PRICE_TOTAL_LEN + "," + PRICE_FRACTION_LEN + "), "
                + "QUANTITY decimal(" + QUANTITY_TOTAL_LEN + "," + QUANTITY_FRACTION_LEN + "), "
                + "OLDQUANTITY decimal(" + QUANTITY_TOTAL_LEN + "," + QUANTITY_FRACTION_LEN + "), "  // used in stock split transactions
                + "TRANSFERREMINDER varchar(" + TRANSACTION_TRANSFER_REMINDER_LEN + "), "
                + "COMMISSION decimal(20,4), "
                + "ACCRUEDINTEREST decimal(20,4), "
                + "AMOUNTTRANSFERRED decimal(20,4), "
                + "MATCHTRANSACTIONID integer, "   // matching transfer transaction id
                + "MATCHSPLITTRANSACTIONID integer, "  // matching split
                + "FITID varchar(" + TRANSACTION_FITID_LEN + ") not null, "
                + "primary key (ID));";
        executeUpdateQuery(sqlCmd);

        // LotMATCH table
        sqlCmd = "create table LOTMATCH ("
                + "TransID integer NOT NULL, "
                + "MatchID integer NOT NULL, "
                + "MatchQuantity decimal(" + QUANTITY_TOTAL_LEN + ","  + QUANTITY_FRACTION_LEN + "), "
                + "Constraint UniquePair unique (TransID, MatchID));";
        executeUpdateQuery(sqlCmd);

        // SavedReports table
        sqlCmd = "create table SAVEDREPORTS ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), "  // make sure to start with 1
                + "NAME varchar (" + MainModel.SAVEDREPORTS_NAME_LEN + ") UNIQUE NOT NULL, "       // name of the report
                + "TYPE varchar (16) NOT NULL, "              // type of the report
                + "DATEPERIOD varchar (16) NOT NULL, "        // enum for dateperiod
                + "SDATE date NOT NULL, "                              // customized start date
                + "EDATE date NOT NULL, "                              // customized start date
                + "FREQUENCY varchar (16) NOT NULL, "                 // frequency enum
                + "PAYEECONTAINS varchar (80), "
                + "PAYEEREGEX boolean, "
                + "MEMOCONTAINS varchar (80), "
                + "MEMOREGEX boolean);";
        executeUpdateQuery(sqlCmd);

        // SavedReportDetails table
        sqlCmd = "create table SAVEDREPORTDETAILS ("
                + "REPORTID integer NOT NULL, "
                + "ITEMNAME varchar(16) NOT NULL, "
                + "ITEMVALUE varchar(16) NOT NULL);";
        executeUpdateQuery(sqlCmd);

        // Tag table
        sqlCmd = "create table TAGS ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), " // starting 1
                + "NAME varchar(20) UNIQUE NOT NULL, "
                + "DESCRIPTION varchar(80) NOT NULL, "
                + "primary key(ID));";
        executeUpdateQuery(sqlCmd);

        // Reminders table
        sqlCmd = "create table REMINDERS ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), "  // make sure to start with 1
                + "TYPE varchar(" + 12 + "), "
                + "PAYEE varchar (" + TRANSACTION_PAYEE_LEN + "), "
                + "AMOUNT decimal(20, 4), "
                + "ESTCOUNT integer, "
                + "ACCOUNTID integer NOT NULL, "
                + "CATEGORYID integer, "
                + "TRANSFERACCOUNTID integer, "
                + "TAGID integer, "
                + "MEMO varchar(" + TRANSACTION_MEMO_LEN + "), "
                + "STARTDATE date NOT NULL, "
                + "ENDDATE date, "
                + "BASEUNIT varchar(8) NOT NULL, "
                + "NUMPERIOD integer NOT NULL, "
                + "ALERTDAYS integer NOT NULL, "
                + "ISDOM boolean NOT NULL, "
                + "ISFWD boolean NOT NULL, "
                + "primary key (ID));";
        executeUpdateQuery(sqlCmd);

        // ReminderTransactions table
        sqlCmd = "create table REMINDERTRANSACTIONS ("
                + "REMINDERID integer NOT NULL, "
                + "DUEDATE date, "
                + "TRANSACTIONID integer)";
        executeUpdateQuery(sqlCmd);
    }

    // bring database to the latest version
    private void updateDB(int oldV, int newV) throws SQLException {
        if (oldV == newV)
            return; // nothing to do.

        if (oldV > newV) {
            throw new IllegalArgumentException("updateDB called with unsupported versions, "
                    + "old version = " + oldV + ", new version = " + newV);
        }

        if (newV - oldV > 1)
            updateDB(oldV, newV-1); // recursively bring from oldV up.

        // need to run this to update DBVERSION
        if (newV == 10) {
            // change account table type column size
            executeUpdateQuery("alter table ACCOUNTS alter column TYPE varchar(16) not null");
            executeUpdateQuery("update ACCOUNTS set TYPE = 'CHECKING' where TYPE = 'SPENDING'");
            executeUpdateQuery("update ACCOUNTS set TYPE = 'BROKERAGE' where TYPE = 'INVESTING'");
            executeUpdateQuery("update ACCOUNTS set TYPE = 'HOUSE' where TYPE = 'PROPERTY'");
            executeUpdateQuery("update ACCOUNTS set TYPE = 'LOAN' where TYPE = 'DEBT'");
            executeUpdateQuery("alter table SETTINGS alter column VALUE varchar(255) not null");
            executeUpdateQuery("alter table SPLITTRANSACTIONS drop column PAYEE");
        } else if (newV == 9) {
            // change PRICES table structure, remove duplicate, etc.
            final String alterPRICESTableSQL = "alter table PRICES add TICKER varchar(16);"
                    + "update PRICES p set p.TICKER = (select s.TICKER from SECURITIES s where s.ID = p.SECURITYID);"
                    + "alter table PRICES drop primary key;"
                    + "update PRICES set SECURITYID = 0 where length(TICKER) > 0;"
                    + "create table NEWPRICES as select * from PRICES group by SECURITYID, TICKER, DATE;"
                    + "drop table PRICES;"
                    + "alter table NEWPRICES rename to PRICES;"
                    + "alter table PRICES alter column SECURITYID int not null;"
                    + "alter table PRICES alter column TICKER varchar(16) not null;"
                    + "alter table PRICES alter column DATE date not null;"
                    + "alter table PRICES add primary key (securityid, ticker, date);";
            executeUpdateQuery(alterPRICESTableSQL);
        } else if (newV == 8) {
            // replace XIN to DEPOSIT AND XOUT to WITHDRAW in Transactions table
            final String updateXIN = "update TRANSACTIONS set TRADEACTION = 'DEPOSIT' where TRADEACTION = 'XIN'";
            final String updateXOUT = "update TRANSACTIONS set TRADEACTION = 'WITHDRAW' where TRADEACTION = 'XOUT'";
            final String updateReminder = "update REMINDERS set (TYPE, CATEGORYID) = ('PAYMENT', -TRANSFERACCOUNTID) "
                    + "where TYPE = 'TRANSFER'";
            // first copy XIN and XOUT to temp table
            // delete XIN and XOUT in savedreportdetails
            // change XIN to DEPOSIT and XOUT to WITHDRAW in temp
            // insert altered rows back to savedreportdetails if such a row doesn't already exist
            final String updateSavedReportDetails = "create table TEMP as select * from SAVEDREPORTDETAILS "
                    + "where ITEMVALUE = 'XIN' or ITEMVALUE = 'XOUT'; "
                    + "delete from SAVEDREPORTDETAILS where  ITEMVALUE = 'XIN' or ITEMVALUE = 'XOUT'; "
                    + "update TEMP set ITEMVALUE = ('DEPOSIT') where ITEMVALUE = 'XIN'; "
                    + "update TEMP set ITEMVALUE = ('WITHDRAW') where ITEMVALUE = 'XOUT'; "
                    + "insert into SAVEDREPORTDETAILS select * from TEMP t where not exists("
                    + "select * from SAVEDREPORTDETAILS s where s.REPORTID = t.REPORTID and s.ITEMNAME = t.ITEMNAME);"
                    + "drop table TEMP;";
            final String alterTable = "alter table REMINDERS drop TRANSFERACCOUNTID";

            executeUpdateQuery(updateXIN);
            executeUpdateQuery(updateXOUT);
            executeUpdateQuery(updateReminder);
            executeUpdateQuery(alterTable);
            executeUpdateQuery(updateSavedReportDetails);
        } else if (newV == 7) {
            alterAccountDCSTable();
        } else if (newV == 6) {
            createDirectConnectTables();

            executeUpdateQuery("update TRANSACTIONS set MEMO = '' where MEMO is null");
            executeUpdateQuery("alter table TRANSACTIONS alter column MEMO varchar("
                    + TRANSACTION_MEMO_LEN + ") not null default''");

            executeUpdateQuery("update TRANSACTIONS set REFERENCE = '' where REFERENCE is null");
            executeUpdateQuery("alter table Transactions alter column REFERENCE varchar("
                    + TRANSACTION_REF_LEN + ")");

            executeUpdateQuery("update TRANSACTIONS set PAYEE = '' where PAYEE is null");
            executeUpdateQuery("alter table Transactions alter column PAYEE varchar("
                    + TRANSACTION_PAYEE_LEN + ")");

            executeUpdateQuery("alter table TRANSACTIONS add (FITID varchar("
                    + TRANSACTION_FITID_LEN + ") NOT NULL default '')");
        } else if (newV == 5) {
            final String updateSQL = "alter table TRANSACTIONS add (ACCRUEDINTEREST decimal(20, 4) default 0);";
            executeUpdateQuery(updateSQL);
        } else if (newV == 4) {
            final String updateSQL = "alter table ACCOUNTS add (LASTRECONCILEDATE Date)";
            executeUpdateQuery(updateSQL);
        } else if (newV == 3) {
            // cleared column was populated with int value of ascii code
            // setup new STATUS column and set value according to the cleared column
            // then drop cleared column
            final String updateSQL0 = "alter table TRANSACTIONS add (STATUS varchar("
                    + TRANSACTION_STATUS_LEN + ") NOT NULL default '" + Transaction.Status.UNCLEARED.name() + "')";
            final String updateSQL1 = "update TRANSACTIONS set STATUS = "
                    + "case CLEARED "
                    + "when 42 then '" + Transaction.Status.CLEARED.name() + "'"// '*', cleared
                    + "when 82 then '" + Transaction.Status.RECONCILED.name() + "'"// 'R', reconciled
                    + "when 88 then '" + Transaction.Status.RECONCILED.name() + "'"// 'X', reconciled
                    + "when 99 then '" + Transaction.Status.CLEARED.name() + "'"// 'c', cleared
                    + "else '" + Transaction.Status.UNCLEARED.name() + "'" // default uncleared
                    + "end";
            final String updateSQL2 = "alter table TRANSACTIONS drop CLEARED";
            final String updateSQL3 = "alter table SPLITTRANSACTIONS add (TAGID int)";
            executeUpdateQuery(updateSQL0);
            executeUpdateQuery(updateSQL1);
            executeUpdateQuery(updateSQL2);
            executeUpdateQuery(updateSQL3);
        } else if (newV == 2) {
            final String alterSQL = "alter table SAVEDREPORTS add (" +
                    "PAYEECONTAINS varchar(80) NOT NULL default '', " +
                    "PAYEEREGEX boolean NOT NULL default FALSE, " +
                    "MEMOCONTAINS varchar(80) NOT NULL default '', " +
                    "MEMOREGEX boolean NOT NULL default FALSE)";
            executeUpdateQuery(alterSQL);
        } else if (newV == 1) {
            // converting self transferring transaction to DEPOSIT or WITHDRAW
            // and set categoryid to 1 (in invalid category.
            final String updateSQL0 = "update TRANSACTIONS " +
                    "set TRADEACTION = casewhen(tradeaction = 'XIN', 'DEPOSIT', 'WITHDRAW'), " +
                    "categoryid = 1 where categoryid = -accountid and (tradeaction = 'XIN' or tradeaction = 'XOUT')";
            final String updateSQL1 = "update TRANSACTIONS " +
                    "set TRADEACTION = casewhen(TRADEACTION = 'WITHDRAW', 'XOUT', 'XIN') " +
                    "where categoryid < 0 and categoryid <> - accountid and  " +
                    "(tradeaction = 'DEPOSIT' OR TRADEACTION = 'WITHDRAW')";
            executeUpdateQuery(updateSQL0);
            executeUpdateQuery(updateSQL1);
        } else {
            throw new IllegalArgumentException("updateDBVersion called with unsupported versions, " +
                    "old version: " + oldV + ", new version: " + newV + ".");
        }

        // finally, we set DB_VERSION
        putSetting(DB_VERSION_NAME, String.valueOf(DB_VERSION_VALUE));
    }

    // add LASTDOWNLOADLEDGEBAL column
    private void alterAccountDCSTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("alter table ACCOUNTDCS add (LASTDOWNLOADLEDGEBAL decimal(20, 4))");
        }
    }

    // create ACCOUNTDCS table, DCINFO table, and FIDATA table for Direct Connect functionality.
    private void createDirectConnectTables() throws SQLException {
        String sqlCmd;
        sqlCmd = "create table ACCOUNTDCS ("
                + "ACCOUNTID integer UNIQUE NOT NULL, "
                + "ACCOUNTTYPE varchar(16) NOT NULL, "
                + "DCID integer NOT NULL, "
                + "ROUTINGNUMBER varchar(9) NOT NULL, "
                + "ACCOUNTNUMBER varchar(256) NOT NULL, "  // encrypted account number
                + "LASTDOWNLOADDATE Date NOT NULL, "
                + "LASTDOWNLOADTIME Time Not NULL, "
                + "primary key (ACCOUNTID))";
        executeUpdateQuery(sqlCmd);

        sqlCmd = "create table DCINFO ("
                + "ID integer NOT NULL AUTO_INCREMENT (10), "
                + "NAME varchar(128) UNIQUE NOT NULL, "
                + "FIID integer NOT NULL, "
                + "USERNAME varchar(256), "   // encrypted user name
                + "PASSWORD varchar(256), "   // encrypted password
                + "primary key (ID))";
        executeUpdateQuery(sqlCmd);

        sqlCmd = "create table FIDATA ("
                + "ID integer NOT NULL AUTO_INCREMENT (10), "
                + "FIID varchar(32) NOT NULL, "  // not null, can be empty
                + "SUBID varchar(32) NOT NULL, " // not null, can be empty
                + "NAME varchar(128) UNIQUE NOT NULL, " // not null, can be empty
                + "ORG varchar(128) NOT NULL, "
                + "BROKERID varchar(32) NOT NULL, "
                + "URL varchar(2084) UNIQUE NOT NULL, "
                + "primary key (ID), "
                + "CONSTRAINT FIID_SUBID UNIQUE(FIID, SUBID))";
        executeUpdateQuery(sqlCmd);
    }

    // create settings table and populate database version
    private void createSettingsTable() throws SQLException {
        executeUpdateQuery("create table SETTINGS (" +
                "NAME varchar(32) UNIQUE NOT NULL," +
                "VALUE varchar(255) NOT NULL," +
                "primary key (NAME))");
    }

    // execute an update query on connection
    private void executeUpdateQuery(String updateSQL) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(updateSQL);
        }
    }

    /**
     *
     * @param passwords - a list of String.  The first one is the old password, the second new password
     * @throws DaoException - database operation
     */
    public void changeDBPassword(List<String> passwords) throws DaoException {
        int numberOfPasswordsChanged = 0;
        try {
            String url = connection.getMetaData().getURL();
            final File dbFile = new File(getDBFileName());
            closeConnection();

            // first change encryption password
            ChangeFileEncryption.execute(dbFile.getParent(), dbFile.getName(), "AES", passwords.get(0).toCharArray(),
                    passwords.get(1).toCharArray(), true);
            numberOfPasswordsChanged++;

            // now re-open connection
            url += ";" + CIPHER_CLAUSE + IF_EXIST_CLAUSE;
            connection = DriverManager.getConnection(url, DB_OWNER, passwords.get(1) + " " + passwords.get(0));
            try (PreparedStatement preparedStatement = connection.prepareStatement("Alter User "
                    + DB_OWNER + " set password ?")) {
                preparedStatement.setString(1, passwords.get(1));
                preparedStatement.execute();
                numberOfPasswordsChanged++;
            }
        } catch (SQLException e) {
            final String msg;
            if (numberOfPasswordsChanged == 1)
                msg = "Encryption password changed, DB_OWNER password change failed";
            else
                msg = "";
            throw new DaoException(DaoException.ErrorCode.FAIL_TO_CHANGE_PASSWORD, msg, e);
        }
    }

    // getter for various Dao class objects
    public enum DaoType {
        ACCOUNT, SECURITY, TRANSACTION, PAIR_TID_SPLIT_TRANSACTION, PAIR_TID_MATCH_INFO, SECURITY_PRICE, FIDATA,
        ACCOUNT_DC, DIRECT_CONNECTION, TAG, CATEGORY, REMINDER, REMINDER_TRANSACTION, REPORT_SETTING, REPORT_DETAIL
    }

    private final Map<DaoType, Dao<?,?>> daoMap = new HashMap<>();

    public Dao<?,?> getDao(DaoType daoType) {
        switch (daoType) {
            case ACCOUNT:
                return daoMap.computeIfAbsent(daoType, o -> new AccountDao(connection));
            case SECURITY:
                return daoMap.computeIfAbsent(daoType, o -> new SecurityDao(connection));
            case TRANSACTION: {
                final SecurityDao securityDao = (SecurityDao) getDao(DaoType.SECURITY);
                final PairTidSplitTransactionListDao pairTidSplitTransactionListDao =
                        (PairTidSplitTransactionListDao) getDao(DaoType.PAIR_TID_SPLIT_TRANSACTION);
                return daoMap.computeIfAbsent(daoType,
                        o -> new TransactionDao(connection, securityDao, pairTidSplitTransactionListDao));
            }
            case PAIR_TID_SPLIT_TRANSACTION:
                return daoMap.computeIfAbsent(daoType, o -> new PairTidSplitTransactionListDao(connection));
            case PAIR_TID_MATCH_INFO:
                return daoMap.computeIfAbsent(daoType, o -> new PairTidMatchInfoListDao(connection));
            case SECURITY_PRICE:
                return daoMap.computeIfAbsent(daoType, o -> new SecurityPriceDao(connection));
            case FIDATA:
                return daoMap.computeIfAbsent(daoType, o -> new FIDataDao(connection));
            case ACCOUNT_DC:
                return daoMap.computeIfAbsent(daoType, o -> new AccountDCDao(connection));
            case DIRECT_CONNECTION:
                return daoMap.computeIfAbsent(daoType, o -> new DirectConnectionDao(connection));
            case TAG:
                return daoMap.computeIfAbsent(daoType, o -> new TagDao(connection));
            case CATEGORY:
                return daoMap.computeIfAbsent(daoType, o -> new CategoryDao(connection));
            case REMINDER: {
                final PairTidSplitTransactionListDao pairTidSplitTransactionListDao =
                        (PairTidSplitTransactionListDao) getDao(DaoType.PAIR_TID_SPLIT_TRANSACTION);
                return daoMap.computeIfAbsent(daoType, o -> new ReminderDao(connection,
                        pairTidSplitTransactionListDao));
            }
            case REMINDER_TRANSACTION:
                ReminderDao reminderDao = (ReminderDao) getDao(DaoType.REMINDER);
                return daoMap.computeIfAbsent(daoType, o -> new ReminderTransactionDao(connection, reminderDao));
            case REPORT_SETTING:
                ReportDetailDao reportDetailDao = (ReportDetailDao) getDao(DaoType.REPORT_DETAIL);
                return daoMap.computeIfAbsent(daoType, o -> new ReportSettingDao(connection, reportDetailDao));
            case REPORT_DETAIL:
                return daoMap.computeIfAbsent(daoType, o -> new ReportDetailDao(connection));
            default:
                throw new IllegalArgumentException("DaoType " + daoType + " not implemented");
        }
    }
}
