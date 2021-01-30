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

package net.taihuapp.pachira;

import com.opencsv.CSVReader;
import com.webcohesion.ofx4j.OFXException;
import com.webcohesion.ofx4j.client.AccountStatement;
import com.webcohesion.ofx4j.client.FinancialInstitution;
import com.webcohesion.ofx4j.client.FinancialInstitutionAccount;
import com.webcohesion.ofx4j.client.impl.BaseFinancialInstitutionData;
import com.webcohesion.ofx4j.client.impl.FinancialInstitutionImpl;
import com.webcohesion.ofx4j.client.net.OFXV1Connection;
import com.webcohesion.ofx4j.domain.data.banking.AccountType;
import com.webcohesion.ofx4j.domain.data.banking.BankAccountDetails;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponse;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import com.webcohesion.ofx4j.domain.data.signup.AccountProfile;
import com.webcohesion.ofx4j.io.OFXParseException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXMLLoader;
import javafx.geometry.HPos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import net.taihuapp.pachira.dc.AccountDC;
import net.taihuapp.pachira.dc.Vault;
import org.apache.log4j.Logger;
import org.h2.tools.ChangeFileEncryption;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.sql.Date;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static net.taihuapp.pachira.QIFUtil.EOL;
import static net.taihuapp.pachira.Transaction.TradeAction.*;

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

    static final ObjectProperty<LocalDate> CURRENTDATEPROPERTY = new SimpleObjectProperty<>(LocalDate.now());

    // minimum 2 decimal places, maximum 4 decimal places
    static final DecimalFormat DOLLAR_CENT_FORMAT = new DecimalFormat("###,##0.00##");

    private static final String ACKNOWLEDGETIMESTAMP = "ACKDT";
    private static final int MAXOPENEDDBHIST = 5; // keep max 5 opened files
    private static String KEY_OPENEDDBPREFIX = "OPENEDDB#";
    private static final String DBOWNER = "ADMPACHIRA";
    private static final String DBPOSTFIX = ".mv.db"; // was .h2.db in h2-1.3.176, changed to .mv.db in h2-1.4.196
    private static final String URLPREFIX = "jdbc:h2:";
    private static final String CIPHERCLAUSE="CIPHER=AES;";
    private static final String IFEXISTCLAUSE="IFEXISTS=TRUE;";

    private static final String DBVERSIONNAME = "DBVERSION";
    private static final int DBVERSIONVALUE = 9;  // need DBVERSION to run properly.

    private static final int ACCOUNTNAMELEN = 40;
    private static final int ACCOUNTDESCLEN = 256;
    private static final int SECURITYTICKERLEN = 16;
    private static final int SECURITYNAMELEN = 64;

    private static final int CATEGORYNAMELEN = 40;
    private static final int CATEGORYDESCLEN = 256;

    private static final int AMOUNT_TOTAL_LEN = 20;
    private static final int AMOUNT_FRACTION_LEN = 4;

    private static final int TRANSACTIONMEMOLEN = 255;
    private static final int TRANSACTIONREFLEN = 16;
    private static final int TRANSACTIONPAYEELEN = 64;
    private static final int TRANSACTIONTRADEACTIONLEN = 16;
    private static final int TRANSACTIONSTATUSLEN = 16;
    private static final int TRANSACTIONTRANSFERREMINDERLEN = 40;
    // OFX FITID.  Specification says up to 255 char
    private static final int TRANSACTIONFITIDLEN = 256;
    private static final int ADDRESSLINELEN = 32;

    private static final int AMORTLINELEN = 32;

    private static final int PRICE_TOTAL_LEN = 20;
    static final int PRICE_FRACTION_LEN = 8;
    static final int PRICE_FRACTION_DISP_LEN = 6;

    private static final int QUANTITY_TOTAL_LEN = 20;
    static final int QUANTITY_FRACTION_LEN = 8;
    static final int QUANTITY_FRACTION_DISP_LEN = 6;

    static final int SAVEDREPORTSNAMELEN = 32;

    private static final String HASHEDMASTERPASSWORDNAME = "HASHEDMASTERPASSWORD";

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

    private final ObjectProperty<Connection> mConnectionProperty = new SimpleObjectProperty<>(null);
    ObjectProperty<Connection> getConnectionProperty() { return mConnectionProperty; }
    private Connection getConnection() { return getConnectionProperty().get(); }
    private void setConnection(Connection c) { getConnectionProperty().set(c); }

    private Savepoint mSavepoint = null;

    private final Vault mVault = new Vault();

    // mTransactionList is ordered by ID.  It's important for getTransactionByID to work
    // mTransactionListSort2 is ordered by accountID, Date, and ID
    private final ObservableList<Transaction> mTransactionList = FXCollections.observableArrayList();
    private final SortedList<Transaction> mTransactionListSort2 = new SortedList<>(mTransactionList,
            Comparator.comparing(Transaction::getAccountID).thenComparing(Transaction::getTDate)
                    .thenComparing(Transaction::getID));

    // we want to watch the change of hiddenFlag and displayOrder
    // Todo why do we need observe on current balance?
    private final ObservableList<Account> mAccountList = FXCollections.observableArrayList(
            a -> new Observable[] { a.getHiddenFlagProperty(), a.getDisplayOrderProperty(),
                    a.getCurrentBalanceProperty() });
    private final ObservableList<AccountDC> mAccountDCList = FXCollections.observableArrayList();
    private final ObservableList<Tag> mTagList = FXCollections.observableArrayList();
    private final ObservableList<Category> mCategoryList = FXCollections.observableArrayList();
    private final ObservableList<Security> mSecurityList = FXCollections.observableArrayList();
    private final ObservableList<SecurityHolding> mSecurityHoldingList = FXCollections.observableArrayList();
    private final ObservableList<DirectConnection.FIData> mFIDataList = FXCollections.observableArrayList();
    private final ObservableList<DirectConnection> mDCInfoList = FXCollections.observableArrayList();

    private final SecurityHolding mRootSecurityHolding = new SecurityHolding("Root");

    private final Map<Integer, Reminder> mReminderMap = new HashMap<>();

    private final ObservableList<ReminderTransaction> mReminderTransactionList = FXCollections.observableArrayList();

    private final ObjectProperty<Account> mCurrentAccountProperty = new SimpleObjectProperty<>(null);
    ObjectProperty<Account> getCurrentAccountProperty() { return mCurrentAccountProperty; }
    void setCurrentAccount(Account a) { mCurrentAccountProperty.set(a); }
    Account getCurrentAccount() { return mCurrentAccountProperty.get(); }

    private final BooleanProperty mHasMasterPasswordProperty = new SimpleBooleanProperty(false);

    private final ScheduledExecutorService mExecutorService = Executors.newScheduledThreadPool(1);

    ObservableList<AccountDC> getAccountDCList() { return mAccountDCList; }
    AccountDC getAccountDC(int accountID) {
        for (AccountDC adc : getAccountDCList())
            if (adc.getAccountID() == accountID)
                return adc;
        return null;
    }

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
        mLogger.debug("deleteTransactionFromDB(" + tid + ")");
        String sqlCmd0 = "delete from TRANSACTIONS where ID = " + tid;
        String sqlCmd1 = "delete from SPLITTRANSACTIONS where TRANSACTIONID = " + tid;
        try (Statement statement = getConnection().createStatement()) {
            statement.executeUpdate(sqlCmd0);
            statement.executeUpdate(sqlCmd1);
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
        if (idx >= 0)
            return mTransactionList.get(idx);
        return null;
    }

    void showInformationDialog(String title, String header, String content) {
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
        alert.setResizable(true);
        alert.showAndWait();
    }

    // http://code.makery.ch/blog/javafx-dialogs-official/
    static void showExceptionDialog(Stage initOwner, String title, String header, String content, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(initOwner);
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

    String exportToQIF(boolean exportAccount, boolean exportCategory, boolean exportSecurity,
                       boolean exportTransaction, LocalDate fromDate, LocalDate toDate, List<Account> accountList) {
        final StringBuilder stringBuilder = new StringBuilder();

        if (exportCategory) {
            // export Tags first
            stringBuilder.append("!Type:Tag").append(EOL);
            getTagList().forEach(t -> stringBuilder.append(t.toQIF()));

            stringBuilder.append("!Type:Cat").append(EOL);
            getCategoryList().forEach(c -> stringBuilder.append(c.toQIF()));
        }

        if (exportAccount || accountList.size() > 1) {
            // need to export account information
            stringBuilder.append("!Option:AutoSwitch").append(EOL);
            stringBuilder.append("!Account").append(EOL);
            getAccountList(null, null, true).forEach(a -> stringBuilder.append(a.toQIF()));
            stringBuilder.append("!Clear:AutoSwitch").append(EOL);
        }

        if (exportSecurity) {
            stringBuilder.append("!Type:Security").append(EOL);
            getSecurityList().forEach(s -> stringBuilder.append(s.toQIF()));
        }

        if (exportTransaction) {
            stringBuilder.append("!Option:AutoSwitch").append(EOL);
            accountList.forEach(account -> {
                stringBuilder.append("!Account").append(EOL);
                stringBuilder.append(account.toQIF());
                stringBuilder.append("!Type:").append(account.getType().toString2()).append(EOL);
                account.getTransactionList().filtered(t ->
                        ((!t.getTDate().isBefore(fromDate)) && (!t.getTDate().isAfter(toDate))))
                        .forEach(transaction -> stringBuilder.append(transaction.toQIF(this)));
            });
        }

        if (exportSecurity) {
            // need to export prices if securities are exported.
            getSecurityList().stream().filter(s-> !s.getTicker().isEmpty()).forEach(s ->
                getSecurityPrice(s.getID(), s.getTicker()).forEach(p -> stringBuilder.append(p.toQIF(s.getTicker()))));
        }

        return stringBuilder.toString();
    }

    // given a transaction ID, return a ID list of transactions covering it
    List<Integer> lotMatchedBy(int tid) throws SQLException {
        List<Integer> lotMatchList = new ArrayList<>();
        String sqlCmd = "select TRANSID from LOTMATCH where MATCHID = " + tid;
        try (Statement statement = getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                lotMatchList.add(resultSet.getInt("TRANSID"));
            }
            return lotMatchList;
        }
    }

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

        try (Statement statement = getConnection().createStatement();
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
        mSavepoint = getConnection().setSavepoint();
        getConnection().setAutoCommit(false);
        return true;
    }
    void releaseDBSavepoint() throws SQLException {
        getConnection().setAutoCommit(true);
        getConnection().releaseSavepoint(mSavepoint);
        mSavepoint = null;
    }
    void rollbackDB() throws SQLException {
            //mConnection.rollback(mSavepoint);
        getConnection().rollback();
    }
    void commitDB() throws SQLException {
        getConnection().commit();
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
        Connection connection = getConnection();
        try (PreparedStatement preparedStatement =
                     connection.prepareStatement(sqlCmd, Statement.RETURN_GENERATED_KEYS)) {
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
            try (Statement statement = connection.createStatement();
                 PreparedStatement preparedStatement1 = connection.prepareStatement(sqlCmd1)) {
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
                    showExceptionDialog(mPrimaryStage, title, header, content, e);
                    rollbackDB();
                } catch (SQLException e1) {
                    mLogger.error("SQLException: " + e1.getSQLState(), e1);
                    String title = "Database Error";
                    String header = "Unable to roll back";
                    String content = SQLExceptionToString(e1);
                    showExceptionDialog(mPrimaryStage, title, header, content, e1);
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
                    showExceptionDialog(mPrimaryStage, title, header, content, e);
                }
            }
        }
    }

    // todo use try-with-resources
    // delete reminder
    void deleteReminderFromDB(int reminderID) throws SQLException {
        String sqlCmd = "delete from REMINDERS where ID = ?";
        PreparedStatement preparedStatement = getConnection().prepareStatement(sqlCmd);
        preparedStatement.setInt(1, reminderID);
        preparedStatement.executeUpdate();
    }

    // insert or update reminder
    void insertUpdateReminderToDB(Reminder reminder) throws SQLException {
        String sqlCmd;
        if (reminder.getID() <= 0) {
            sqlCmd = "insert into REMINDERS "
                    + "(TYPE, PAYEE, AMOUNT, ACCOUNTID, CATEGORYID, "
                    + "TAGID, MEMO, STARTDATE, ENDDATE, "
                    + "BASEUNIT, NUMPERIOD, ALERTDAYS, ISDOM, ISFWD, ESTCOUNT) "
                    + "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        } else {
            sqlCmd = "update REMINDERS set "
                    + "TYPE = ?, PAYEE = ?, AMOUNT = ?, ACCOUNTID = ?, CATEGORYID = ?, "
                    + "TAGID = ?, MEMO = ?, STARTDATE = ?, ENDDATE = ?, "
                    + "BASEUNIT = ?, NUMPERIOD = ?, ALERTDAYS = ?, ISDOM = ?, ISFWD = ?, ESTCOUNT = ? "
                    + "where ID = ?";
        }

        boolean savepointSetHere = false; // did I set it?
        try (PreparedStatement preparedStatement =
                     getConnection().prepareStatement(sqlCmd, Statement.RETURN_GENERATED_KEYS)) {
            savepointSetHere = setDBSavepoint();

            preparedStatement.setString(1, reminder.getType().name());
            preparedStatement.setString(2, reminder.getPayee());
            preparedStatement.setBigDecimal(3, reminder.getAmount());
            preparedStatement.setInt(4, reminder.getAccountID());
            preparedStatement.setInt(5, reminder.getCategoryID());
            preparedStatement.setInt(6, reminder.getTagID());
            preparedStatement.setString(7, reminder.getMemo());
            preparedStatement.setDate(8, Date.valueOf(reminder.getDateSchedule().getStartDate()));

            preparedStatement.setDate(9, reminder.getDateSchedule().getEndDate() == null ?
                    null : Date.valueOf(reminder.getDateSchedule().getEndDate()));
            preparedStatement.setString(10, reminder.getDateSchedule().getBaseUnit().name());
            preparedStatement.setInt(11, reminder.getDateSchedule().getNumPeriod());
            preparedStatement.setInt(12, reminder.getDateSchedule().getAlertDay());
            preparedStatement.setBoolean(13, reminder.getDateSchedule().isDOMBased());
            preparedStatement.setBoolean(14, reminder.getDateSchedule().isForward());
            preparedStatement.setInt(15, reminder.getEstimateCount());
            if (reminder.getID() > 0)
                preparedStatement.setInt(16, reminder.getID());

            preparedStatement.executeUpdate();
            if (reminder.getID() <= 0) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    resultSet.next();
                    reminder.setID(resultSet.getInt(1));
                }
            }

            insertUpdateSplitTransactionsToDB(-reminder.getID(), reminder.getSplitTransactionList());

            if (savepointSetHere) {
                commitDB();
            }
        } catch (SQLException e) {
            if (savepointSetHere) {
                try {
                    mLogger.error("SQLException: " + e.getSQLState(), e);
                    showExceptionDialog(mPrimaryStage, "Database Error", "insert/update Reminder failed",
                            SQLExceptionToString(e), e);
                    rollbackDB();
                } catch (SQLException e1) {
                    mLogger.error("SQLException: " + e1.getSQLState(), e1);
                    showExceptionDialog(mPrimaryStage, "Database Error",
                            "Failed to rollback reminder database update", SQLExceptionToString(e1), e1);
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
                    showExceptionDialog(mPrimaryStage, "Database Error",
                            "after insertUpdateReminber, set autocommit failed",
                            SQLExceptionToString(e), e);
                }
            }
        }
    }

    void insertReminderTransactions(ReminderTransaction rt, int tid) {
        rt.setTransactionID(tid);
        String sqlCmd = "insert into REMINDERTRANSACTIONS (REMINDERID, DUEDATE, TRANSACTIONID) "
                + "values (?, ?, ?)";
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(sqlCmd)) {
            preparedStatement.setInt(1, rt.getReminder().getID());
            preparedStatement.setDate(2, Date.valueOf(rt.getDueDate()));
            preparedStatement.setInt(3, tid);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            mLogger.error("SQLException: " + e.getSQLState(), e);
            showExceptionDialog(mPrimaryStage, "Database Error",
                    "Failed to insert into ReminderTransactions!",
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
                    "REFERENCE, SPLITFLAG, ACCRUEDINTEREST, FITID) " +
                    "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            sqlCmd = "update TRANSACTIONS set " +
                    "ACCOUNTID = ?, DATE = ?, AMOUNT = ?, TRADEACTION = ?, " +
                    "SECURITYID = ?, STATUS = ?, CATEGORYID = ?, TAGID = ?, MEMO = ?, " +
                    "PRICE = ?, QUANTITY = ?, COMMISSION = ?, " +
                    "MATCHTRANSACTIONID = ?, MATCHSPLITTRANSACTIONID = ?, " +
                    "PAYEE = ?, ADATE = ?, OLDQUANTITY = ?, REFERENCE = ?, SPLITFLAG = ?, ACCRUEDINTEREST = ?," +
                    "FITID = ? " +
                    "where ID = ?";
        }

        boolean savepointSetHere = false; // did I set savepoint?
        try (PreparedStatement preparedStatement =
                     getConnection().prepareStatement(sqlCmd, Statement.RETURN_GENERATED_KEYS)) {
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
            preparedStatement.setBigDecimal(20, t.getAccruedInterest());
            preparedStatement.setString(21, t.getFITID());
            if (t.getID() > 0)
                preparedStatement.setInt(22, t.getID());

            if (preparedStatement.executeUpdate() == 0) {
                String message = t.getID() > 0 ? ("Update transaction " + t.getID()) : "Insert transaction";
                throw (new SQLException(message + " failed. " + sqlCmd));
            }

            if (t.getID() <= 0) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    resultSet.next();
                    t.setID(resultSet.getInt(1));
                }
            }

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
                    showExceptionDialog(mPrimaryStage, "Database Error", "update transaction failed",
                            SQLExceptionToString(e), e);
                    rollbackDB();
                } catch (SQLException e1) {
                    mLogger.error("SQLException: " + e1.getSQLState(), e1);
                    // error in rollback
                    showExceptionDialog(mPrimaryStage, "Database Error",
                            "Failed to rollback transaction database update",
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
                    showExceptionDialog(mPrimaryStage, "Database Error", "set autocommit failed",
                            SQLExceptionToString(e), e);
                }
            }
        }
        return 0;
    }

    private void insertUpdateSplitTransactionsToDB(int tid, List<SplitTransaction> stList) throws SQLException {
        // load all existing split transactions for tid from database
        List<SplitTransaction> oldSTList = loadSplitTransactions(tid).get(tid);

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
        Connection connection = getConnection();
        try (Statement statement = connection.createStatement();
             PreparedStatement insertStatement =
                     connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement updateStatement = connection.prepareStatement(updateSQL)) {
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

        try (PreparedStatement preparedStatement =
                     getConnection().prepareStatement(sqlCmd, Statement.RETURN_GENERATED_KEYS)) {
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
            showExceptionDialog(mPrimaryStage, "Database Error", "Insert/Update Tag Failed",
                    SQLExceptionToString(e), e);
        } catch (NullPointerException e) {
            mLogger.error("NullPointerException", e);
            showExceptionDialog(mPrimaryStage, "Database Error", "mConnection is null",
                    "Database not connected", e);
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

        try (PreparedStatement preparedStatement =
                     getConnection().prepareStatement(sqlCmd, Statement.RETURN_GENERATED_KEYS)) {
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
            showExceptionDialog(mPrimaryStage, "Database Error", "Insert/Update Category Failed",
                    SQLExceptionToString(e), e);
        } catch (NullPointerException e) {
            mLogger.error("NullPointerException", e);
            showExceptionDialog(mPrimaryStage, "Database Error", "mConnection is null",
                    "Database not connected", e);
        }
        return false;
    }

    // insert or update DirectConnection to DB.
    // for insertion, the ID field of input dc is set to the database id.
    void insertUpdateDCToDB(DirectConnection dc) throws SQLException {
        String sqlCmd;
        int id = dc.getID();
        if (id <= 0) {
            sqlCmd = "insert into DCINFO (NAME, FIID, USERNAME, PASSWORD) "
                    + "values(?, ?, ?, ?)";
        } else {
            sqlCmd = "update DCINFO set "
                    + "NAME = ?, FIID = ?, USERNAME = ?, PASSWORD = ? "
                    + "where ID = ?";
        }

        boolean savepointSetHere = false;
        try (PreparedStatement preparedStatement =
                     getConnection().prepareStatement(sqlCmd, Statement.RETURN_GENERATED_KEYS)) {
            savepointSetHere = setDBSavepoint();
            preparedStatement.setString(1, dc.getName());
            preparedStatement.setInt(2, dc.getFIID());
            preparedStatement.setString(3, dc.getEncryptedUserName());
            preparedStatement.setString(4, dc.getEncryptedPassword());
            if (id > 0) {
                preparedStatement.setInt(5, id);
            }
            if (preparedStatement.executeUpdate() == 0)
                throw new SQLException("Failure: " + sqlCmd);
            if (id <= 0) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    resultSet.next();
                    dc.setID(resultSet.getInt(1));
                }
            }
            if (savepointSetHere)
                commitDB();
        } catch (SQLException e) {
            // Database problem
            if (savepointSetHere) {
                rollbackDB();
            }
            throw e;
        } finally {
            if (savepointSetHere)
                releaseDBSavepoint();
        }
    }

    // return the row id if merge succeeded.
    // this method doesn't set savepoint
    int insertUpdateFIDataToDB(DirectConnection.FIData fiData) throws SQLException {
        String sqlCmd;
        int id = fiData.getID();
        if (id <= 0) {
            sqlCmd = "insert into FIData "
                    + "(FIID, SUBID, NAME, ORG, BROKERID, URL) "
                    + "values(?, ?, ?, ?, ?, ?)";
        } else {
            sqlCmd = "update FIData set "
                    + "FIID = ?, SUBID = ?, NAME = ?, ORG = ?, BROKERID = ?, URL = ? "
                    + "where ID = ?";
        }

        try (PreparedStatement preparedStatement =
                     getConnection().prepareStatement(sqlCmd, Statement.RETURN_GENERATED_KEYS)) {
            preparedStatement.setString(1, fiData.getFIID());
            preparedStatement.setString(2, fiData.getSubID());
            preparedStatement.setString(3, fiData.getName());
            preparedStatement.setString(4, fiData.getORG());
            preparedStatement.setString(5, fiData.getBrokerID());
            preparedStatement.setString(6, fiData.getURL());
            if (id > 0) {
                preparedStatement.setInt(7, fiData.getID());
            }

            if (preparedStatement.executeUpdate() == 0)
                throw(new SQLException("Failure: " + sqlCmd));

            if (id <= 0) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    resultSet.next();
                    id = resultSet.getInt(1);
                    fiData.setID(id);
                }
            }
            return fiData.getID();
        }
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

        try (PreparedStatement preparedStatement =
                     getConnection().prepareStatement(sqlCmd, Statement.RETURN_GENERATED_KEYS)) {
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
    boolean deleteSecurityPriceFromDB(Integer securityID, String ticker, LocalDate date) {
        String sqlCmd;
        if (ticker.isEmpty())
            sqlCmd = "delete from PRICES where SECURITYID = ? and DATE = ?";
        else
            sqlCmd = "delete from PRICES where TICKER = ? and DATE = ?";
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(sqlCmd)) {
            if (ticker.isEmpty())
                preparedStatement.setInt(1, securityID);
            else
                preparedStatement.setString(1, ticker);
            preparedStatement.setDate(2, Date.valueOf(date));
            preparedStatement.executeUpdate();
            return true;
        } catch (SQLException e) {
            mLogger.error("SQLExceptoin " + e.getSQLState(), e);
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle("Database Fail Warning");
            alert.setHeaderText("Failed deleting price:");
            alert.setContentText("Ticker:      " + ticker + "\n" +
                    "Security ID: " + securityID + "\n" +
                    "Date:        " + date + "\n");
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
    boolean insertUpdatePriceToDB(Integer securityID, String ticker, LocalDate date, BigDecimal p, int mode) {
        if (ticker.isEmpty() && securityID <= 0)
            throw new IllegalArgumentException("insertUpdatePriceToDB need valid ticker or securityID:"
                    + "ID: " + securityID + ", Ticker: '" + ticker + "'");

        boolean status = false;
        String sqlCmd;
        switch (mode) {
            case 0:
            case 1:
                sqlCmd = "insert into PRICES (PRICE, TICKER, DATE, SECURITYID) values (?, ?, ?, ?)";
                break;
            case 2:
                sqlCmd = "update PRICES set PRICE = ? where TICKER = ? and DATE = ? and SECURITYID = ?";
                break;
            case 3:
                return insertUpdatePriceToDB(securityID, ticker, date, p, 0)
                        || insertUpdatePriceToDB(securityID, ticker, date, p, 2);
            default:
                throw new IllegalArgumentException("insertUpdatePriceToDB called with bad mode = " + mode);
        }

        try (PreparedStatement preparedStatement = getConnection().prepareStatement(sqlCmd)) {
            preparedStatement.setBigDecimal(1, p);
            preparedStatement.setString(2, ticker);
            preparedStatement.setDate(3, Date.valueOf(date));
            preparedStatement.setInt(4, ticker.isEmpty() ? securityID : 0);
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

        try (PreparedStatement preparedStatement = getConnection().prepareStatement(sqlCmd)) {
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

        try (PreparedStatement preparedStatement =
                     getConnection().prepareStatement(sqlCmd.toString(), Statement.RETURN_GENERATED_KEYS)) {
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

        try (PreparedStatement preparedStatement =
                     getConnection().prepareStatement(sqlCmd.toString(), Statement.RETURN_GENERATED_KEYS)) {
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
                    "PAYEE, SPLITFLAG, ADDRESSID, AMORTIZATIONID, TRADEACTION, TAGID, FITID" +
                    ") values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

            try (PreparedStatement preparedStatement =
                         getConnection().prepareStatement(sqlCmd, Statement.RETURN_GENERATED_KEYS)) {
                String categoryOrTransferStr = bt.getCategoryOrTransfer();
                int categoryOrTransferID = mapCategoryOrAccountNameToID(categoryOrTransferStr);
                if (categoryOrTransferID == -account.getID()) {
                    // self transferring, set categiryID to 0
                    categoryOrTransferID = 0;
                }
                int tagID = mapTagNameToID(bt.getTag());
                Transaction.TradeAction ta = bt.getTAmount().signum() >= 0 ?
                        Transaction.TradeAction.DEPOSIT : Transaction.TradeAction.WITHDRAW;
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
                preparedStatement.setString(14, "");
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
        Connection connection = getConnection();
        connection.setAutoCommit(false);

        String sqlCmd = "insert into TRANSACTIONS " +
                "(ACCOUNTID, DATE, AMOUNT, TRADEACTION, SECURITYID, " +
                "STATUS, CATEGORYID, MEMO, PRICE, QUANTITY, COMMISSION, OLDQUANTITY, FITID, REFERENCE, PAYEE) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCmd,
                Statement.RETURN_GENERATED_KEYS)){
            int cid = mapCategoryOrAccountNameToID(tt.getCategoryOrTransfer());
            QIFParser.TradeTransaction.Action action = tt.getAction();
            if (cid == -account.getID()) {
                // self transfer, set to no transfer
                cid = 0;
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
            preparedStatement.setString(13, ""); // empty string for FITID
            preparedStatement.setString(14, ""); // empty string for REFERENCE
            preparedStatement.setString(15, ""); // empty string for PAYEE
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
            connection.rollback();
        else
            connection.commit();

        // we are done here
        connection.setAutoCommit(true);
        return rowID;
    }

    private void insertCategoryToDB(Category category) {
        String sqlCmd;
        sqlCmd = "insert into CATEGORIES (NAME, DESCRIPTION, INCOMEFLAG, TAXREFNUM, BUDGETAMOUNT) "
                + "values (?,?,?, ?, ?)";

        try (PreparedStatement preparedStatement =
                     getConnection().prepareStatement(sqlCmd, Statement.RETURN_GENERATED_KEYS)) {
            preparedStatement.setString(1, category.getName());
            preparedStatement.setString(2, category.getDescription());
            preparedStatement.setBoolean(3, category.getIsIncome());
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
            sqlCmd = "insert into ACCOUNTS (TYPE, NAME, DESCRIPTION, HIDDENFLAG, DISPLAYORDER, LASTRECONCILEDATE) "
                    + "values (?,?,?,?,?,?)";
        } else {
            sqlCmd = "update ACCOUNTS set "
                    + "TYPE = ?, NAME = ?, DESCRIPTION = ? , HIDDENFLAG = ?, DISPLAYORDER = ?, LASTRECONCILEDATE = ? "
                    + "where ID = ?";
        }

        try (PreparedStatement preparedStatement =
                     getConnection().prepareStatement(sqlCmd, Statement.RETURN_GENERATED_KEYS)) {
            preparedStatement.setString(1, account.getType().name());
            preparedStatement.setString(2, account.getName());
            preparedStatement.setString(3, account.getDescription());
            preparedStatement.setBoolean(4, account.getHiddenFlag());
            preparedStatement.setInt(5, account.getDisplayOrder());
            preparedStatement.setObject(6, account.getLastReconcileDate());
            if (account.getID() >= MIN_ACCOUNT_ID) {
                preparedStatement.setInt(7, account.getID());
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
                updateAccountBalance(account);
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

    void initReminderMap() {
        if (getConnection() == null) return;

        mReminderMap.clear();
        String sqlCmd = "select * from REMINDERS";
        try (Statement statement = getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                String type = resultSet.getString("TYPE");
                String payee = resultSet.getString("PAYEE");
                BigDecimal amount = resultSet.getBigDecimal("AMOUNT");
                int estCnt = resultSet.getInt("ESTCOUNT");
                int accountID = resultSet.getInt("ACCOUNTID");
                int categoryID = resultSet.getInt("CATEGORYID");
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
                        accountID, categoryID, tagID, memo, ds,
                        loadSplitTransactions(-id).getOrDefault(-id, new ArrayList<>())));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
    }

    void initReminderTransactionList() {
        if (getConnection() == null) return;

        mReminderTransactionList.clear();
        String sqlCmd = "select * from REMINDERTRANSACTIONS order by REMINDERID, DUEDATE";
        int ridPrev = -1;
        Reminder reminder = null;
        Map<Integer, LocalDate> lastDueDateMap = new HashMap<>();
        try (Statement statement = getConnection().createStatement();
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
                        amt = amt.divide(BigDecimal.valueOf(cnt), AMOUNT_FRACTION_LEN, RoundingMode.HALF_UP);
                    amt = amt.setScale(AMOUNT_FRACTION_LEN, RoundingMode.HALF_UP);
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
        if (getConnection() == null) return;

        mTagList.clear();
        String sqlCmd = "select * from TAGS";
        try (Statement statement = getConnection().createStatement();
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
        if (getConnection() == null) return;

        mCategoryList.clear();
        Statement statement = null;
        ResultSet resultSet = null;
        String sqlCmd = "select * from CATEGORIES order by INCOMEFLAG DESC, NAME";
        try {
            statement = getConnection().createStatement();
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

    void updateAccountBalance() {
        for (Account account : getAccountList(null, false, true)) {
            updateAccountBalance(account);
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
        updateAccountBalance(account);
    }

    void updateAccountBalance(Account account) {
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

    // reload from AccountDC table
    void initAccountDCList() {
        mAccountDCList.clear();
        if (getConnection() == null)
            return;
        String sqlCmd = "select ACCOUNTID, ACCOUNTTYPE, DCID, ROUTINGNUMBER, ACCOUNTNUMBER, "
                + "LASTDOWNLOADDATE, LASTDOWNLOADTIME, LASTDOWNLOADLEDGEBAL from ACCOUNTDCS order by ACCOUNTID";
        try (Statement statement = getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                int accountID = resultSet.getInt("ACCOUNTID");
                int dcID = resultSet.getInt("DCID");
                String accountType = resultSet.getString("ACCOUNTTYPE");
                String routingNumber = resultSet.getString("ROUTINGNUMBER");
                String accountNumber = resultSet.getString("ACCOUNTNUMBER");
                java.util.Date lastDownloadDate = resultSet.getDate("LASTDOWNLOADDATE");
                java.sql.Time lastDownloadTime = resultSet.getTime("LASTDOWNLOADTIME");
                lastDownloadDate.setTime(lastDownloadTime.getTime());
                BigDecimal ledgeBal = resultSet.getBigDecimal("LASTDOWNLOADLEDGEBAL");
                mAccountDCList.add(new AccountDC(accountID, accountType, dcID, routingNumber, accountNumber,
                        lastDownloadDate, ledgeBal));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
    }

    // should be called after mTransactionList being properly initialized
    void initAccountList() {
        mAccountList.clear();
        if (getConnection() == null) return;

        try (Statement statement = getConnection().createStatement()) {
            String sqlCmd = "select ID, TYPE, NAME, DESCRIPTION, HIDDENFLAG, DISPLAYORDER, LASTRECONCILEDATE "
                    + "from ACCOUNTS"; // order by TYPE, ID";
            ResultSet rs = statement.executeQuery(sqlCmd);
            while (rs.next()) {
                int id = rs.getInt("ID");
                Account.Type type = Account.Type.valueOf(rs.getString("TYPE"));
                String name = rs.getString("NAME");
                String description = rs.getString("DESCRIPTION");
                Boolean hiddenFlag = rs.getBoolean("HIDDENFLAG");
                Integer displayOrder = rs.getInt("DISPLAYORDER");
                LocalDate lastReconcileDate = rs.getObject("LASTRECONCILEDATE", LocalDate.class);
                mAccountList.add(new Account(id, type, name, description, hiddenFlag, displayOrder,
                        lastReconcileDate, BigDecimal.ZERO));
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
            updateAccountBalance(account);
        }
    }

    private void initSecurityList() {
        mSecurityList.clear();
        if (getConnection() == null) return;

        try (Statement statement = getConnection().createStatement()) {
            String sqlCmd = "select ID, TICKER, NAME, TYPE from SECURITIES order by NAME";
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

    // save hashed and encoded master password into database.
    private void mergeMasterPasswordToDB(String encodedHashedMasterPassword) throws SQLException {
        boolean savepointSetHere = false;
        try (Statement statement = getConnection().createStatement()) {
            savepointSetHere = setDBSavepoint();
            statement.executeUpdate("merge into DCINFO (NAME, FIID, PASSWORD) key(NAME) values('"
                    + HASHEDMASTERPASSWORDNAME + "', 0, '" + encodedHashedMasterPassword + "')");
            if (savepointSetHere)
                commitDB();
        } catch (SQLException e) {
            if (savepointSetHere)
                rollbackDB();

            throw e;
        } finally {
            if (savepointSetHere)
                releaseDBSavepoint();
        }
    }

    // initialize DCInfo List from Database table
    // return encoded hashed master password or null if not exist
    String initDCInfoList() throws SQLException {
        getDCInfoList().clear();
        if (getConnection() == null)
            return null;

        String ehmp = null;
        String sqlCmd = "select * from DCINFO order by ID;";
        try (Statement statement = getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                String name = resultSet.getString("NAME");
                int fiID = resultSet.getInt("FIID");
                String eun = resultSet.getString("USERNAME");
                String epwd = resultSet.getString("PASSWORD");

                if (HASHEDMASTERPASSWORDNAME.equals(name))
                    ehmp = epwd;
                else
                    mDCInfoList.add(new DirectConnection(id, name, fiID, eun, epwd));
            }
            return ehmp;
        }
    }

    void initFIDataList() {

        if (getConnection() == null)
            return;

        String sqlCmd;
        mFIDataList.clear();
        sqlCmd = "select * from FIData order by ID;";
        try (Statement statement = getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd) ) {
            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                String fiid = resultSet.getString("FIID");
                String subid = resultSet.getString("SUBID");
                String name = resultSet.getString("NAME");
                String org = resultSet.getString("ORG");
                String brokerID = resultSet.getString("BROKERID");
                String url = resultSet.getString("URL");

                mFIDataList.add(new DirectConnection.FIData(id, fiid, subid, brokerID,
                        name, org, url));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException on initFIDataList " + e.getSQLState(), e);
            showExceptionDialog(mPrimaryStage,"Exception", "SQLException", SQLExceptionToString(e), e);
        }
    }

    void deleteFIDataFromDB(int fiDataID) throws SQLException {
        try (Statement statement = getConnection().createStatement()) {
            statement.executeUpdate("delete from FIDATA where ID = " + fiDataID);
        }
    }

    private void initVault() {

        if (getConnection() == null)
            return;

        hasMasterPasswordProperty().set(false);
        try {
            mVault.setupKeyStore();
        } catch (NoSuchAlgorithmException | KeyStoreException | IOException | CertificateException
                | InvalidKeySpecException e) {
            mLogger.error("Vault.setupKeyStore throws exception " + e.getClass().getName(), e);
            showExceptionDialog(mPrimaryStage,"Exception", e.getClass().getName(),
                    "In Vault.setupKeyStore", e);
            return; // can't continue, return here.
        }

        try {
            String ehmp = initDCInfoList();
            if (ehmp != null) {
                mVault.setHashedMasterPassword(ehmp);
                hasMasterPasswordProperty().set(true);
            }
        } catch (SQLException e) {
            mLogger.error("SQLException on select DCINFO table " + e.getSQLState(), e);
            showExceptionDialog(mPrimaryStage,"Database Error", "Select DCInfo Error",
                    SQLExceptionToString(e), e);
        }
    }

    // if input tid == 0, returns a Map with
    //     keys of the Map is Transaction id
    //     corresponding values are the List of SplitTransaction for the given TransactionID
    // if input tid != 0, returns a Map with one single entry
    // values in TRANSACTIONID column could be either positive or negative
    // a negative TRANSACTIONID value means the the splittransaction belongs to a Reminder Transaction
    // instead of a normal transaction
    private Map<Integer, List<SplitTransaction>> loadSplitTransactions(int tidIn) {
        Map<Integer, List<SplitTransaction>> stListMap = new HashMap<>();

        int key = tidIn;
        List<SplitTransaction> value = null;
        if (tidIn != 0) {
            value = new ArrayList<>();
            stListMap.put(key, value);
        }

        String sqlCmd;
        if (tidIn <= 0)
            sqlCmd = "select * from SPLITTRANSACTIONS order by TRANSACTIONID, ID";
        else
            sqlCmd = "select * from SPLITTRANSACTIONS where TRANSACTIONID = " + tidIn + " order by ID";

        try (Statement statement = getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                int tid = resultSet.getInt("TRANSACTIONID");
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

                // tid == 0 should never happen.
                // but if I don't put it there, Intellij warns possible null pointer exception
                if (tid != key || tid == 0) {
                    // we have a new Transaction id
                    key = tid;
                    value = new ArrayList<>();
                    stListMap.put(key, value);
                }
                value.add(new SplitTransaction(id, cid, tagid, payee, memo, amount, matchID));
            }
        }  catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }

        return stListMap;
    }

    // initialize mTransactionList order by ID
    // mSecurityList should be loaded prior this call.
    private void initTransactionList() {
        mTransactionList.clear();
        if (getConnection() == null)
            return;

        // load ALL split transactions
        Map<Integer, List<SplitTransaction>> stListMap = loadSplitTransactions(0);

        List<Transaction> tList = new ArrayList<>();  // a simple list to temporarily hold all transactions
        String sqlCmd = "select * from TRANSACTIONS order by ID";
        try (Statement statement = getConnection().createStatement();
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
                    aDate = null;
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
                BigDecimal accruedInterest = resultSet.getBigDecimal("ACCRUEDINTEREST");
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

                String fitid = resultSet.getString("FITID");
                Transaction transaction = new Transaction(id, aid, tDate, aDate, tradeAction, status, name, reference,
                        payee, price, quantity, oldQuantity, memo, commission, accruedInterest, amount,
                        cid, tagID, matchID, matchSplitID,
                        resultSet.getBoolean("SPLITFLAG") ?
                                stListMap.getOrDefault(id, new ArrayList<>()) : null, fitid);
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

    void showReconcileDialog() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/view/ReconcileDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setTitle("Reconcile Account: " + getCurrentAccount().getName());
            dialogStage.setScene(new Scene(loader.load()));
            ReconcileDialogController controller = loader.getController();
            controller.setMainApp(this, dialogStage);
            dialogStage.setOnCloseRequest(e -> controller.handleCancel());
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

    void showDirectConnectionListDialog() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/DirectConnectionListDialog.fxml"));

            Stage stage = new Stage();
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mPrimaryStage);
            stage.setTitle("Direct Connection List");
            stage.setScene(new Scene(loader.load()));

            DirectConnectionListDialogController controller = loader.getController();
            controller.setMainApp(this, stage);
            stage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException when open Direct Connection List dialog", e);
        }
    }

    void showFinancialInstitutionListDialog() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/FinancialInstitutionListDialog.fxml"));

            Stage stage = new Stage();
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mPrimaryStage);
            stage.setTitle("Manage Financial Institution List");
            stage.setScene(new Scene(loader.load()));

            FinancialInstitutionListDialogController controller = loader.getController();
            controller.setMainApp(this, stage);
            stage.showAndWait();

        } catch (IOException e) {
            mLogger.error("IOException when open Financial Institution List Dialog", e);
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
            showExceptionDialog(mPrimaryStage,"Exception", "IO Exception",
                    "showTagListDialog IO Exception", e);
        } catch (NullPointerException e) {
            showExceptionDialog(mPrimaryStage, "Exception", "Null pointer exception",
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
            showExceptionDialog(mPrimaryStage,"Exception", "IO Exception",
                    "showCategoryListDialog IO Exception", e);
        } catch (NullPointerException e) {
            showExceptionDialog(mPrimaryStage,"Exception", "Null pointer exception",
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

    // returns a list of passwords, the length of list can be either 0 or 2.
    // for creating password, empty string is in [0] and the new password is in [1]
    // for entering password, empty string is in [0] and the password is in [1]
    // for changing password, the old password is in [0] and the new one in [1]
    List<String> showPasswordDialog(String prompt, PasswordDialogController.MODE mode) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/PasswordDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle(prompt);
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

    // update HoldingsList to date but exclude transaction exTid
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
                    if (t.getID() == exTid)
                        continue;
                    if (t.getTDate().isAfter(date)) {
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
            securityHoldingList.add(cashHolding);
            securityHoldingList.add(totalHolding);
            return securityHoldingList;
        }

        // deal with Investing account here
        ObservableList<Transaction> tList = FXCollections.observableArrayList();
        for (int i = 0; i < account.getTransactionList().size(); i++) {
            // copy over the transactions we are interested
            Transaction t = account.getTransactionList().get(i);
            if (t.getID() == exTid)
                continue;
            if (t.getTDate().isAfter(date))
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
            LocalDate aDate1 = o1.getADate();
            LocalDate aDate2 = o2.getADate();
            if (aDate1 == null)
                aDate1 = o1.getTDate();
            if (aDate2 == null)
                aDate2 = o2.getTDate();
            dateComparison = aDate1.compareTo(aDate2);
            if (dateComparison != 0)
                return dateComparison;

            // compare TradeAction if dates are the same
            // we want to have SELL and CVTSHRT at the end
            if (o1.getTradeAction() == SELL
                    || o1.getTradeAction() == Transaction.TradeAction.CVTSHRT)
                return (o2.getTradeAction() == SELL
                        || o2.getTradeAction() == Transaction.TradeAction.CVTSHRT) ? 0 : 1;
            if (o2.getTradeAction() == SELL
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
                    LocalDate aDate = t.getADate();
                    if (aDate == null)
                        aDate = t.getTDate();
                    securityHoldingList.get(index).addLot(new SecurityHolding.LotInfo(t.getID(), name,
                            t.getTradeAction(), aDate, t.getPrice(), t.getSignedQuantity(), t.getCostBasis()),
                            getMatchInfoList(tid));
                }
            }
        }

        BigDecimal totalMarketValue = totalCash;
        BigDecimal totalCostBasis = totalCash;
        for (Iterator<SecurityHolding> securityHoldingIterator = securityHoldingList.iterator();
             securityHoldingIterator.hasNext(); ) {
            SecurityHolding securityHolding = securityHoldingIterator.next();

            if (securityHolding.getQuantity().setScale(QUANTITY_FRACTION_DISP_LEN,
                    RoundingMode.HALF_UP).signum() == 0) {
                // remove security with zero quantity
                securityHoldingIterator.remove();
                continue;
            }

            Price price = getLatestSecurityPrice(getSecurityByName(securityHolding.getSecurityName()), date);
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

        if (getConnection() == null) {
            mLogger.error("DB connection down?! ");
            return matchInfoList;
        }

        try (Statement statement = getConnection().createStatement()){
            String sqlCmd = "select TRANSID, MATCHID, MATCHQUANTITY from LOTMATCH " +
                    "where TRANSID = " + tid + " order by MATCHID";
            ResultSet rs = statement.executeQuery(sqlCmd);
            while (rs.next()) {
                int mid = rs.getInt("MATCHID");
                BigDecimal quantity = rs.getBigDecimal("MATCHQUANTITY");
                matchInfoList.add(new SecurityHolding.MatchInfo(mid, quantity));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }
        return matchInfoList;
    }

    // return all the prices in a list, sorted ascending by date.
    List<Price> getSecurityPrice(Integer securityID, String ticker) {
        List<Price> priceList = new ArrayList<>();

        String sqlCmd;
        if (ticker.isEmpty())
            sqlCmd = "select DATE, PRICE from PRICES where SECURITYID = " + securityID;
        else
            sqlCmd = "select DATE, PRICE from PRICES where TICKER = '" + ticker + "'";

        try (Statement statement = getConnection().createStatement()) {
            ResultSet resultSet = statement.executeQuery(sqlCmd);
            while (resultSet.next()) {
                priceList.add(new Price(resultSet.getDate(1).toLocalDate(), resultSet.getBigDecimal(2)));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }

        return priceList;
    }

    // retrieve the latest price no later than requested date
    private Price getLatestSecurityPrice(Security security, LocalDate date) {
        Price price = null;
        if (security == null)
            return null;
        String sqlCmd;
        String ticker = security.getTicker();
        if (ticker.isEmpty())
            sqlCmd = "select top 1 PRICE, DATE from PRICES where SECURITYID = " + security.getID() + " " +
                    "and DATE <= '" + date.toString() + "' order by DATE desc";
        else
            sqlCmd = "select top 1 PRICE, DATE from PRICES where TICKER = '" + security.getTicker() + "' " +
                    "and DATE <= '" + date.toString() + "' order by DATE desc";
        try (Statement statement = getConnection().createStatement();
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
        if (getConnection() == null) {
            mLogger.error("DB connection down?!");
            return;
        }

        // delete any existing
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("delete from LOTMATCH where TRANSID = " + tid);
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
        }

        if (matchInfoList.size() == 0)
            return;

        // insert list
        String sqlCmd = "insert into LOTMATCH (TRANSID, MATCHID, MATCHQUANTITY) values (?, ?, ?)";
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(sqlCmd)) {
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
    List<SplitTransaction> showSplitTransactionsDialog(Stage parent, int accountID, List<SplitTransaction> stList,
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
            controller.setMainApp(this, accountID, dialogStage, stList, netAmount);
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
        List<Transaction.TradeAction> taList = (getCurrentAccount().getType() == Account.Type.INVESTING) ?
                Arrays.asList(Transaction.TradeAction.values()) :
                Arrays.asList(Transaction.TradeAction.WITHDRAW, Transaction.TradeAction.DEPOSIT);
        showEditTransactionDialog(parent, transaction, Collections.singletonList(getCurrentAccount()),
                getCurrentAccount(), taList);
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

            EditTransactionDialogControllerNew controller = loader.getController();
            controller.setMainApp(this, transaction, dialogStage, accountList, defaultAccount, taList);
            dialogStage.showAndWait();
            return controller.getTransactionID();
        } catch (IOException e) {
            mLogger.error("IOException", e);
            return -1;
        }
    }

    void showAccountHoldings() {
        if (getCurrentAccount() == null) {
            mLogger.error("Can't show holdings for null account.");
            return;
        }
        if (getCurrentAccount().getType() != Account.Type.INVESTING) {
            mLogger.error("Show holdings only applicable for trading account");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/HoldingsDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Account Holdings: " + getCurrentAccount().getName());
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
        if (getConnection() != null) {
            try {
                getConnection().close();
            } catch (SQLException e) {
                mLogger.error("SQLException " + e.getSQLState(), e);
            }
            getConnectionProperty().set(null);
        }
    }

    boolean isConnected() { return getConnection() != null; }

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
            if ((fromAccountID == -t1.getCategoryID()) && (cashFlow.add(t1.cashTransferAmount()).signum() == 0))
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
        mLogger.info("Total " + nTrans + " transactions");

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
                            st.getAmount(), transactionList.subList(i+1, nTrans));
                    if (matchIdx < 0) {
                        // didn't find match, it's possible more than one split transaction transfering
                        // to the same account, the receiving account aggregates all into one transaction.
                        modeAgg = true; // try aggregate mode
                        mLogger.info("Aggregate mode");
                        BigDecimal cf = BigDecimal.ZERO;
                        for (int s1 = s; s1 < t0.getSplitTransactionList().size(); s1++) {
                            SplitTransaction st1 = t0.getSplitTransactionList().get(s1);
                            if (st1.getCategoryID().equals(st.getCategoryID()))
                                cf = cf.add(st1.getAmount());
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
                        t0.cashTransferAmount(), transactionList.subList(i+1, nTrans));
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
                showExceptionDialog(mPrimaryStage,"FixDB Failed", "Failed updating Transaction",
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
        mLogger.info(message);
    }

    // import OFX Account statement
    // current account has to be non-null
    void importOFXAccountStatement() {
        if (getCurrentAccount() == null) {
            showExceptionDialog(mPrimaryStage,"Exception", "Strange error happened",
                    "Current Account is null", null);
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ofx files",
                Arrays.asList("*.ofx", "*.OFX")));
        fileChooser.setTitle("Import OFX Account Statements file...");
        File file = fileChooser.showOpenDialog(mPrimaryStage);
        if (file == null) {
            mLogger.info("Import cancelled");
            return;
        }
        mLogger.info("import" + file.getAbsolutePath());

        OFXBankStatementReader reader = new OFXBankStatementReader();
        BankStatementResponse statement;
        try {
            statement = reader.readOFXStatement(new FileInputStream(file));

            String warning = reader.getWarning();
            if (warning != null) {
                mLogger.warn("importOFXAccountStatement " + file.getAbsolutePath()
                        + " encountered warning:\n" + warning);
                showWarningDialog("Warning","Import OFX Statement Warning", warning);
            }

            // show confirmation dialog
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            GridPane gridPane = new GridPane();
            gridPane.setHgap(10);
            gridPane.setVgap(10);
            gridPane.setMaxWidth(Double.MAX_VALUE);
            gridPane.add(new Label("Routing Number"), 1, 0);

            Label accountNumber = new Label("Account Number");
            accountNumber.setTextAlignment(TextAlignment.RIGHT);
            gridPane.add(accountNumber, 2, 0);

            gridPane.add(new Label("Current Account"), 0, 1);
            gridPane.add(new Label("routing #"), 1, 1);

            Label currentAccountNumber = new Label("account #");
            currentAccountNumber.setTextAlignment(TextAlignment.RIGHT);
            gridPane.add(currentAccountNumber, 2, 1);

            gridPane.add(new Label("Import Info"), 0, 2);
            gridPane.add(new Label(statement.getAccount().getBankId()), 1, 2);
            Label importAccountNumber = new Label(statement.getAccount().getAccountNumber());
            importAccountNumber.setTextAlignment(TextAlignment.RIGHT);
            gridPane.add(importAccountNumber, 2, 2);

            GridPane.setHalignment(accountNumber, HPos.RIGHT);
            GridPane.setHalignment(currentAccountNumber, HPos.RIGHT);
            GridPane.setHalignment(importAccountNumber, HPos.RIGHT);

            alert.getDialogPane().setContent(gridPane);
            alert.setTitle("Confirmation");
            int nTrans = statement.getTransactionList().getTransactions().size();
            alert.setHeaderText("Do you want to import " + nTrans + " transactions?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK)
                return;

            importAccountStatement(getCurrentAccount(), statement);
        } catch (IOException | OFXParseException | SQLException e) {
            mLogger.error("ImportOFXAccountStatement exception", e);
            showExceptionDialog(mPrimaryStage,"Exception", e.getClass().getName(),
                    "Import OFX Account Statement Exception", e);
        }
    }

    // import price data stored in a 3+ column csv file
    // The csv file may have headers
    // the 3 required columns are
    // Symbol, Price, Date
    // All other columns after the first 3 columns are ignored
    void importPrices() {
        // first present a file chooser to select the csv file to be imported
        File file;
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("csv files",
                Arrays.asList("*.csv", "*.CSV")));
        fileChooser.setTitle("Import Prices in CSV file...");
        file = fileChooser.showOpenDialog(mPrimaryStage);
        if (file == null) {
            mLogger.info("import cancelled");
            return;
        }
        mLogger.info("import " + file.getAbsolutePath());

        // pair of security id and price
        List<Pair<String, Price>> priceList = new ArrayList<>();
        List<String[]> skippedLines = new ArrayList<>();
        // parse the csv file
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            Set<String> tickerSet = new HashSet<>();
            getSecurityList().forEach(s -> tickerSet.add(s.getTicker()));
            List<String[]> lines = reader.readAll();
            for (String[] line : lines) {
                if (line[0].equals("Symbol")) {
                    skippedLines.add(line);
                    continue; // this is the header line
                }
                if (line.length < 3) {
                    mLogger.warn("Bad formatted line: " + String.join(",", line));
                    skippedLines.add(line);
                    continue;
                }

                BigDecimal p = new BigDecimal(line[1]);
                if (p.compareTo(BigDecimal.ZERO) < 0) {
                    mLogger.warn("Negative Price: " + line[0] + "," + line[1] + "," + line[2]);
                    skippedLines.add(line);
                    continue;
                }

                if (!tickerSet.contains(line[0])) {
                    mLogger.warn("Unknown ticker: " + line[0]);
                    skippedLines.add(line);
                    continue;
                }

                List<String> patterns = Arrays.asList("yyyy/M/d", "M/d/yyyy", "M/d/yy");
                for (String s : patterns) {
                    try {
                        LocalDate ld = LocalDate.parse(line[2], DateTimeFormatter.ofPattern(s));
                        priceList.add(new Pair<>(line[0], new Price(ld, p)));
                        break;
                    } catch (DateTimeParseException ignored) {
                    }
                }
            }
        } catch (IOException e) {
            showExceptionDialog(mPrimaryStage, "Exception Dialog", e.getClass().getName(),
                    e.getLocalizedMessage(), e);
        }

        // database work
        boolean savepointSetHere;
        try {
            savepointSetHere = setDBSavepoint();
            if (!savepointSetHere) {
                SQLException e = new SQLException("Database savepoint unexpectedly set by the caller. Can't continue");
                mLogger.error(SQLExceptionToString(e), e);
                throw e;
            }

            mergePricesToDB(priceList);

            // savepoint is set here, so commit now
            commitDB();

            StringBuilder message = new StringBuilder(priceList.size() + " prices imported.\n");
            if (skippedLines.size() > 0) {
                message.append('\n').append(skippedLines.size()).append(" lines skipped:\n");
                for (String[] skippedLine : skippedLines) {
                    message.append("  ");
                    for (int i = 0; i < Math.min(3, skippedLine.length); i++)
                        message.append(skippedLine[i]).append(',');
                    if (skippedLine.length > 3)
                        message.append("...\n");
                    else
                        message.append('\n');
                }
            }
            showInformationDialog("Import Prices", "Done", message.toString());
        } catch (SQLException e) {
            // savepoint was set here, roll back after exception
            try {
                rollbackDB();
                mLogger.error("SQLException: " + e.getSQLState(), e);
                showExceptionDialog(mPrimaryStage,"Database Error", "Reconcile account failed",
                        SQLExceptionToString(e), e);
            } catch (SQLException e1) {
                mLogger.error("SQLException: " + e1.getSQLState(), e1);
                showExceptionDialog(mPrimaryStage,"Database Error", "Unable to rollback",
                        SQLExceptionToString(e1), e1);
            }
        }  finally {
            // savepoint was set here, release now
            try {
                releaseDBSavepoint();
            } catch (SQLException e) {
                mLogger.error("SQLException: " + e.getSQLState(), e);
                showExceptionDialog(mPrimaryStage, "Database Error", "releaseDBSavepint failed",
                        SQLExceptionToString(e), e);
            }
        }

        // reload account list
        initAccountList();
    }

    // merge prices to database
    private void mergePricesToDB(List<Pair<String, Price>> pList) throws SQLException {
        String mergeSQL = "merge into PRICES (SECURITYID, TICKER, DATE, PRICE) values (0, ?, ?, ?)";
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(mergeSQL)) {
            Iterator<Pair<String, Price>> iterator = pList.iterator();
            Pair<String, Price> p;
            while (iterator.hasNext()) {
                p = iterator.next();
                preparedStatement.setString(1, p.getKey());
                preparedStatement.setDate(2, Date.valueOf(p.getValue().getDate()));
                preparedStatement.setBigDecimal(3, p.getValue().getPrice());

                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            mLogger.error(SQLExceptionToString(e), e);
            throw e;
        }
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
        qifParser.getAccountList().forEach(this::insertUpdateAccountToDB);
        initAccountList();

        qifParser.getSecurityList().forEach(this::insertUpdateSecurityToDB);
        initSecurityList();

        for (Pair<String, Price> ticker_price : qifParser.getPriceList()) {
            String ticker = ticker_price.getKey();
            Price p = ticker_price.getValue();
            if (!insertUpdatePriceToDB(0, ticker, p.getDate(), p.getPrice(), 3)) {
                mLogger.error("Insert to PRICE failed with "
                        + ticker + ", " + p.getDate() + ", " + p.getPrice());
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
                    Security security = getSecurityByName(tt.getSecurityName());
                    if (security != null && p != null && p.signum() > 0) {
                        insertUpdatePriceToDB(security.getID(), security.getTicker(), tt.getDate(), p, 0);
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
        if (getConnection() == null) {
            showExceptionDialog(mPrimaryStage, "Exception Dialog", "Null pointer exception",
                    "mConnection is null", null);
            return null;
        }

        String backupFileName = null;
        try (PreparedStatement preparedStatement = getConnection().prepareStatement("Backup to ?")) {
            backupFileName = getBackupDBFileName();
            preparedStatement.setString(1, backupFileName);
            preparedStatement.execute();
            showInformationDialog("Backup Information", "Successful",
                    "Backup to " + backupFileName + " successful");
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
            showExceptionDialog(mPrimaryStage, "Exception Dialog", "SQLException",
                    "Backup failed", e);
        }
        return backupFileName;
    }

    void changePassword() {
        Connection connection = getConnection();
        if (connection == null) {
            showExceptionDialog(mPrimaryStage, "Exception Dialog", "Null pointer exception",
                    "mConnection is null", null);
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText("Changing password will close current database file.");
        alert.setContentText("Do you want to proceed?");
        Optional<ButtonType> result = alert.showAndWait();
        if (!(result.isPresent() && (result.get() == ButtonType.OK)))
            return;  // do nothing

        List<String> passwords = showPasswordDialog("Change Password", PasswordDialogController.MODE.CHANGE);
        if (passwords == null || passwords.size() != 2) {
            // action cancelled
            return;
        }

        String backupFileName = null; // if later this is not null, that means we have a backup
        int passwordChanged = 0;
        PreparedStatement preparedStatement = null;
        try {
            Class.forName("org.h2.Driver");
            String url = connection.getMetaData().getURL();
            File dbFile = new File(getDBFileNameFromURL(url));
            // backup database first
            backupFileName = doBackup();
            connection.close();
            getConnectionProperty().set(null);
            // change encryption password first
            ChangeFileEncryption.execute(dbFile.getParent(), dbFile.getName(), "AES", passwords.get(0).toCharArray(),
                    passwords.get(1).toCharArray(), true);
            passwordChanged++;  // changed 1
            // DBOWNER password has not changed yet.
            url += ";"+CIPHERCLAUSE+IFEXISTCLAUSE;
            connection = DriverManager.getConnection(url, DBOWNER, passwords.get(1) + " " + passwords.get(0));
            getConnectionProperty().set(connection);
            preparedStatement = connection.prepareStatement("Alter User " + DBOWNER + " set password ?");
            preparedStatement.setString(1, passwords.get(1));
            preparedStatement.execute();
            passwordChanged++;
        } catch (SQLException e) {
            mLogger.error("SQLException " + e.getSQLState(), e);
            showExceptionDialog(mPrimaryStage,"Exception", "SQLException", e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            mLogger.error("IllegalArgumentException", e);
            showExceptionDialog(mPrimaryStage,"Exception", "IllegalArgumentException", e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            mLogger.error("ClassNotFoundException", e);
            showExceptionDialog(mPrimaryStage, "Exception", "ClassNotFoundException", e.getMessage(), e);
        } finally {
            try {
                if (preparedStatement != null)
                    preparedStatement.close();
            } catch (SQLException e) {
                mLogger.error("SQLException " + e.getSQLState(), e);
                showExceptionDialog(mPrimaryStage, "Exception", "SQLException", e.getMessage(), e);
            }
            if (passwordChanged == 1) {
                showExceptionDialog(mPrimaryStage, "Exception", "Change password failed!",
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
        return getDBFileNameFromURL(getConnection().getMetaData().getURL()) + "Backup"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + ".zip";
    }

    String getDBFileName() throws SQLException {
        return getDBFileNameFromURL(getConnection().getMetaData().getURL());
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
            List<String> passwords;
            if (isNew)
                passwords = showPasswordDialog("Create New Password", PasswordDialogController.MODE.NEW);
            else
                passwords = showPasswordDialog("Enter Password", PasswordDialogController.MODE.ENTER);

            if (passwords == null || passwords.size() == 0 || passwords.get(1) == null) {
                if (isNew) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.initOwner(mPrimaryStage);
                    alert.setTitle("Password not set");
                    alert.setHeaderText("Need a password to continue...");
                    alert.showAndWait();
                }
                return;
            }
            password = passwords.get(1);
        }

        try {
            String url = URLPREFIX+dbName+";"+CIPHERCLAUSE;
            if (!isNew) {
                // open existing
                url += IFEXISTCLAUSE;
            }
            // we use same password for file encryption and admin user
            Class.forName("org.h2.Driver");
            getConnectionProperty().set(DriverManager.getConnection(url, DBOWNER, password + ' ' + password));
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
            showExceptionDialog(mPrimaryStage, "Exception", "ClassNotFoundException", e.getMessage(), e);
        }

        if (getConnection() == null) {
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
                mLogger.info("DBVersion updated from " + dbVersion + " to " + DBVERSIONVALUE);
                showInformationDialog("Database Version Updated",
                        "Database Version Updated from " + dbVersion + " to " + DBVERSIONVALUE,
                        "Your database was updated from version " + dbVersion + " to " + DBVERSIONVALUE + ". " +
                                "The old database was saved in " + backupFileName);
            } catch (SQLException e) {
                // Failed
                mLogger.error("SQLException " + e.getSQLState(), e);

                showExceptionDialog(mPrimaryStage, "Database Version Update Failed",
                        "Database Version Update Failed",
                        "Your database failed to update from version " + dbVersion + " to " + DBVERSIONVALUE +
                                ". The old database was saved in " + backupFileName, e);
                closeConnection();
                return;
            } catch (IllegalArgumentException e) {
                // version not supported
                mLogger.error("IllegalArgumentException", e);
                showExceptionDialog(mPrimaryStage, "Database Version Update Failed",
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

        if (newV - oldV > 1)
            updateDBVersion(oldV, newV-1);  // bring oldV to newV-1.

        // need to run this to update DBVERSION
        final String mergeSQL = "merge into SETTINGS (NAME, VALUE) values ('" + DBVERSIONNAME + "', " + newV + ")";
        if (newV == 9) {
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
            try (Statement statement = getConnection().createStatement()) {
                statement.executeUpdate(alterPRICESTableSQL);
                statement.executeUpdate(mergeSQL);
            }
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

            try (Statement statement = getConnection().createStatement()) {
                statement.executeUpdate(updateXIN);
                statement.executeUpdate(updateXOUT);
                statement.executeUpdate(updateReminder);
                statement.executeUpdate(alterTable);
                statement.executeUpdate(updateSavedReportDetails);
                statement.executeUpdate(mergeSQL);
            }
        } else if (newV == 7) {
            try (Statement statement = getConnection().createStatement()) {
                alterAccountDCSTable();
                statement.executeUpdate(mergeSQL);
            }
        } else if (newV == 6) {
            createDirectConnectTables();
            try (Statement statement = getConnection().createStatement()) {

                statement.executeUpdate("update TRANSACTIONS set MEMO = '' where MEMO is null");
                statement.executeUpdate("alter table TRANSACTIONS alter column MEMO varchar("
                        + TRANSACTIONMEMOLEN + ") not null default''");

                statement.executeUpdate("update TRANSACTIONS set REFERENCE = '' where REFERENCE is null");
                statement.executeUpdate("alter table Transactions alter column REFERENCE varchar("
                        + TRANSACTIONREFLEN + ")");

                statement.executeUpdate("update TRANSACTIONS set PAYEE = '' where PAYEE is null");
                statement.executeUpdate("alter table Transactions alter column PAYEE varchar("
                        + TRANSACTIONPAYEELEN + ")");

                statement.executeUpdate("alter table TRANSACTIONS add (FITID varchar("
                        + TRANSACTIONFITIDLEN + ") NOT NULL default '')");

                statement.executeUpdate(mergeSQL);
            }
        } else if (newV == 5) {
            final String updateSQL = "alter table TRANSACTIONS add (ACCRUEDINTEREST decimal(20, 4) default 0);";
            try (Statement statement = getConnection().createStatement()) {
                statement.executeUpdate(updateSQL);
                statement.executeUpdate(mergeSQL);
            }
        } else if (newV == 4) {
            final String updateSQL = "alter table ACCOUNTS add (LASTRECONCILEDATE Date)";
            try (Statement statement = getConnection().createStatement()) {
                statement.executeUpdate(updateSQL);
                statement.executeUpdate(mergeSQL);
            }
        } else if (newV == 3) {
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
            try (Statement statement = getConnection().createStatement()) {
                statement.executeUpdate(updateSQL0);
                statement.executeUpdate(updateSQL1);
                statement.executeUpdate(updateSQL2);
                statement.executeUpdate(updateSQL3);
                statement.executeUpdate(mergeSQL);
            }
        } else if (newV == 2) {
            final String alterSQL = "alter table SAVEDREPORTS add (" +
                    "PAYEECONTAINS varchar(80) NOT NULL default '', " +
                    "PAYEEREGEX boolean NOT NULL default FALSE, " +
                    "MEMOCONTAINS varchar(80) NOT NULL default '', " +
                    "MEMOREGEX boolean NOT NULL default FALSE)";
            try (Statement statement = getConnection().createStatement()) {
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
            try (Statement statement = getConnection().createStatement()) {
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

        initFIDataList();
        initVault();
        initAccountDCList();
    }

    // encrypt a char array using master password in the vault, return encrypted and encoded
    String encrypt(final char[] secret) throws NoSuchAlgorithmException, InvalidKeySpecException,
            KeyStoreException, UnrecoverableKeyException, NoSuchPaddingException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        return getVault().encrypt(secret);
    }

    char[] decrypt(final String encodedEncryptedSecretWithSaltAndIV) throws IllegalArgumentException,
            NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException, UnrecoverableKeyException,
            NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {
        return getVault().decrypt(encodedEncryptedSecretWithSaltAndIV);
    }

    BooleanProperty hasMasterPasswordProperty() {
        return mHasMasterPasswordProperty;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean hasMasterPasswordInKeyStore() { return getVault().hasMasterPasswordInKeyStore(); }

    // verify if the input password matches the master password
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean verifyMasterPassword(final String password) throws NoSuchAlgorithmException, InvalidKeySpecException,
            KeyStoreException, UnrecoverableKeyException {
        char[] mpChars = password.toCharArray();
        try {
            return getVault().verifyMasterPassword(mpChars);
        } finally {
            Arrays.fill(mpChars, ' ');
        }
    }

    // set master password in Vault
    // save hashed and encoded master password to database
    void setMasterPassword(final String password) throws NoSuchAlgorithmException, InvalidKeySpecException,
            KeyStoreException, UnrecoverableKeyException, SQLException {
        char[] mpChars = password.toCharArray();
        try {
            getVault().setMasterPassword(mpChars);
            mergeMasterPasswordToDB(getVault().getEncodedHashedMasterPassword());
        } finally {
            Arrays.fill(mpChars, ' ');
            hasMasterPasswordProperty().set(getVault().hasMasterPassword());
        }
    }

    // delete everything in DCInfo Table
    private void emptyDCTableAndAccountDCSTable() throws SQLException {
        boolean savepointSetHere = false;
        try (Statement statement = getConnection().createStatement()) {
            savepointSetHere = setDBSavepoint();
            statement.execute("delete from DCINFO");
            statement.execute("delete from ACCOUNTDCS");
            if (savepointSetHere)
                commitDB();
        } catch (SQLException e) {
            if (savepointSetHere)
                rollbackDB();
            throw e;
        } finally {
            if (savepointSetHere)
                releaseDBSavepoint();
        }
    }

    // delete master password in vault
    // empty DCInfo table and AccountDCS table
    // empty DCInfoList
    void deleteMasterPassword() throws KeyStoreException, SQLException {
        getVault().deleteMasterPassword();
        emptyDCTableAndAccountDCSTable();
        mAccountDCList.clear();
        mDCInfoList.clear();
        hasMasterPasswordProperty().set(getVault().hasMasterPassword());
    }

    // this method setDBSavepoint.  If the save point is set by the caller, an SQLException will be thrown
    // return false when isUpdate is true and curPassword doesn't match existing master password
    // exception will be thrown if any is encounted
    // otherwise, return true
    boolean updateMasterPassword(final String curPassword, final String newPassword)
            throws NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException,
            UnrecoverableKeyException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException, SQLException {

        if (!verifyMasterPassword(curPassword))
            return false;  // in update mode, curPassword doesn't match existing master password

        final char[] newPasswordChars = newPassword.toCharArray();

        final List<char[]> clearUserNameList = new ArrayList<>();
        final List<char[]> clearPasswordList = new ArrayList<>();
        final List<char[]> clearAccountNumberList = new ArrayList<>();

        try {
            for (DirectConnection dc : getDCInfoList()) {
                clearUserNameList.add(decrypt(dc.getEncryptedUserName()));
                clearPasswordList.add(decrypt(dc.getEncryptedPassword()));
            }
            for (AccountDC adc : getAccountDCList())
                clearAccountNumberList.add(decrypt(adc.getEncryptedAccountNumber()));

            getVault().setMasterPassword(newPasswordChars);

            for (int i = 0; i < getDCInfoList().size(); i++) {
                DirectConnection dc = getDCInfoList().get(i);
                dc.setEncryptedUserName(encrypt(clearUserNameList.get(i)));
                dc.setEncryptedPassword(encrypt(clearPasswordList.get(i)));
            }
            for (int i = 0; i < getAccountDCList().size(); i++) {
                AccountDC adc = getAccountDCList().get(i);
                adc.setEncryptedAccountNumber(encrypt(clearAccountNumberList.get(i)));
            }
        } finally {
            // wipe it clean
            Arrays.fill(newPasswordChars, ' ');

            for (char[] chars : clearUserNameList)
                Arrays.fill(chars, ' ');
            for (char[] chars : clearPasswordList)
                Arrays.fill(chars, ' ');
        }

        // save new information to database now
        boolean savepointSetHere = false;
        try {
            savepointSetHere = setDBSavepoint();
            if (!savepointSetHere) {
                SQLException e = new SQLException("Database savepoint unexpectedly set by the caller. Can't continue");
                mLogger.error(SQLExceptionToString(e), e);
                throw e;
            }
            mergeMasterPasswordToDB(getVault().getEncodedHashedMasterPassword());
            for (DirectConnection dc : getDCInfoList()) {
                insertUpdateDCToDB(dc);
            }
            for (AccountDC adc : getAccountDCList())
                mergeAccountDCToDB(adc);

            commitDB();
            return true;
        } catch (SQLException e) {
            if (savepointSetHere)
                rollbackDB();

            initVault(); // reload Vault
            throw e;
        } finally {
            // wipe it clean
            Arrays.fill(newPasswordChars, ' ');

            for (char[] chars : clearUserNameList)
                Arrays.fill(chars, ' ');
            for (char[] chars : clearPasswordList)
                Arrays.fill(chars, ' ');
            for (char[] chars : clearAccountNumberList)
                Arrays.fill(chars, ' ');

            if (savepointSetHere)
                releaseDBSavepoint();
        }
    }

    private void sqlCreateTable(String createSQL) {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = getConnection().prepareStatement(createSQL);
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

    // In Database Only:
    //   change cleared Transaction status to reconciled for the given account
    //   and mark the account reconciled date as d.
    // Caller is responsible to update objects in memory
    //
    // return true for success, false for error.
    // if false is returned, the database is not changed.
    boolean reconcileAccountToDB(Account a, LocalDate d) {
        List<Transaction> tList = new ArrayList<>(a.getTransactionList()
                .filtered(t -> t.getStatus().equals(Transaction.Status.CLEARED)));
        boolean savepointSetHere = false;
        try (Statement statement = getConnection().createStatement()) {
            savepointSetHere = setDBSavepoint();
            //
            for (Transaction t : tList) {
                statement.addBatch("update TRANSACTIONS set STATUS = '"
                    + Transaction.Status.RECONCILED.name() + "' where ID = " + t.getID());
            }

            statement.addBatch("update ACCOUNTS set LASTRECONCILEDATE = '" + d.toString()
                    + "' where ID = "+ a.getID());
            statement.executeBatch();
            if (savepointSetHere)
                commitDB();
            return true;
        } catch (SQLException e) {
            if (savepointSetHere) {
                try {
                    rollbackDB();
                    mLogger.error("SQLException: " + e.getSQLState(), e);
                    showExceptionDialog(mPrimaryStage, "Database Error", "Reconcile account failed",
                            SQLExceptionToString(e), e);
                } catch (SQLException e1) {
                    mLogger.error("SQLException: " + e1.getSQLState(), e1);
                    showExceptionDialog(mPrimaryStage, "Database Error", "Unable to rollback",
                            SQLExceptionToString(e1), e1);
                }
            }
        } finally {
            if (savepointSetHere) {
                try {
                    releaseDBSavepoint();
                } catch (SQLException e) {
                    mLogger.error("SQLException: " + e.getSQLState(), e);
                    showExceptionDialog(mPrimaryStage, "Database Error", "releaseDBSavepint failed",
                            SQLExceptionToString(e), e);
                }
            }
        }
        return false;
    }

    // update Status column in Transaction Table for the given tid.
    // return true for success and false for failure.
    boolean setTransactionStatusInDB(int tid, Transaction.Status s) {
        boolean savepointSetHere = false;
        try (Statement statement = getConnection().createStatement()) {
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
                    showExceptionDialog(mPrimaryStage, "Database Error", "Unable update transaction status",
                            SQLExceptionToString(e), e);
                } catch (SQLException e1) {
                    mLogger.error("SQLException: " + e1.getSQLState(), e1);
                    showExceptionDialog(mPrimaryStage, "Database Error", "Unable to rollback",
                            SQLExceptionToString(e1), e1);
                }
            }
        } finally {
            if (savepointSetHere) {
                try {
                    releaseDBSavepoint();
                } catch (SQLException e) {
                    mLogger.error("SQLException" + e.getSQLState(), e);
                    showExceptionDialog(mPrimaryStage, "Database Error", "releaseDBSavepint failed",
                            SQLExceptionToString(e), e);
                }
            }
        }
        return false;
    }

    // Alter, including insert, delete, and modify a transaction, both in DB and in MasterList.
    // It performs all the necessary consistency checks.
    // If oldT is null, newT is inserted
    // If newT is null, oldT is deleted
    // If oldT and newT both are not null, oldT is modified to newT.
    // return true for success and false for failure
    // this is the new one
    boolean alterTransaction(Transaction oldT, Transaction newT, List<SecurityHolding.MatchInfo> newMatchInfoList) {
        if (oldT == null && newT == null)
            return true; // both null, no-op.

        final Set<Transaction> updateTSet = new HashSet<>(); // transactions need updated in MasterList
        final Set<Integer> deleteTIDSet = new HashSet<>(); // transactions need to be deleted
        final Set<Integer> accountIDSet = new HashSet<>(); // accounts need to update balance

        try {
            // set save point, in case something wrong so we can rollback
            if (!setDBSavepoint()) {
                showWarningDialog("Unexpected situation", "Database savepoint already set?",
                        "Please restart application");
                return false;
            }

            if (newT != null) {
                final Transaction.TradeAction newTTA = newT.getTradeAction();

                // check quantity
                if (newTTA == SELL || newTTA == SHRSOUT || newTTA == CVTSHRT) {
                    final BigDecimal quantity = updateAccountSecurityHoldingList(getAccountByID(newT.getAccountID()),
                            newT.getTDate(), newT.getID()).stream()
                            .filter(sh -> sh.getSecurityName().equals(newT.getSecurityName()))
                            .map(SecurityHolding::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (((newTTA == SELL || newTTA == SHRSOUT) && quantity.compareTo(newT.getQuantity()) <= 0)
                        || ((newTTA == CVTSHRT) && quantity.negate().compareTo(newT.getQuantity()) <= 0)) {
                        // existing quantity not enough for the new trade
                        mLogger.warn("New " + newTTA + " transaction quantity exceeds existing quantity");
                        showWarningDialog("New " + newTTA + " transaction quantity exceeds existing quantity",
                                newTTA + " quantity can not exceeds existing quantity",
                                "Please check transaction quantity");
                        rollbackDB();
                        return false;
                    }
                }

                // check ADate for SHRSIN
                if (newTTA == SHRSIN) {
                    final LocalDate aDate = newT.getADate();
                    if (aDate == null || aDate.isAfter(newT.getTDate())) {
                        mLogger.warn("Acquisition date has to be on or before trade date");
                        showWarningDialog("Invalid acquisition date",
                                "Acquisition date is " + (aDate == null ? "null" : aDate),
                                "Please enter a valid acquisition date");
                        rollbackDB();
                        return false;
                    }
                }

                // we need to save newT and newMatchInfoList to DB
                final int newTID = insertUpdateTransactionToDB(newT);
                putMatchInfoList(newTID, newMatchInfoList);

                if (newT.isSplit()) {
                    // split transaction shouldn't have any match id and match split id.
                    newT.setMatchID(-1, -1);
                    // check transfer in split transactions
                    for (SplitTransaction st : newT.getSplitTransactionList()) {
                        if (st.isTransfer(newT.getAccountID())) {
                            if (st.getCategoryID() == -newT.getAccountID()) {
                                mLogger.warn("Split transaction is transferring back to the same account");
                                showWarningDialog("Split transaction linked back to the same account",
                                        "Same account transferring is not allowed",
                                        "Please change the transferring account");
                                rollbackDB();
                                return false;
                            }
                            // this split transaction is a transfer transaction
                            Transaction stXferT = null;
                            if (st.getMatchID() > 0) {
                                // it is modify exist transfer transaction
                                stXferT = getTransactionByID(st.getMatchID());
                                if (stXferT == null) {
                                    mLogger.warn("Split Transaction (" + newT.getID() + ", " + st.getID()
                                            + ") linked to null");
                                    showWarningDialog("Split transaction linked to null",
                                            "Split transaction cannot be linked to null",
                                            "Something is wrong.  Help is needed");
                                    rollbackDB();
                                    return false;
                                }
                                if (!stXferT.isCash()) {
                                    // transfer transaction is an invest transaction, check trade action compatibility
                                    if (stXferT.TransferTradeAction() != (st.getAmount().compareTo(BigDecimal.ZERO) >= 0 ?
                                            Transaction.TradeAction.DEPOSIT : Transaction.TradeAction.WITHDRAW)) {
                                        mLogger.warn("Split transaction cash flow not compatible with "
                                                + "transfer transaction trade action");
                                        showWarningDialog("Trade Action MisMatch",
                                                "Transfer transaction trade action not compatible",
                                                "Transfer transaction " + stXferT.getTradeAction()
                                                        + ", split transaction cash flow " + st.getAmount()
                                                        + ", not compatible");
                                        rollbackDB();
                                        return false;
                                    }
                                    // for existing transfer transactions, we only update the minimum information
                                    stXferT.setAccountID(-newT.getCategoryID());
                                    stXferT.setCategoryID(-newT.getAccountID());
                                    stXferT.setTDate(newT.getTDate());
                                    stXferT.setAmount(st.getAmount().abs());
                                }
                            }

                            if (stXferT == null || stXferT.isCash()) {
                                // we need to create new xfer transaction
                                stXferT = new Transaction(-st.getCategoryID(), newT.getTDate(),
                                        (st.getAmount().compareTo(BigDecimal.ZERO) >= 0 ?
                                                Transaction.TradeAction.WITHDRAW : Transaction.TradeAction.DEPOSIT),
                                        -newT.getAccountID());
                                stXferT.setPayee(st.getPayee());
                                stXferT.setMemo(st.getMemo());
                                stXferT.setMatchID(newTID, st.getID());
                                stXferT.setAmount(st.getAmount().abs());
                            }

                            // insert stXferT to database and update st match id
                            st.setMatchID(insertUpdateTransactionToDB(stXferT));

                            // we need to update stXferT later in MasterList
                            updateTSet.add(stXferT);

                            // we need to update the transfer account
                            accountIDSet.add(-st.getCategoryID());
                        } else {
                            // st is not a transfer, set match id to 0
                            st.setMatchID(0);
                        }
                    }

                    // match id and/or match split id in newT might have changed
                    // match id in split transactions might have changed
                    insertUpdateTransactionToDB(newT);

                } else if (newT.isTransfer()) {
                    if (newT.getCategoryID() == -newT.getAccountID()) {
                        mLogger.warn("Transaction is transferring back to the same account");
                        showWarningDialog("Transaction linked back to the same account",
                                "Same account transferring is not allowed",
                                "Please change the transferring account");
                        rollbackDB();
                        return false;
                    }

                    // non split, transfer
                    Transaction xferT = null;
                    if (newT.getMatchID() > 0) {
                        // newT linked to a split transaction
                        xferT = getTransactionByID(newT.getMatchID());
                        if (xferT == null) {
                            mLogger.warn("Transaction " + newT.getID() + " is linked to null");
                            showWarningDialog("Transaction linked to null",
                                    "Transaction cannot be linked to null",
                                    "Something is wrong.  Help is needed");
                            rollbackDB();
                            return false;
                        }
                        if (xferT.isSplit()) {
                            if (newT.getMatchSplitID() <= 0) {
                                mLogger.warn("Transaction (" + newT.getMatchID() + ") missing match split id");
                                showWarningDialog("Transaction (" + newT.getMatchID() + ") missing match split id",
                                        "Transaction linked to a split transaction, need match split id",
                                        "Something is wrong.  Help is needed");
                                rollbackDB();
                                return false;
                            }
                            if (!newT.getTDate().equals(xferT.getTDate())
                                    || (-newT.getCategoryID() != xferT.getAccountID())) {
                                mLogger.warn("Can't change date and/or transfer account to a transaction linked to a split transaction.");
                                showWarningDialog("Date or transfer account mismatch",
                                        "Cannot change date and/or transfer account "
                                        + "on transaction linked to a split transaction",
                                        "Please change linked split transaction");
                                rollbackDB();
                                return false;
                            }
                            final List<SplitTransaction> stList = xferT.getSplitTransactionList();
                            final SplitTransaction xferSt = stList.stream()
                                    .filter(st -> st.getID() == newT.getMatchSplitID())
                                    .findFirst()
                                    .orElse(null);
                            if (xferSt == null) {
                                // didn't find matching split transaction
                                mLogger.warn("Transaction (" + newT.getID() + ") linked to null split transaction");
                                showWarningDialog("Transaction linked to null split transaction",
                                        "Transaction cannot be linked to null split transaction",
                                        "Something is wrong.  Help is needed");
                                rollbackDB();
                                return false;
                            }
                            xferSt.setAmount(newT.TransferTradeAction() == DEPOSIT ?
                                    newT.getAmount() : newT.getAmount().negate());
                            xferSt.getCategoryIDProperty().set(-newT.getAccountID());
                            if (xferSt.getMemo().isEmpty())
                                xferSt.setMemo(newT.getMemo());
                            if (xferSt.getPayee().isEmpty())
                                xferSt.setPayee(newT.getPayee());
                            final BigDecimal amount = xferT.getSplitTransactionList().stream()
                                    .map(SplitTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                            if (((xferT.TransferTradeAction() == WITHDRAW)
                                    && (amount.compareTo(BigDecimal.ZERO) < 0))
                                    || ((xferT.TransferTradeAction() == DEPOSIT)
                                    && (amount.compareTo(BigDecimal.ZERO) > 0))) {
                                mLogger.warn("Modify transaction linked to a split transaction "
                                        + "resulting inconsistency in linked transaction");
                                showWarningDialog("Resulting inconsistency in linked split transaction",
                                        "Transaction linked to a split transaction",
                                        "Please edit linked split transaction first");
                                rollbackDB();
                                return false;
                            }
                            xferT.setAmount(amount.abs());
                        } else if (!xferT.isCash()) {
                            // non cash, check trade action compatibility
                            if (xferT.TransferTradeAction() != newT.getTradeAction()) {
                                mLogger.warn("Transfer transaction has an investment trade action not compatible with "
                                        + newT.getTradeAction());
                                showWarningDialog("Non compatible trade action",
                                        "Linked transaction trade action is not compatible",
                                        "Please adjust linked transaction first");
                                rollbackDB();
                                return false;
                            }
                            xferT.setAccountID(-newT.getCategoryID());
                            xferT.setCategoryID(-newT.getAccountID());
                            xferT.setTDate(newT.getTDate());
                            xferT.setAmount(newT.getAmount());
                        }
                    }

                    if ((xferT == null) || (!xferT.isSplit() && xferT.isCash())) {
                        xferT = new Transaction(-newT.getCategoryID(), newT.getTDate(), newT.TransferTradeAction(),
                                -newT.getAccountID());
                        xferT.setPayee(newT.getPayee());
                        xferT.setMemo(newT.getMemo());
                        xferT.setMatchID(newT.getID(), -1);
                        xferT.setAmount(newT.getAmount());
                    }

                    // we might need to set matchID if xferT is newly created
                    // but we never create a new match split transaction
                    // so we keep the matchSplitID the same as before
                    newT.setMatchID(insertUpdateTransactionToDB(xferT), newT.getMatchSplitID());

                    // update newT MatchID in DB
                    insertUpdateTransactionToDB(newT);

                    // we need to insert/update xferT in master list
                    updateTSet.add(xferT);

                    // we need to update xfer account
                    accountIDSet.add(-newT.getCategoryID());
                } else {
                    // non-split, non transfer, make sure set match id properly
                    newT.setMatchID(-1,-1);
                    insertUpdateTransactionToDB(newT);
                }


                final Security security;
                final BigDecimal price;
                if (!newT.isCash()) {
                    security = getSecurityByName(newT.getSecurityName());
                    price = newT.getPrice();
                } else {
                    security = null;
                    price = null;
                }

                if (security != null && price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                    insertUpdatePriceToDB(security.getID(), security.getTicker(), newT.getTDate(), price, 0);

                    getAccountList(Account.Type.INVESTING, false, true).stream()
                            .filter(a -> a.hasSecurity(security)).forEach(a -> accountIDSet.add(a.getID()));
                }

                // we need to update a copy of newT in the master list
                updateTSet.add(new Transaction(newT));

                accountIDSet.add(newT.getAccountID());
            }

            if (oldT != null) {
                // we have an oldT, need to delete the related transactions, if those are not updated

                // first make sure it is not covered by SELL or CVTSHT
                final List<Integer> matchList = lotMatchedBy(oldT.getID());
                if (!matchList.isEmpty()) {
                    mLogger.warn("Cannot delete transaction (" + oldT.getID() + ") which is lot matched");
                    showWarningDialog("Transaction is lot matched",
                            "Transaction is lot matched by " + matchList.size() + " transaction(s)",
                            "Cannot delete/modify lot matched transaction");
                    rollbackDB();
                    return false;
                }

                if (oldT.isSplit()) {
                    // this is a split transaction
                    oldT.getSplitTransactionList().forEach(st -> {
                        if (st.isTransfer(oldT.getID())) {
                            // check if the transfer transaction is updated
                            if (updateTSet.stream().noneMatch(t -> t.getID() == st.getMatchID())) {
                                // not being updated
                                deleteTransactionFromDB(st.getMatchID());
                                deleteTIDSet.add(st.getMatchID());
                            }

                            // need to update the account later
                            accountIDSet.add(-st.getCategoryID());
                        }
                    });
                } else if (oldT.isTransfer()) {
                    // oldT is a transfer.  Check if the xferT is updated
                    if (updateTSet.stream().noneMatch(t -> t.getID() == oldT.getMatchID())) {
                        // linked transaction is not being updated
                        if (oldT.getMatchSplitID() > 0) {
                            // oldT is a transfer from a split transaction,
                            // we will delete the corresponding split, adjust amount of
                            // the main transferring transaction, and update it
                            final Transaction xferT = getTransactionByID(oldT.getMatchID());
                            if (xferT == null) {
                                // didn't find the transfer transaction
                                mLogger.warn("Transaction (" + oldT.getID() + ") linked to null transaction");
                                showWarningDialog("Transaction linked to null split transaction",
                                        "Transaction cannot be linked to null split transaction",
                                        "Something is wrong.  Help is needed");
                                rollbackDB();
                                return false;
                            }
                            // delete the split transaction matching split id.
                            final List<SplitTransaction> stList = xferT.getSplitTransactionList().stream()
                                    .filter(st -> st.getID() != oldT.getMatchSplitID()).collect(Collectors.toList());
                            // check consistency of resulting amount with xferT trade action
                            final BigDecimal amount = stList.stream().map(SplitTransaction::getAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            if (((xferT.TransferTradeAction() == WITHDRAW)
                                    && (amount.compareTo(BigDecimal.ZERO) > 0))
                                    || ((xferT.TransferTradeAction() == DEPOSIT)
                                    && (amount.compareTo(BigDecimal.ZERO) < 0))) {
                                mLogger.warn("Modify/delete transaction linked to a split transaction, "
                                        + "resulting inconsistency in linked transaction");
                                showWarningDialog("Resulting inconsistency in linked transaction",
                                        "Transaction linked to split transaction",
                                        "Please edit linked split transaction.");
                                rollbackDB();
                                return false;
                            }
                            xferT.setAmount(amount.abs());
                            if (stList.size() == 1) {
                                // only one split transaction in the list, no need to split
                                // make it a simple transfer
                                // todo, maybe this should be wrapped into setSplitTransactionList
                                final SplitTransaction st = stList.get(0);
                                xferT.getCategoryIDProperty().set(st.getCategoryID());
                                if (xferT.getMemo().isEmpty())
                                    xferT.setMemo(st.getMemo());
                                if (xferT.getPayee().isEmpty())
                                    xferT.setPayee(st.getPayee());
                                xferT.setMatchID(st.getMatchID(),-1);

                                final Transaction xferXferT = getTransactionByID(st.getMatchID());
                                if (xferXferT == null) {
                                    final String message = "(" + oldT.getID() + ", " + st.getID()
                                            + ") is linked to missing transaction " + st.getMatchID();
                                    mLogger.warn(message);
                                    showWarningDialog("Missing transaction", message, "Help is needed");
                                    rollbackDB();
                                    return false;
                                }
                                stList.clear();

                                // xferXferT was linked to (xferT, st), now is only linked to xferT
                                xferXferT.setMatchID(xferXferT.getMatchID(), -1);
                                insertUpdateTransactionToDB(xferXferT);

                                updateTSet.add(xferXferT);
                                accountIDSet.add(xferXferT.getAccountID());
                            }
                            xferT.setSplitTransactionList(stList);
                            insertUpdateTransactionToDB(xferT);

                            updateTSet.add(xferT);
                        } else {
                            // oldT linked to non-split transaction
                            deleteTransactionFromDB(oldT.getMatchID());
                            deleteTIDSet.add(oldT.getMatchID());
                        }

                        accountIDSet.add(-oldT.getCategoryID());
                    }
                }

                // now ready to delete oldT
                accountIDSet.add(oldT.getAccountID());
                if (updateTSet.stream().noneMatch(t -> t.getID() == oldT.getID())) {
                    deleteTransactionFromDB(oldT.getID());
                    deleteTIDSet.add(oldT.getID());
                }

                // need to update account
                accountIDSet.add(oldT.getAccountID());
            }

            // commit to database
            commitDB();

            // done with database work, update MasterList now

            // delete these transactions from master list
            deleteTIDSet.forEach(this::deleteTransactionFromMasterList);

            // insert/update transactions to master list
            updateTSet.forEach(this::insertUpdateTransactionToMasterList);

            // update account balances
            accountIDSet.forEach(this::updateAccountBalance);

            // we are done
            return true;
        }  catch (SQLException e) {
            try {
                mLogger.error("SQLException: " + e.getSQLState(), e);
                showExceptionDialog(mPrimaryStage,"SQLException", "Database error, no changes are made",
                        SQLExceptionToString(e), e);
                rollbackDB();
            } catch (SQLException e1) {
                mLogger.error("SQLException: " + e1.getSQLState(), e1);
                showExceptionDialog(mPrimaryStage,"Database Error", "Unable to rollback to savepoint",
                        SQLExceptionToString(e1), e1);
            }
        } finally {
            try {
                releaseDBSavepoint();
            } catch (SQLException e) {
                mLogger.error("SQLException: " + e.getSQLState(), e);
                showExceptionDialog(mPrimaryStage,"Database Error",
                        "Unable to release savepoint and set DB autocommit",
                        SQLExceptionToString(e), e);
            }
        }

        // something happened that the try block didn't complete successfully.
        return false;
    }

    // Alter, including insert and delete a transaction, both in DB and in MasterList.
    // It also perform various consistency tasks.
    // if oldT is null, the newT is inserted
    // if newT is null, the oldT is deleted
    // otherwise, oldT is updated with information from newT,
    //   newT.getID() should be return the same value as oldT.getID()
    //   newT should be a valid transaction (Transaction::validate().isValid() == true)
    // returns true for success, false for failure
    boolean alterTransactionOld(Transaction oldT, Transaction newT, List<SecurityHolding.MatchInfo> newMatchInfoList) {
        // there are five possibilities each for oldT and newT:
        // null, simple transaction, a transfer transaction, a split transaction (non-transferring),
        // and splittransaction with at least one transferring splittransaction.
        // thus there are 5x5 = 25 different situations
        if (oldT == null && newT == null)
            return true; // nothing to do

        if (oldT != null) {
            if (newT == null) {
                // deleting oldT, make sure it is not covered by SELL or CVTSHT
                try {
                    List<Integer> matchList = lotMatchedBy(oldT.getID());
                    int len = matchList.size();
                    if (!matchList.isEmpty()) {
                        showWarningDialog("Transaction is lot matched",
                                "Transaction is lot matched by " + len + " transaction(s)",
                                "Cannot delete transaction which is lot matched.");
                        return false;
                    }
                } catch (SQLException e) {
                    mLogger.error("SQLException: " + e.getSQLState(), e);
                    showExceptionDialog(mPrimaryStage, "Database Error", "Unable to check LotMatch",
                            SQLExceptionToString(e), e);
                    return false;
                }
            }

            if (oldT.getMatchID() > 0) {
                // oldT is a linked transaction,
                if (oldT.getMatchSplitID() > 0) {
                    // oldT is linked to a SplitTransaction
                    showWarningDialog("Linked to A Split Transaction",
                            "Linked to a split transaction",
                            "Please edit the linked split transaction.");
                    return false;
                }
                Transaction.TradeAction oldTA = oldT.getTradeAction();
                if (oldTA == Transaction.TradeAction.DEPOSIT || oldTA == Transaction.TradeAction.WITHDRAW) {
                    Transaction oldXferT = getTransactionByID(oldT.getMatchID());
                    if (oldXferT != null) {
                        Transaction.TradeAction oldXferTA = oldXferT.getTradeAction();
                        if (oldXferTA != Transaction.TradeAction.DEPOSIT && oldXferTA != Transaction.TradeAction.WITHDRAW) {
                            showWarningDialog("Linked to An Investing Transaction",
                                    "Linked to an investing transaction",
                                    "Please edit the linked investing transaction.");
                            return false;
                        }
                    } else {
                        // it shouldn't happen
                        showWarningDialog("Linked to an null transaction",
                                "Linked to a null transaction", "Something is wrong!");
                        return false;
                    }
                }
            }
        }

        final Set<Transaction> updateTSet = new HashSet<>();
        final Set<Integer> deleteTIDSet = new HashSet<>();
        final Set<Integer> accountIDSet = new HashSet<>();
        Security security = null;
        BigDecimal price = null;

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
                // insert/update MatchInfo to database
                putMatchInfoList(newTID, newMatchInfoList);

                // handle transfer
                if(-newT.getCategoryID() > MIN_ACCOUNT_ID && !newT.isSplit()) {
                    // transfer transaction, no split
                    final Transaction.TradeAction xferTA = newT.TransferTradeAction();
                    if (xferTA == null) {
                        showWarningDialog("Warning", "Inconsistent Information",
                                "Transaction has a transfer account but incorrect TradeAction.");
                        mLogger.error("Transaction has a transfer account but incorrect TradeAction. "
                                + newT.getID() + " " + newT.getTDate() + " " + newT.getAccountID()
                                + " " + newT.getTradeAction().toString());
                        return false;
                    }
                    String newPayee;
                    switch (newT.getTradeAction()) {
                        case DEPOSIT:
                        case WITHDRAW:
                            newPayee = newT.getPayee();
                            break;
                        default:
                            newPayee = newT.getSecurityName();
                            break;
                    }

                    Transaction newLinkedT = new Transaction(-newT.getCategoryID(), newT.getTDate(), xferTA,
                            -newT.getAccountID());
                    newLinkedT.setID(newT.getMatchID());
                    newLinkedT.setMatchID(newTID, -1);
                    newLinkedT.setAmount(newT.getAmount());
                    newLinkedT.setMemo(newT.getMemo());
                    newLinkedT.setPayee(newPayee);

                    newT.setMatchID(insertUpdateTransactionToDB(newLinkedT), -1);

                    updateTSet.add(newLinkedT);
                    accountIDSet.add(newLinkedT.getAccountID());
                } else {
                    // non transfer, make sure it's not linked to anything
                    newT.setMatchID(-1, -1);
                }

                // handle transfer in split transaction
                for (SplitTransaction st : newT.getSplitTransactionList()) {
                    if (st.isTransfer(newT.getAccountID())) {
                        // this st is a transfer
                        Transaction stLinkedT = new Transaction(-st.getCategoryID(), newT.getTDate(),
                                st.getAmount().compareTo(BigDecimal.ZERO) >= 0 ?
                                        Transaction.TradeAction.WITHDRAW : Transaction.TradeAction.DEPOSIT,
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

                insertUpdateTransactionToDB(newT);  // update MatchID for newT in DB

                updateTSet.add(new Transaction(newT));  // add a copy of newT to master list
                accountIDSet.add(newT.getAccountID());

                // update price for involved security
                security = newT.getSecurityName() == null ? null :
                        getSecurityByName(newT.getSecurityName());
                price = newT.getPrice();
                if (Transaction.hasQuantity(newT.getTradeAction())
                        && (security != null) && (price != null) && !security.getTicker().isEmpty()
                        && (price.compareTo(BigDecimal.ZERO) != 0)) {
                    insertUpdatePriceToDB(security.getID(), security.getTicker(), newT.getTDate(), price, 0);
                }
            }

            if (oldT != null) {
                // need to delete or update certain oldT related info
                if (newT == null || oldT.getID() != newT.getID()) {
                    // clear MatchInfoList for oldT, if not replaced by newT
                    putMatchInfoList(oldT.getID(), new ArrayList<>());
                }

                // handle transfer in splittransaction
                for (SplitTransaction st : oldT.getSplitTransactionList()) {
                    if (st.isTransfer(oldT.getAccountID())) {
                        // this st is a transfer
                        // may need to delete linked transaction
                        final int stLinkedTID = st.getMatchID();
                        if (stLinkedTID <= 0) {
                            mLogger.error("Transfer SplitTransaction has no linked tid");
                        } else {
                            boolean updated = false;
                            // check if stLinkedTID is being updated
                            for (Transaction t : updateTSet) {
                                if (t.getID() == stLinkedTID) {
                                    updated = true;
                                    break;
                                }
                            }
                            if (!updated) {
                                // stLinkedTID is not updated, safely delete
                                mLogger.debug("deleteTransactionFromDB(" + stLinkedTID + ")");
                                deleteTransactionFromDB(stLinkedTID);
                                deleteTIDSet.add(stLinkedTID);
                            }

                            accountIDSet.add(-st.getCategoryID());
                        }
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
                        mLogger.debug("deleteTransactionFromDB("+oldLinkedTID+")");
                        deleteTransactionFromDB(oldLinkedTID);
                        deleteTIDSet.add(oldLinkedTID);
                    }

                    accountIDSet.add(-oldT.getCategoryID());
                }

                // finally deal with oldT
                boolean updated = false;
                for (Transaction t : updateTSet) {
                    if (t.getID() == oldT.getID()) {
                        updated = true;
                        break;
                    }
                }
                if (!updated) {
                    // delete oldT if it is no longer needed.
                    mLogger.debug("deleteTransactionFromDB(" + oldT.getID() + ")");
                    deleteTransactionFromDB(oldT.getID());
                    deleteTIDSet.add(oldT.getID());
                }

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
            if (security != null && price != null && price.compareTo(BigDecimal.ZERO) != 0) {
                // altered transaction might have impact on other accounts containing
                // the same security
                for (Account a : getAccountList(Account.Type.INVESTING, false, true)) {
                    if (a.hasSecurity(security)) {
                        accountIDSet.add(a.getID());
                    }
                }
            }

            for (Integer aid : accountIDSet) {
                updateAccountBalance(aid);
            }
            return true;

        } catch (SQLException e) {
            try {
                mLogger.error("SQLException: " + e.getSQLState(), e);
                showExceptionDialog(mPrimaryStage,"SQLException", "Datebase error, no changes are made",
                        SQLExceptionToString(e), e);
                rollbackDB();
            } catch (SQLException e1) {
                mLogger.error("SQLException: " + e1.getSQLState(), e1);
                showExceptionDialog(mPrimaryStage,"Database Error", "Unable to rollback to savepoint",
                        SQLExceptionToString(e1), e1);
            }
        } finally {
            try {
                releaseDBSavepoint();
            } catch (SQLException e) {
                mLogger.error("SQLException: " + e.getSQLState(), e);
                showExceptionDialog(mPrimaryStage,"Database Error",
                        "Unable to release savepoint and set DB autocommit",
                        SQLExceptionToString(e), e);
            }
        }

        return false;
    }

    // create SETTINGS table and populate DBVERSION
    private void createSettingsTable(int dbVersion) {
        try (ResultSet resultSet = getConnection().getMetaData().getTables(null, null,
                "SETTINGS", new String[]{"TABLE"});
             Statement statement = getConnection().createStatement()) {
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
            showExceptionDialog(mPrimaryStage,"Exception", "Database Exception",
                    "Failed to create SETTINGS table", e);
        }
    }

    private int getDBVersion() {
        String sqlCmd = "select VALUE from SETTINGS where NAME = '" + DBVERSIONNAME + "'";
        int dbVersion = 0;
        try (Statement statement = getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            if (resultSet.next())
                dbVersion = resultSet.getInt(1);
        } catch (SQLException e) {
            mLogger.error("SQLException: " + e.getSQLState(), e);
        }
        return dbVersion;
    }

    // todo
    // These few methods should belong to Direction, but I need to put a FIData object instead of
    // a FIID in DirectConnection
    // Later.
    private FinancialInstitution DCGetFinancialInstitution(DirectConnection directConnection)
            throws MalformedURLException {
        OFXV1Connection connection = new OFXV1Connection();
        DirectConnection.FIData fiData = getFIDataByID(directConnection.getFIID());
        BaseFinancialInstitutionData bfid = new BaseFinancialInstitutionData();
        bfid.setFinancialInstitutionId(fiData.getFIID());
        bfid.setOFXURL(new URL(fiData.getURL()));
        bfid.setName(fiData.getName());
        bfid.setOrganization(fiData.getORG());
        FinancialInstitution fi = new FinancialInstitutionImpl(bfid, connection);
        fi.setLanguage(Locale.US.getISO3Language().toUpperCase());
        return fi;
    }

    Collection<AccountProfile> DCDownloadFinancialInstitutionAccountProfiles(DirectConnection directConnection)
            throws MalformedURLException, NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException,
            UnrecoverableKeyException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException, OFXException {
        FinancialInstitution financialInstitution = DCGetFinancialInstitution(directConnection);
        String username = new String(decrypt(directConnection.getEncryptedUserName()));
        String password = new String(decrypt(directConnection.getEncryptedPassword()));

        return financialInstitution.readAccountProfiles(username, password);
    }

    // download account statement from DirectConnection
    // currently only support SPENDING account type
    void DCDownloadAccountStatement(Account account)
            throws IllegalArgumentException, MalformedURLException, NoSuchAlgorithmException,
            InvalidKeySpecException, KeyStoreException, UnrecoverableKeyException,
            NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException, SQLException, OFXException {
        Account.Type accountType = account.getType();
        if (!accountType.equals(Account.Type.SPENDING)) {
            throw new IllegalArgumentException("DCDownloadAccountStatement currently only supports SPENDING account, "
                    + account.getType() + " is currently not supported.");
        }

        AccountDC adc = getAccountDC(account.getID());
        DirectConnection directConnection = getDCInfoByID(adc.getDCID());
        FinancialInstitution financialInstitution = DCGetFinancialInstitution(directConnection);

        BankAccountDetails bankAccountDetails = new BankAccountDetails();
        bankAccountDetails.setAccountNumber(new String(decrypt(adc.getEncryptedAccountNumber())));
        bankAccountDetails.setAccountType(AccountType.valueOf(adc.getAccountType()));
        bankAccountDetails.setBankId(adc.getRoutingNumber());
        String username = new String(decrypt(directConnection.getEncryptedUserName()));
        String password = new String(decrypt(directConnection.getEncryptedPassword()));
        FinancialInstitutionAccount fiAccount = financialInstitution.loadBankAccount(bankAccountDetails,
                username, password);
        java.util.Date endDate = new java.util.Date();
        java.util.Date startDate = adc.getLastDownloadDateTime();

        // lastReconcileDate should correspond to the end of business of on that day
        // at the local time zone (use systemDefault zone for now).
        // so we use the start of the next day as the start date
        LocalDate lastReconcileDate = account.getLastReconcileDate();
        java.util.Date lastReconcileDatePlusOneDay = lastReconcileDate == null ?
                null : Date.from(lastReconcileDate.plusDays(1).atStartOfDay()
                .atZone(ZoneId.systemDefault()).toInstant());
        if (lastReconcileDatePlusOneDay != null && startDate.compareTo(lastReconcileDatePlusOneDay) < 0)
            startDate = lastReconcileDatePlusOneDay;

        AccountStatement statement = fiAccount.readStatement(startDate, endDate);
        importAccountStatement(account, statement);
        adc.setLastDownloadInfo(statement.getLedgerBalance().getAsOfDate(),
                BigDecimal.valueOf(statement.getLedgerBalance().getAmount())
                        .setScale(SecurityHolding.CURRENCYDECIMALLEN, RoundingMode.HALF_UP));
        mergeAccountDCToDB(adc);
    }

    // Banking transaction logic is currently coded in.
    private void importAccountStatement(Account account, AccountStatement statement)
            throws SQLException {
        if (statement.getTransactionList() == null)
            return;  // didn't download any transaction, do nothing

        HashSet<String> downloadedIDSet = new HashSet<>();
        for (Transaction t : account.getTransactionList()) {
            if (!t.getFITID().isEmpty())
                downloadedIDSet.add(t.getFITID());
        }

        Set<TransactionType> testedTransactionType = new HashSet<>(Arrays.asList(TransactionType.OTHER,
                TransactionType.CREDIT, TransactionType.DEBIT, TransactionType.CHECK));
        Set<TransactionType> unTestedTransactionType = new HashSet<>();

        ArrayList<Transaction> tobeImported = new ArrayList<>();
        for (com.webcohesion.ofx4j.domain.data.common.Transaction ofx4jT
                : statement.getTransactionList().getTransactions()) {
            if (downloadedIDSet.contains(ofx4jT.getId()))
                continue; // this transaction has been downloaded. skip

            // posted is always at 1200 UTC, which would convert to the same date at any time zone
            LocalDate tDate = ofx4jT.getDatePosted().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            Transaction.TradeAction ta = null;
            BigDecimal amount = ofx4jT.getBigDecimalAmount();
            Category category = new Category();
            switch (ofx4jT.getTransactionType()) {
                case CREDIT:
                case DEP:
                case DIRECTDEP:
                    ta = Transaction.TradeAction.DEPOSIT;
                    break;
                case CHECK:
                case DEBIT:
                case DIRECTDEBIT:
                case PAYMENT:
                case REPEATPMT:
                    ta = Transaction.TradeAction.WITHDRAW;
                    amount = amount.negate();
                    break;
                case INT:
                    ta = Transaction.TradeAction.DEPOSIT;
                    category = getCategoryByName("Interest Inc");
                    break;
                case DIV:
                    ta = Transaction.TradeAction.DEPOSIT;
                    category = getCategoryByName("Div Income");
                    break;
                case FEE:
                case SRVCHG:
                    ta = Transaction.TradeAction.WITHDRAW;
                    category = getCategoryByName("Fees & Charges");
                    break;
                case XFER:
                    if (amount.compareTo(BigDecimal.ZERO) >= 0)
                        ta = Transaction.TradeAction.DEPOSIT;
                    else {
                        ta = Transaction.TradeAction.WITHDRAW;
                        amount = amount.negate();
                    }
                    break;
                case ATM:
                case CASH:
                case OTHER:
                case POS:
                    if (amount.compareTo(BigDecimal.ZERO) >= 0) {
                        ta = Transaction.TradeAction.DEPOSIT;
                    } else {
                        ta = Transaction.TradeAction.WITHDRAW;
                        amount = amount.negate();
                    }
                    break;
            }

            if (!testedTransactionType.contains(ofx4jT.getTransactionType()))
                unTestedTransactionType.add(ofx4jT.getTransactionType());

            Transaction transaction = new Transaction(account.getID(), tDate, ta,
                    category == null ? 0 : category.getID());

            transaction.setAmount(amount);
            transaction.setFIDID(ofx4jT.getId());

            String refString;
            if (ofx4jT.getCheckNumber() != null) {
                refString = ofx4jT.getCheckNumber();
                if (ofx4jT.getReferenceNumber() != null)
                    refString += " " + ofx4jT.getReferenceNumber();
            } else {
                if (ofx4jT.getReferenceNumber() != null)
                    refString = ofx4jT.getReferenceNumber();
                else
                    refString = "";
            }
            transaction.setReference(refString);


            String payee;
            if (ofx4jT.getName() != null) {
                payee = ofx4jT.getName();
                if (ofx4jT.getPayee() != null && ofx4jT.getPayee().getName() != null)
                    payee += " " + ofx4jT.getPayee().getName();
            } else {
                if (ofx4jT.getPayee() != null && ofx4jT.getPayee().getName() != null)
                    payee = ofx4jT.getPayee().getName();
                else
                    payee = "";
            }
            transaction.setPayee(payee);

            String memo = ofx4jT.getMemo();
            transaction.setMemo(memo == null ? "" : memo);

            transaction.setStatus(Transaction.Status.CLEARED); // downloaded transactions are all cleared

            tobeImported.add(transaction);
        }

        if (!tobeImported.isEmpty()) {
            // we need to import some transactions
            boolean savepointSetHere = false;
            try {
                savepointSetHere = setDBSavepoint();
                for (Transaction t : tobeImported) {
                    insertUpdateTransactionToDB(t);
                }

                if (savepointSetHere)
                    commitDB();

                for (Transaction t : tobeImported)
                    insertUpdateTransactionToMasterList(t);

                updateAccountBalance(account.getID());

            } catch (SQLException e) {
                if (savepointSetHere) {
                    try {
                        mLogger.error("SQLException: " + e.getSQLState(), e);
                        String title = "Database Error";
                        String header = "Unable to insert/update SAVEDREPORTS Setting";
                        String content = SQLExceptionToString(e);
                        showExceptionDialog(mPrimaryStage,title, header, content, e);
                        rollbackDB();
                    } catch (SQLException e1) {
                        mLogger.error("SQLException: " + e1.getSQLState(), e1);
                        String title = "Database Error";
                        String header = "Unable to roll back";
                        String content = SQLExceptionToString(e1);
                        showExceptionDialog(mPrimaryStage,title, header, content, e1);
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
                        String title = "Database Error";
                        String header = "Unable to release savepoint and set DB autocommit";
                        String content = SQLExceptionToString(e);
                        showExceptionDialog(mPrimaryStage,title, header, content, e);
                    }
                }
            }

            if (!unTestedTransactionType.isEmpty()) {
                StringBuilder context = new StringBuilder();
                for (TransactionType tt : unTestedTransactionType)
                    context.append(tt.toString()).append("\n");
                showWarningDialog("Untested Download Transaction Type",
                        "The following downloaded transaction type are not fully tested, proceed with caution:",
                        context.toString());
            }
        }
    }

    private void alterAccountDCSTable() {
        try (Statement statement = getConnection().createStatement()) {
            statement.executeUpdate("alter table ACCOUNTDCS add (LASTDOWNLOADLEDGEBAL decimal(20, 4))");
        } catch (SQLException e) {
            mLogger.error("SQLException", e);
        }
    }

    private void createDirectConnectTables() {
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
        sqlCreateTable(sqlCmd);

        sqlCmd = "create table DCINFO ("
                + "ID integer NOT NULL AUTO_INCREMENT (10), "
                + "NAME varchar(128) UNIQUE NOT NULL, "
                + "FIID integer NOT NULL, "
                + "USERNAME varchar(256), "   // encrypted user name
                + "PASSWORD varchar(256), "   // encrypted password
                + "primary key (ID))";
        sqlCreateTable(sqlCmd);

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
        sqlCreateTable(sqlCmd);
    }

    // initialize database structure
    private void initDBStructure() {
        if (getConnection() == null)
            return;

        // create Settings Table first.ACCOUNTDC
        createSettingsTable(DBVERSIONVALUE);

        // create AccountDC, DCInfo, and FIData Tables
        createDirectConnectTables();
        alterAccountDCSTable();

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
                + "LASTRECONCILEDATE date, "
                + "primary key (ID));";
        sqlCreateTable(sqlCmd);

        // insert Deleted account as the first account, so the account number is MIN_ACCOUND_ID
        insertUpdateAccountToDB(new Account(-1, Account.Type.SPENDING, DELETED_ACCOUNT_NAME,
                "Placeholder for the Deleted Account", true, Integer.MAX_VALUE,
                null, BigDecimal.ZERO));

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
        // a price can be keyed by its ticker or its security id.
        // On insert, if the ticker is not empty, then security id is set to 0, otherwise, a real security id is used.
        // On retrieve, return either ticker match or security id match.
        sqlCmd = "create table PRICES ("
                + "SECURITYID integer NOT NULL, "
                + "TICKER varchar(" + SECURITYTICKERLEN + ") NOT NULL, "
                + "DATE date NOT NULL, "
                + "PRICE decimal(" + PRICE_TOTAL_LEN + "," + PRICE_FRACTION_LEN + "),"
                + "PRIMARY KEY (SECURITYID, TICKER, DATE));";
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
                + "MEMO varchar(" + TRANSACTIONMEMOLEN + ") not null, "
                + "REFERENCE varchar (" + TRANSACTIONREFLEN + ") not null, "  // reference or check number as string
                + "PAYEE varchar (" + TRANSACTIONPAYEELEN + ") not null, "
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
                + "ACCRUEDINTEREST decimal(20,4), "
                + "AMOUNTTRANSFERRED decimal(20,4), "
                + "MATCHTRANSACTIONID integer, "   // matching transfer transaction id
                + "MATCHSPLITTRANSACTIONID integer, "  // matching split
                + "FITID varchar(" + TRANSACTIONFITIDLEN + ") not null, "
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
        if (!ta.equals(SELL) && ta.equals(Transaction.TradeAction.CVTSHRT))
            return null;

        Account account = getAccountByID(transaction.getAccountID());
        List<SecurityHolding> securityHoldingList = updateAccountSecurityHoldingList(account,
                transaction.getTDate(), transaction.getID());
        List<SecurityHolding.MatchInfo> miList = getMatchInfoList(transaction.getID());
        List<CapitalGainItem> capitalGainItemList = new ArrayList<>();
        for (SecurityHolding securityHolding : securityHoldingList) {
            if (!securityHolding.getSecurityName().equals(transaction.getSecurityName()))
                continue;  // different security, skip

            // we have the right security holding here now
            BigDecimal remainCash = transaction.getAmount();
            BigDecimal remainQuantity = transaction.getQuantity();
            FilteredList<SecurityHolding.LotInfo> lotInfoList = new FilteredList<>(securityHolding.getLotInfoList());
            Map<Integer, SecurityHolding.MatchInfo> matchMap = new HashMap<>();
            if (!miList.isEmpty()) {
                // we have a matchInfo list,
                for (SecurityHolding.MatchInfo mi : miList)
                    matchMap.put(mi.getMatchTransactionID(), mi);
                lotInfoList.setPredicate(li -> matchMap.containsKey(li.getTransactionID()));
            }
            //for (SecurityHolding.LotInfo li : securityHolding.getLotInfoList()) {
            for (SecurityHolding.LotInfo li : lotInfoList) {
                BigDecimal costBasis;
                BigDecimal proceeds;
                Transaction matchTransaction;

                matchTransaction = getTransactionByID(li.getTransactionID());
                SecurityHolding.MatchInfo mi = matchMap.get(li.getTransactionID());
                BigDecimal liMatchQuantity = (mi == null) ?
                        li.getQuantity().min(remainQuantity) : mi.getMatchQuantity();

                costBasis = li.getCostBasis().multiply(liMatchQuantity).divide(li.getQuantity(), RoundingMode.HALF_UP);
                proceeds = remainCash.multiply(liMatchQuantity).divide(remainQuantity, RoundingMode.HALF_UP);
                remainCash = remainCash.subtract(proceeds);
                remainQuantity = remainQuantity.subtract(liMatchQuantity);
                if (ta.equals(SELL))
                    capitalGainItemList.add(new CapitalGainItem(transaction, matchTransaction, liMatchQuantity,
                            costBasis, proceeds));
                else
                    capitalGainItemList.add(new CapitalGainItem(transaction, matchTransaction, liMatchQuantity,
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

    ObservableList<Transaction> getMergeCandidateTransactionList(final Transaction transaction)
            throws IllegalArgumentException {
        final Account account = getAccountByID(transaction.getAccountID());
        if (!account.getType().equals(Account.Type.SPENDING)) {
            // for account type other than SPENDING,
            throw new IllegalArgumentException("Account type " + account.getType().toString()
                    + " is not supported yet");
        }
        BigDecimal netAmount = transaction.getDeposit().subtract(transaction.getPayment());
        final Predicate<Transaction> filterCriteria = t ->
                t.getTDate().isAfter(transaction.getTDate().minusWeeks(2))
                        && t.getTDate().isBefore(transaction.getTDate().plusWeeks(2))
                        && !t.getStatus().equals(Transaction.Status.RECONCILED)
                        && t.getFITID().isEmpty()
                        && t.getDeposit().subtract(t.getPayment()).compareTo(netAmount) == 0;
        return new FilteredList<>(account.getTransactionList(), filterCriteria);
    }

    ObservableList<Transaction> getStringSearchTransactionList(String searchString) {
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


    private Vault getVault() { return mVault; }

    void deleteAccountDCFromDB(int accountID) throws SQLException {
        try (Statement statement = getConnection().createStatement()) {
            statement.executeUpdate("delete from ACCOUNTDCS where ACCOUNTID = " + accountID);
        }
    }

    void mergeAccountDCToDB(AccountDC adc) throws SQLException {
        try (Statement statement = getConnection().createStatement()) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            TimeZone tzUTC = TimeZone.getTimeZone("UTC");
            dateFormat.setTimeZone(tzUTC);
            timeFormat.setTimeZone(tzUTC);
            String dateString = dateFormat.format(adc.getLastDownloadDateTime());
            String timeString = timeFormat.format(adc.getLastDownloadDateTime());
            BigDecimal ledgeBal = adc.getLastDownloadLedgeBalance();
            if (ledgeBal != null) {
                statement.executeUpdate(
                        "merge into ACCOUNTDCS (ACCOUNTID, ACCOUNTTYPE, DCID, ROUTINGNUMBER, ACCOUNTNUMBER, "
                                + "LASTDOWNLOADDATE, LASTDOWNLOADTIME, LASTDOWNLOADLEDGEBAL) values ("
                                + adc.getAccountID() + ", "
                                + "'" + adc.getAccountType() + "', "
                                + adc.getDCID() + ", "
                                + "'" + adc.getRoutingNumber() + "', "
                                + "'" + adc.getEncryptedAccountNumber() + "', "
                                + "'" + dateString + "', "
                                + "'" + timeString + "', "
                                + ledgeBal.toString() + ")");
            } else {
                statement.executeUpdate(
                        "merge into ACCOUNTDCS (ACCOUNTID, ACCOUNTTYPE, DCID, ROUTINGNUMBER, ACCOUNTNUMBER, "
                                + "LASTDOWNLOADDATE, LASTDOWNLOADTIME) values ("
                                + adc.getAccountID() + ", "
                                + "'" + adc.getAccountType() + "', "
                                + adc.getDCID() + ", "
                                + "'" + adc.getRoutingNumber() + "', "
                                + "'" + adc.getEncryptedAccountNumber() + "', "
                                + "'" + dateString + "', "
                                + "'" + timeString + "')");
            }
        }
    }

    // FIDataList
    ObservableList<DirectConnection.FIData> getFIDataList() { return mFIDataList; }
    DirectConnection.FIData getFIDataByName(String s) {
        for (DirectConnection.FIData fiData : getFIDataList()) {
            if (fiData.getName().equals(s))
                return fiData;
        }
        return null;
    }
    DirectConnection.FIData getFIDataByID(int id) {
        for (DirectConnection.FIData fiData : getFIDataList()) {
            if (id == fiData.getID())
                return fiData;
        }
        return null;
    }

    // DCInfoList
    ObservableList<DirectConnection> getDCInfoList() { return mDCInfoList; }
    DirectConnection getDCInfoByName(String s) {
        for (DirectConnection dc : getDCInfoList())
            if (dc.getName().equals(s))
                return dc;
        return null;
    }
    DirectConnection getDCInfoByID(int id) {
        for (DirectConnection dc : getDCInfoList())
            if (dc.getID() == id)
                return dc;
        return null;
    }

    @Override
    public void stop() {
        closeConnection();  // close database connection if any.
        mExecutorService.shutdown();
        Platform.exit(); // this shutdown JavaFX
        System.exit(0);  // this is needed to stop any timer tasks not otherwise stopped.
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
        mExecutorService.scheduleAtFixedRate(() -> {
            // check if current date property is still correct.
            if (CURRENTDATEPROPERTY.get().compareTo(LocalDate.now()) != 0) {
                Platform.runLater(() -> CURRENTDATEPROPERTY.set(LocalDate.now()));
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        // set error stream to a file in the current directory
        final String title = MainApp.class.getPackage().getImplementationTitle();
        final String version = MainApp.class.getPackage().getImplementationVersion();
        if (version.endsWith("SNAPSHOT"))
            KEY_OPENEDDBPREFIX = "SNAPSHOT-" + KEY_OPENEDDBPREFIX;

        mLogger.info(title + " " + version);

        launch(args);
    }
}
