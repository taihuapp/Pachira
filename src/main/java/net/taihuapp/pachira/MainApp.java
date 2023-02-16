/*
 * Copyright (C) 2018-2023.  Guangliang He.  All Rights Reserved.
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

import com.webcohesion.ofx4j.client.FinancialInstitution;
import com.webcohesion.ofx4j.client.impl.BaseFinancialInstitutionData;
import com.webcohesion.ofx4j.client.impl.FinancialInstitutionImpl;
import com.webcohesion.ofx4j.client.net.OFXV1Connection;
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
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.tools.ChangeFileEncryption;
import org.h2.tools.RunScript;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.sql.Date;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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

    private static final Logger mLogger = LogManager.getLogger(MainApp.class);

    static final ObjectProperty<LocalDate> CURRENT_DATE_PROPERTY = new SimpleObjectProperty<>(LocalDate.now());

    private static final int MAXOPENEDDBHIST = 5; // keep max 5 opened files
    private static String KEY_OPENEDDBPREFIX = "OPENEDDB#";
    private static final String DBOWNER = "ADMPACHIRA";
    private static final String DBPOSTFIX = ".mv.db"; // was .h2.db in h2-1.3.176, changed to .mv.db in h2-1.4.196
    private static final String URLPREFIX = "jdbc:h2:";
    private static final String CIPHERCLAUSE="CIPHER=AES;";
    private static final String IFEXISTCLAUSE="IFEXISTS=TRUE;";

    private static final String DBVERSIONNAME = "DBVERSION";

    static final int TRANSACTIONMEMOLEN = 255;
    private static final int TRANSACTIONREFLEN = 16;
    static final int TRANSACTIONPAYEELEN = 64;
    private static final int TRANSACTIONSTATUSLEN = 16;
    // OFX FITID.  Specification says up to 255 char
    private static final int TRANSACTIONFITIDLEN = 256;

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
            a -> new Observable[] { a.getHiddenFlagProperty(), a.getDisplayOrderProperty(), a.getTypeProperty(),
                    a.getCurrentBalanceProperty() });
    private final ObservableList<AccountDC> mAccountDCList = FXCollections.observableArrayList();
    private final ObservableList<Security> mSecurityList = FXCollections.observableArrayList();
    private final ObservableList<DirectConnection.FIData> mFIDataList = FXCollections.observableArrayList();
    private final ObservableList<DirectConnection> mDCInfoList = FXCollections.observableArrayList();

    private final ObjectProperty<Account> mCurrentAccountProperty = new SimpleObjectProperty<>(null);
    Account getCurrentAccount() { return mCurrentAccountProperty.get(); }

    private final BooleanProperty mHasMasterPasswordProperty = new SimpleBooleanProperty(false);

    private final ScheduledExecutorService mExecutorService = Executors.newScheduledThreadPool(1);

    ObservableList<AccountDC> getAccountDCList() { return mAccountDCList; }

    // return accounts for given type t or all account if t is null
    // return either hidden or Unhidden account based on hiddenflag, or all if hiddenflag is null
    // include DELETED_ACCOUNT if exDeleted is false.
    SortedList<Account> getAccountList(Account.Type.Group g, Boolean hidden, Boolean exDeleted) {
        FilteredList<Account> fList = new FilteredList<>(mAccountList,
                a -> (g == null || a.getType().isGroup(g)) && (hidden == null || a.getHiddenFlag() == hidden)
                        && !(exDeleted && a.getName().equals(DELETED_ACCOUNT_NAME)));

        // sort accounts by type first, then displayOrder, then ID
        return new SortedList<>(fList, Comparator.comparing(Account::getType).thenComparing(Account::getDisplayOrder)
                .thenComparing(Account::getID));
    }

    ObservableList<Security> getSecurityList() { return mSecurityList; }


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

    // Given a transaction t, find a index location in (sorted by ID) mTransactionList
    // for the matching ID.
    private int getTransactionIndex(Transaction t) {
        return Collections.binarySearch(mTransactionList, t, Comparator.comparing(Transaction::getID));
    }

    void showInformationDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(mPrimaryStage);
        alert.initModality(Modality.WINDOW_MODAL);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // work around for non-resizable alert dialog truncates message
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
            preparedStatement.setString(9, t.getMemo());
            preparedStatement.setBigDecimal(10, t.getPrice());
            preparedStatement.setBigDecimal(11, t.getQuantity());
            preparedStatement.setBigDecimal(12, t.getCommission());
            preparedStatement.setInt(13, t.getMatchID()); // matchTransactionID, ignore for now
            preparedStatement.setInt(14, t.getMatchSplitID()); // matchSplitTransactionID, ignore for now
            preparedStatement.setString(15, t.getPayee());
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
                + "(TRANSACTIONID, CATEGORYID, MEMO, AMOUNT, MATCHTRANSACTIONID, TAGID) "
                + "values (?, ?, ?, ?, ?, ?)";
        String updateSQL = "update SPLITTRANSACTIONS set "
                + "TRANSACTIONID = ?, CATEGORYID = ?, MEMO = ?, AMOUNT = ?, MATCHTRANSACTIONID = ?, "
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
                String memo = st.getMemo();
                if (memo != null && memo.length() > TRANSACTIONMEMOLEN)
                    memo = memo.substring(0, TRANSACTIONMEMOLEN);
                if (st.getID() <= 0) {
                    insertStatement.setInt(1, tid);
                    insertStatement.setInt(2, st.getCategoryID());
                    insertStatement.setString(3, memo);
                    insertStatement.setBigDecimal(4, st.getAmount());
                    insertStatement.setInt(5, st.getMatchID());
                    insertStatement.setInt(6, st.getTagID());

                    if (insertStatement.executeUpdate() == 0) {
                        throw new SQLException("Insert to splitTransactions failed");
                    }
                    try (ResultSet resultSet = insertStatement.getGeneratedKeys()) {
                        resultSet.next();
                        idArray[i] = resultSet.getInt(1); // retrieve id from resultSet
                    }
                } else {
                    idArray[i] = st.getID();

                    updateStatement.setInt(1, tid);
                    updateStatement.setInt(2, st.getCategoryID());
                    updateStatement.setString(3, memo);
                    updateStatement.setBigDecimal(4, st.getAmount());
                    updateStatement.setInt(5, st.getMatchID());
                    updateStatement.setInt(6, st.getTagID());
                    updateStatement.setInt(7, st.getID());

                    updateStatement.executeUpdate();
                }
            }
        }
        for (int i = 0; i < stList.size(); i++) {
            if (stList.get(i).getID() <= 0)
                stList.get(i).setID(idArray[i]);
        }
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
                int tagId = resultSet.getInt("TAGID");
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
                value.add(new SplitTransaction(id, cid, tagId, memo, amount, matchID));
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
                        payee, quantity, oldQuantity, memo, commission, accruedInterest, amount,
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
            return new ArrayList<>();
        }
    }

    // load MatchInfoList from database
    List<MatchInfo> getMatchInfoList(int tid) {
        List<MatchInfo> matchInfoList = new ArrayList<>();

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
                matchInfoList.add(new MatchInfo(mid, quantity));
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
    void putMatchInfoList(int tid, List<MatchInfo> matchInfoList) {
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
            for (MatchInfo matchInfo : matchInfoList) {
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
            //controller.setMainApp(this, accountID, dialogStage, stList, netAmount);
            dialogStage.showAndWait();
            return controller.getSplitTransactionList();
        } catch (IOException e) {
            mLogger.error("IOException", e);
            return null;
        }
    }

    void showSpecifyLotsDialog(Stage parent, Transaction t, List<MatchInfo> matchInfoList) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/SpecifyLotsDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Specify Lots...");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(parent);
            dialogStage.setScene(new Scene(loader.load()));
            SpecifyLotsDialogController controller = loader.getController();
//            controller.setMainApp(this, t, matchInfoList, dialogStage);
            dialogStage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }
    }

    // The input transaction is not changed.
    void showEditTransactionDialog(Stage parent, Transaction transaction) {
        List<Transaction.TradeAction> taList = getCurrentAccount().getType().isGroup(Account.Type.Group.INVESTING) ?
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
//            controller.setMainApp(this, transaction, dialogStage, accountList, defaultAccount, taList);
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
        if (!getCurrentAccount().getType().isGroup(Account.Type.Group.INVESTING)) {
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
//            controller.setMainApp(this);
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
                        // didn't find match, it's possible more than one split transaction transferring
                        // to the same account, the receiving account aggregates all into one transaction.
                        modeAgg = true; // try aggregate mode
                        mLogger.debug("Aggregate mode");
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
        if (passwords.size() != 2) {
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

    void DBToSQL() {
        // first let user select input DB file
        final File dbFile = getUserFile("Select DB File...",
                new FileChooser.ExtensionFilter("DB", "*" + DBPOSTFIX),
                null, null, false);
        if (dbFile == null)
            return;

        if (!dbFile.exists() || !dbFile.canRead()) {
            final String reason = !dbFile.exists() ? "not exist" : "not readable";
            showWarningDialog("File " + reason + "!", dbFile.getAbsolutePath() + " " + reason,
                    "Cannot continue");
            return;
        }

        // now let user select output sql file
        final File sqlFile = getUserFile("Select output sql script...",
                null, null, null, true);
        if (sqlFile == null)
            return;

        String dbName = dbFile.getAbsolutePath();
        if (dbName.endsWith(DBPOSTFIX))
            dbName = dbName.substring(0, dbName.length()-DBPOSTFIX.length());
        final List<String> passwords = showPasswordDialog("Please enter password for " + dbName,
                PasswordDialogController.MODE.ENTER);
        if (passwords.size() != 2)
            return;

        try {
            Path scriptFile = Files.createTempFile("dumpScript", ".sql");
            Files.write(scriptFile, ("SCRIPT TO '" + sqlFile.getAbsolutePath() + "'")
                    .getBytes(StandardCharsets.UTF_8));
            RunScript.execute(URLPREFIX + dbName + ";" + CIPHERCLAUSE,
                    DBOWNER, passwords.get(1) + " " + passwords.get(1) , scriptFile.toString(),
                    null, false);
            showInformationDialog("Success!","DB to SQL conversion succeed.",
                    dbName + DBPOSTFIX + " successfully converted to " + sqlFile.getAbsolutePath());
            Files.deleteIfExists(scriptFile);
        } catch (Exception e) {
            // catch Exception for runtime exception
            showExceptionDialog(getStage(), "Exception", "Converting DB to SQL failed", "", e);
            mLogger.error(e);
        }
    }

    // show a FileChooser dialog box to let user select file
    // if ef is null, then ExtensionFilter is not set
    // if initDir is null, then initial directory is not set
    // if initFile is null, then initial file is not set.
    File getUserFile(final String title, final FileChooser.ExtensionFilter ef,
                     final File initDir, final String initFileName, final boolean isSave) {
        final FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);

        if (ef != null)
            fileChooser.getExtensionFilters().add(ef);
        if (initDir != null)
            fileChooser.setInitialDirectory(initDir);
        if (initFileName != null)
            fileChooser.setInitialFileName(initFileName);

        if (isSave)
            return fileChooser.showSaveDialog(getStage());

        return fileChooser.showOpenDialog(getStage());
    }

    // create a new db from a sql file (previously converted from DBToSQL)
    void SQLToDB() {
        // first let user select input SQL file
        final File sqlFile = getUserFile("Select SQL script file...",
                null, null, null, false);
        if (sqlFile == null)
            return;

        if (!sqlFile.exists() || !sqlFile.canRead()) {
            final String reason = !sqlFile.exists() ? "not exist" : "not readable";
            showWarningDialog("File " + reason, sqlFile.getAbsolutePath() +  " " + reason,
                    "Cannot continue");
            return;
        }

        // now let user select output DB file
        File dbFile = getUserFile("Select database file...",
                new FileChooser.ExtensionFilter("DB", "*" + DBPOSTFIX), null, null, true);
        if (dbFile == null)
            return;

        String dbName = dbFile.getAbsolutePath();
        if (!dbName.endsWith(DBPOSTFIX)) {
            // user didn't enter the postfix, add it and check existence again
            dbName = dbName + DBPOSTFIX;
            dbFile = new File(dbName);

            if (dbFile.exists()) {
                final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirmation");
                alert.setHeaderText("File " + dbFile.getAbsolutePath() + " exists. "
                        + "All information in the file will be overwritten. "
                        + "Click OK to continue, click Cancel otherwise.");
                final Optional<ButtonType> result = alert.showAndWait();
                if (result.orElse(ButtonType.CANCEL) != ButtonType.OK)
                    return;
            }
        }

        // strip the postfix
        dbName = dbName.substring(0, dbName.length()-DBPOSTFIX.length());

        final List<String> passwords = showPasswordDialog("Please create new password for " + dbName,
                PasswordDialogController.MODE.NEW);
        if (passwords.size() != 2) {
            return;
        }

        try {
            // make sure dbFile does not exist before we run.
            Files.deleteIfExists(dbFile.toPath());

            RunScript.execute(URLPREFIX + dbName + ";" + CIPHERCLAUSE,
                    DBOWNER, passwords.get(1) + " " + passwords.get(1), sqlFile.getAbsolutePath(),
                    null, false);
            showInformationDialog("Success!","SQL to DB conversion succeed.",
                    sqlFile.getAbsolutePath() + " is successfully converted to " + dbName + DBPOSTFIX);
        } catch (Exception e) {
            // in addition to SQLException, RunScript.execute also converting IOException to a runtime exception
            // We need to catch Exception to catch it.
            showExceptionDialog(getStage(), "Exception", "Converting SQL to DB failed", "", e);
            mLogger.error(e);
        }
    }

    // create a new database
/*
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

            if (passwords.isEmpty() || passwords.get(1) == null) {
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
            final String title;
            final String header;
            int errorCode = e.getErrorCode();
            // 90049 -- bad encryption password
            // 28000 -- wrong user name or password
            // 90020 -- Database may be already in use: locked by another process
            switch (errorCode) {
                case 90049:
                case 28000:
                    title = "Bad password";
                    header = "Wrong password for " + dbName;
                    break;
                case 90020:
                    title = "File locked";
                    header = "File may be already in use, locked by another process.";
                    break;
                case 90013:
                    title = "File not exist";
                    header = "File may be moved or deleted.";
                    break;
                default:
                    title = "SQL Error";
                    header = "Error Code: " + errorCode;
                    break;
            }
            showExceptionDialog(getStage(), title, header, SQLExceptionToString(e), e);
        } catch (ClassNotFoundException e){
            mLogger.error("ClassNotFoundException", e);
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
*/

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
        if (newV == 10) {
            try (Statement statement = getConnection().createStatement()) {
                // change account table type column size
                statement.executeUpdate("alter table ACCOUNTS alter column TYPE varchar(16) not null");
                statement.executeUpdate("update ACCOUNTS set TYPE = 'CHECKING' where TYPE = 'SPENDING'");
                statement.executeUpdate("update ACCOUNTS set TYPE = 'BROKERAGE' where TYPE = 'INVESTING'");
                statement.executeUpdate("update ACCOUNTS set TYPE = 'HOUSE' where TYPE = 'PROPERTY'");
                statement.executeUpdate("update ACCOUNTS set TYPE = 'LOAN' where TYPE = 'DEBT'");
                statement.executeUpdate("alter table SETTINGS alter column VALUE varchar(255) not null");
                statement.executeUpdate("alter table SPLITTRANSACTIONS drop column PAYEE");
                statement.executeUpdate(mergeSQL);
            }
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

    // create SETTINGS table and populate DBVERSION
    private void createSettingsTable(int dbVersion) {
        try (ResultSet resultSet = getConnection().getMetaData().getTables(null, null,
                "SETTINGS", new String[]{"TABLE"});
             Statement statement = getConnection().createStatement()) {
            if (!resultSet.next()) {
                // Settings table is not created yet, create it now
                sqlCreateTable("create table SETTINGS (" +
                        "NAME varchar(32) UNIQUE NOT NULL," +
                        "VALUE varchar(255) NOT NULL," +
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
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            int columnType = resultSetMetaData.getColumnType(1);
            if (resultSet.next()) {
                if (columnType == Types.INTEGER)
                    dbVersion = resultSet.getInt(1);
                else
                    dbVersion = Integer.parseInt(resultSet.getString(1));
            }
        } catch (SQLException e) {
            mLogger.error("SQLException: " + e.getSQLState(), e);
        }
        return dbVersion;
    }

    // todo
    // These few methods should belong to Direct Connection, but I need to put a FIData object instead of
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

    static String SQLExceptionToString(SQLException e) {
        StringBuilder s = new StringBuilder();
        while (e != null) {
            s.append("--- SQLException ---" + "  SQL State: ").append(e.getSQLState())
                    .append("  Message:   ").append(e.getMessage()).append("\n");
            e = e.getNextException();
        }
        return s.toString();
    }

    // init the main layout
    private void initMainLayout() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/MainLayout.fxml"));
            mPrimaryStage.setScene(new Scene(loader.load()));
            mPrimaryStage.show();
            //((MainController) loader.getController()).setMainApp(this);
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
                                + ledgeBal + ")");
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
        mExecutorService.shutdown();
        Platform.exit(); // this shutdown JavaFX
        System.exit(0);  // this is needed to stop any timer tasks not otherwise stopped.
    }

    @Override
    public void init() { mPrefs = Preferences.userNodeForPackage(MainApp.class); }

    @Override
    public void start(final Stage stage) {
        mPrimaryStage = stage;
        mPrimaryStage.setTitle(MainApp.class.getPackage().getImplementationTitle());
        initMainLayout();
        mExecutorService.scheduleAtFixedRate(() -> {
            // check if current date property is still correct.
            if (CURRENT_DATE_PROPERTY.get().compareTo(LocalDate.now()) != 0) {
                Platform.runLater(() -> CURRENT_DATE_PROPERTY.set(LocalDate.now()));
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
