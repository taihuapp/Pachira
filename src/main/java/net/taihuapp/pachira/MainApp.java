/*
 * Copyright (C) 2018.  Guangliang He.  All Rights Reserved.
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

package net.taihuapp.pachira;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.log4j.Logger;
import org.h2.tools.ChangeFileEncryption;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.sql.Date;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class MainApp extends Application {

    // these characters are not allowed in account names and
    // security names
    static final Set<Character> BANNED_CHARACTER_SET = new HashSet<>(Arrays.asList(
            '/', ':', ']', '[', '|', '^'));
    static boolean hasBannedCharacter(String name) {
        for (int i = 0; i < name.length(); i++)
            if (BANNED_CHARACTER_SET.contains(name.charAt(i)))
                return true;
        return false;
    }

    private static final Logger mLogger = Logger.getLogger(MainApp.class);

    // minimum 2 decimal places, maximum 4 decimal places
    static final DecimalFormat DOLLAR_CENT_FORMAT = new DecimalFormat("###,##0.00##");

    private static final String ACKNOWLEDGETIMESTAMP = "ACKDT";
    private static final int MAXOPENEDDBHIST = 5; // keep max 5 opened files
    private static final String KEY_OPENEDDBPREFIX = "OPENEDDB#";
    private static final String DBOWNER = "ADMPACHIRA";
    private static final String DBPOSTFIX = ".mv.db"; // was .h2.db in h2-1.3.176, changed to .mv.db in h2-1.4.196
    private static final String URLPREFIX = "jdbc:h2:";
    private static final String CIPHERCLAUSE="CIPHER=AES;";
    private static final String IFEXISTCLAUSE="IFEXISTS=TRUE;";

    private static final String DBVERSIONNAME = "DBVERSION";
    private static final int DBVERSIONVALUE = 3;  // need DBVERSION to run properly.

    private static final int ACCOUNTNAMELEN = 40;
    private static final int ACCOUNTDESCLEN = 256;
    private static final int SECURITYTICKERLEN = 16;
    private static final int SECURITYNAMELEN = 64;

    private static final int CATEGORYNAMELEN = 40;
    private static final int CATEGORYDESCLEN = 256;

    private static final int AMOUNT_TOTAL_LEN = 20;
    private static final int AMOUNT_FRACTION_LEN = 4;

    private static final int TRANSACTIONMEMOLEN = 64;
    private static final int TRANSACTIONREFLEN = 8;
    private static final int TRANSACTIONPAYEELEN = 64;
    private static final int TRANSACTIONTRADEACTIONLEN = 16;
    private static final int TRANSACTIONSTATUSLEN = 16;
    private static final int TRANSACTIONTRANSFERREMINDERLEN = 40;
    private static final int ADDRESSLINELEN = 32;

    private static final int AMORTLINELEN = 32;

    private static final int PRICE_TOTAL_LEN = 20;
    static final int PRICE_FRACTION_LEN = 8;
    static final int PRICE_FRACTION_DISP_LEN = 6;

    private static final int QUANTITY_TOTAL_LEN = 20;
    private static final int QUANTITY_FRACTION_LEN = 8;
    static final int QUANTITY_FRACTION_DISP_LEN = 6;

    static final int SAVEDREPORTSNAMELEN = 32;

    // Category And Transfer Account are often shared as the following:
    // String     #    Meaning
    // Blank      0    no transfer, no category
    // [Deleted] -??   transfer to deleted account
    // [A Name]  -AID  transfer to account with id = AID
    // Cat Name   CID  category with id = CID
    static final int MIN_ACCOUNT_ID = 10;
    static final String DELETED_ACCOUNT_NAME = "Deleted Account";
    static final int MIN_CATEGORY_ID = 10;

    private Preferences mPrefs;
    private Stage mPrimaryStage;
    private Connection mConnection = null;  // todo replace Connection with a custom db class object
    private Savepoint mSavepoint = null;

    // mTransactionList is ordered by ID.  It's important for getTransactionByID to work
    // mTransactionListSort2 is ordered by accountID, Date, and ID
    private final ObservableList<Transaction> mTransactionList = FXCollections.observableArrayList();
    private final SortedList<Transaction> mTransactionListSort2 = new SortedList<>(mTransactionList,
            Comparator.comparing(Transaction::getAccountID).thenComparing(Transaction::getTDate)
                    .thenComparing(Transaction::getID));

    // we want to watch the change of hiddenflag and displayOrder
    private ObservableList<Account> mAccountList = FXCollections.observableArrayList(
            a -> new Observable[] { a.getHiddenFlagProperty(), a.getDisplayOrderProperty(),
                    a.getCurrentBalanceProperty() });
    private ObservableList<Tag> mTagList = FXCollections.observableArrayList();
    private ObservableList<Category> mCategoryList = FXCollections.observableArrayList();
    private ObservableList<Security> mSecurityList = FXCollections.observableArrayList();
    private ObservableList<SecurityHolding> mSecurityHoldingList = FXCollections.observableArrayList();
    private SecurityHolding mRootSecurityHolding = new SecurityHolding("Root");

    private final Map<Integer, Reminder> mReminderMap = new HashMap<>();

    private final ObservableList<ReminderTransaction> mReminderTransactionList = FXCollections.observableArrayList();

    private Account mCurrentAccount = null;

    void setCurrentAccount(Account a) { mCurrentAccount = a; }
    Account getCurrentAccount() { return mCurrentAccount; }

    // get opened named from pref
    List<String> getOpenedDBNames() {
        List<String> fileNameList = new ArrayList<>();

        for (int i = 0; i < MAXOPENEDDBHIST; i++) {
            String fileName = mPrefs.get(KEY_OPENEDDBPREFIX + i, "");
            if (!fileName.isEmpty()) {
                fileNameList.add(fileName);
            }
        }
        return fileNameList;
    }

    LocalDateTime getAcknowledgeTimeStamp() {
        String ldtStr = mPrefs.get(ACKNOWLEDGETIMESTAMP, null);
        if (ldtStr == null)
            return null;
        try {
            return LocalDateTime.parse(ldtStr);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    void putAcknowledgeTimeStamp(LocalDateTime ldt) {
        mPrefs.put(ACKNOWLEDGETIMESTAMP, ldt.toString());
        try {
            mPrefs.flush();
        } catch (BackingStoreException be) {
            mLogger.error("BackingStoreException encountered when storing Acknowledge date time.", be);
        }
    }

    // return accounts for given type t or all account if t is null
    // return either hidden or nonhidden account based on hiddenflag, or all if hiddenflag is null
    // include DELETED_ACCOUNT if exDeleted is false.
    SortedList<Account> getAccountList(Account.Type t, Boolean hidden, Boolean exDeleted) {
        FilteredList<Account> fList = new FilteredList<>(mAccountList,
                a -> (t == null || a.getType() == t) && (hidden == null || a.getHiddenFlag() == hidden)
                        && !(exDeleted && a.getName().equals(DELETED_ACCOUNT_NAME)));

        // sort accounts by type first, then displayOrder, then ID
        return new SortedList<>(fList, Comparator.comparing(Account::getType).thenComparing(Account::getDisplayOrder)
                .thenComparing(Account::getID));
    }

    ObservableList<Tag> getTagList() { return mTagList; }
    ObservableList<Category> getCategoryList() { return mCategoryList; }
    ObservableList<Security> getSecurityList() { return mSecurityList; }
    ObservableList<SecurityHolding> getSecurityHoldingList() { return mSecurityHoldingList; }
    void setCurrentAccountSecurityHoldingList(LocalDate date, int exID) {
        mSecurityHoldingList.setAll(updateAccountSecurityHoldingList(getCurrentAccount(), date, exID));
    }
    SecurityHolding getRootSecurityHolding() { return mRootSecurityHolding; }

    FilteredList<ReminderTransaction> getReminderTransactionList(boolean showCompleted) {
        return new FilteredList<>(mReminderTransactionList,
                rt -> (showCompleted || (!rt.getStatus().equals(ReminderTransaction.COMPLETED)
                        && !rt.getStatus().equals(ReminderTransaction.SKIPPED))));
    }

    private Map<Integer, Reminder> getReminderMap() { return mReminderMap; }

    Account getAccountByName(String name) {
        for (Account a : getAccountList(null, null, false)) {
            if (a.getName().equals(name)) {
                return a;
            }
        }
        return null;
    }

    Account getAccountByID(int id) {
        for (Account a : getAccountList(null, null, false)) {
            if (a.getID() == id)
                return a;
        }
        return null;
    }

    Security getSecurityByID(int id) {
        for (Security s : getSecurityList()) {
            if (s.getID() == id)
                return s;
        }
        return null;
    }

    Security getSecurityByName(String name) {
        for (Security s : getSecurityList()) {
            if (s.getName().equals(name))
                return s;
        }
        return null;
    }

    Category getCategoryByID(int id) {
        for (Category c : getCategoryList()) {
            if (c.getID() == id)
                return c;
        }
        return null;
    }

    Category getCategoryByName(String name) {
        for (Category c : getCategoryList()) {
            if (c.getName().equals(name)) return c;
        }
        return null;
    }

    Tag getTagByID(int id) {
        for (Tag t : getTagList())
            if (t.getID() == id)
                return t;
        return null;
    }

    Tag getTagByName(String name) {
        for (Tag t : getTagList())
            if (t.getName().equals(name))
                return t;
        return null;
    }

    void deleteTransactionFromDB(int tid) {
        String sqlCmd = "delete from TRANSACTIONS where ID = ?";
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            preparedStatement.setInt(1, tid);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            mLogger.error("SQLException: " + e.getSQLState(), e);
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle("Database Error");
            alert.setHeaderText("Unable to delete Transactions, id = " + tid);
            alert.setContentText(SQLExceptionToString(e));
            alert.showAndWait();
        }
    }

    // Given a transaction t, find a index location in (sorted by ID) mTransactionList
    // for the matching ID.
    private int getTransactionIndex(Transaction t) {
        return Collections.binarySearch(mTransactionList, t, Comparator.comparing(Transaction::getID));
    }

    // For Transaction with ID tid, this method returns its location index in
    // (sorted by ID) mTransactionList.
    // If transaction with tid is not found in mTransactionList by binarySearch
    // return -(1+insertLocation).
    private int getTransactionIndexByID(int tid) {
        // make up a dummy transaction for search
        Transaction t = new Transaction(-1, LocalDate.MAX, Transaction.TradeAction.BUY, 0);
        t.setID(tid);
        return getTransactionIndex(t);
    }

    Transaction getTransactionByID(int tid) {
        int idx = getTransactionIndexByID(tid);
        if (idx > 0)
            return mTransactionList.get(idx);
        return null;
    }

    private void showInformationDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(mPrimaryStage);
        alert.initModality(Modality.WINDOW_MODAL);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // work around for non resizable alert dialog truncates message
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(480, 320);

        alert.showAndWait();
    }

    // return true if OK
    //        false otherwise
    static boolean showConfirmationDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && (result.get() == ButtonType.OK);
    }

    static void showWarningDialog(String title, String header, String content) {
        final Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // http://code.makery.ch/blog/javafx-dialogs-official/
    void showExceptionDialog(String title, String header, String content, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(mPrimaryStage);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        if (e != null) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);

            Label label = new Label("The exception stacktrace was:");
            TextArea textArea = new TextArea(stringWriter.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);

            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);

            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(label, 0, 0);
            expContent.add(textArea, 0, 1);

            alert.getDialogPane().setExpandableContent(expContent);
        }
        alert.setResizable(true);
        alert.showAndWait();
    }

    Stage getStage() { return mPrimaryStage; }

    // if id <= 0, load all settings and return a list
    // if id > 0, load the setting with given id, return a list of length 1 (or 0 if no matching id found)
    List<ReportDialogController.Setting> loadReportSetting(int id) {
        List<ReportDialogController.Setting> settingList = new ArrayList<>();
        String sqlCmd = "select s.*, d.* from SAVEDREPORTS s left join SAVEDREPORTDETAILS d "
                + "on s.ID = d.REPORTID ";
        if (id > 0)
            sqlCmd += "where s.ID = " + id;
        else
            sqlCmd += " order by s.ID";

        try (Statement statement = mConnection.createStatement();
             ResultSet rs = statement.executeQuery(sqlCmd)) {
            ReportDialogController.Setting setting = null;
            while (rs.next()) {
                int sID = rs.getInt("ID");
                if (setting == null || sID != setting.getID()) {
                    // a new Setting
                    setting = new ReportDialogController.Setting(sID,
                            ReportDialogController.ReportType.valueOf(rs.getString("TYPE")));
                    settingList.add(setting);
                    setting.setName(rs.getString("NAME"));
                    setting.setDatePeriod(ReportDialogController.DatePeriod.valueOf(rs.getString("DATEPERIOD")));
                    setting.setStartDate(rs.getDate("SDATE").toLocalDate());
                    setting.setEndDate(rs.getDate("EDATE").toLocalDate());
                    setting.setFrequency(ReportDialogController.Frequency.valueOf(rs.getString("FREQUENCY")));
                    setting.setPayeeContains(rs.getString("PAYEECONTAINS"));
                    setting.setPayeeRegEx(rs.getBoolean("PAYEEREGEX"));
                    setting.setMemoContains(rs.getString("MEMOCONTAINS"));
                    setting.setMemoRegEx(rs.getBoolean("MEMOREGEX"));
                }
                String itemName = rs.getString("ITEMNAME");
                if (itemName != null)
                switch (ReportDialogController.ItemName.valueOf(itemName)) {
                    case ACCOUNTID:
                        int accountID = Integer.parseInt(rs.getString("ITEMVALUE"));
                        Account account = getAccountByID(accountID);
                        if (account != null)
                            setting.getSelectedAccountSet().add(account);
                        break;
                    case CATEGORYID:
                        setting.getSelectedCategoryIDSet().add(Integer.parseInt(rs.getString("ITEMVALUE")));
                        break;
                    case SECURITYID:
                        setting.getSelectedSecurityIDSet().add(Integer.parseInt(rs.getString("ITEMVALUE")));
                        break;
                    case TRADEACTION:
                        setting.getSelectedTradeActionSet().add(
                                Transaction.TradeAction.valueOf(rs.getString("ITEMVALUE")));
                        break;
                    default:
                        mLogger.error("loadReportSetting: ItemName " + itemName + " not implemented yet");
                        break;
                }
            }
        } catch (SQLException e) {
            mLogger.error("SQLException: " + e.getSQLState(), e);
        }
        return settingList;
    }

    // return true if a savepoint is set here.
    // false if a savepoint was previously set elsewhere
    boolean setDBSavepoint() throws SQLException {
        if (mSavepoint != null)
            return false;  // was set at some place else.
        mSavepoint = mConnection.setSavepoint();
        mConnection.setAutoCommit(false);
        return true;
    }
    void releaseDBSavepoint() throws SQLException {
        mConnection.setAutoCommit(true);
        mConnection.releaseSavepoint(mSavepoint);
        mSavepoint = null;
    }
    void rollbackDB() throws SQLException {
            //mConnection.rollback(mSavepoint);
        mConnection.rollback();
    }
    void commitDB() throws SQLException {
        mConnection.commit();
    }

    void insertUpdateReportSettingToDB(ReportDialogController.Setting setting) throws SQLException {
        String sqlCmd;
        int id = setting.getID();
        if (id <= 0) {
            // new setting, insert
            sqlCmd = "insert into SAVEDREPORTS "
                    + "(NAME, TYPE, DATEPERIOD, SDATE, EDATE, FREQUENCY,"
                    + " PAYEECONTAINS, PAYEEREGEX, MEMOCONTAINS, MEMOREGEX)"
                    + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            sqlCmd = "update SAVEDREPORTS set "
                    + "NAME = ?, TYPE = ?, DATEPERIOD = ?, SDATE = ?, EDATE = ?, FREQUENCY = ?, "
                    + "PAYEECONTAINS = ?, PAYEEREGEX = ?, MEMOCONTAINS = ?, MEMOREGEX = ? "
                    + "where ID = ?";
        }

        boolean savepointSetHere = false;  // did I set savepoint?
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            savepointSetHere = setDBSavepoint();

            preparedStatement.setString(1, setting.getName());
            preparedStatement.setString(2, setting.getType().name());
            preparedStatement.setString(3, setting.getDatePeriod().name());
            preparedStatement.setDate(4, Date.valueOf(setting.getStartDate()));
            preparedStatement.setDate(5, Date.valueOf(setting.getEndDate()));
            preparedStatement.setString(6, setting.getFrequency().name());
            preparedStatement.setString(7, setting.getPayeeContains());
            preparedStatement.setBoolean(8, setting.getPayeeRegEx());
            preparedStatement.setString(9, setting.getMemoContains());
            preparedStatement.setBoolean(10, setting.getMemoRegEx());

            if (id > 0)
                preparedStatement.setInt(11, id);
            preparedStatement.executeUpdate();
            if (id <= 0) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    if (resultSet.next())
                        setting.setID(id = resultSet.getInt(1));
                    else
                        throw new SQLException(("Insert into SAVEDREPORTS failed."));
                }
            }

            // now deal with setting details
            String sqlCmd1 = "insert into SAVEDREPORTDETAILS (REPORTID, ITEMNAME, ITEMVALUE) "
                    + "values (?, ?, ?)";
            try (Statement statement = mConnection.createStatement();
                 PreparedStatement preparedStatement1 = mConnection.prepareStatement(sqlCmd1)) {
                statement.execute("delete from SAVEDREPORTDETAILS where REPORTID = " + id);
                // loop through account list
                for (Account account : setting.getSelectedAccountSet()) {
                    preparedStatement1.setInt(1, id);
                    preparedStatement1.setString(2, ReportDialogController.ItemName.ACCOUNTID.name());
                    preparedStatement1.setString(3, String.valueOf(account.getID()));

                    preparedStatement1.executeUpdate();
                }

                for (Integer cid : setting.getSelectedCategoryIDSet()) {
                    preparedStatement1.setInt(1, id);
                    preparedStatement1.setString(2, ReportDialogController.ItemName.CATEGORYID.name());
                    preparedStatement1.setString(3, String.valueOf(cid));

                    preparedStatement1.executeUpdate();
                }

                for (Integer sid : setting.getSelectedSecurityIDSet()) {
                    preparedStatement1.setInt(1, id);
                    preparedStatement1.setString(2, ReportDialogController.ItemName.SECURITYID.name());
                    preparedStatement1.setString(3, String.valueOf(sid));

                    preparedStatement1.executeUpdate();
                }

                for (Transaction.TradeAction ta : setting.getSelectedTradeActionSet()) {
                    preparedStatement1.setInt(1, id);
                    preparedStatement1.setString(2, ReportDialogController.ItemName.TRADEACTION.name());
                    preparedStatement1.setString(3, ta.name());

                    preparedStatement1.executeUpdate();
                }
            }
            if (savepointSetHere) {// only commit if a savepoint is set here otherwise, pass to the caller.
                commitDB();
            }
        } catch (SQLException e) {
            if (savepointSetHere) {
                try {
                    mLogger.error("SQLException: " + e.getSQLState(), e);
                    String title = "Database Error";
                    String header = "Unable to insert/update SAVEDREPORTS Setting";
                    String content = SQLExceptionToString(e);
                    showExceptionDialog(title, header, content, e);
                    rollbackDB();
                } catch (SQLException e1) {
                    mLogger.error("SQLException: " + e1.getSQLState(), e1);
                    String title = "Database Error";
                    String header = "Unable to roll back";
                    String content = SQLExceptionToString(e1);
                    showExceptionDialog(title, header, content, e1);
                }
            } else {
                // savepoint was set by the caller, throw.
                throw e;
            }
        } finally {
            if (savepointSetHere) {
                try {
                    releaseDBSavepoint();
                } catch (SQLException e) {
                    mLogger.error("SQLException: " + e.getSQLState(), e);
                    String title = "Database Error";
                    String header = "Unable to release savepoint and set DB autocommit";
                    String content = SQLExceptionToString(e);
                    showExceptionDialog(title, header, content, e);
                }
            }
        }
    }

    // delete reminder
    void deleteReminderFromDB(int reminderID) throws SQLException {
        String sqlCmd = "delete from REMINDERS where ID = ?";
        PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd);
        preparedStatement.setInt(1, reminderID);
        preparedStatement.executeUpdate();
    }

    // insert or update reminder
    void insertUpdateReminderToDB(Reminder reminder) throws SQLException {
        String sqlCmd;
        if (reminder.getID() <= 0) {
            sqlCmd = "insert into REMINDERS "
                    + "(TYPE, PAYEE, AMOUNT, ACCOUNTID, CATEGORYID, "
                    + "TRANSFERACCOUNTID, TAGID, MEMO, STARTDATE, ENDDATE, "
                    + "BASEUNIT, NUMPERIOD, ALERTDAYS, ISDOM, ISFWD, ESTCOUNT) "
                    + "values(?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?,?)";
        } else {
            sqlCmd = "update REMINDERS set "
                    + "TYPE = ?, PAYEE = ?, AMOUNT = ?, ACCOUNTID = ?, CATEGORYID = ?, "
                    + "TRANSFERACCOUNTID = ?, TAGID = ?, MEMO = ?, STARTDATE = ?, ENDDATE = ?, "
                    + "BASEUNIT = ?, NUMPERIOD = ?, ALERTDAYS = ?, ISDOM = ?, ISFWD = ?, ESTCOUNT = ? "
                    + "where ID = ?";
        }

        boolean savepointSetHere = false; // did I set it?
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            savepointSetHere = setDBSavepoint();

            preparedStatement.setString(1, reminder.getType().name());
            preparedStatement.setString(2, reminder.getPayee());
            preparedStatement.setBigDecimal(3, reminder.getAmount());
            preparedStatement.setInt(4, reminder.getAccountID());
            preparedStatement.setInt(5, reminder.getCategoryID());
            preparedStatement.setInt(6, reminder.getTransferAccountID());
            preparedStatement.setInt(7, reminder.getTagID());
            preparedStatement.setString(8, reminder.getMemo());
            preparedStatement.setDate(9, Date.valueOf(reminder.getDateSchedule().getStartDate()));

            preparedStatement.setDate(10, reminder.getDateSchedule().getEndDate() == null ?
                    null : Date.valueOf(reminder.getDateSchedule().getEndDate()));
            preparedStatement.setString(11, reminder.getDateSchedule().getBaseUnit().name());
            preparedStatement.setInt(12, reminder.getDateSchedule().getNumPeriod());
            preparedStatement.setInt(13, reminder.getDateSchedule().getAlertDay());
            preparedStatement.setBoolean(14, reminder.getDateSchedule().isDOMBased());
            preparedStatement.setBoolean(15, reminder.getDateSchedule().isForward());
            preparedStatement.setInt(16, reminder.getEstimateCount());
            if (reminder.getID() > 0)
                preparedStatement.setInt(17, reminder.getID());

            preparedStatement.executeUpdate();
            if (reminder.getID() <= 0) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    resultSet.next();
                    reminder.setID(resultSet.getInt(1));
                }
            }

            if (!reminder.getSplitTransactionList().isEmpty()) {
                insertUpdateSplitTransactionsToDB(-reminder.getID(), reminder.getSplitTransactionList());
            }

            if (savepointSetHere) {
                commitDB();
            }
        } catch (SQLException e) {
            if (savepointSetHere) {
                try {
                    mLogger.error("SQLException: " + e.getSQLState(), e);
                    showExceptionDialog("Database Error", "insert/update Reminder failed",
                            SQLExceptionToString(e), e);
                    rollbackDB();
                } catch (SQLException e1) {
                    mLogger.error("SQLException: " + e1.getSQLState(), e1);
                    showExceptionDialog("Database Error", "Failed to rollback reminder database update",
                            SQLExceptionToString(e1), e1);
                }
            } else {
                throw e;
            }
        } finally {
            if (savepointSetHere) {
                try {
                    releaseDBSavepoint();
                } catch (SQLException e) {
                    mLogger.error("SQLException: " + e.getSQLState(), e);
                    showExceptionDialog("Database Error", "after insertUpdateReminber, set autocommit failed",
                            SQLExceptionToString(e), e);
                }
            }
        }
    }

    void insertReminderTransactions(ReminderTransaction rt, int tid) {
        rt.setTransactionID(tid);
        String sqlCmd = "insert into REMINDERTRANSACTIONS (REMINDERID, DUEDATE, TRANSACTIONID) "
                + "values (?, ?, ?)";
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            preparedStatement.setInt(1, rt.getReminder().getID());
            preparedStatement.setDate(2, Date.valueOf(rt.getDueDate()));
            preparedStatement.setInt(3, tid);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            mLogger.error("SQLException: " + e.getSQLState(), e);
            showExceptionDialog("Database Error", "Failed to insert into ReminderTransactions!",
                    SQLExceptionToString(e), e);
        }
    }

    // insert or update transaction in the master list.
    void insertUpdateTransactionToMasterList(Transaction t) {
        int idx = getTransactionIndex(t);
        if (idx < 0) {
            // not in the list, insert a copy of t
            mTransactionList.add(-(1+idx), t);
        } else {
            // exist in list, replace with a copy of t
            mTransactionList.set(idx, t);
        }
    }

    // delete transaction from mTransactionList
    void deleteTransactionFromMasterList(int tid) {
        int idx = getTransactionIndexByID(tid);
        if (idx < 0)
            return;

        mTransactionList.remove(idx);
    }

    // insert or update the input transaction into DB
    // t.ID will be set if insert/update succeeded.
    // return affected transaction id if success, 0 for failure.
    int insertUpdateTransactionToDB(Transaction t) throws SQLException {
        String sqlCmd;
        // be extra careful about the order of the columns
        if (t.getID() <= 0) {
            sqlCmd = "insert into TRANSACTIONS " +
                    "(ACCOUNTID, DATE, AMOUNT, TRADEACTION, SECURITYID, " +
                    "STATUS, CATEGORYID, TAGID, MEMO, PRICE, QUANTITY, COMMISSION, " +
                    "MATCHTRANSACTIONID, MATCHSPLITTRANSACTIONID, PAYEE, ADATE, OLDQUANTITY, " +
                    "REFERENCE, SPLITFLAG) " +
                    "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            sqlCmd = "update TRANSACTIONS set " +
                    "ACCOUNTID = ?, DATE = ?, AMOUNT = ?, TRADEACTION = ?, " +
                    "SECURITYID = ?, STATUS = ?, CATEGORYID = ?, TAGID = ?, MEMO = ?, " +
                    "PRICE = ?, QUANTITY = ?, COMMISSION = ?, " +
                    "MATCHTRANSACTIONID = ?, MATCHSPLITTRANSACTIONID = ?, " +
                    "PAYEE = ?, ADATE = ?, OLDQUANTITY = ?, REFERENCE = ?, SPLITFLAG = ? " +
                    "where ID = ?";
        }

        boolean savepointSetHere = false; // did I set savepoint?
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            savepointSetHere = setDBSavepoint();

            preparedStatement.setInt(1, t.getAccountID());
            preparedStatement.setDate(2, Date.valueOf(t.getTDate()));
            preparedStatement.setBigDecimal(3, t.getAmount());
            preparedStatement.setString(4, t.getTradeActionProperty().get().name());
            Security security = getSecurityByName(t.getSecurityName());
            if (security != null)
                preparedStatement.setObject(5, security.getID());
            else
                preparedStatement.setObject(5, null);
            preparedStatement.setString(6, t.getStatus().name());
            preparedStatement.setInt(7, t.getCategoryID());
            preparedStatement.setInt(8, t.getTagID());
            preparedStatement.setString(9, t.getMemoProperty().get());
            preparedStatement.setBigDecimal(10, t.getPrice());
            preparedStatement.setBigDecimal(11, t.getQuantity());
            preparedStatement.setBigDecimal(12, t.getCommission());
            preparedStatement.setInt(13, t.getMatchID()); // matchTransactionID, ignore for now
            preparedStatement.setInt(14, t.getMatchSplitID()); // matchSplitTransactionID, ignore for now
            preparedStatement.setString(15, t.getPayeeProperty().get());
            if (t.getADate() == null)
                preparedStatement.setNull(16, java.sql.Types.DATE);
            else
                preparedStatement.setDate(16, Date.valueOf(t.getADate()));
            preparedStatement.setBigDecimal(17, t.getOldQuantity());
            preparedStatement.setString(18, t.getReferenceProperty().get());
            preparedStatement.setBoolean(19, !t.getSplitTransactionList().isEmpty());
            if (t.getID() > 0)
                preparedStatement.setInt(20, t.getID());

            if (preparedStatement.executeUpdate() == 0)
                throw(new SQLException("Failure: " + sqlCmd));

            if (t.getID() <= 0) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    resultSet.next();
                    t.setID(resultSet.getInt(1));
                }
            }

            if (!t.getSplitTransactionList().isEmpty())
                insertUpdateSplitTransactionsToDB(t.getID(), t.getSplitTransactionList());

            if (savepointSetHere) {
                commitDB();
            }

            return t.getID();
        } catch (SQLException e) {
            // something went wrong
            if (savepointSetHere) {
                try {
                    mLogger.error("SQLException: " + e.getSQLState(), e);
                    showExceptionDialog("Database Error", "update transaction failed", SQLExceptionToString(e), e);
                    rollbackDB();
                } catch (SQLException e1) {
                    mLogger.error("SQLException: " + e1.getSQLState(), e1);
                    // error in rollback
                    showExceptionDialog("Database Error", "Failed to rollback transaction database update",
                            SQLExceptionToString(e1), e1);
                }
            } else {
                throw e;
            }
        } finally {
            if (savepointSetHere) {
                try {
                    releaseDBSavepoint();
                } catch (SQLException e) {
                    mLogger.error("SQLException: " + e.getSQLState(), e);
                    showExceptionDialog("Database Error", "set autocommit failed", SQLExceptionToString(e), e);
                }
            }
        }
        return 0;
    }

    private void insertUpdateSplitTransactionsToDB(int tid, List<SplitTransaction> stList) throws SQLException {
        // load all existing split transactions for tid from database
        List<SplitTransaction> oldSTList = loadSplitTransactions(tid);

        // delete those split transactions not in stList
        List<Integer> exIDList = new ArrayList<>();
        for (SplitTransaction t0 : oldSTList) {
            boolean isIn = false;
            for (SplitTransaction t1 : stList) {
                if (t0.getID() == t1.getID()) {
                    isIn = true;
                    break; // old id is in the new list
                }
            }
            if (!isIn) {
                exIDList.add(t0.getID());
            }
        }

        final int[] idArray = new int[stList.size()];
        String insertSQL = "insert into SPLITTRANSACTIONS "
                + "(TRANSACTIONID, CATEGORYID, PAYEE, MEMO, AMOUNT, MATCHTRANSACTIONID, TAGID) "
                + "values (?, ?, ?, ?, ?, ?, ?)";
        String updateSQL = "update SPLITTRANSACTIONS set "
                + "TRANSACTIONID = ?, CATEGORYID = ?, PAYEE = ?, MEMO = ?, AMOUNT = ?, MATCHTRANSACTIONID = ?, "
                + "TAGID = ?"
                + "where ID = ?";
        try (Statement statement = mConnection.createStatement();
             PreparedStatement insertStatement = mConnection.prepareStatement(insertSQL);
             PreparedStatement updateStatement = mConnection.prepareStatement(updateSQL)) {
            if (!exIDList.isEmpty()) {
                // delete these split transactions
                String sqlCmd = "delete from SPLITTRANSACTIONS where ID in ("
                        + exIDList.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
                statement.executeUpdate(sqlCmd);
            }

            // insert or update stList
            for (int i = 0; i < stList.size(); i++) {
                SplitTransaction st = stList.get(i);
                String payee = st.getPayee();
                if (payee != null && payee.length() > TRANSACTIONPAYEELEN)
                    payee = payee.substring(0, TRANSACTIONPAYEELEN);
                String memo = st.getMemo();
                if (memo != null && memo.length() > TRANSACTIONMEMOLEN)
                    memo = memo.substring(0, TRANSACTIONMEMOLEN);
                if (st.getID() <= 0) {
                    insertStatement.setInt(1, tid);
                    insertStatement.setInt(2, st.getCategoryID());
                    insertStatement.setString(3, payee);
                    insertStatement.setString(4, memo);
                    insertStatement.setBigDecimal(5, st.getAmount());
                    insertStatement.setInt(6, st.getMatchID());
                    insertStatement.setInt(7, st.getTagID());

                    if (insertStatement.executeUpdate() == 0) {
                        throw new SQLException("Insert to splittransactions failed");
                    }
                    try (ResultSet resultSet = insertStatement.getGeneratedKeys()) {
                        resultSet.next();
                        idArray[i] = resultSet.getInt(1); // retrieve id from resultset
                    }
                } else {
                    idArray[i] = st.getID();

                    updateStatement.setInt(1, tid);
                    updateStatement.setInt(2, st.getCategoryID());
                    updateStatement.setString(3, payee);
                    updateStatement.setString(4, memo);
                    updateStatement.setBigDecimal(5, st.getAmount());
                    updateStatement.setInt(6, st.getMatchID());
                    updateStatement.setInt(7, st.getTagID());
                    updateStatement.setInt(8, st.getID());

                    updateStatement.executeUpdate();
                }
            }
        }
        for (int i = 0; i < stList.size(); i++) {
            if (stList.get(i).getID() <= 0)
                stList.get(i).setID(idArray[i]);
        }
    }

    boolean insertUpdateTagToDB(Tag tag) {
        String sqlCmd;
        if (tag.getID() <= 0) {
            sqlCmd = "insert into TAGS (NAME, DESCRIPTION) values (?, ?)";
        } else {
            sqlCmd = "update TAGS set NAME = ?, DESCRIPTION = ? where ID = ?";
        }

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            preparedStatement.setString(1, tag.getName());
            preparedStatement.setString(2, tag.getDescription());
            if (tag.getID() > 0) {
                preparedStatement.setInt(3, tag.getID());
            }
            preparedStatement.executeUpdate();

            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                if (resultSet.next()) {
                    tag.setID(resultSet.getInt(1));
                }
            }
            return true;
        } catch (SQLException e) {
            mLogger.error("SQLException: " + e.getSQLState(), e);
            showExceptionDialog("Database Error", "Insert/Update Tag Failed", SQLExceptionToString(e), e);
        } catch (NullPointerException e) {
            mLogger.error("NullPointerException", e);
            showExceptionDialog("Database Error", "mConnection is null", "Database not connected", e);
        }
        return false;
    }

    boolean insertUpdateCategoryToDB(Category category) {
        String sqlCmd;
        if (category.getID() <= 0) {
            sqlCmd = "insert into CATEGORIES (NAME, DESCRIPTION, INCOMEFLAG, TAXREFNUM, BUDGETAMOUNT) "
                    + "values (?, ?, ?, ?, ?)";
        } else {
            sqlCmd = "update CATEGORIES set NAME = ?, DESCRIPTION = ?, INCOMEFLAG = ?, TAXREFNUM = ?, BUDGETAMOUNT = ? "
                    + "where ID = ?";
        }

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            preparedStatement.setString(1, category.getName());
            preparedStatement.setString(2, category.getDescription());
            preparedStatement.setBoolean(3, category.getIsIncome());
            preparedStatement.setInt(4, category.getTaxRefNum());
            preparedStatement.setBigDecimal(5, category.getBudgetAmount());
            if (category.getID() > 0) {
                preparedStatement.setInt(6, category.getID());
            }
            preparedStatement.executeUpdate();

            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                if (resultSet.next()) {
                    category.setID(resultSet.getInt(1));
                }
            }
            return true;
        } catch (SQLException e) {
            mLogger.error("SQLException: " + e.getSQLState(), e);
            showExceptionDialog("Database Error", "Insert/Update Category Failed", SQLExceptionToString(e), e);
        } catch (NullPointerException e) {
            mLogger.error("NullPointerException", e);
            showExceptionDialog("Database Error", "mConnection is null", "Database not connected", e);
        }
        return false;
    }

    // return true for DB operation success
    // false otherwise
    boolean insertUpdateSecurityToDB(Security security) {
        String sqlCmd;
        if (security.getID() <= 0) {
            // this security has not have a ID yet, insert and retrieve an ID
            sqlCmd = "insert into SECURITIES (TICKER, NAME, TYPE) values (?,?,?)";
        } else {
            // update
            sqlCmd = "update SECURITIES set TICKER = ?, NAME = ?, TYPE = ? where ID = ?";
        }

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            preparedStatement.setString(1, security.getTicker());
            preparedStatement.setString(2, security.getName());
            preparedStatement.setString(3, security.getType().name());
            if (security.getID() > 0) {
                preparedStatement.setInt(4, security.getID());
            }
            preparedStatement.executeUpdate();

            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                if (resultSet.next()) {
                    security.setID(resultSet.getInt(1));
                }
            }
        } catch (SQLException e) {
            String title = "Database Error";
            String headerText = "Unknown DB error";
            if (e.getErrorCode() == 23505) {
                title = "Duplicate Security Ticker or Name";
                headerText = "Security ticker/name " + security.getTicker() + "/"
                        + security.getName() + " is already taken.";
            } else {
                mLogger.error("SQLException: " + e.getSQLState(), e);
            }

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle(title);
            alert.setHeaderText(headerText);
            alert.showAndWait();
            return false;
        } catch (NullPointerException e) {
            mLogger.error("NullPointerException", e);
            return false;
        }
        return true;
    }

    // return true for DB operation success
    // false otherwise
    boolean deleteSecurityPriceFromDB(int securityID, LocalDate date) {
        String sqlCmd = "delete from PRICES where SECURITYID = ? and DATE = ?";
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            preparedStatement.setInt(1, securityID);
            preparedStatement.setDate(2, Date.valueOf(date));
            preparedStatement.executeUpdate();
            return true;
        } catch (SQLException e) {
            mLogger.error("SQLExceptoin " + e.getSQLState(), e);
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle("Database Fail Warning");
            alert.setHeaderText("Failed deleting price:");
            alert.setContentText("Security ID: " + securityID + "\n"
                               + "Date:        " + date + "\n");
            alert.showAndWait();
            return false;
        }
    }

    // mode = 0, insert and not print error
    //        1, insert
    //        2  update
    //        3 insert and update
    // return true of operation successful
    //        false otherwise
    boolean insertUpdatePriceToDB(Integer securityID, LocalDate date, BigDecimal p, int mode) {
        boolean status = false;
        String sqlCmd;
        switch (mode) {
            case 0:
            case 1:
                sqlCmd = "insert into PRICES (PRICE, SECURITYID, DATE) values (?, ?, ?)";
                break;
            case 2:
                sqlCmd = "update PRICES set PRICE = ? where SECURITYID = ? and DATE = ?";
                break;
            case 3:
                return insertUpdatePriceToDB(securityID, date, p, 0) || insertUpdatePriceToDB(securityID, date, p, 2);
            default:
                throw new IllegalArgumentException("insertUpdatePriceToDB called with bad mode = " + mode);
        }

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            preparedStatement.setBigDecimal(1, p);
            preparedStatement.setInt(2, securityID);
            preparedStatement.setDate(3, Date.valueOf(date));
            preparedStatement.executeUpdate();
            status = true;
        } catch (SQLException e) {
            if (mode != 0) {
                mLogger.error("SQLException " + e.getSQLState(), e);
            }
        } catch (NullPointerException e) {
            if (mode != 0) {
                mLogger.error("NullPointerException", e);
            }
        }
        return status;
    }

    // construct a wrapped account name
    static String getWrappedAccountName(Account a) {
        if (a == null)
            return "";
        return "[" + a.getName() + "]";
    }

    // Take categoryOrTransferID cid, and signedAmount for a banking transaction
    // output matching Transaction.TradeAction
    private static Transaction.TradeAction mapBankingTransactionTA(int cid, BigDecimal signedAmount) {
        if (categoryOrTransferTest(cid) >= 0)
            return signedAmount.signum() >= 0 ?  Transaction.TradeAction.DEPOSIT : Transaction.TradeAction.WITHDRAW;

        return signedAmount.signum() >= 0 ? Transaction.TradeAction.XIN : Transaction.TradeAction.XOUT;
    }

    private static int categoryOrTransferTest(int cid) {
        if (cid >= MIN_CATEGORY_ID)
            return 1; // is category
        if (cid <= -MIN_ACCOUNT_ID)
            return -1; // is transfer account
        return 0;  // neither
    }

    // name is a category name or an account name wrapped by [].
    // if a valid account is seen, the negative of the corresponding account id is returned.
    // if a wrapped name cannot be mapped to a valid account, then the Deleted Account is used
    // if a valid category name is seen, the corresponding id is returned
    // otherwise, 0 is returned
    int mapCategoryOrAccountNameToID(String name) {
        if (name == null)
            return 0;
        if (name.startsWith("[") && name.endsWith("]")) {
            Account a = getAccountByName(name.substring(1, name.length()-1));
            if (a != null)
                return -a.getID();
            a = getAccountByName(DELETED_ACCOUNT_NAME);
            return -a.getID();
        } else {
            Category c = getCategoryByName(name);
            if (c != null)
                return c.getID();
        }
        return 0;
    }

    String mapCategoryOrAccountIDToName(int id) {
        if (id >= MIN_CATEGORY_ID) {
            Category c = getCategoryByID(id);
            return c == null ? "" : c.getName();
        } else if (-id >= MIN_ACCOUNT_ID) {
            return getWrappedAccountName(getAccountByID(-id));
        } else {
            return "";
        }
    }

    int mapTagNameToID(String name) {
        if (name == null)
            return 0;
        for (Tag tag : getTagList())
            if (tag.getName().equals(name))
                return tag.getID();
        return 0;
    }

    // take a transaction id, and a list of split BT, insert the list of bt into database
    // return the number of splitBT inserted, which should be same as the length of
    // the input list
    private int insertSplitBTToDB(int btID, List<QIFParser.BankTransaction.SplitBT> splitBTList) {
        int cnt = 0;

        String sqlCmd = "insert into SPLITTRANSACTIONS (TRANSACTIONID, CATEGORYID, MEMO, AMOUNT, PERCENTAGE, TAGID) "
                + "values (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            for (QIFParser.BankTransaction.SplitBT sbt : splitBTList) {
                preparedStatement.setInt(1, btID);
                preparedStatement.setInt(2, mapCategoryOrAccountNameToID(sbt.getCategory()));
                preparedStatement.setString(3, sbt.getMemo());
                preparedStatement.setBigDecimal(4, sbt.getAmount());
                preparedStatement.setBigDecimal(5, sbt.getPercentage());
                preparedStatement.setInt(6, mapTagNameToID(sbt.getTag()));
                preparedStatement.executeUpdate();
                cnt++;
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
        return cnt;
    }

    // return the inserted rowID, -1 if error.
    private int insertAddressToDB(List<String> address) {
        int rowID = -1;
        int nLines = Math.min(6, address.size());  // max 6 lines
        StringBuilder sqlCmd = new StringBuilder("insert into ADDRESSES (");
        for (int i = 0; i < nLines; i++) {
            sqlCmd.append("LINE").append(i);
            if (i < nLines-1)
                sqlCmd.append(",");
        }
        sqlCmd.append(") values (");
        for (int i = 0; i < nLines; i++) {
            sqlCmd.append("?");
            if (i < nLines-1)
                sqlCmd.append(",");
        }
        sqlCmd.append(")");

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd.toString())) {
            for (int i = 0; i < nLines; i++)
                preparedStatement.setString(i+1, address.get(i));

            if (preparedStatement.executeUpdate() != 0) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    if (resultSet.next())
                        rowID = resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
        return rowID;
    }

    // return inserted rowID or -1 for failure
    private int insertAmortizationToDB(String[] amortLines) {
        int rowID = -1;
        int nLines = 7;
        StringBuilder sqlCmd = new StringBuilder("insert into AMORTIZATIONLINES (");
        for (int i = 0; i < nLines; i++) {
            sqlCmd.append("LINE").append(i);
            if (i < nLines-1)
                sqlCmd.append(",");
        }
        sqlCmd.append(") values (");
        for (int i = 0; i < nLines; i++) {
            sqlCmd.append("?");
            if (i < nLines-1)
                sqlCmd.append(",");
        }
        sqlCmd.append(")");

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd.toString())) {
            for (int i = 0; i < nLines; i++)
                preparedStatement.setString(i+1, amortLines[i]);

            if (preparedStatement.executeUpdate() != 0) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    if (resultSet.next())
                        rowID = resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
        return rowID;
    }

    // insert transaction to database and returns rowID
    // return -1 if failed
    private int insertTransactionToDB(QIFParser.BankTransaction bt) throws SQLException {
        int rowID = -1;
        String accountName = bt.getAccountName();
        Account account = getAccountByName(accountName);
        if (account == null) {
            mLogger.error("Account [" + accountName + "] not found, nothing inserted");
            return -1;
        }
        if (account.getType() == Account.Type.INVESTING) {
            mLogger.error("Account " + account.getName() + " is not an investing account");
            return -1;
        }

        boolean success = true;
        boolean savepointSetHere = setDBSavepoint();

        List<String> address = bt.getAddressList();
        int addressID = -1;
        if (!address.isEmpty()) {
            addressID = insertAddressToDB(address);
            if (addressID < 0)
                success = false;
        }

        String[] amortLines = bt.getAmortizationLines();
        int amortID = -1;
        if (success && (amortLines != null)) {
            amortID = insertAmortizationToDB(amortLines);
            if (amortID < 0)
                success = false;
        }

        List<QIFParser.BankTransaction.SplitBT> splitList = bt.getSplitList();

        if (success) {
            String sqlCmd;
            sqlCmd = "insert into TRANSACTIONS " +
                    "(ACCOUNTID, DATE, AMOUNT, STATUS, CATEGORYID, " +
                    "MEMO, REFERENCE, " +
                    "PAYEE, SPLITFLAG, ADDRESSID, AMORTIZATIONID, TRADEACTION, TAGID" +
                    ") values (?,?,?,?,?,?,?,?,?,?,?,?,?)";

            try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
                String categoryOrTransferStr = bt.getCategoryOrTransfer();
                int categoryOrTransferID = mapCategoryOrAccountNameToID(categoryOrTransferStr);
                if (categoryOrTransferID == -account.getID()) {
                    // self transferring, set categiryID to 0
                    categoryOrTransferID = 0;
                }
                int tagID = mapTagNameToID(bt.getTag());
                Transaction.TradeAction ta = mapBankingTransactionTA(categoryOrTransferID, bt.getTAmount());
                preparedStatement.setInt(1, account.getID());
                preparedStatement.setDate(2, Date.valueOf(bt.getDate()));
                preparedStatement.setBigDecimal(3, bt.getTAmount().abs());
                preparedStatement.setString(4, bt.getStatus().name());
                preparedStatement.setInt(5, categoryOrTransferID);
                preparedStatement.setString(6, bt.getMemo());
                preparedStatement.setString(7, bt.getReference());
                preparedStatement.setString(8, bt.getPayee());
                preparedStatement.setBoolean(9, !splitList.isEmpty());
                preparedStatement.setInt(10, addressID);
                preparedStatement.setInt(11, amortID);
                preparedStatement.setString(12, ta.name());
                preparedStatement.setInt(13, tagID);
                preparedStatement.executeUpdate();
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    if (resultSet.next()) {
                        rowID = resultSet.getInt(1);
                    }
                }
            } catch (SQLException e) {
                mLogger.error("SQLException " + e.getSQLState(), e);
            }

            if (rowID < 0)
                success = false;
        }

        if (success && !splitList.isEmpty() && (insertSplitBTToDB(rowID, splitList) != splitList.size()))
            success = false;

        if (savepointSetHere) {
            if (!success) {
                rollbackDB();
            } else {
                commitDB();
            }
            releaseDBSavepoint();
        } else {
            if (!success) {
                throw new SQLException("SQL Error in insertTransactionToDB");
            }
        }

        return rowID;
    }

    // insert trade transaction to database and returns rowID
    // return -1 if failed
    private int insertTransactionToDB(QIFParser.TradeTransaction tt) throws SQLException {
        int rowID = -1;
        Account account = getAccountByName(tt.getAccountName());
        if (account == null) {
            mLogger.error("Account [" + tt.getAccountName() + "] not found, nothing inserted");
            return -1;
        }

        // temporarily unset autocommit
        mConnection.setAutoCommit(false);

        String sqlCmd = "insert into TRANSACTIONS " +
                "(ACCOUNTID, DATE, AMOUNT, TRADEACTION, SECURITYID, " +
                "STATUS, CATEGORYID, MEMO, PRICE, QUANTITY, COMMISSION, OLDQUANTITY) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)){
            int cid = mapCategoryOrAccountNameToID(tt.getCategoryOrTransfer());
            QIFParser.TradeTransaction.Action action = tt.getAction();
            if (cid == -account.getID()) {
                // self transfer, set to no transfer and change XIN/XOUT to DEPOSIT/WITHDRW
                cid = 0;
                switch (action) {
                    case XIN:
                        action = QIFParser.TradeTransaction.Action.DEPOSIT;
                        break;
                    case XOUT:
                        action = QIFParser.TradeTransaction.Action.WITHDRAW;
                        break;
                    default:
                        break;
                }
            }
            preparedStatement.setInt(1, account.getID());
            preparedStatement.setDate(2, Date.valueOf(tt.getDate()));
            preparedStatement.setBigDecimal(3, tt.getTAmount());
            preparedStatement.setString(4, action.name());
            String name = tt.getSecurityName();
            if (name != null && name.length() > 0) {
                //preparedStatement.setInt(5, getSecurityByName(name).getID());
                preparedStatement.setObject(5, getSecurityByName(name).getID());
            } else {
                preparedStatement.setObject(5, null);
            }
            preparedStatement.setString(6, tt.getStatus().name());
            preparedStatement.setInt(7, cid);
            preparedStatement.setString(8, tt.getMemo());
            preparedStatement.setBigDecimal(9, tt.getPrice());
            preparedStatement.setBigDecimal(10, tt.getQuantity());
            preparedStatement.setBigDecimal(11, tt.getCommission());
            if (tt.getAction() == QIFParser.TradeTransaction.Action.STKSPLIT)
                preparedStatement.setBigDecimal(12, BigDecimal.TEN);
            else
                preparedStatement.setBigDecimal(12, null);
            preparedStatement.executeUpdate();
            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                if (resultSet.next()) {
                    rowID = resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }

        if (rowID < 0)
            mConnection.rollback();
        else
            mConnection.commit();

        // we are done here
        mConnection.setAutoCommit(true);
        return rowID;
    }

    private void insertCategoryToDB(QIFParser.Category category) {
        String sqlCmd;
        sqlCmd = "insert into CATEGORIES (NAME, DESCRIPTION, INCOMEFLAG, TAXREFNUM, BUDGETAMOUNT) "
                + "values (?,?,?, ?, ?)";

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)){
            preparedStatement.setString(1, category.getName());
            preparedStatement.setString(2, category.getDescription());
            preparedStatement.setBoolean(3, category.isIncome());
            preparedStatement.setInt(4, category.getTaxRefNum());
            preparedStatement.setBigDecimal(5, category.getBudgetAmount());
            preparedStatement.executeUpdate();

            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                resultSet.next();
                category.setID(resultSet.getInt(1));
            }
        } catch (SQLException e) {
            String title = "Database Error";
            String headerText = "Unknown DB error";
            if (e.getErrorCode() == 23505) {
                title = "Duplicate Category Name";
                headerText = "Category name " + category.getName() + " exists already.";
            }

            mLogger.error("SQLException " + e.getSQLState(), e);

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle(title);
            alert.setHeaderText(headerText);
            alert.showAndWait();
        } catch (NullPointerException e) {
            mLogger.error("NullPointerException", e);
        }
    }

    // insert or update an account in database, return the account ID, or -1 for failure
    void insertUpdateAccountToDB(Account account) {
        String sqlCmd;
        if (account.getID() < MIN_ACCOUNT_ID) {
            // new account, insert
            sqlCmd = "insert into ACCOUNTS (TYPE, NAME, DESCRIPTION, HIDDENFLAG, DISPLAYORDER) values (?,?,?,?,?)";
        } else {
            sqlCmd = "update ACCOUNTS set TYPE = ?, NAME = ?, DESCRIPTION = ? , HIDDENFLAG = ?, DISPLAYORDER = ? " +
                    "where ID = ?";
        }

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            preparedStatement.setString(1, account.getType().name());
            preparedStatement.setString(2, account.getName());
            preparedStatement.setString(3, account.getDescription());
            preparedStatement.setBoolean(4, account.getHiddenFlag());
            preparedStatement.setInt(5, account.getDisplayOrder());
            if (account.getID() >= MIN_ACCOUNT_ID) {
                preparedStatement.setInt(6, account.getID());
            }
            if (preparedStatement.executeUpdate() == 0) {
                throw new SQLException("Insert Account failed, no rows affected");
            }

            if (account.getID() < MIN_ACCOUNT_ID) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    if (resultSet.next()) {
                        account.setID(resultSet.getInt(1));
                    } else {
                        throw new SQLException("\n" + sqlCmd + "\nInsert Account failed, no ID obtained");
                    }
                }
            }

            // update account list
            Account a = getAccountByID(account.getID());
            if (a == null) {
                // new account, add
                mAccountList.add(account);
                account.setTransactionList(getTransactionListByAccountID(account.getID()));
                updateAccountBalance(account.getID());
            } else if (a != account) {
                // old account, replace
                mLogger.error("insertupdateaccounttodb, how did we get here");
                mAccountList.set(mAccountList.indexOf(a), account);
            }

        } catch (SQLException e) {
            String title = "Database Error";
            String headerText = "Unknown DB error";
            if (e.getErrorCode() == 23505) {
                title = "Duplicate Account Name";
                headerText = "Account name " + account.getName() + " is already taken.";
            } else {
                mLogger.error("SQLException " + e.getSQLState(), e);
            }

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle(title);
            alert.setHeaderText(headerText);
            alert.showAndWait();

        } catch (NullPointerException e) {
            mLogger.error("NullPointerException", e);
        }
    }

    private int getSecurityID(String ticker) {
        if (mConnection == null) {
            return -1;
        }
        String sqlCmd = "select ID from SECURITIES where TICKER = '"
                + ticker + "'";
        int id = -1;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            statement = mConnection.createStatement();
            resultSet = statement.executeQuery(sqlCmd);
            if (resultSet.next()) {
                id = resultSet.getInt("ID");
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        } finally {
            try {
                if (statement != null) {
                    statement.close();
                }
                if (resultSet != null) {
                    resultSet.close();
                }
            } catch (SQLException e) {
                mLogger.error("SQLException " + e.getSQLState(), e);
            }
        }
        return id;
    }

    void initReminderMap() {
        if (mConnection == null) return;

        mReminderMap.clear();
        String sqlCmd = "select * from REMINDERS";
        try (Statement statement = mConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                String type = resultSet.getString("TYPE");
                String payee = resultSet.getString("PAYEE");
                BigDecimal amount = resultSet.getBigDecimal("AMOUNT");
                int estCnt = resultSet.getInt("ESTCOUNT");
                int accountID = resultSet.getInt("ACCOUNTID");
                int categoryID = resultSet.getInt("CATEGORYID");
                int transferAccountID = resultSet.getInt("TRANSFERACCOUNTID");
                int tagID = resultSet.getInt("TAGID");
                String memo = resultSet.getString("MEMO");
                LocalDate startDate = resultSet.getDate("STARTDATE").toLocalDate();
                LocalDate endDate = resultSet.getDate("ENDDATE") == null ?
                        null : resultSet.getDate("ENDDATE").toLocalDate();
                DateSchedule.BaseUnit bu = DateSchedule.BaseUnit.valueOf(resultSet.getString("BASEUNIT"));
                int np = resultSet.getInt("NUMPERIOD");
                int ad = resultSet.getInt("ALERTDAYS");
                boolean isDOM = resultSet.getBoolean("ISDOM");
                boolean isFWD = resultSet.getBoolean("ISFWD");

                DateSchedule ds = new DateSchedule(bu, np, startDate, endDate, ad, isDOM, isFWD);
                mReminderMap.put(id, new Reminder(id, Reminder.Type.valueOf(type), payee, amount, estCnt,
                        accountID, categoryID, transferAccountID, tagID, memo, ds, loadSplitTransactions(-id)));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
    }

    void initReminderTransactionList() {
        if (mConnection == null) return;

        mReminderTransactionList.clear();
        String sqlCmd = "select * from REMINDERTRANSACTIONS order by REMINDERID, DUEDATE";
        int ridPrev = -1;
        Reminder reminder = null;
        Map<Integer, LocalDate> lastDueDateMap = new HashMap<>();
        try (Statement statement = mConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                int rid = resultSet.getInt("REMINDERID");
                LocalDate dueDate = resultSet.getDate("DUEDATE").toLocalDate();
                int tid = resultSet.getInt("TRANSACTIONID");

                // keep track latest due date
                lastDueDateMap.put(rid, dueDate);

                if (rid != ridPrev) {
                    // new rid
                    reminder = getReminderMap().get(rid);
                    if (reminder == null)
                        continue; // zombie reminderTransaction

                    ridPrev = rid; // save rid to ridPrev
                }

                mReminderTransactionList.add(new ReminderTransaction(reminder, dueDate, tid));
            }

            // add one unfulfilled reminders
            for (Integer rid : getReminderMap().keySet()) {
                reminder = getReminderMap().get(rid);
                if (reminder.getEstimateCount() > 0) {
                    // estimate amount
                    FilteredList<ReminderTransaction> frtList = new FilteredList<>(mReminderTransactionList,
                            rt -> rt.getReminder().getID() == rid);
                    SortedList<ReminderTransaction> sfrtList = new SortedList<>(frtList,
                            Comparator.comparing(ReminderTransaction::getDueDate));
                    BigDecimal amt = BigDecimal.ZERO;
                    int cnt = 0;
                    for (int i = sfrtList.size()-1; i >= 0 && cnt < reminder.getEstimateCount(); i--) {
                        ReminderTransaction rt = sfrtList.get(i);
                        int tid = rt.getTransactionID();
                        if (tid > 0) {
                            int idx = getTransactionIndexByID(tid);
                            if (idx > 0) {
                                amt = amt.add(mTransactionList.get(idx).getAmount());
                            } else {
                                // tid not found, treat it as skipped
                                mLogger.error("initReminderTransactionList: Transaction " + tid + " not found. " +
                                        "Probably deleted, treat as skipped.");
                            }
                        }
                        cnt++;
                    }
                    if (cnt > 0)
                        amt = amt.divide(BigDecimal.valueOf(cnt), amt.scale());
                    amt = amt.setScale(AMOUNT_FRACTION_LEN, BigDecimal.ROUND_HALF_UP);
                    reminder.setAmount(amt);
                }
                LocalDate lastDueDate = lastDueDateMap.get(rid);
                if (lastDueDate != null)
                    lastDueDate = lastDueDate.plusDays(1);
                LocalDate dueDate = reminder.getDateSchedule().getNextDueDate(lastDueDate);
                mReminderTransactionList.add(new ReminderTransaction(reminder, dueDate, -1));
            }
            mReminderTransactionList.sort(Comparator.comparing(ReminderTransaction::getDueDate));
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
    }

    void initTagList() {
        if (mConnection == null) return;

        mTagList.clear();
        String sqlCmd = "select * from TAGS";
        try (Statement statement = mConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)){
            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                String name = resultSet.getString("NAME");
                String description = resultSet.getString("DESCRIPTION");
                mTagList.add(new Tag(id, name, description));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
    }

    void initCategoryList() {
        if (mConnection == null) return;

        mCategoryList.clear();
        Statement statement = null;
        ResultSet resultSet = null;
        String sqlCmd = "select * from CATEGORIES order by INCOMEFLAG DESC, NAME";
        try {
            statement = mConnection.createStatement();
            resultSet = statement.executeQuery(sqlCmd);
            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                String name = resultSet.getString("NAME");
                String description = resultSet.getString("DESCRIPTION");
                boolean incomeFlag = resultSet.getBoolean("INCOMEFLAG");
                int taxRefNum = resultSet.getInt("TAXREFNUM");
                BigDecimal budgetAmount = resultSet.getBigDecimal("BUDGETAMOUNT");
                Category category = new Category();
                category.setID(id);
                category.setName(name);
                category.setDescription(description);
                category.setIsIncome(incomeFlag);
                category.setTaxRefNum(taxRefNum);
                category.setBudgetAmount(budgetAmount);
                mCategoryList.add(category);
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        } finally {
            try {
                if (statement != null) statement.close();
                if (resultSet != null) resultSet.close();
            } catch (SQLException e) {
                mLogger.error("SQLException " + e.getSQLState(), e);
            }
        }
    }

    void updateAccountBalance(Security security) {
        // update account balance for all non-hidden accounts contains security in currentsecuritylist
        for (Account account : getAccountList(Account.Type.INVESTING, false, true)) {
            if (account.hasSecurity(security)) {
                updateAccountBalance(account.getID());
            }
        }
    }

    // update account balances and Account::mCurrentSecurityList (sorted by Name).
    void updateAccountBalance(int accountID) {
        Account account = getAccountByID(accountID);
        if (account == null) {
            mLogger.error("Invalid account ID: " + accountID);
            return;
        }

        // update holdings and balance for INVESTING account
        if (account.getType() == Account.Type.INVESTING) {
            List<SecurityHolding> shList = updateAccountSecurityHoldingList(account, LocalDate.now(), 0);
            SecurityHolding totalHolding = shList.get(shList.size() - 1);
            if (totalHolding.getSecurityName().equals("TOTAL")) {
                account.setCurrentBalance(totalHolding.getMarketValue());
            } else {
                mLogger.error("Missing Total Holding in account " + account.getName() + " holding list");
            }

            ObservableList<Security> accountSecurityList = account.getCurrentSecurityList();
            accountSecurityList.clear();
            for (SecurityHolding sh : shList) {
                String securityName = sh.getSecurityName();
                if (!securityName.equals("TOTAL") && !securityName.equals("CASH")) {
                    Security se = getSecurityByName(securityName);
                    if (se == null) {
                        mLogger.error("Failed to find security with name: '" + securityName + "'");
                    } else {
                        accountSecurityList.add(se);
                    }
                }
                // sort securities by name
                FXCollections.sort(accountSecurityList, Comparator.comparing(Security::getName));
            }
        }

        account.updateTransactionListBalance();
    }

    // should be called after mTransactionList being properly initialized
    void initAccountList() {
        mAccountList.clear();
        if (mConnection == null) return;

        try (Statement statement = mConnection.createStatement()) {
            String sqlCmd = "select ID, TYPE, NAME, DESCRIPTION, HIDDENFLAG, DISPLAYORDER "
                    + "from ACCOUNTS"; // order by TYPE, ID";
            ResultSet rs = statement.executeQuery(sqlCmd);
            while (rs.next()) {
                int id = rs.getInt("ID");
                Account.Type type = Account.Type.valueOf(rs.getString("TYPE"));
                String name = rs.getString("NAME");
                String description = rs.getString("DESCRIPTION");
                Boolean hiddenFlag = rs.getBoolean("HIDDENFLAG");
                Integer displayOrder = rs.getInt("DISPLAYORDER");
                mAccountList.add(new Account(id, type, name, description, hiddenFlag, displayOrder, BigDecimal.ZERO));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }

        // load transactions and set account balance
        // we don't care about deleted account
        for (Account account : getAccountList(null, null, true)) {
            // load transaction list
            // this method will set account balance for SPENDING account
            account.setTransactionList(getTransactionListByAccountID(account.getID()));
            updateAccountBalance(account.getID());
        }
    }

    private void initSecurityList() {
        mSecurityList.clear();
        if (mConnection == null) return;

        try (Statement statement = mConnection.createStatement()) {
            String sqlCmd = "select ID, TICKER, NAME, TYPE from SECURITIES order by ID";
            ResultSet rs = statement.executeQuery(sqlCmd);
            while (rs.next()) {
                int id = rs.getInt("ID");
                String ticker = rs.getString("TICKER");
                String name = rs.getString("NAME");
                Security.Type type = Security.Type.valueOf(rs.getString("TYPE"));
                if (type == Security.Type.INDEX)
                    continue; // skip index
                mSecurityList.add(new Security(id, ticker, name, type));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
    }

    private List<SplitTransaction> loadSplitTransactions(int tid) {
        List<SplitTransaction> stList = new ArrayList<>();

        String sqlCmd = "select * from SPLITTRANSACTIONS where TRANSACTIONID = " + tid + " order by ID";

        try (Statement statement = mConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                int cid = resultSet.getInt("CATEGORYID");
                int tagid = resultSet.getInt("TAGID");
                String payee = resultSet.getString("PAYEE");
                String memo = resultSet.getString("MEMO");
                BigDecimal amount = resultSet.getBigDecimal("AMOUNT");
                if (amount == null) {
                    amount = BigDecimal.ZERO;
                }
                // todo
                // do we need percentage?
                // ignore it for now
                int matchID = resultSet.getInt("MATCHTRANSACTIONID");
                stList.add(new SplitTransaction(id, cid, tagid, payee, memo, amount, matchID));
            }
        }  catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
        return stList;
    }

    // initialize mTransactionList order by ID
    // mSecurityList should be loaded prior this call.
    private void initTransactionList() {
        mTransactionList.clear();
        if (mConnection == null)
            return;

        List<Transaction> tList = new ArrayList<>();  // a simple list to temporarily hold all transactions
        String sqlCmd = "select * from TRANSACTIONS order by ID";
        try (Statement statement = mConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                int aid = resultSet.getInt("ACCOUNTID");
                LocalDate tDate = resultSet.getDate("DATE").toLocalDate();
                Date sqlDate = resultSet.getDate("ADATE");
                LocalDate aDate;
                if (sqlDate != null)
                    aDate = sqlDate.toLocalDate();
                else
                    aDate = tDate;
                String reference = resultSet.getString("REFERENCE");
                String payee = resultSet.getString("PAYEE");
                String memo = resultSet.getString("MEMO");
                BigDecimal amount = resultSet.getBigDecimal("AMOUNT");
                if (amount == null) {
                    amount = BigDecimal.ZERO;
                }
                int cid = resultSet.getInt("CATEGORYID");
                int tagID = resultSet.getInt("TAGID");
                Transaction.TradeAction tradeAction = null;
                String taStr = resultSet.getString("TRADEACTION");
                if (taStr != null && taStr.length() > 0) tradeAction = Transaction.TradeAction.valueOf(taStr);
                if (tradeAction == null) {
                    mLogger.error("Bad trade action value in transaction " + id);
                    continue;
                }

                Transaction.Status status = Transaction.Status.valueOf(resultSet.getString("STATUS"));
                int securityID = resultSet.getInt("SECURITYID");
                BigDecimal quantity = resultSet.getBigDecimal("QUANTITY");
                BigDecimal commission = resultSet.getBigDecimal("COMMISSION");
                BigDecimal price = resultSet.getBigDecimal("PRICE");
                BigDecimal oldQuantity = resultSet.getBigDecimal("OLDQUANTITY");
                int matchID = resultSet.getInt("MATCHTRANSACTIONID");
                int matchSplitID = resultSet.getInt("MATCHSPLITTRANSACTIONID");

                String name = "";
                if (securityID > 0) {
                    Security security = getSecurityByID(securityID);
                    if (security != null)
                        name = security.getName();
                }

                Transaction transaction = new Transaction(id, aid, tDate, aDate, tradeAction, status, name, reference,
                        payee, price, quantity, oldQuantity, memo, commission, amount, cid, tagID, matchID,
                        matchSplitID, resultSet.getBoolean("SPLITFLAG") ? loadSplitTransactions(id) : null);

                tList.add(transaction);  // add transaction to simple list first.
            }
            mTransactionList.setAll(tList); // now all all contents of the simple list to main list.
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
    }

    // return a list of transactions sorted for Date and transaction ID for the given accountID
    private ObservableList<Transaction> getTransactionListByAccountID(int accountID) {
        return new FilteredList<>(mTransactionListSort2, t -> t.getAccountID() == accountID);
    }

    void putOpenedDBNames(List<String> openedDBNames) {
        for (int i = 0; i < openedDBNames.size(); i++) {
            mPrefs.put(KEY_OPENEDDBPREFIX + i, openedDBNames.get(i));
        }
        for (int i = openedDBNames.size(); i < MAXOPENEDDBHIST; i++) {
            mPrefs.remove(KEY_OPENEDDBPREFIX+i);
        }
    }

    private List<String> updateOpenedDBNames(List<String> openedDBNames, String fileName) {
        int idx = openedDBNames.indexOf(fileName);
        if (idx > -1) {
            openedDBNames.remove(idx);
        }
        openedDBNames.add(0, fileName);  // always add on the top

        // keep only MAXOPENEDDBHIST
        while (openedDBNames.size() > MAXOPENEDDBHIST) {
            openedDBNames.remove(MAXOPENEDDBHIST);
        }

        return openedDBNames;
    }

    void showSplashScreen(boolean firstTime) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/SplashScreenDialog.fxml"));
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            SplashScreenDialogController controller = loader.getController();
            if (controller == null) {
                mLogger.error("Null SpashScreenDialogController?");
                Platform.exit();
                System.exit(0);
            }
            controller.setMainApp(this, dialogStage, firstTime);
            dialogStage.setOnCloseRequest(e -> controller.handleClose());
            dialogStage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }
    }

    void showReportDialog(ReportDialogController.Setting setting) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/ReportDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            ReportDialogController controller = loader.getController();
            if (controller == null) {
                mLogger.error("Null ReportDialogController");
                return;
            }
            controller.setMainApp(setting, this, dialogStage);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }
    }

    void showAccountListDialog() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/AccountListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Account List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            AccountListDialogController controller = loader.getController();
            if (controller == null) {
                mLogger.error("Null AccountListDialog controller?");
                return;
            }
            controller.setMainApp(this, dialogStage);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }
    }

    void showBillIncomeReminderDialog() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/ReminderTransactionListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Reminder Transaction List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            ReminderTransactionListDialogController controller = loader.getController();
            if (controller == null) {
                mLogger.error("Null controller for ReminderTransactionListDialog");
                return;
            }
            controller.setMainApp(this, dialogStage);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }
    }

    void showTagListDialog() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/TagListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Tag List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            TagListDialogController controller = loader.getController();
            controller.setMainApp(this, dialogStage);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            showExceptionDialog("Exception", "IO Exception", "showTagListDialog IO Exception", e);
        } catch (NullPointerException e) {
            showExceptionDialog("Exception", "Null pointer exception",
                    "showTagListDialog null pointer exception", e);
        }
    }

    void showCategoryListDialog() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/CategoryListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Category List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            CategoryListDialogController controller = loader.getController();
            controller.setMainApp(this, dialogStage);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            showExceptionDialog("Exception", "IO Exception", "showCategoryListDialog IO Exception", e);
        } catch (NullPointerException e) {
            showExceptionDialog("Exception", "Null pointer exception",
                    "showCategoryListDialog null pointer exception", e);
        }
    }

    void showSecurityListDialog() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/SecurityListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Security List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            SecurityListDialogController controller = loader.getController();
            if (controller == null) {
                mLogger.error("Null controller for SecurityListDialog");
                return;
            }
            controller.setMainApp(this, dialogStage);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }
    }

    // returns a list of passwords, the length of list can be 0, 1, or 2.
    // length 0 means some exception happened
    // length 1 means normal situation (for creation db or normal login)
    // length 2 means old password and new password (for changing password)
    private List<String> showPasswordDialog(PasswordDialogController.MODE mode) {
        String title;
        switch (mode) {
            case ENTER:
                title = "Enter Password";
                break;
            case NEW:
                title = "Set New Password";
                break;
            case CHANGE:
                title = "Change Password";
                break;
            default:
                mLogger.error("Unknown MODE" + mode.toString());
                title = "Unknown";
        }

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/PasswordDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle(title);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            PasswordDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setMode(mode);
            dialogStage.showAndWait();

            return controller.getPasswords();
        } catch (IOException e) {
            mLogger.error("IOException", e);
            return null;
        }
    }

    // update HoldingsList to date but exclude transaction exTid and after.
    // an list of cash and total is returned if the account is not an investing account
    List<SecurityHolding> updateAccountSecurityHoldingList(Account account, LocalDate date, int exTid) {
        // empty the list first
        List<SecurityHolding> securityHoldingList = new ArrayList<>();
        if (account.getType() != Account.Type.INVESTING) {
            // deal with non investing account here
            BigDecimal totalCash = null;
            int n = account.getTransactionList().size();
            if (n == 0) {
                totalCash = BigDecimal.ZERO;
            } else {
                for (int i = 0; i < account.getTransactionList().size(); i++) {
                    Transaction t = account.getTransactionList().get(i);
                    if (t.getTDate().isAfter(date) || t.getID() == exTid) {
                        if (i == 0) {
                            // all transaction are after given date, balance is zero
                            totalCash = BigDecimal.ZERO;
                        } else {
                            // t is already after given date, use previous
                            totalCash = account.getTransactionList().get(i - 1).getBalanceProperty().get();
                        }
                        break; // we are done
                    }
                }
                if (totalCash == null) {
                    // all transactions happened on or before date, take the balance of last one
                    totalCash = account.getTransactionList().get(n-1).getBalanceProperty().get();
                }
            }

            SecurityHolding cashHolding = new SecurityHolding("CASH");
            cashHolding.getPriceProperty().set(null);
            cashHolding.getQuantityProperty().set(null);
            cashHolding.setCostBasis(totalCash);
            cashHolding.getMarketValueProperty().set(totalCash);

            SecurityHolding totalHolding = new SecurityHolding("TOTAL");
            totalHolding.getMarketValueProperty().set(totalCash);
            totalHolding.setQuantity(null);
            totalHolding.setCostBasis(totalCash);
            totalHolding.getPNLProperty().set(BigDecimal.ZERO);
            totalHolding.getPriceProperty().set(null); // don't want to display any price
            totalHolding.updatePctRet();

            // nothing to sort here, but for symmetry...
            securityHoldingList.sort(Comparator.comparing(SecurityHolding::getSecurityName));
            securityHoldingList.add(cashHolding);
            securityHoldingList.add(totalHolding);
            return securityHoldingList;
        }

        // deal with Investing account here
        ObservableList<Transaction> tList = FXCollections.observableArrayList();
        for (int i = 0; i < account.getTransactionList().size(); i++) {
            // copy over the transactions we are interested
            Transaction t = account.getTransactionList().get(i);
            if (t.getTDate().isAfter(date) || t.getID() == exTid)
                break;
            tList.add(t);
        }

        BigDecimal totalCash = BigDecimal.ZERO.setScale(SecurityHolding.CURRENCYDECIMALLEN, RoundingMode.HALF_UP);
        Map<String, Integer> indexMap = new HashMap<>();  // security name and location index
        Map<String, List<Transaction>> stockSplitTransactionListMap = new HashMap<>();

        // sort the transaction list first
        // we want to sort the transactions by dates first, then by TradeAction, in which we want to put
        // SELL and CVSHRT at the end in case the transaction is closing the positions opened on the same date
        SortedList<Transaction> sortedTransactionList = new SortedList<>(tList, (o1, o2) -> {
            // first compare dates
            int dateComparison = o1.getTDate().compareTo(o2.getTDate());
            if (dateComparison != 0)
                return dateComparison;
            dateComparison = o1.getADate().compareTo(o2.getADate());
            if (dateComparison != 0)
                return dateComparison;

            // compare TradeAction if dates are the same
            // we want to have SELL and CVTSHRT at the end
            if (o1.getTradeAction() == Transaction.TradeAction.SELL
                    || o1.getTradeAction() == Transaction.TradeAction.CVTSHRT)
                return (o2.getTradeAction() == Transaction.TradeAction.SELL
                        || o2.getTradeAction() == Transaction.TradeAction.CVTSHRT) ? 0 : 1;
            if (o2.getTradeAction() == Transaction.TradeAction.SELL
                    || o2.getTradeAction() == Transaction.TradeAction.CVTSHRT)
                return -1;
            return o1.getTradeAction().compareTo(o2.getTradeAction());
        });

        for (Transaction t : sortedTransactionList) {
            int tid = t.getID();

            totalCash = totalCash.add(t.getCashAmount().setScale(SecurityHolding.CURRENCYDECIMALLEN,
                    RoundingMode.HALF_UP));
            String name = t.getSecurityName();

            if (name != null && !name.isEmpty()) {
                // it's not cash transaction, add security lot
                Integer index = indexMap.get(name);
                if (index == null) {
                    // first time seeing this security, add to the end
                    index = securityHoldingList.size();
                    indexMap.put(name, index);
                    securityHoldingList.add(new SecurityHolding(name));
                }
                if (t.getTradeAction() == Transaction.TradeAction.STKSPLIT) {
                    securityHoldingList.get(index).adjustStockSplit(t.getQuantity(), t.getOldQuantity());
                    List<Transaction> splitList = stockSplitTransactionListMap.computeIfAbsent(t.getSecurityName(),
                            k -> new ArrayList<>());
                    splitList.add(t);
                } else if (Transaction.hasQuantity(t.getTradeAction())) {
                    securityHoldingList.get(index).addLot(new SecurityHolding.LotInfo(t.getID(), name,
                            t.getTradeAction(), t.getADate(), t.getPrice(), t.getSignedQuantity(), t.getCostBasis()),
                            getMatchInfoList(tid));
                }
            }
        }

        BigDecimal totalMarketValue = totalCash;
        BigDecimal totalCostBasis = totalCash;
        for (Iterator<SecurityHolding> securityHoldingIterator = securityHoldingList.iterator();
             securityHoldingIterator.hasNext(); ) {
            SecurityHolding securityHolding = securityHoldingIterator.next();

            if (securityHolding.getQuantity().signum() == 0) {
                // remove security with zero quantity
                securityHoldingIterator.remove();
                continue;
            }

            Price price = getLatestSecurityPrice(securityHolding.getSecurityName(), date);
            BigDecimal p = price == null ? BigDecimal.ZERO : price.getPrice(); // assume zero if no price found
            if (price != null && price.getDate().isBefore(date)) {
                // need to check if there is stock split between "date" and price.getDate()
                List<Transaction> splitList = stockSplitTransactionListMap.get(securityHolding.getSecurityName());
                if (splitList != null) {
                    // we have a list of stock splits, check now
                    // since this list is ordered by date, we start from the end
                    ListIterator<Transaction> li = splitList.listIterator(splitList.size());
                    while (li.hasPrevious()) {
                        Transaction t = li.previous();
                        if (t.getTDate().isBefore(price.getDate()))
                            break; // the split is prior to the price date, no need to adjust
                        p = p.multiply(t.getOldQuantity()).divide(t.getQuantity(), PRICE_FRACTION_LEN,
                                RoundingMode.HALF_UP);
                    }
                }
            }
            securityHolding.updateMarketValue(p);
            securityHolding.updatePctRet();

            // both cost basis and market value are properly scaled
            totalMarketValue = totalMarketValue.add(securityHolding.getMarketValue());
            totalCostBasis = totalCostBasis.add(securityHolding.getCostBasis());
        }

        SecurityHolding cashHolding = new SecurityHolding("CASH");
        cashHolding.getPriceProperty().set(null);
        cashHolding.getQuantityProperty().set(null);
        cashHolding.setCostBasis(totalCash);
        cashHolding.getMarketValueProperty().set(totalCash);

        SecurityHolding totalHolding = new SecurityHolding("TOTAL");
        totalHolding.getMarketValueProperty().set(totalMarketValue);
        totalHolding.setQuantity(null);
        totalHolding.setCostBasis(totalCostBasis);
        totalHolding.getPNLProperty().set(totalMarketValue.subtract(totalCostBasis));
        totalHolding.getPriceProperty().set(null); // don't want to display any price
        totalHolding.updatePctRet();

        securityHoldingList.sort(Comparator.comparing(SecurityHolding::getSecurityName));
        // put cash holding at the bottom
        if (totalCash.signum() != 0)
            securityHoldingList.add(cashHolding);
        securityHoldingList.add(totalHolding);

        return securityHoldingList;
    }

    // load MatchInfoList from database
    List<SecurityHolding.MatchInfo> getMatchInfoList(int tid) {
        List<SecurityHolding.MatchInfo> matchInfoList = new ArrayList<>();

        if (mConnection == null) {
            mLogger.error("DB connection down?! ");
            return matchInfoList;
        }

        try (Statement statement = mConnection.createStatement()){
            String sqlCmd = "select TRANSID, MATCHID, MATCHQUANTITY from LOTMATCH " +
                    "where TRANSID = " + tid + " order by MATCHID";
            ResultSet rs = statement.executeQuery(sqlCmd);
            while (rs.next()) {
                int mid = rs.getInt("MATCHID");
                BigDecimal quantity = rs.getBigDecimal("MATCHQUANTITY");
                matchInfoList.add(new SecurityHolding.MatchInfo(tid, mid, quantity));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
        return matchInfoList;
    }

    // return all the prices in a list, sorted ascending by date.
    List<Price> getSecurityPrice(int securityID) {
        List<Price> priceList = new ArrayList<>();

        String sqlCmd = "select DATE, PRICE from PRICES where SECURITYID = " + securityID
                + " order by DATE asc";
        try (Statement statement = mConnection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(sqlCmd);
            while (resultSet.next()) {
                priceList.add(new Price(resultSet.getDate(1).toLocalDate(), resultSet.getBigDecimal(2)));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }

        return priceList;
    }

    // retrive the latest price no later than requested date
    private Price getLatestSecurityPrice(String securityName, LocalDate date) {
        Price price = null;
        String sqlCmd = "select top 1 p.price, p.date from PRICES p inner join SECURITIES s " +
                "where s.NAME = '" + securityName + "' and s.ID = p.SECURITYID " +
                " and p.DATE <= '" + date.toString() + "' order by DATE desc";
        try (Statement statement = mConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            if (resultSet.next()) {
                price = new Price(resultSet.getDate(2).toLocalDate(), resultSet.getBigDecimal(1));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
        return price;
    }

    // Take an integer transaction id and a list of MatchInfo,
    // delete all MatchInfo in the database with same TransactionID
    // save new MatchInfo.
    // Note: The TransactionID field of input matchInfoList is not used.
    void putMatchInfoList(int tid, List<SecurityHolding.MatchInfo> matchInfoList) {
        if (mConnection == null) {
            mLogger.error("DB connection down?!");
            return;
        }

        // delete any existing
        try (Statement statement = mConnection.createStatement()) {
            statement.execute("delete from LOTMATCH where TRANSID = " + tid);
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }

        if (matchInfoList.size() == 0)
            return;

        // insert list
        String sqlCmd = "insert into LOTMATCH (TRANSID, MATCHID, MATCHQUANTITY) values (?, ?, ?)";
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            for (SecurityHolding.MatchInfo matchInfo : matchInfoList) {
                preparedStatement.setInt(1, tid);
                preparedStatement.setInt(2, matchInfo.getMatchTransactionID());
                preparedStatement.setBigDecimal(3, matchInfo.getMatchQuantity());

                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
    }

    // return true if splittransaction list is changed, false if not.
    List<SplitTransaction> showSplitTransactionsDialog(Stage parent, List<SplitTransaction> stList,
                                                       BigDecimal netAmount) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/SplitTransactionsDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Split Transaction");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(parent);
            dialogStage.setScene(new Scene(loader.load()));
            dialogStage.setUserData(false);
            SplitTransactionsDialogController controller = loader.getController();
            controller.setMainApp(this, dialogStage, stList, netAmount);
            dialogStage.showAndWait();
            return controller.getSplitTransactionList();
        } catch (IOException e) {
            mLogger.error("IOException", e);
            return null;
        }
    }

    void showSpecifyLotsDialog(Stage parent, Transaction t, List<SecurityHolding.MatchInfo> matchInfoList) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/SpecifyLotsDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Specify Lots...");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(parent);
            dialogStage.setScene(new Scene(loader.load()));
            SpecifyLotsDialogController controller = loader.getController();
            controller.setMainApp(this, t, matchInfoList, dialogStage);
            dialogStage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }
    }

    // The input transaction is not changed.
    void showEditTransactionDialog(Stage parent, Transaction transaction) {
        List<Transaction.TradeAction> taList = (mCurrentAccount.getType() == Account.Type.INVESTING) ?
                Arrays.asList(Transaction.TradeAction.values()) :
                Arrays.asList(Transaction.TradeAction.WITHDRAW, Transaction.TradeAction.DEPOSIT,
                        Transaction.TradeAction.XIN, Transaction.TradeAction.XOUT);
        showEditTransactionDialog(parent, transaction, Collections.singletonList(mCurrentAccount),
                mCurrentAccount, taList);
    }

    // return transaction id or -1 for failure
    // The input transaction is not changed.
    int showEditTransactionDialog(Stage parent, Transaction transaction, List<Account> accountList,
                                          Account defaultAccount, List<Transaction.TradeAction> taList) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation((MainApp.class.getResource("/view/EditTransactionDialog.fxml")));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Enter Transaction:");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(parent);
            dialogStage.setScene(new Scene(loader.load()));

            EditTransactionDialogController controller = loader.getController();
            controller.setMainApp(this, transaction, dialogStage, accountList, defaultAccount, taList);
            dialogStage.showAndWait();
            return controller.getTransactionID();
        } catch (IOException e) {
            mLogger.error("IOException", e);
            return -1;
        }
    }

    void showAccountHoldings() {
        if (mCurrentAccount == null) {
            mLogger.error("Can't show holdings for null account.");
            return;
        }
        if (mCurrentAccount.getType() != Account.Type.INVESTING) {
            mLogger.error("Show holdings only applicable for trading account");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/HoldingsDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Account Holdings: " + mCurrentAccount.getName());
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));

            HoldingsDialogController controller = loader.getController();
            controller.setMainApp(this);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }
    }

    private void closeConnection() {
        if (mConnection != null) {
            try {
                mConnection.close();
            } catch (SQLException e) {
                mLogger.error("SQLException " + e.getSQLState(), e);
            }
            mConnection = null;
        }
    }

    boolean isConnected() { return mConnection != null; }

    // given a date, an originating accountID, and receiving accountid, and the amount of cashflow
    // find a matching transaction in a sorted list
    // return the index of matching transaction in tList
    // return -1 if no match found or t is a split transaction
    // tList is sorted according to the date, reverse(isSplit), accountid
    // transactions with getMatchID > 0 will not be considered.

    private int findMatchingTransaction(LocalDate date, int fromAccountID, int toAccountID, BigDecimal cashFlow,
                                List<Transaction> tList) {
        for (int j = 0; j < tList.size(); j++) {
            Transaction t1 = tList.get(j);
            if (t1.getTDate().isBefore(date) || (t1.getAccountID() < toAccountID) || t1.isSplit())
                continue;

            if ((t1.getAccountID() > toAccountID) || t1.getTDate().isAfter(date)) {
                // pass the date or the account ID
                return -1;
            }
            if (!t1.isTransfer() || (t1.getMatchID() > 0))
                continue;
            if ((fromAccountID == -t1.getCategoryID()) && (cashFlow.add(t1.cashFlow()).signum() == 0))
                return j;
        }
        return -1;
    }

    // fixed DB inconsistency due to import
    void fixDB() {
        // load all transactions
        final SortedList<Transaction> transactionList = new SortedList<>(mTransactionList,
                Comparator.comparing(Transaction::getTDate)
                        .reversed().thenComparing(Transaction::isSplit).reversed()  // put split first
                        .thenComparing(Transaction::getAccountID));
        final int nTrans = transactionList.size();
        mLogger.error("Total " + nTrans + " transactions");

        if (nTrans == 0)
            return; // nothing to do

        final List<Transaction> updateList = new ArrayList<>();  // transactions needs to be updated in DB
        final List<Transaction> unMatchedList = new ArrayList<>(); // (partially) unmatched transactions

        for (int i = 0; i < nTrans; i++) {
            Transaction t0 = transactionList.get(i);
            if (t0.isSplit()) {
                boolean needUpdate = false;
                int unMatched = 0;
                for (int s = 0; s < t0.getSplitTransactionList().size(); s++) {
                    // loop through split transaction list
                    SplitTransaction st = t0.getSplitTransactionList().get(s);
                    if (!st.isTransfer(t0.getAccountID()) || (st.getMatchID() > 0)) {
                        // either not a transfer, or already matched
                        continue;
                    }

                    // transfer split transaction
                    unMatched++;  // we've seen a unmatched
                    boolean modeAgg = false; // default not aggregate
                    int matchIdx = findMatchingTransaction(t0.getTDate(), t0.getAccountID(), -st.getCategoryID(),
                            st.getAmount().negate(), transactionList.subList(i+1, nTrans));
                    if (matchIdx < 0) {
                        // didn't find match, it's possible more than one split transaction transfering
                        // to the same account, the receiving account aggregates all into one transaction.
                        modeAgg = true; // try aggregate mode
                        BigDecimal cf = BigDecimal.ZERO;
                        for (int s1 = s; s1 < t0.getSplitTransactionList().size(); s1++) {
                            SplitTransaction st1 = t0.getSplitTransactionList().get(s1);
                            if (st1.getCategoryID().equals(st.getCategoryID()))
                                cf = cf.add(st1.getAmount().negate());
                        }
                        matchIdx = findMatchingTransaction(t0.getTDate(), t0.getAccountID(), -st.getCategoryID(),
                                cf, transactionList.subList(i+1, nTrans));
                    }
                    if (matchIdx >= 0) {
                        // found a match
                        needUpdate = true;
                        unMatched--;
                        Transaction t1 = transactionList.get(i+1+matchIdx);
                        if (modeAgg) {
                            // found a match via modeAgg
                            for (int s1 = s; s1 < t0.getSplitTransactionList().size(); s1++) {
                                SplitTransaction st1 = t0.getSplitTransactionList().get(s1);
                                if (st1.getCategoryID().equals(st.getCategoryID()))
                                    st1.setMatchID(t1.getID());
                            }
                        } else {
                            st.setMatchID(t1.getID());
                        }
                        t1.setMatchID(t0.getID(), st.getID());
                        updateList.add(t1);
                    }
                }
                if (needUpdate) {
                    updateList.add(t0);
                }
                if (unMatched != 0) {
                    unMatchedList.add(t0);
                }
            } else {
                // single transaction
                // loop through the remaining transaction for the same day
                if (!t0.isTransfer() || (t0.getMatchID() > 0)) {
                    continue;
                }
                int matchIdx = findMatchingTransaction(t0.getTDate(), t0.getAccountID(), -t0.getCategoryID(),
                        t0.cashFlow(), transactionList.subList(i+1, nTrans));
                if (matchIdx >= 0) {
                    Transaction t1 = transactionList.get(i+1+matchIdx);
                    t0.setMatchID(t1.getID(), -1);
                    t1.setMatchID(t0.getID(), -1);
                    updateList.add(t0);
                    updateList.add(t1);
                } else {
                    unMatchedList.add(t0);
                }
            }
        }

        int cnt = 0;
        for (Transaction t : updateList) {
            try {
                insertUpdateTransactionToDB(t);
                insertUpdateTransactionToMasterList(t);
                cnt++;
            } catch (SQLException e) {
                showExceptionDialog("FixDB Failed", "Failed updating Transaction",
                        t.getTDate() + "\n" + getAccountByID(t.getAccountID()).getName() + "\n"
                                + t.getAmount() + "\n"+ t.getDescription() + "\n", e);
                mLogger.error("SQLException " + e.getSQLState(), e);
            }
        }

        String message = "Total " + nTrans + " transactions processed." + "\n"
                + "Found " + updateList.size() + " matching transactions." + "\n"
                + "Updated " + cnt + " transactions." + "\n"
                + "Remain " + unMatchedList.size() + " unmatched transactions.";

        showInformationDialog("FixDB", "Information", message);
        mLogger.error(message);
    }

    // import data from QIF file
    void importQIF() {
        ChoiceDialog<String> accountChoiceDialog = new ChoiceDialog<>();
        accountChoiceDialog.getItems().add("");
        for (Account account : getAccountList(null, null, true))
            accountChoiceDialog.getItems().add(account.getName());
        accountChoiceDialog.setSelectedItem("");
        accountChoiceDialog.setTitle("Importing...");
        accountChoiceDialog.setHeaderText("Default account for transactions:");
        accountChoiceDialog.setContentText("Select default account");
        Optional<String> result = accountChoiceDialog.showAndWait();

        File file;
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("QIF", "*.QIF"));
        if (result.isPresent())
            fileChooser.setTitle("Import QIF file for default account: " + result.get());
        else
            fileChooser.setTitle("Import QIF file...");
        file = fileChooser.showOpenDialog(mPrimaryStage);
        if (file == null) {
            return;
        }

        QIFParser qifParser = new QIFParser(result.orElse(""));
        try {
            if (qifParser.parseFile(file) < 0) {
                mLogger.error("Failed to parse " + file);
            }
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }

        // process parsed records
        List<QIFParser.Account> aList = qifParser.getAccountList();
        for (QIFParser.Account qa : aList) {
            Account.Type at = null;
            switch (qa.getType()) {
                case "Bank":
                case "Cash":
                case "CCard":
                    at = Account.Type.SPENDING;
                    break;
                case "Mutual":
                case "Port":
                case "401(k)/403(b)":
                    at = Account.Type.INVESTING;
                    break;
                case "Oth A":
                    at = Account.Type.PROPERTY;
                    break;
                case "Oth L":
                    at = Account.Type.DEBT;
                    break;
                default:
                    break;
            }
            if (at != null) {
                insertUpdateAccountToDB(new Account(-1, at, qa.getName(), qa.getDescription(), false,
                        Integer.MAX_VALUE, BigDecimal.ZERO));
            } else {
                mLogger.error("Unknown account type: " + qa.getType()
                        + " for account [" + qa.getName() + "], skip.");
            }
        }
        initAccountList();

        List<QIFParser.Security> sList = qifParser.getSecurityList();
        for (QIFParser.Security s : sList) {
            insertUpdateSecurityToDB(new Security(-1, s.getSymbol(), s.getName(),
                    Security.Type.fromString(s.getType())));
        }
        initSecurityList();

        List<QIFParser.Price> pList = qifParser.getPriceList();
        HashMap<String, Integer> tickerIDMap = new HashMap<>();
        for (QIFParser.Price p : pList) {
            String security = p.getSecurity();
            Integer id = tickerIDMap.computeIfAbsent(security, this::getSecurityID);
            if (!insertUpdatePriceToDB(id, p.getDate(), p.getPrice(), 3)) {
                mLogger.error("Insert to PRICE failed with "
                        + security + "(" + id + ")," + p.getDate() + "," + p.getPrice());
            }
        }

        qifParser.getCategorySet().forEach(this::insertCategoryToDB);
        initCategoryList();

        qifParser.getTagSet().forEach(this::insertUpdateTagToDB);
        initTagList();

        for (QIFParser.BankTransaction bt : qifParser.getBankTransactionList()) {
            try {
                int rowID = insertTransactionToDB(bt);
                if (rowID < 0) {
                    mLogger.error("Failed to insert transaction: " + bt.toString());
                }
            } catch (SQLException e) {
                mLogger.error("SQLException " + e.getSQLState(), e);
            } catch (Exception e) {
                mLogger.error("Exception", e);
            }
        }

        for (QIFParser.TradeTransaction tt : qifParser.getTradeTransactionList()) {
            try {
                int rowID = insertTransactionToDB(tt);
                if (rowID < 0) {
                    mLogger.error("Failed to insert transaction: " + tt.toString());
                } else {
                    // insert transaction successful, insert price is it has one.
                    BigDecimal p = tt.getPrice();
                    if (p != null && p.signum() > 0) {
                        insertUpdatePriceToDB(getSecurityByName(tt.getSecurityName()).getID(), tt.getDate(), p, 0);
                    }
                }
            } catch (SQLException e) {
                mLogger.error("SQLException " + e.getSQLState(), e);
            }
        }

        initTransactionList();
        mLogger.info("Imported " + file);
    }

    // todo need to handle error gracefully
    String doBackup() {
        if (mConnection == null) {
            showExceptionDialog("Exception Dialog", "Null pointer exception", "mConnection is null", null);
            return null;
        }

        String backupFileName = null;
        try (PreparedStatement preparedStatement = mConnection.prepareStatement("Backup to ?")) {
            backupFileName = getBackupDBFileName();
            preparedStatement.setString(1, backupFileName);
            preparedStatement.execute();
            showInformationDialog("Backup Information", "Successful",
                    "Backup to " + backupFileName + " successful");
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
            showExceptionDialog("Exception Dialog", "SQLException", "Backup failed", e);
        }
        return backupFileName;
    }

    void changePassword() {
        if (mConnection == null) {
            showExceptionDialog("Exception Dialog", "Null pointer exception", "mConnection is null", null);
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText("Changing password will close current database file.");
        alert.setContentText("Do you want to proceed?");
        Optional<ButtonType> result = alert.showAndWait();
        if (!(result.isPresent() && (result.get() == ButtonType.OK)))
            return;  // do nothing

        List<String> passwords = showPasswordDialog(PasswordDialogController.MODE.CHANGE);
        if (passwords == null || passwords.size() != 2) {
            // action cancelled
            return;
        }

        String backupFileName = null; // if later this is not null, that means we have a backup
        int passwordChanged = 0;
        PreparedStatement preparedStatement = null;
        try {
            Class.forName("org.h2.Driver");
            String url = mConnection.getMetaData().getURL();
            File dbFile = new File(getDBFileNameFromURL(url));
            // backup database first
            backupFileName = doBackup();
            mConnection.close();
            mConnection = null;
            // change encryption password first
            ChangeFileEncryption.execute(dbFile.getParent(), dbFile.getName(), "AES", passwords.get(1).toCharArray(),
                    passwords.get(0).toCharArray(), true);
            passwordChanged++;  // changed 1
            // DBOWNER password has not changed yet.
            url += ";"+CIPHERCLAUSE+IFEXISTCLAUSE;
            mConnection = DriverManager.getConnection(url, DBOWNER, passwords.get(0) + " " + passwords.get(1));
            preparedStatement = mConnection.prepareStatement("Alter User " + DBOWNER + " set password ?");
            preparedStatement.setString(1, passwords.get(0));
            preparedStatement.execute();
            passwordChanged++;
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
            showExceptionDialog("Exception", "SQLException", e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            mLogger.error("IllegalArgumentException", e);
            showExceptionDialog("Exception", "IllegalArgumentException", e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            mLogger.error("ClassNotFoundException", e);
            showExceptionDialog("Exception", "ClassNotFoundException", e.getMessage(), e);
        } finally {
            try {
                if (preparedStatement != null)
                    preparedStatement.close();
            } catch (SQLException e) {
                mLogger.error("SQLException " + e.getSQLState(), e);
                showExceptionDialog("Exception", "SQLException", e.getMessage(), e);
            }
            if (passwordChanged == 1) {
                showExceptionDialog("Exception", "Change password failed!",
                        "Quit now and restore database:\nunzip " + backupFileName, null);
            }
        }
    }

    // return the backup DBFileName
    // return null if mConnection is null or mConnection has a bad formatted url.
    // TODO: 6/7/16  need to add functionality to change backup settings
    //   backup location
    //   filename pattern
    private String getBackupDBFileName() throws SQLException {
        return getDBFileNameFromURL(mConnection.getMetaData().getURL()) + "Backup"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + ".zip";
    }

    // return the file path of current open connection (without .h2.db postfix)
    // null is returned if connection is null, or url doesn't parse correctly
    // url is in the format of jdbc:h2:filename;key=value...
    private String getDBFileNameFromURL(String url) throws IllegalArgumentException {
        int index = url.indexOf(';');
        if (index > 0)
            url = url.substring(0, index);  // remove anything on and after first ';'
        if (!url.startsWith(URLPREFIX)) {
            throw new IllegalArgumentException("Bad formatted url: " + url
                    + ". Url should start with '" + URLPREFIX + "'");
        }
        return url.substring(URLPREFIX.length());
    }

    // create a new database
    void openDatabase(boolean isNew, String dbName, String password) {
        File file;
        if (dbName != null) {
            if (!dbName.endsWith(DBPOSTFIX))
                dbName += DBPOSTFIX;
            file = new File(dbName);
        } else {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DB", "*" + DBPOSTFIX));
            String title;
            if (isNew) {
                title = "Create a new " + MainApp.class.getPackage().getImplementationTitle() + " database...";
            } else {
                title = "Open an existing " + MainApp.class.getPackage().getImplementationTitle() + " database...";
            }
            fileChooser.setTitle(title);
            if (isNew) {
                file = fileChooser.showSaveDialog(mPrimaryStage);
            } else {
                file = fileChooser.showOpenDialog(mPrimaryStage);
            }

            if (file == null) {
                return;
            }
            dbName = file.getAbsolutePath();
            if (!dbName.endsWith(DBPOSTFIX)) {
                dbName += DBPOSTFIX;
                file = new File(dbName);
            }
        }
        // we have enough information to open a new db, close the current db now
        closeConnection();
        final String appName = MainApp.class.getPackage().getImplementationTitle();
        final String appVersion = MainApp.class.getPackage().getImplementationVersion();

        mPrimaryStage.setTitle(appName);
        setCurrentAccount(null);
        initializeLists();

        // Trying to create a new db, but unable to delete existing same name db
        if (isNew && file.exists() && !file.delete()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle("Unable to delete " + dbName);
            alert.showAndWait();
            return;
        }

        // trim the POSTFIX
        if (dbName.endsWith(DBPOSTFIX)) {
            dbName = dbName.substring(0, dbName.length()-DBPOSTFIX.length());
        }

        if (password == null) {
            List<String> passwords = showPasswordDialog(
                    isNew ? PasswordDialogController.MODE.NEW : PasswordDialogController.MODE.ENTER);
            if (passwords == null || passwords.size() == 0 || passwords.get(0) == null) {
                if (isNew) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.initOwner(mPrimaryStage);
                    alert.setTitle("Password not set");
                    alert.setHeaderText("Need a password to continue...");
                    alert.showAndWait();
                }
                return;
            }
            password = passwords.get(0);
        }

        try {
            String url = URLPREFIX+dbName+";"+CIPHERCLAUSE;
            if (!isNew) {
                // open existing
                url += IFEXISTCLAUSE;
            }
            // we use same password for file encryption and admin user
            Class.forName("org.h2.Driver");
            mConnection = DriverManager.getConnection(url, DBOWNER, password + ' ' + password);
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);

            int errorCode = e.getErrorCode();
            // 90049 -- bad encryption password
            // 28000 -- wrong user name or password
            // 90020 -- Database may be already in use: locked by another process
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            switch (errorCode) {
                case 90049:
                case 28000:
                    alert.setTitle("Bad password");
                    alert.setHeaderText("Wrong password for " + dbName);
                    break;
                case 90020:
                    alert.setTitle("Filed locked");
                    alert.setHeaderText("File may be already in use, locked by another process.");
                    break;
                case 90013:
                    alert.setTitle("File does not exist.");
                    alert.setHeaderText("File may be moved or deleted.");
                    break;
                default:
                    alert.setTitle("SQL Error");
                    alert.setHeaderText("Error Code: " + errorCode);
                    alert.setContentText(SQLExceptionToString(e));
                    break;
            }
            alert.showAndWait();
        } catch (ClassNotFoundException e){
            showExceptionDialog("Exception", "ClassNotFoundException", e.getMessage(), e);
        }

        if (mConnection == null) {
            return;
        }

        // save opened DB hist
        putOpenedDBNames(updateOpenedDBNames(getOpenedDBNames(), dbName));

        if (isNew) {
            initDBStructure();
        } else {
            // if SETTING table does exist, the following call will do nothing.
            // if SETTING table not present, this is an old version 0 database
            // create SETTINGS table and set version number to be 0.
            createSettingsTable(0);
        }

        final int dbVersion = getDBVersion();
        if (dbVersion > DBVERSIONVALUE) {
            showInformationDialog("Version Mismatch",
                    "This version of " + appName + " is out of date",
                    "Database Version Number " + dbVersion + ", " + appName + " " + appVersion + " " +
                            "needs Database Version " + DBVERSIONVALUE + ", please update " + appName + ".");
            closeConnection();
            return;
        } else if (dbVersion < DBVERSIONVALUE) {
            // backup first
            String backupFileName = doBackup();

            // run update
            try {
                updateDBVersion(dbVersion, DBVERSIONVALUE);
                showInformationDialog("Database Version Updated",
                        "Database Version Updated from " + dbVersion + " to " + DBVERSIONVALUE,
                        "Your database was updated from version " + dbVersion + " to " + DBVERSIONVALUE + ". " +
                                "The old database was saved in " + backupFileName);
            } catch (SQLException e) {
                // Failed
                mLogger.error("SQLException " + e.getSQLState(), e);

                showExceptionDialog("Database Version Update Failed",
                        "Database Version Update Failed",
                        "Your database failed to update from version " + dbVersion + " to " + DBVERSIONVALUE +
                                ". The old database was saved in " + backupFileName, e);
                closeConnection();
                return;
            } catch (IllegalArgumentException e) {
                // version not supported
                mLogger.error("IllegalArgumentException", e);
                showExceptionDialog("Database Version Update Failed",
                        "Database Version Update not supported",
                        e.getMessage() + " " + "Database version update from " + dbVersion +
                                " to " + DBVERSIONVALUE + " not supported. " +
                                "The old database was saved in " + backupFileName, e);
            }
        }

        initializeLists();

        mPrimaryStage.setTitle(appName+ " " + dbName);
    }

    // When oldV < newV, update database from version oldV to version newV.
    // when oldV == newV, no-op
    // when oldV > newV, error
    // return true for success and false for failure
    // Otherwise, the behavior is not defined.
    private void updateDBVersion(int oldV, int newV) throws SQLException, IllegalArgumentException {

        if (oldV == newV)
            return;  // no op

        if (oldV > newV) {
            throw new IllegalArgumentException("updateDBVersion called with unsupported versions, " +
                    "old version: " + oldV + ", new version: " + newV + ".");
        }

        // need to run this to update DBVERSION
        final String mergeSQL = "merge into SETTINGS (NAME, VALUE) values ('" + DBVERSIONNAME + "', " + newV + ")";

        if (newV == 3) {
            if (oldV < 2)
                updateDBVersion(oldV, 2); // bring DB version to 2

            // cleared column was populated with int value of ascii code
            // setup new STATUS column and set value according to the cleared column
            // then drop cleared column
            final String updateSQL0 = "alter table TRANSACTIONS add (STATUS varchar("
                    + TRANSACTIONSTATUSLEN + ") NOT NULL default '" + Transaction.Status.UNCLEARED.name() + "')";
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
            try (Statement statement = mConnection.createStatement()) {
                statement.executeUpdate(updateSQL0);
                statement.executeUpdate(updateSQL1);
                statement.executeUpdate(updateSQL2);
                statement.executeUpdate(updateSQL3);
                statement.executeUpdate(mergeSQL);
            }
        } else if (newV == 2) {
            // update to version 1 first
            if (oldV < 1)
                updateDBVersion(oldV, 1);

            final String alterSQL = "alter table SAVEDREPORTS add (" +
                    "PAYEECONTAINS varchar(80) NOT NULL default '', " +
                    "PAYEEREGEX boolean NOT NULL default FALSE, " +
                    "MEMOCONTAINS varchar(80) NOT NULL default '', " +
                    "MEMOREGEX boolean NOT NULL default FALSE)";
            try (Statement statement = mConnection.createStatement()) {
                statement.executeUpdate(alterSQL);
                statement.executeUpdate(mergeSQL);
            }
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
            try (Statement statement = mConnection.createStatement()) {
                statement.executeUpdate(updateSQL0);
                statement.executeUpdate(updateSQL1);
                statement.executeUpdate(mergeSQL);
            }
        } else {
            throw new IllegalArgumentException("updateDBVersion called with unsupported versions, " +
                    "old version: " + oldV + ", new version: " + newV + ".");
        }
    }

    TreeSet<String> getPayeeSet() {
        TreeSet<String> allPayees = new TreeSet<>(Comparator.comparing(String::toLowerCase));
        for (Transaction t : mTransactionList) {
            String payee = t.getPayee();
            if (payee != null && payee.length() > 0) {
                allPayees.add(t.getPayee());
            }
        }
        return allPayees;
    }

    void initializeLists() {
        // initialize
        initCategoryList();
        initTagList();
        initSecurityList();
        initTransactionList();
        initAccountList();  // this should be done after securitylist and categorylist are loaded
        initReminderMap();
        initReminderTransactionList();
    }

    private void sqlCreateTable(String createSQL) {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = mConnection.prepareStatement(createSQL);
            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (NullPointerException e) {
            mLogger.error("Null mConnection");
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        } finally {
            try {
                if (preparedStatement != null)
                    preparedStatement.close();
            } catch (SQLException e) {
                mLogger.error("SQLException " + e.getSQLState(), e);
            }
        }
    }

    // update Status column in Transaction Table for the given tid.
    // return true for success and false for failure.
    boolean setTransactionStatusInDB(int tid, Transaction.Status s) {
        boolean savepointSetHere = false;
        try (Statement statement = mConnection.createStatement()) {
            savepointSetHere = setDBSavepoint();
            statement.executeUpdate("update TRANSACTIONS set STATUS = '" + s.name() + "' where ID = " + tid);
            if (savepointSetHere)
                commitDB();
            return true;
        } catch (SQLException e) {
            if (savepointSetHere) {
                try {
                    rollbackDB();
                    mLogger.error("SQLException: " + e.getSQLState(), e);
                    showExceptionDialog("Database Error", "Unable update transaction status",
                            SQLExceptionToString(e), e);
                } catch (SQLException e1) {
                    mLogger.error("SQLException: " + e1.getSQLState(), e1);
                    showExceptionDialog("Database Error", "Unable to rollback",
                            SQLExceptionToString(e1), e1);
                }
            }
        }
        return false;
    }

    // Alter (including insert and delete a transaction, both in DB and in MasterList.
    // It also perform various consistency tasks.
    // if oldT is null, the newT is inserted
    // if newT is null, the oldT is deleted
    // otherwise, oldT is updated with information from newT,
    //   newT.getID() should be return the same value as oldT.getID()
    // returns true for success, false for failure
    boolean alterTransaction(Transaction oldT, Transaction newT, List<SecurityHolding.MatchInfo> newMatchInfoList) {
        // there are four possibilities each for oldT and newT:
        // null, simple transaction, a transfer transaction, a split transaction
        // thus there are 4x4 = 16 different situations
        if (oldT == null && newT == null)
            return true; // nothing to do

        if (oldT != null && oldT.getMatchID() > 0) {
            // oldT is a linked transaction,
            if (oldT.getMatchSplitID() > 0) {
                showWarningDialog("Linked to A Split Transaction",
                        "Linked to a split transaction",
                        "Please edit the linked split transaction.");
                return false;
            }
            Transaction.TradeAction oldTA = oldT.getTradeAction();
            if (oldTA == Transaction.TradeAction.XIN || oldTA == Transaction.TradeAction.XOUT) {
                Transaction oldXferT = getTransactionByID(oldT.getMatchID());
                if (oldXferT != null) {
                    Transaction.TradeAction oldXferTA = oldXferT.getTradeAction();
                    if (oldXferTA != Transaction.TradeAction.XIN && oldXferTA != Transaction.TradeAction.XOUT) {
                        showWarningDialog("Linked to An Investing Transaction",
                                "Linked to an investing transaction",
                                "Please edit the linked investing transaction.");
                        return false;
                    }
                }
            }
        }

        final Set<Transaction> updateTSet = new HashSet<>();
        final Set<Integer> deleteTIDSet = new HashSet<>();
        final Set<Integer> accountIDSet = new HashSet<>();
        Transaction newLinkedT = null;
        Security security = null;

        if (newT != null && !newT.isSplit() && -newT.getCategoryID() >= MIN_ACCOUNT_ID) {
            // handle the case newT is a split transaction
            final Transaction.TradeAction xferTA = newT.TransferTradeAction();
            if (xferTA == null) {
                showWarningDialog("Warning", "Inconsistent Information",
                        "Transaction has a transfer account but without proper TradeAction.");
                return false;
            }

            // get the payee information
            String newPayee;
            switch (newT.getTradeAction()) {
                case DEPOSIT:
                case WITHDRAW:
                case XIN:
                case XOUT:
                    newPayee = newT.getPayee();
                    break;
                default:
                    // put security name information as payee for transfer transaction
                    newPayee = newT.getSecurityName();
                    break;
            }

            newLinkedT = new Transaction(-newT.getCategoryID(), newT.getTDate(), xferTA, -newT.getAccountID());
            newLinkedT.setID(newT.getMatchID());
            newLinkedT.setAmount(newT.getAmount());
            newLinkedT.setMemo(newT.getMemo());
            newLinkedT.setPayee(newPayee);
        }

        // ready to do database work now
        try {
            if (!setDBSavepoint()) {
               showWarningDialog("Unexpected situation", "Database savepoint already set?",
                        "Please restart application");
               return false;
            }

            if (newT != null) {
                // either adding a new transaction, or modifying an old one
                final int newTID = insertUpdateTransactionToDB(newT);

                updateTSet.add(newT);
                accountIDSet.add(newT.getAccountID());

                // insert/update MatchInfo to database
                putMatchInfoList(newTID, newMatchInfoList);

                // handle transfer in split transaction
                for (SplitTransaction st : newT.getSplitTransactionList()) {
                    if (-st.getCategoryID() >= MIN_ACCOUNT_ID) {
                        // this is a transfer
                        Transaction stLinkedT = new Transaction(-st.getCategoryID(), newT.getTDate(),
                                st.getAmount().compareTo(BigDecimal.ZERO) >= 0 ?
                                        Transaction.TradeAction.XOUT : Transaction.TradeAction.XIN,
                                -newT.getAccountID());
                        stLinkedT.setID(st.getMatchID());
                        stLinkedT.setAmount(st.getAmount().abs());
                        stLinkedT.setPayee(st.getPayee());
                        stLinkedT.setMemo(st.getMemo());
                        stLinkedT.setPayee(newT.getPayee());
                        stLinkedT.setMatchID(newTID, st.getID());

                        st.setMatchID(insertUpdateTransactionToDB(stLinkedT));

                        updateTSet.add(stLinkedT);
                        accountIDSet.add(stLinkedT.getAccountID());
                    } else {
                        st.setMatchID(0);
                    }
                }

                if (newLinkedT != null) {
                    newLinkedT.setMatchID(newTID, 0);
                    newT.setMatchID(insertUpdateTransactionToDB(newLinkedT),0);

                    updateTSet.add(newLinkedT);
                    accountIDSet.add(newLinkedT.getAccountID());
                }

                // update price for involved security
                security = newT.getSecurityName() == null ? null :
                        getSecurityByName(newT.getSecurityName());
                final BigDecimal price = newT.getPrice();
                if (Transaction.hasQuantity(newT.getTradeAction())
                        && (security != null) && (price != null)
                        && (price.compareTo(BigDecimal.ZERO) != 0)) {
                    insertUpdatePriceToDB(security.getID(), newT.getTDate(), price, 0);
                }
            }

            if (oldT != null) {
                if (newT == null) {
                    // clear MatchInfoList for oldT, if not replaced by newT
                    putMatchInfoList(oldT.getID(), new ArrayList<>());
                }

                // handle transfer in splittransaction
                for (SplitTransaction st : oldT.getSplitTransactionList()) {
                    final int stLinkedTID = st.getMatchID();

                    boolean updated = false;
                    for (Transaction t : updateTSet) {
                        if (t.getID() == stLinkedTID) {
                            updated = true;
                            break;
                        }
                    }
                    if (!updated) {
                        deleteTransactionFromDB(stLinkedTID);
                        deleteTIDSet.add(stLinkedTID);
                        accountIDSet.add(-st.getCategoryID());
                    }
                }

                // handle transfer
                final int oldLinkedTID = oldT.getMatchID();
                if (oldLinkedTID > 0) {
                    boolean updated = false;
                    for (Transaction t : updateTSet) {
                        if (t.getID() == oldLinkedTID) {
                            updated = true;
                            break;
                        }
                    }
                    if (!updated) {
                        deleteTransactionFromDB(oldLinkedTID);
                        deleteTIDSet.add(oldLinkedTID);
                        accountIDSet.add(-oldT.getCategoryID());
                    }
                }

                deleteTransactionFromDB(oldT.getID());
                deleteTIDSet.add(oldT.getID());
                accountIDSet.add(oldT.getAccountID());
            }

            // now commit
            commitDB();

            for (Integer tid : deleteTIDSet) {
                deleteTransactionFromMasterList(tid);
            }
            for (Transaction t : updateTSet) {
                insertUpdateTransactionToMasterList(t);
            }
            if (security != null) {
                updateAccountBalance(security);
            }
            for (Integer aid : accountIDSet) {
                updateAccountBalance(aid);
            }
            return true;

        } catch (SQLException e) {
            try {
                mLogger.error("SQLException: " + e.getSQLState(), e);
                rollbackDB();
            } catch (SQLException e1) {
                mLogger.error("SQLException: " + e1.getSQLState(), e1);
                showExceptionDialog("Database Error", "Unable to rollback to savepoint",
                        SQLExceptionToString(e1), e1);
            }
        } finally {
            try {
                releaseDBSavepoint();
            } catch (SQLException e) {
                mLogger.error("SQLException: " + e.getSQLState(), e);
                showExceptionDialog("Database Error",
                        "Unable to release savepoint and set DB autocommit",
                        SQLExceptionToString(e), e);
            }
        }

        return false;
    }

    // create SETTINGS table and populate DBVERSION
    private void createSettingsTable(int dbVersion) {
        try (ResultSet resultSet = mConnection.getMetaData().getTables(null, null,
                "SETTINGS", new String[]{"TABLE"});
             Statement statement = mConnection.createStatement()) {
            if (!resultSet.next()) {
                // Settings table is not created yet, create it now
                sqlCreateTable("create table SETTINGS (" +
                        "NAME varchar(32) UNIQUE NOT NULL," +
                        "VALUE integer NOT NULL," +
                        "primary key (NAME))");
                statement.executeUpdate("merge into SETTINGS (NAME, VALUE) values (" +
                        "'" + DBVERSIONNAME + "', " + dbVersion + ")");
            }
        } catch (SQLException e) {
            mLogger.error("SQLException: " + e.getSQLState(), e);
            showExceptionDialog("Exception", "Database Exception",
                    "Failed to create SETTINGS table",e);
        }
    }

    private int getDBVersion() {
        String sqlCmd = "select VALUE from SETTINGS where NAME = '" + DBVERSIONNAME + "'";
        int dbVersion = 0;
        try (Statement statement = mConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            if (resultSet.next())
                dbVersion = resultSet.getInt(1);
        } catch (SQLException e) {
            mLogger.error("SQLException: " + e.getSQLState(), e);
        }
        return dbVersion;
    }

    // initialize database structure
    private void initDBStructure() {
        if (mConnection == null)
            return;

        // create Settings Table first.

        createSettingsTable(DBVERSIONVALUE);

        // Accounts table
        // ID starts from 1
        String sqlCmd = "create table ACCOUNTS ("
                // make sure to start from MIN_ACCOUNT_ID
                + "ID integer NOT NULL AUTO_INCREMENT (" + MIN_ACCOUNT_ID + "), "
                + "TYPE varchar (10) NOT NULL, "
                + "NAME varchar(" + ACCOUNTNAMELEN + ") UNIQUE NOT NULL, "
                + "DESCRIPTION varchar(" + ACCOUNTDESCLEN + ") NOT NULL, "
                + "HIDDENFLAG boolean NOT NULL, "
                + "DISPLAYORDER integer NOT NULL, "
                + "primary key (ID));";
        sqlCreateTable(sqlCmd);

        // insert Deleted account
        insertUpdateAccountToDB(new Account(MIN_ACCOUNT_ID-1, Account.Type.SPENDING, DELETED_ACCOUNT_NAME,
                "Placeholder for the Deleted Account", true, Integer.MAX_VALUE, BigDecimal.ZERO));

        // Security Table
        // ID starts from 1
        sqlCmd = "create table SECURITIES ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), "  // make sure starts with 1
                + "TICKER varchar(" + SECURITYTICKERLEN + ") NOT NULL, "
                + "NAME varchar(" + SECURITYNAMELEN + ") UNIQUE NOT NULL, "
                + "TYPE varchar(16) NOT NULL, "
                + "primary key (ID));";
        sqlCreateTable(sqlCmd);

        // Price Table
        sqlCmd = "create table PRICES ("
                + "SECURITYID integer NOT NULL, "
                + "DATE date NOT NULL, "
                + "PRICE decimal(" + PRICE_TOTAL_LEN + "," + PRICE_FRACTION_LEN + "),"
                + "PRIMARY KEY (SECURITYID, DATE));";
        sqlCreateTable(sqlCmd);

        // Category Table
        // ID starts from 1
        sqlCmd = "create table CATEGORIES ("
                // make sure to start from MIN_CATEGORY_ID
                + "ID integer NOT NULL AUTO_INCREMENT (" + MIN_CATEGORY_ID + "), "
                + "NAME varchar(" + CATEGORYNAMELEN + ") UNIQUE NOT NULL, "
                + "DESCRIPTION varchar(" + CATEGORYDESCLEN + ") NOT NULL, "
                + "INCOMEFLAG boolean, "
                + "TAXREFNUM integer, "
                + "BUDGETAMOUNT decimal(20,4), "
                + "primary key (ID))";
        sqlCreateTable(sqlCmd);

        // SplitTransaction
        // ID starts from 1
        sqlCmd = "create table SPLITTRANSACTIONS ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), "
                + "TRANSACTIONID integer NOT NULL, "
                + "CATEGORYID integer, "
                + "TAGID integer, "
                + "PAYEE varchar (" + TRANSACTIONPAYEELEN + "), "
                + "MEMO varchar (" + TRANSACTIONMEMOLEN + "), "
                + "AMOUNT decimal(" + AMOUNT_TOTAL_LEN + "," + AMOUNT_FRACTION_LEN + "), "
                + "PERCENTAGE decimal(20,4), "
                + "MATCHTRANSACTIONID integer, "
                + "MATCHSPLITTRANSACTIONID integer, "
                + "primary key (ID));";
        sqlCreateTable(sqlCmd);

        // Addresses table
        // ID starts from 1
        sqlCmd = "create table ADDRESSES ("
                + "ID integer not null auto_increment (1), "
                + "LINE0 varchar(" + ADDRESSLINELEN + "), "
                + "LINE1 varchar(" + ADDRESSLINELEN + "), "
                + "LINE2 varchar(" + ADDRESSLINELEN + "), "
                + "LINE3 varchar(" + ADDRESSLINELEN + "), "
                + "LINE4 varchar(" + ADDRESSLINELEN + "), "
                + "LINE5 varchar(" + ADDRESSLINELEN + "), "
                + "primary key (ID));";
        sqlCreateTable(sqlCmd);

        // amortlines table
        // ID starts from 1
        sqlCmd = "create table AMORTIZATIONLINES ("
                + "ID integer not null auto_increment (1), "
                + "LINE0 varchar(" + AMORTLINELEN + "), "
                + "LINE1 varchar(" + AMORTLINELEN + "), "
                + "LINE2 varchar(" + AMORTLINELEN + "), "
                + "LINE3 varchar(" + AMORTLINELEN + "), "
                + "LINE4 varchar(" + AMORTLINELEN + "), "
                + "LINE5 varchar(" + AMORTLINELEN + "), "
                + "LINE6 varchar(" + AMORTLINELEN + "), "
                + "primary key (ID)); ";
        sqlCreateTable(sqlCmd);

        // Transactions
        // ID starts from 1
        sqlCmd = "create table TRANSACTIONS ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), " // make sure to start with 1
                + "ACCOUNTID integer NOT NULL, "
                + "DATE date NOT NULL, "
                + "ADATE date, "
                + "AMOUNT decimal(20,4), "
                + "STATUS varchar(" + TRANSACTIONSTATUSLEN + ") not null, " // status
                + "CATEGORYID integer, "   // positive for category ID, negative for transfer account id
                + "TAGID integer, "
                + "MEMO varchar(" + TRANSACTIONMEMOLEN + "), "
                + "REFERENCE varchar (" + TRANSACTIONREFLEN + "), "  // reference or check number as string
                + "PAYEE varchar (" + TRANSACTIONPAYEELEN + "), "
                + "SPLITFLAG boolean, "
                + "ADDRESSID integer, "
                + "AMORTIZATIONID integer, "
                + "TRADEACTION varchar(" + TRANSACTIONTRADEACTIONLEN + "), "
                + "SECURITYID integer, "
                + "PRICE decimal(" + PRICE_TOTAL_LEN + "," + PRICE_FRACTION_LEN + "), "
                + "QUANTITY decimal(" + QUANTITY_TOTAL_LEN + "," + QUANTITY_FRACTION_LEN + "), "
                + "OLDQUANTITY decimal(" + QUANTITY_TOTAL_LEN + "," + QUANTITY_FRACTION_LEN + "), "  // used in stock split transactions
                + "TRANSFERREMINDER varchar(" + TRANSACTIONTRANSFERREMINDERLEN + "), "
                + "COMMISSION decimal(20,4), "
                + "AMOUNTTRANSFERRED decimal(20,4), "
                + "MATCHTRANSACTIONID integer, "   // matching transfer transaction id
                + "MATCHSPLITTRANSACTIONID integer, "  // matching split
                + "primary key (ID));";
        sqlCreateTable(sqlCmd);

        // LotMATCH table
        sqlCmd = "create table LOTMATCH ("
                + "TransID integer NOT NULL, "
                + "MatchID integer NOT NULL, "
                + "MatchQuantity decimal(" + QUANTITY_TOTAL_LEN + ","  + QUANTITY_FRACTION_LEN + "), "
                + "Constraint UniquePair unique (TransID, MatchID));";
        sqlCreateTable(sqlCmd);

        // SavedReports table
        sqlCmd = "create table SAVEDREPORTS ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), "  // make sure to start with 1
                + "NAME varchar (" + SAVEDREPORTSNAMELEN + ") UNIQUE NOT NULL, "       // name of the report
                + "TYPE varchar (16) NOT NULL, "              // type of the report
                + "DATEPERIOD varchar (16) NOT NULL, "        // enum for dateperiod
                + "SDATE date NOT NULL, "                              // customized start date
                + "EDATE date NOT NULL, "                              // customized start date
                + "FREQUENCY varchar (16) NOT NULL, "                 // frequency enum
                + "PAYEECONTAINS varchar (80), "
                + "PAYEEREGEX boolean, "
                + "MEMOCONTAINS varchar (80), "
                + "MEMOREGEX boolean);";
        sqlCreateTable(sqlCmd);

        // SavedReportDetails table
        sqlCmd = "create table SAVEDREPORTDETAILS ("
                + "REPORTID integer NOT NULL, "
                + "ITEMNAME varchar(16) NOT NULL, "
                + "ITEMVALUE varchar(16) NOT NULL);";
        sqlCreateTable(sqlCmd);

        // Tag table
        sqlCmd = "create table TAGS ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), " // starting 1
                + "NAME varchar(20) UNIQUE NOT NULL, "
                + "DESCRIPTION varchar(80) NOT NULL, "
                + "primary key(ID));";
        sqlCreateTable(sqlCmd);

        // Reminders table
        sqlCmd = "create table REMINDERS ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), "  // make sure to start with 1
                + "TYPE varchar(" + 12 + "), "
                + "PAYEE varchar (" + TRANSACTIONPAYEELEN + "), "
                + "AMOUNT decimal(20, 4), "
                + "ESTCOUNT integer, "
                + "ACCOUNTID integer NOT NULL, "
                + "CATEGORYID integer, "
                + "TRANSFERACCOUNTID integer, "
                + "TAGID integer, "
                + "MEMO varchar(" + TRANSACTIONMEMOLEN + "), "
                + "STARTDATE date NOT NULL, "
                + "ENDDATE date, "
                + "BASEUNIT varchar(8) NOT NULL, "
                + "NUMPERIOD integer NOT NULL, "
                + "ALERTDAYS integer NOT NULL, "
                + "ISDOM boolean NOT NULL, "
                + "ISFWD boolean NOT NULL, "
                + "primary key (ID));";
        sqlCreateTable(sqlCmd);

        // ReminderTransactions table
        sqlCmd = "create table REMINDERTRANSACTIONS ("
                + "REMINDERID integer NOT NULL, "
                + "DUEDATE date, "
                + "TRANSACTIONID integer)";
        sqlCreateTable(sqlCmd);
    }

    static String SQLExceptionToString(SQLException e) {
        StringBuilder s = new StringBuilder();
        while (e != null) {
            s.append("--- SQLException ---" + "  SQL State: ").append(e.getSQLState())
                    .append("  Message:   ").append(e.getMessage()).append("\n");
            e = e.getNextException();
        }
        return s.toString();
    }

    // For SELL or CVTSHRT transactions, return the list of capital gain items
    // incomplete list will be returned if there is a data inconsistency.
    //
    // for other type transactions, null is returned

    List<CapitalGainItem> getCapitalGainItemList(Transaction transaction) {
        Transaction.TradeAction ta = transaction.getTradeAction();
        if (!ta.equals(Transaction.TradeAction.SELL) && ta.equals(Transaction.TradeAction.CVTSHRT))
            return null;

        Account account = getAccountByID(transaction.getAccountID());
        List<SecurityHolding> securityHoldingList = updateAccountSecurityHoldingList(account,
                transaction.getTDate(), transaction.getID());
        List<SecurityHolding.MatchInfo> miList = getMatchInfoList(transaction.getID());
        int scale = transaction.getAmount().scale();
        List<CapitalGainItem> capitalGainItemList = new ArrayList<>();
        for (SecurityHolding securityHolding : securityHoldingList) {
            if (!securityHolding.getSecurityName().equals(transaction.getSecurityName()))
                continue;  // different security, skip

            // we have the right security holding here now
            BigDecimal remainCash = transaction.getAmount();
            BigDecimal remainQuantity = transaction.getQuantity();
            FilteredList<SecurityHolding.LotInfo> lotInfoList = new FilteredList<>(securityHolding.getLotInfoList());
            if (!miList.isEmpty()) {
                // we have a matchInfo list,
                Set<Integer> matchTIDSet = new HashSet<>();
                for (SecurityHolding.MatchInfo mi : miList)
                    matchTIDSet.add(mi.getMatchTransactionID());
                lotInfoList.setPredicate(li -> matchTIDSet.contains(li.getTransactionID()));
            }
            for (SecurityHolding.LotInfo li : securityHolding.getLotInfoList()) {
                BigDecimal costBasis;
                BigDecimal proceeds;
                BigDecimal matchQuantity;
                Transaction matchTransaction;

                matchTransaction = getTransactionByID(li.getTransactionID());
                if (li.getQuantity().compareTo(remainQuantity) < 0) {
                    // li doesn't have enough to offset all.
                    matchQuantity = li.getQuantity();

                    costBasis = li.getCostBasis();
                    proceeds = remainCash.multiply(li.getQuantity()).divide(remainQuantity,
                            scale, RoundingMode.HALF_UP);
                    remainCash = remainCash.subtract(proceeds);
                    remainQuantity = remainQuantity.subtract(matchQuantity);
                } else {
                    // li can offset all
                    matchQuantity = remainQuantity;
                    costBasis = li.getCostBasis().multiply(remainQuantity).divide(li.getQuantity(),
                            scale, RoundingMode.HALF_UP);
                    proceeds = remainCash;
                    remainQuantity = BigDecimal.ZERO;
                    remainCash = BigDecimal.ZERO;
                }
                if (ta.equals(Transaction.TradeAction.SELL))
                    capitalGainItemList.add(new CapitalGainItem(transaction, matchTransaction, matchQuantity,
                            costBasis, proceeds));
                else
                    capitalGainItemList.add(new CapitalGainItem(transaction, matchTransaction, matchQuantity,
                            proceeds, costBasis));

                if (remainQuantity.compareTo(BigDecimal.ZERO) == 0)
                    break; // done
            }
        }

        return capitalGainItemList;
    }

    // take a Transaction input (with SELL or CVTSHRT), compute the realize gain
    BigDecimal calcRealizedGain(Transaction transaction) {
        BigDecimal realizedGain = BigDecimal.ZERO;
        for (CapitalGainItem cgi : getCapitalGainItemList(transaction)) {
            realizedGain = realizedGain.add(cgi.getProceeds()).subtract(cgi.getCostBasis());
        }
        return realizedGain;
    }

    ObservableList<Transaction> getFilteredTransactionList(String searchString) {
        // search is case insensitive
        final String lowerSearchString = searchString.toLowerCase();

        // category name match
        Set<Integer> categoryOrTransferAccountNameMatchIDSet = new HashSet<>();
        getCategoryList().forEach(c -> {
            if (c.getName().toLowerCase().contains(lowerSearchString))
                categoryOrTransferAccountNameMatchIDSet.add(c.getID());
        });

        // match in transfer account name
        getAccountList(null, false, true).forEach(a -> {
            if (a.getName().toLowerCase().contains(lowerSearchString))
                categoryOrTransferAccountNameMatchIDSet.add(-a.getID());
        });

        // match in Tag names
        Set<Integer> tagNameMatchIDSet = new HashSet<>();
        getTagList().forEach(tag -> {
            if (tag.getName().toLowerCase().contains(lowerSearchString))
                tagNameMatchIDSet.add(tag.getID());
        });

        Predicate<Transaction> filterCriteria = transaction -> {
            String lowerPayee = transaction.getPayee();
            if (lowerPayee == null)
                lowerPayee = "";
            else
                lowerPayee = lowerPayee.toLowerCase();
            String lowerSN = transaction.getSecurityName();
            if (lowerSN == null)
                lowerSN = "";
            else
                lowerSN = lowerSN.toLowerCase();
            String lowerMemo = transaction.getMemo();
            if (lowerMemo == null)
                lowerMemo = "";
            else
                lowerMemo = lowerMemo.toLowerCase();
            Transaction.TradeAction ta = transaction.getTradeAction();
            String lowerTAN = "";
            if (ta != null)
                lowerTAN = ta.toString().toLowerCase();

            return (categoryOrTransferAccountNameMatchIDSet.contains(transaction.getCategoryID())
                    || lowerPayee.contains(lowerSearchString)
                    || lowerSN.contains(lowerSearchString)
                    || lowerMemo.contains(lowerSearchString)
                    || lowerTAN.contains(lowerSearchString)
                    || tagNameMatchIDSet.contains(transaction.getTagID()));
        };

        return new FilteredList<>(mTransactionList, filterCriteria);
    }

    // init the main layout
    private void initMainLayout() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/MainLayout.fxml"));
            mPrimaryStage.setScene(new Scene(loader.load()));
            mPrimaryStage.show();
            ((MainController) loader.getController()).setMainApp(this);
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }
    }

    @Override
    public void stop() {
        closeConnection();
    }

    @Override
    public void init() {
        mPrefs = Preferences.userNodeForPackage(MainApp.class);
        DOLLAR_CENT_FORMAT.setParseBigDecimal(true);  // always parse BigDecimal
    }

    @Override
    public void start(final Stage stage) {
        mPrimaryStage = stage;
        mPrimaryStage.setTitle(MainApp.class.getPackage().getImplementationTitle());
        initMainLayout();
    }

    public static void main(String[] args) {
        // set error stream to a file in the current directory
        mLogger.info(MainApp.class.getPackage().getImplementationTitle()
                + " " + MainApp.class.getPackage().getImplementationVersion());

        launch(args);
    }
}
