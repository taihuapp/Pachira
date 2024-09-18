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

package net.taihuapp.pachira;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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

    private static String KEY_OPENEDDBPREFIX = "OPENEDDB#";
    private static final String DBVERSIONNAME = "DBVERSION";

    static final int TRANSACTIONMEMOLEN = 255;
    static final int TRANSACTIONPAYEELEN = 64;
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

    private Stage mPrimaryStage;

    private final ObjectProperty<Connection> mConnectionProperty = new SimpleObjectProperty<>(null);
    ObjectProperty<Connection> getConnectionProperty() { return mConnectionProperty; }
    private Connection getConnection() { return getConnectionProperty().get(); }

    private Savepoint mSavepoint = null;

    private final Vault mVault = new Vault();

    // mTransactionList is ordered by ID.  It's important for getTransactionByID to work
    // mTransactionListSort2 is ordered by accountID, Date, and ID
    private final ObservableList<Transaction> mTransactionList = FXCollections.observableArrayList();

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

    // Given a transaction t, find an index location in (sorted by ID) mTransactionList
    // for the matching ID.
    private int getTransactionIndex(Transaction t) {
        return Collections.binarySearch(mTransactionList, t, Comparator.comparing(Transaction::getID));
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

    // if input tid == 0, returns a Map with
    //     keys of the Map is Transaction id
    //     corresponding values are the List of SplitTransaction for the given TransactionID
    // if input tid != 0, returns a Map with one single entry
    // values in TRANSACTIONID column could be either positive or negative
    // a negative TRANSACTIONID value means the splittransaction belongs to a Reminder Transaction
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

    BooleanProperty hasMasterPasswordProperty() {
        return mHasMasterPasswordProperty;
    }

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
            MainController controller = loader.getController();
            controller.setHostServices(getHostServices());
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

    // FIDataList
    ObservableList<DirectConnection.FIData> getFIDataList() { return mFIDataList; }
    DirectConnection.FIData getFIDataByID(int id) {
        for (DirectConnection.FIData fiData : getFIDataList()) {
            if (id == fiData.getID())
                return fiData;
        }
        return null;
    }

    // DCInfoList
    ObservableList<DirectConnection> getDCInfoList() { return mDCInfoList; }

    @Override
    public void stop() {
        mExecutorService.shutdown();
        Platform.exit(); // this shutdown JavaFX
        System.exit(0);  // this is needed to stop any timer tasks not otherwise stopped.
    }

    @Override
    public void start(final Stage stage) {
        mPrimaryStage = stage;
        mPrimaryStage.setTitle(MainApp.class.getPackage().getImplementationTitle());
        initMainLayout();
        mExecutorService.scheduleAtFixedRate(() -> {
            // check if current date property is still correct.
            if (!CURRENT_DATE_PROPERTY.get().isEqual(LocalDate.now())) {
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

        mLogger.info("{} {}", title, version);

        launch(args);
    }
}
