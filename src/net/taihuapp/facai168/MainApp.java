package net.taihuapp.facai168;

import javafx.application.Application;
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
import org.h2.tools.ChangeFileEncryption;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static net.taihuapp.facai168.Transaction.TradeAction.CVTSHRT;
import static net.taihuapp.facai168.Transaction.TradeAction.SELL;

public class MainApp extends Application {

    // these characters are not allowed in account names and
    // security names
    static final Set<Character> BANNED_CHARACTER_SET = new HashSet<>(Arrays.asList(
            new Character[] {'/', ':', ']', '[', '|', '^'}));
    static boolean hasBannedCharacter(String name) {
        for (int i = 0; i < name.length(); i++)
            if (BANNED_CHARACTER_SET.contains(name.charAt(i)))
                return true;
        return false;
    }

    private static int MAXOPENEDDBHIST = 5; // keep max 5 opened files
    private static String KEY_OPENEDDBPREFIX = "OPENEDDB#";
    private static String DBOWNER = "FC168ADM";
    private static String DBPOSTFIX = ".h2.db"; // it is changes to mv.db in H2 1.4beta when MVStore enabled
    private static String URLPREFIX = "jdbc:h2:";
    private static String CIPHERCLAUSE="CIPHER=AES;";
    private static String IFEXISTCLAUSE="IFEXISTS=TRUE;";


    private static int ACCOUNTNAMELEN = 40;
    private static int ACCOUNTDESCLEN = 256;
    private static int SECURITYTICKERLEN = 16;
    private static int SECURITYNAMELEN = 64;

    private static int CATEGORYNAMELEN = 40;
    private static int CATEGORYDESCLEN = 256;

    private static int TRANSACTIONMEMOLEN = 64;
    private static int TRANSACTIONREFLEN = 8;
    private static int TRANSACTIONPAYEELEN = 64;
    private static int TRANSACTIONTRACEACTIONLEN = 16;
    private static int TRANSACTIONTRANSFERREMINDERLEN = 40;
    private static int ADDRESSLINELEN = 32;

    private static int AMORTLINELEN = 32;

    private static final int PRICE_TOTAL_LEN = 20;
    final static int PRICE_FRACTION_LEN = 8;

    private static final int QUANTITY_TOTAL_LEN = 20;
    static final int QUANTITY_FRACTION_LEN = 8;


    // Category And Transfer Account are often shared as the following:
    // String     #    Meaning
    // Blank      0    no transfer, no category
    // [Deleted] -??   transfer to deleted account
    // [A Name]  -AID  transfer to account with id = AID
    // Cat Name   CID  category with id = CID
    static final int MIN_ACCOUNT_ID = 10;
    static final String DELETED_ACCOUNT_NAME = "Deleted Account";
    private static final int MIN_CATEGORY_ID = 10;

    private Preferences mPrefs;
    private Stage mPrimaryStage;
    private Connection mConnection = null;  // todo replace Connection with a custom db class object

    // mTransactionList is ordered by ID.  It's important for getTransactionByID to work
    // mTransactionListSort2 is ordered by accountID, Date, and ID
    private final ObservableList<Transaction> mTransactionList = FXCollections.observableArrayList();
    private final SortedList<Transaction> mTransactionListSort2 = new SortedList<>(mTransactionList,
            Comparator.comparing(Transaction::getAccountID).thenComparing(Transaction::getTDate)
                    .thenComparing(Transaction::getID));

    // we want to watch the change of hiddenflag and displayOrder
    private ObservableList<Account> mAccountList = FXCollections.observableArrayList(
            a -> new Observable[] { a.getHiddenFlagProperty(), a.getDisplayOrderProperty() });
    private ObservableList<Tag> mTagList = FXCollections.observableArrayList();
    private ObservableList<Category> mCategoryList = FXCollections.observableArrayList();
    private ObservableList<Security> mSecurityList = FXCollections.observableArrayList();
    private ObservableList<SecurityHolding> mSecurityHoldingList = FXCollections.observableArrayList();
    private SecurityHolding mRootSecurityHolding = new SecurityHolding("Root");

    private Map<Integer, Reminder> mReminderMap = new HashMap<>();

    private ObservableList<ReminderTransaction> mReminderTransactionList = FXCollections.observableArrayList();

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

            int idx = getTransactionIndexByID(tid);
            if (idx < 0) {
                System.err.println("Transaction " + tid + " not in master list? How did this happen?");
            } else {
                mTransactionList.remove(idx);
            }
        } catch (SQLException e) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle("Database Error");
            alert.setHeaderText("Unable to delete Transactions, id = " + tid);
            alert.setContentText(SQLExceptionToString(e));
            alert.showAndWait();
        }
    }

    // For Transaction with ID tid, this method returns its location index in
    // (sorted by ID) mTransactionIndex.
    // If transaction with tid is not found in mTransactionList by binarySearch
    // return -(1+insertLocation).
    private int getTransactionIndexByID(int tid) {
        // make up a dummy transaction for search
        Transaction t = new Transaction(-1, LocalDate.MAX, Transaction.TradeAction.BUY, 0);
        t.setID(tid);
        return Collections.binarySearch(mTransactionList, t, Comparator.comparing(Transaction::getID));
    }

    Transaction getTransactionByID(int tid) {
        int idx = getTransactionIndexByID(tid);
        if (idx > 0)
            return mTransactionList.get(idx);
        return null;
    }

    // http://code.makery.ch/blog/javafx-dialogs-official/
    private void showExceptionDialog(String title, String header, String content, Exception e) {
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
                }
                String itemName = rs.getString("ITEMNAME");
                if (itemName != null)
                switch (ReportDialogController.ItemName.valueOf(itemName)) {
                    case ACCOUNTID:
                        int accountID = Integer.parseInt(rs.getString("ITEMVALUE"));
                        int selectedOrder = rs.getInt("SELECTEDORDER");
                        setting.getSelectedAccountList().add(new ReportDialogController.SelectedAccount(
                                getAccountByID(accountID), selectedOrder));
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
                        System.err.println("loadReportSetting: ItemName " + itemName + " not implemented yet");
                        break;
                }
            }
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
        return settingList;
    }

    int insertUpdateReportSettingToDB(ReportDialogController.Setting setting) {
        String sqlCmd;
        int id = setting.getID();
        if (id <= 0) {
            // new setting, insert
            sqlCmd = "insert into SAVEDREPORTS "
                    + "(NAME, TYPE, DATEPERIOD, SDATE, EDATE, FREQUENCY) "
                    + "values (?, ?, ?, ?, ?, ?)";
        } else {
            sqlCmd = "update SAVEDREPORTS set "
                    + "NAME = ?, TYPE = ?, DATEPERIOD = ?, SDATE = ?, EDATE = ?, FREQUENCY = ? "
                    + "where ID = ?";
        }
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            mConnection.setAutoCommit(false);
            preparedStatement.setString(1, setting.getName());
            preparedStatement.setString(2, setting.getType().name());
            preparedStatement.setString(3, setting.getDatePeriod().name());
            preparedStatement.setDate(4, Date.valueOf(setting.getStartDate()));
            preparedStatement.setDate(5, Date.valueOf(setting.getEndDate()));
            preparedStatement.setString(6, setting.getFrequency().name());
            if (id > 0)
                preparedStatement.setInt(7, id);
            preparedStatement.executeUpdate();
            if (id <= 0) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    if (resultSet.next())
                        setting.setID(id = resultSet.getInt(1));
                    else
                        throw new SQLException(("Insert into SAVEDREPORTS failed."));
                } catch (SQLException e) {
                    System.err.println(e.getMessage());
                    throw e;
                }
            }

            // now deal with setting details
            String sqlCmd1 = "insert into SAVEDREPORTDETAILS (REPORTID, ITEMNAME, ITEMVALUE, SELECTEDORDER) "
                    + "values (?, ?, ?, ?)";
            try (Statement statement = mConnection.createStatement();
                 PreparedStatement preparedStatement1 = mConnection.prepareStatement(sqlCmd1)) {
                statement.execute("delete from SAVEDREPORTDETAILS where REPORTID = " + id);
                // loop through account list
                for (ReportDialogController.SelectedAccount sa : setting.getSelectedAccountList()) {
                    preparedStatement1.setInt(1, id);
                    preparedStatement1.setString(2, ReportDialogController.ItemName.ACCOUNTID.name());
                    preparedStatement1.setString(3, String.valueOf(sa.getID()));
                    preparedStatement1.setInt(4, sa.getSelectedOrder());

                    preparedStatement1.executeUpdate();
                }

                for (Integer cid : setting.getSelectedCategoryIDSet()) {
                    preparedStatement1.setInt(1, id);
                    preparedStatement1.setString(2, ReportDialogController.ItemName.CATEGORYID.name());
                    preparedStatement1.setString(3, String.valueOf(cid));
                    preparedStatement1.setInt(4, 0);  // not used

                    preparedStatement1.executeUpdate();
                }

                for (Integer sid : setting.getSelectedSecurityIDSet()) {
                    preparedStatement1.setInt(1, id);
                    preparedStatement1.setString(2, ReportDialogController.ItemName.SECURITYID.name());
                    preparedStatement1.setString(3, String.valueOf(sid));
                    preparedStatement1.setInt(4, 0);  // not used

                    preparedStatement1.executeUpdate();
                }

                for (Transaction.TradeAction ta : setting.getSelectedTradeActionSet()) {
                    preparedStatement1.setInt(1, id);
                    preparedStatement1.setString(2, ReportDialogController.ItemName.TRADEACTION.name());
                    preparedStatement1.setString(3, ta.name());
                    preparedStatement1.setInt(4, 0);  // not used

                    preparedStatement1.executeUpdate();
                }
            } catch (SQLException e) {
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
                return 0;
            }
            mConnection.commit();
            return id;
        } catch (SQLException e) {
            String title = "Database Error";
            String header = "Unable to insert/update SAVEDREPORTS Setting";
            String content = SQLExceptionToString(e);
            showExceptionDialog(title, header, content, e);
        } finally {
            try {
                mConnection.setAutoCommit(true);
            } catch (SQLException e) {
                String title = "Database Error";
                String header = "Unable to set DB autocommit";
                String content = SQLExceptionToString(e);
                showExceptionDialog(title, header, content, e);
            }
        }

        return 0;
    }

    // insert or update reminder
    // return affected reminder id if success, 0 for failure.
    int insertUpdateReminderToDB(Reminder reminder) {
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
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
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
            return reminder.getID();
        } catch (SQLException e) {
            showExceptionDialog("Database Error", "insert/update Reminder failed",
                    SQLExceptionToString(e), e);
        }
        return 0; // failed
    }

    boolean insertReminderTransactions(ReminderTransaction rt, Transaction t) {
        int tid = t == null ? 0 : t.getID();

        rt.setTransactionID(tid);
        String sqlCmd = "insert into REMINDERTRANSACTIONS (REMINDERID, DUEDATE, TRANSACTIONID) "
                + "values (?, ?, ?)";
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            preparedStatement.setInt(1, rt.getReminder().getID());
            preparedStatement.setDate(2, Date.valueOf(rt.getDueDate()));
            preparedStatement.setInt(3, tid);
            preparedStatement.executeUpdate();
            return true;
        } catch (SQLException e) {
            showExceptionDialog("Database Error", "Failed to insert into ReminderTransactions!",
                    SQLExceptionToString(e), e);
        }
        return false;
    }

    // insert or update the input transaction into DB and master transaction list in memory
    // return affected transaction id if success, 0 for failure.
    int insertUpdateTransactionToDB(Transaction t) {
        String sqlCmd;
        // be extra careful about the order of the columns
        if (t.getID() <= 0) {
            sqlCmd = "insert into TRANSACTIONS " +
                    "(ACCOUNTID, DATE, AMOUNT, TRADEACTION, SECURITYID, " +
                    "CLEARED, CATEGORYID, TAGID, MEMO, PRICE, QUANTITY, COMMISSION, " +
                    "MATCHTRANSACTIONID, MATCHSPLITTRANSACTIONID, PAYEE, ADATE, OLDQUANTITY, " +
                    "REFERENCE) " +
                    "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            sqlCmd = "update TRANSACTIONS set " +
                    "ACCOUNTID = ?, DATE = ?, AMOUNT = ?, TRADEACTION = ?, " +
                    "SECURITYID = ?, CLEARED = ?, CATEGORYID = ?, TAGID = ?, MEMO = ?, " +
                    "PRICE = ?, QUANTITY = ?, COMMISSION = ?, " +
                    "MATCHTRANSACTIONID = ?, MATCHSPLITTRANSACTIONID = ?, " +
                    "PAYEE = ?, ADATE = ?, OLDQUANTITY = ? , REFERENCE = ? " +
                    "where ID = ?";
        }
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            mConnection.setAutoCommit(false);

            preparedStatement.setInt(1, t.getAccountID());
            preparedStatement.setDate(2, Date.valueOf(t.getTDate()));
            preparedStatement.setBigDecimal(3, t.getAmount());
            preparedStatement.setString(4, t.getTradeActionProperty().get().name());
            Security security = getSecurityByName(t.getSecurityName());
            if (security != null)
                preparedStatement.setObject(5, security.getID());
            else
                preparedStatement.setObject(5, null);
            preparedStatement.setInt(6, 0); // cleared
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
            if (t.getID() > 0)
                preparedStatement.setInt(19, t.getID());

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

            mConnection.commit();

            int idx = Collections.binarySearch(mTransactionList, t, Comparator.comparing(Transaction::getID));
            if (idx < 0) {
                // not in the list, insert a copy of t
                mTransactionList.add(-(1+idx), t);
            } else {
                // exist in list, replace with a copy of t
                mTransactionList.set(idx, t);
            }

            return t.getID();
        } catch (SQLException e) {
            // something went wrong, roll back
            try {
                showExceptionDialog("Database Error", "update transaction failed", SQLExceptionToString(e), e);
                mConnection.rollback();
            } catch (SQLException e1) {
                // error in rollback
                showExceptionDialog("Database Error", "Failed to rollback transaction database update",
                        SQLExceptionToString(e1), e1);
            }
        } finally {
            try {
                mConnection.setAutoCommit(true);
            } catch (SQLException e) {
                showExceptionDialog("Database Error", "set autocommit failed", SQLExceptionToString(e), e);
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
                            + "(TRANSACTIONID, CATEGORYID, MEMO, AMOUNT, MATCHTRANSACTIONID) "
                            + "values (?, ?, ?, ?, ?)";
        String updateSQL = "update SPLITTRANSACTIONS set "
                            + "TRANSACTIONID = ?, CATEGORYID = ?, MEMO = ?, AMOUNT = ?, MATCHTRANSACTIONID = ? "
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
                if (st.getID() <= 0) {
                    insertStatement.setInt(1, tid);
                    insertStatement.setInt(2, st.getCategoryID());
                    insertStatement.setString(3, st.getMemo());
                    insertStatement.setBigDecimal(4, st.getAmount());
                    insertStatement.setInt(5, st.getMatchID());

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
                    updateStatement.setString(3, st.getMemo());
                    updateStatement.setBigDecimal(4, st.getAmount());
                    updateStatement.setInt(5, st.getMatchID());
                    updateStatement.setInt(6, st.getID());

                    updateStatement.executeUpdate();
                }
            }
        }
        for (int i = 0; i < stList.size(); i++) {
            if (stList.get(i).getID() <= 0)
                stList.get(i).setID(idArray[i]);
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
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
            }

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle(title);
            alert.setHeaderText(headerText);
            alert.showAndWait();
            return false;
        } catch (NullPointerException e) {
            System.err.println("mConnection is null");
            e.printStackTrace();
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
                System.err.println("insertUpdatePriceToDB error");
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
            }
        } catch (NullPointerException e) {
            if (mode != 0)
                e.printStackTrace();
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
    private int mapCategoryOrAccountNameToID(String name) {
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

    // take a transaction id, and a list of split BT, insert the list of bt into database
    // return the number of splitBT inserted, which should be same as the length of
    // the input list
    private int insertSplitBTToDB(int btID, List<QIFParser.BankTransaction.SplitBT> splitBTList) {
        int cnt = 0;

        String sqlCmd = "insert into SPLITTRANSACTIONS (TRANSACTIONID, CATEGORYID, MEMO, AMOUNT, PERCENTAGE) "
                + "values (?, ?, ?, ?, ?)";

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            for (QIFParser.BankTransaction.SplitBT sbt : splitBTList) {
                preparedStatement.setInt(1, btID);
                preparedStatement.setInt(2, mapCategoryOrAccountNameToID(sbt.getCategory()));
                preparedStatement.setString(3, sbt.getMemo());
                preparedStatement.setBigDecimal(4, sbt.getAmount());
                preparedStatement.setBigDecimal(5, sbt.getPercentage());

                preparedStatement.executeUpdate();
                cnt++;
            }
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
        return cnt;
    }

    // return the inserted rowID, -1 if error.
    private int insertAddressToDB(List<String> address) {
        int rowID = -1;
        int nLines = Math.min(6, address.size());  // max 6 lines
        String sqlCmd = "insert into ADDRESSES (";
        for (int i = 0; i < nLines; i++) {
            sqlCmd += ("LINE" + i);
            if (i < nLines-1)
                sqlCmd += ",";
        }
        sqlCmd += ") values (";
        for (int i = 0; i < nLines; i++) {
            sqlCmd += "?";
            if (i < nLines-1)
                sqlCmd += ",";
        }
        sqlCmd += ")";

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            for (int i = 0; i < nLines; i++)
                preparedStatement.setString(i+1, address.get(i));

            if (preparedStatement.executeUpdate() != 0) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    if (resultSet.next())
                        rowID = resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
        return rowID;
    }

    // return inserted rowID or -1 for failure
    private int insertAmortizationToDB(String[] amortLines) {
        int rowID = -1;
        int nLines = 7;
        String sqlCmd = "insert into AMORTIZATIONLINES (";
        for (int i = 0; i < nLines; i++) {
            sqlCmd += ("LINE" + i);
            if (i < nLines-1)
                sqlCmd += ",";
        }
        sqlCmd += ") values (";
        for (int i = 0; i < nLines; i++) {
            sqlCmd += "?";
            if (i < nLines-1)
                sqlCmd += ",";
        }
        sqlCmd += ")";

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            for (int i = 0; i < nLines; i++)
                preparedStatement.setString(i+1, amortLines[i]);

            if (preparedStatement.executeUpdate() != 0) {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    if (resultSet.next())
                        rowID = resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
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
            System.err.println("Account [" + accountName + "] not found, nothing inserted");
            return -1;
        }
        if (account.getType() == Account.Type.INVESTING) {
            System.err.println("Account " + account.getName() + " is not an investing account");
            return -1;
        }

        boolean success = true;

        mConnection.setAutoCommit(false);

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
                    "(ACCOUNTID, DATE, AMOUNT, CLEARED, CATEGORYID, " +
                    "MEMO, REFERENCE, " +
                    "PAYEE, SPLITFLAG, ADDRESSID, AMORTIZATIONID, TRADEACTION" +
                    ") values (?,?,?,?,?,?,?,?,?,?,?,?)";

            try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
                String categoryOrTransferStr = bt.getCategoryOrTransfer();
                int categoryOrTransferID = mapCategoryOrAccountNameToID(categoryOrTransferStr);
                Transaction.TradeAction ta = mapBankingTransactionTA(categoryOrTransferID, bt.getTAmount());
                preparedStatement.setInt(1, account.getID());
                preparedStatement.setDate(2, Date.valueOf(bt.getDate()));
                preparedStatement.setBigDecimal(3, bt.getTAmount().abs());
                preparedStatement.setInt(4, bt.getCleared());
                preparedStatement.setInt(5, categoryOrTransferID);
                preparedStatement.setString(6, bt.getMemo());
                preparedStatement.setString(7, bt.getReference());
                preparedStatement.setString(8, bt.getPayee());
                preparedStatement.setBoolean(9, !splitList.isEmpty());
                preparedStatement.setInt(10, addressID);
                preparedStatement.setInt(11, amortID);
                preparedStatement.setString(12, ta.name());
                preparedStatement.executeUpdate();
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    if (resultSet.next()) {
                        rowID = resultSet.getInt(1);
                    }
                }
            } catch (SQLException e) {
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
            }

            if (rowID < 0)
                success = false;
        }

        if (success && !splitList.isEmpty() && (insertSplitBTToDB(rowID, splitList) != splitList.size()))
            success = false;

        if (!success) {
            mConnection.rollback();
        } else {
            mConnection.commit();
        }
        mConnection.setAutoCommit(true);

        return rowID;
    }

    // insert trade transaction to database and returns rowID
    // return -1 if failed
    private int insertTransactionToDB(QIFParser.TradeTransaction tt) throws SQLException {
        int rowID = -1;
        Account account = getAccountByName(tt.getAccountName());
        if (account == null) {
            System.err.println("Account [" + tt.getAccountName() + "] not found, nothing inserted");
            return -1;
        }

        // temporarily unset autocommit
        mConnection.setAutoCommit(false);

        String sqlCmd = "insert into TRANSACTIONS " +
                "(ACCOUNTID, DATE, AMOUNT, TRADEACTION, SECURITYID, " +
                "CLEARED, CATEGORYID, MEMO, PRICE, QUANTITY, COMMISSION, OLDQUANTITY) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)){
            preparedStatement.setInt(1, account.getID());
            preparedStatement.setDate(2, Date.valueOf(tt.getDate()));
            preparedStatement.setBigDecimal(3, tt.getTAmount());
            preparedStatement.setString(4, tt.getAction().name());
            String name = tt.getSecurityName();
            if (name != null && name.length() > 0) {
                //preparedStatement.setInt(5, getSecurityByName(name).getID());
                preparedStatement.setObject(5, getSecurityByName(name).getID());
            } else {
                preparedStatement.setObject(5, null);
            }
            preparedStatement.setInt(6, tt.getCleared());
            preparedStatement.setInt(7, mapCategoryOrAccountNameToID(tt.getCategoryOrTransfer()));
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
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
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

            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle(title);
            alert.setHeaderText(headerText);
            alert.showAndWait();
        } catch (NullPointerException e) {
            System.err.println("mConnection is null");
            e.printStackTrace();
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
                } catch (SQLException e) {
                    System.err.println(e.getMessage());
                    throw e;
                }
            }

            // update account list
            Account a = getAccountByID(account.getID());
            if (a == null) {
                // new account, add
                mAccountList.add(account);
            } else if (a != account) {
                // old account, replace
                System.err.println("insertupdateaccounttodb, how did we get here");
                mAccountList.set(mAccountList.indexOf(a), account);
            }

        } catch (SQLException e) {
            String title = "Database Error";
            String headerText = "Unknown DB error";
            if (e.getErrorCode() == 23505) {
                title = "Duplicate Account Name";
                headerText = "Account name " + account.getName() + " is already taken.";
            } else {
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
            }

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle(title);
            alert.setHeaderText(headerText);
            alert.showAndWait();

        } catch (NullPointerException e) {
            System.err.println("mConnection is null");
            e.printStackTrace();
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
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        } finally {
            try {
                if (statement != null) {
                    statement.close();
                }
                if (resultSet != null) {
                    resultSet.close();
                }
            } catch (SQLException e) {
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
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
                        accountID, categoryID, transferAccountID, tagID, memo, ds));
            }
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
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
                    int numerator = 0;
                    int cnt = 0;
                    for (int i = sfrtList.size()-1; i >= 0 && cnt < reminder.getEstimateCount(); i--) {
                        ReminderTransaction rt = sfrtList.get(i);
                        int tid = rt.getTransactionID();
                        if (tid > 0) {
                            int idx = getTransactionIndexByID(tid);
                            if (idx > 0) {
                                amt = amt.add(mTransactionList.get(idx).getAmount());
                                numerator++;
                            } else {
                                System.err.println("initReminderTransactionList: Transaction " + tid + " not found?!");
                            }
                        }
                        cnt++; // don't count the w
                    }
                    if (numerator > 0)
                        amt = amt.divide(BigDecimal.valueOf(numerator), amt.scale());
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
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
    }

    private void initTagList() {
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
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
    }

    private void initCategoryList() {
        if (mConnection == null) return;

        mCategoryList.clear();
        Statement statement = null;
        ResultSet resultSet = null;
        String sqlCmd = "select * from CATEGORIES";
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
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        } finally {
            try {
                if (statement != null) statement.close();
                if (resultSet != null) resultSet.close();
            } catch (SQLException e) {
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
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

    void updateAccountBalance(int accountID) {
        Account account = getAccountByID(accountID);
        if (account == null) {
            System.err.println("Invalid account ID: " + accountID);
            return;
        }

        // update holdings and balance for INVESTING account
        if (account.getType() == Account.Type.INVESTING) {
            List<SecurityHolding> shList = updateAccountSecurityHoldingList(account, LocalDate.now(), 0);
            SecurityHolding totalHolding = shList.get(shList.size() - 1);
            if (totalHolding.getSecurityName().equals("TOTAL")) {
                account.setCurrentBalance(totalHolding.getMarketValue());
            } else {
                System.err.println("Missing Total Holding in account " + account.getName() + " holding list");
            }

            ObservableList<Security> accountSecurityList = account.getCurrentSecurityList();
            accountSecurityList.clear();
            for (SecurityHolding sh : shList) {
                String securityName = sh.getSecurityName();
                if (!securityName.equals("TOTAL") && !securityName.equals("CASH")) {
                    Security se = getSecurityByName(securityName);
                    if (se == null) {
                        System.err.println("Failed to find security with name: '" + securityName + "'");
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
                mAccountList.add(new Account(id, type, name, description, hiddenFlag, displayOrder, null));
            }
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
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

    void initSecurityList() {
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
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
    }

    private List<SplitTransaction> loadSplitTransactions(int tid) {
        List<SplitTransaction> stList = new ArrayList<>();

        String sqlCmd = "select st.*, t.ACCOUNTID "
                + "from SPLITTRANSACTIONS st inner join TRANSACTIONS t "
                + "where st.TRANSACTIONID = t.ID and t.ID = " + tid + " order by st.ID";
        try (Statement statement = mConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                int cid = resultSet.getInt("CATEGORYID");
                String memo = resultSet.getString("MEMO");
                BigDecimal amount = resultSet.getBigDecimal("AMOUNT");
                if (amount == null) {
                    amount = BigDecimal.ZERO;
                }
                // todo
                // do we need percentage?
                // ignore it for now
                int matchID = resultSet.getInt("MATCHTRANSACTIONID");
                stList.add(new SplitTransaction(id, cid, memo, amount, matchID));
            }
        }  catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
        return stList;
    }

    // initialize mTransactionList order by ID
    // mSecurityList should be loaded prior this call.
    private void initTransactionList() {
        mTransactionList.clear();
        if (mConnection == null)
            return;

        String sqlCmd = "select * from TRANSACTIONS order by ID";
        try (Statement statement = mConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                int aid = resultSet.getInt("ACCOUNTID");
                LocalDate tDate = resultSet.getDate("DATE").toLocalDate();
                Date sqlDate = resultSet.getDate("ADATE");
                LocalDate aDate = tDate;
                if (sqlDate != null)
                    aDate = sqlDate.toLocalDate();
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
                    System.err.println("Bad trade action value in transaction " + id);
                    continue;
                }
                int securityID = resultSet.getInt("SECURITYID");
                BigDecimal quantity = resultSet.getBigDecimal("QUANTITY");
                BigDecimal commission = resultSet.getBigDecimal("COMMISSION");
                BigDecimal price = resultSet.getBigDecimal("PRICE");
                BigDecimal oldQuantity = resultSet.getBigDecimal("OLDQUANTITY");
                int matchID = resultSet.getInt("MATCHTRANSACTIONID");
                int matchSplitID = resultSet.getInt("MATCHSPLITTRANSACTIONID");

                String name = null;
                if (securityID > 0) {
                    Security security = getSecurityByID(securityID);
                    if (security != null)
                        name = security.getName();
                }
                Transaction transaction = new Transaction(id, aid, tDate, aDate, tradeAction, name, reference,
                        payee, price, quantity, oldQuantity, memo, commission, amount, cid, tagID, matchID,
                        matchSplitID);

                if (resultSet.getBoolean("SPLITFLAG")) {
                    transaction.setSplitTransactionList(loadSplitTransactions(transaction.getID()));
                }

                mTransactionList.add(transaction);
            }
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
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

    void showReportDialog(ReportDialogController.Setting setting) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("ReportDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            ReportDialogController controller = loader.getController();
            if (controller == null) {
                System.err.println("Null ReportDialogController");
                return;
            }
            controller.setMainApp(setting, this, dialogStage);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void showAccountListDialog() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("AccountListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Account List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            AccountListDialogController controller = loader.getController();
            if (controller == null) {
                System.err.println("Null controller for AccountListDialog");
                return;
            }
            controller.setMainApp(this, dialogStage);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void showBillIncomeReminderDialog() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("ReminderTransactionListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Reminder Transaction List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            ReminderTransactionListDialogController controller = loader.getController();
            if (controller == null) {
                System.err.println("Null controller for ReminderTransactionListDialog");
                return;
            }
            controller.setMainApp(this, dialogStage);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void showSecurityListDialog() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("SecurityListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Security List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            SecurityListDialogController controller = loader.getController();
            if (controller == null) {
                System.err.println("Null controller for SecurityListDialog");
                return;
            }
            controller.setMainApp(this, dialogStage);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    boolean showEditAccountDialog(Account account, Account.Type t) {
        String title = (account == null) ? "New Account" : "Edit Account";

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("EditAccountDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle(title);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            EditAccountDialogController controller = loader.getController();
            if (controller == null) {
                System.err.println("Null controller?");
                return false;
            }

            controller.setDialogStage(dialogStage);
            controller.setAccount(this, account, t);
            dialogStage.showAndWait();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
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
                System.err.println("Unknow MODE" + mode.toString());
                title = "Unknown";
        }

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("PasswordDialog.fxml"));

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
            e.printStackTrace();
            return null;
        }
    }

    // update HoldingsList to date but exclude transaction exTid
    // an list of cash and total is returned if the account is not an investing account
    List<SecurityHolding> updateAccountSecurityHoldingList(Account account, LocalDate date, int exTid) {
        // empty the list first
        List<SecurityHolding> securityHoldingList = new ArrayList<>();
        if (account.getType() != Account.Type.INVESTING) {
            BigDecimal totalCash = null;
            int n = account.getTransactionList().size();
            if (n == 0) {
                totalCash = BigDecimal.ZERO;
            } else {
                for (int i = 0; i < account.getTransactionList().size(); i++) {
                    Transaction t = account.getTransactionList().get(i);
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

            securityHoldingList.add(cashHolding);
            securityHoldingList.add(totalHolding);
            return securityHoldingList;
        }

        BigDecimal totalCash = BigDecimal.ZERO.setScale(SecurityHolding.CURRENCYDECIMALLEN, RoundingMode.HALF_UP);
        Map<String, Integer> indexMap = new HashMap<>();  // security name and location index
        Map<String, List<Transaction>> stockSplitTransactionListMap = new HashMap<>();

        // sort the transaction list first
        // we want to sort the transactions by dates first, then by TradeAction, in which we want to put
        // SELL and CVSHRT at the end in case the transaction is closing the positions opened on the same date
        SortedList<Transaction> sortedTransactionList = new SortedList<>(account.getTransactionList(),
                (o1, o2) -> {
                    // first compare dates
                    int dateComparison = o1.getTDate().compareTo(o2.getTDate());
                    if (dateComparison != 0)
                        return dateComparison;

                    // compare TradeAction if dates are the same
                    // we want to have SELL and CVTSHRT at the end
                    if (o1.getTradeAction() == SELL || o1.getTradeAction() == CVTSHRT)
                        return (o2.getTradeAction() == SELL || o2.getTradeAction() == CVTSHRT) ? 0 : 1;
                    if (o2.getTradeAction() == SELL || o2.getTradeAction() == CVTSHRT)
                        return -1;
                    return o1.getTradeAction().compareTo(o2.getTradeAction());
                });

        for (Transaction t : sortedTransactionList) {
            if (t.getTDate().isAfter(date))
                break; // we are done

            int tid = t.getID();
            if (tid == exTid)
                continue;  // exclude exTid from the holdings list

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
                            t.getTradeAction(), t.getTDate(), t.getPrice(), t.getSignedQuantity(), t.getCostBasis()),
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
            System.err.println("DB connection down?! ");
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
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
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
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
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
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
        return price;
    }

    // take a list of MatchInfo, delete all MatchInfo in the database with same TransactionID
    // save new MatchInfo
    void putMatchInfoList(List<SecurityHolding.MatchInfo> matchInfoList) {
        if (matchInfoList.size() == 0)
            return;
        if (mConnection == null) {
            System.err.println("DB connection down?!");
            return;
        }

        int tid = matchInfoList.get(0).getTransactionID();

        // delete any existing
        try (Statement statement = mConnection.createStatement()) {
            statement.execute("delete from LOTMATCH where TRANSID = " + tid);
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }

        // insert list
        String sqlCmd = "insert into LOTMATCH (TRANSID, MATCHID, MATCHQUANTITY) values (?, ?, ?)";
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            for (SecurityHolding.MatchInfo matchInfo : matchInfoList) {
                preparedStatement.setInt(1, matchInfo.getTransactionID());
                preparedStatement.setInt(2, matchInfo.getMatchTransactionID());
                preparedStatement.setBigDecimal(3, matchInfo.getMatchQuantity());

                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
    }

    void showSpecifyLotsDialog(Transaction t, List<SecurityHolding.MatchInfo> matchInfoList) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("SpecifyLotsDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Specify Lots...");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));
            SpecifyLotsDialogController controller = loader.getController();
            controller.setMainApp(this, t, matchInfoList, dialogStage);
            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void showEditTransactionDialog(Stage parent, Transaction transaction) {
        List<Transaction.TradeAction> taList = (mCurrentAccount.getType() == Account.Type.INVESTING) ?
                Arrays.asList(Transaction.TradeAction.values()) :
                Arrays.asList(Transaction.TradeAction.WITHDRAW, Transaction.TradeAction.DEPOSIT,
                        Transaction.TradeAction.XIN, Transaction.TradeAction.XOUT);
        showEditTransactionDialog(parent, transaction, Collections.singletonList(mCurrentAccount),
                mCurrentAccount, taList);
    }

    // return transaction id or -1 for failure
    int showEditTransactionDialog(Stage parent, Transaction transaction, List<Account> accountList,
                                          Account defaultAccount, List<Transaction.TradeAction> taList) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation((MainApp.class.getResource("EditTransactionDialog.fxml")));

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
            e.printStackTrace();
            return -1;
        }
    }

    void showAccountHoldings() {
        if (mCurrentAccount == null) {
            System.err.println("Can't show holdings for null account.");
            return;
        }
        if (mCurrentAccount.getType() != Account.Type.INVESTING) {
            System.err.println("Show holdings only applicable for trading account");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("HoldingsDialog.fxml"));

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
            e.printStackTrace();
        }
    }

    private void closeConnection() {
        if (mConnection != null) {
            try {
                mConnection.close();
            } catch (SQLException e) {
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
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
        System.err.println("Total " + nTrans + " transactions");

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
            if (insertUpdateTransactionToDB(t) > 0)
                cnt++;
            else
                System.err.println("Updating Transaction " + t.getID() + " failed.");
        }
        System.err.println("Total " + nTrans + " transactions processed.");
        System.err.println("Found " + updateList.size() + " matching transactions.");
        System.err.println("Updated " + cnt + " transactions.");
        System.err.println(unMatchedList.size() + " unmatched transactions.");
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

        QIFParser qifParser = new QIFParser(result.isPresent() ? result.get() : "");
        try {
            if (qifParser.parseFile(file) < 0) {
                System.err.println("Failed to parse " + file);
            }
        } catch (IOException e) {
            e.printStackTrace();
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
                        Integer.MAX_VALUE, null));
            } else {
                System.err.println("Unknown account type: " + qa.getType()
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
            Integer id = tickerIDMap.get(security);
            if (id == null) {
                tickerIDMap.put(security, id = getSecurityID(security));
            }
            if (!insertUpdatePriceToDB(id, p.getDate(), p.getPrice(), 3)) {
                System.err.println("Insert to PRICE failed with "
                        + security + "(" + id + ")," + p.getDate() + "," + p.getPrice());
            }
        }

        qifParser.getCategoryList().forEach(this::insertCategoryToDB);
        initCategoryList();

        for (QIFParser.BankTransaction bt : qifParser.getBankTransactionList()) {
            try {
                int rowID = insertTransactionToDB(bt);
                if (rowID < 0) {
                    System.err.println("Failed to insert transaction: " + bt.toString());
                }
            } catch (SQLException e) {
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println(bt);
            }
        }

        for (QIFParser.TradeTransaction tt : qifParser.getTradeTransactionList()) {
            try {
                int rowID = insertTransactionToDB(tt);
                if (rowID < 0) {
                    System.err.println("Failed to insert transaction: " + tt.toString());
                } else {
                    // insert transaction successful, insert price is it has one.
                    BigDecimal p = tt.getPrice();
                    if (p != null && p.signum() > 0) {
                        insertUpdatePriceToDB(getSecurityByName(tt.getSecurityName()).getID(), tt.getDate(), p, 0);
                    }
                }
            } catch (SQLException e) {
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
            }
        }

        initTransactionList();
        System.out.println("Imported " + file);
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
        } catch (SQLException e) {
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
            showExceptionDialog("Exception", "SQLException", e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            showExceptionDialog("Exception", "IllegalArgumentException", e.getMessage(), e);
        } finally {
            try {
                if (preparedStatement != null)
                    preparedStatement.close();
            } catch (SQLException e) {
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
                title = "Create a new FaCai168 database...";
            } else {
                title = "Open an existing FaCai168 database...";
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
        mPrimaryStage.setTitle("FaCai168");
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
            mConnection = DriverManager.getConnection(url, DBOWNER, password + ' ' + password);
        } catch (SQLException e) {
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
                    e.printStackTrace();
                    break;
            }
            alert.showAndWait();
        }

        if (mConnection == null) {
            return;
        }

        // save opened DB hist
        putOpenedDBNames(updateOpenedDBNames(getOpenedDBNames(), dbName));

        if (isNew) {
            initDBStructure();
        }

        initializeLists();

        mPrimaryStage.setTitle("FaCai168 " + dbName);
    }

    private void initializeLists() {
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
            System.err.println("Null mConnection");
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        } finally {
            try {
                if (preparedStatement != null)
                    preparedStatement.close();
            } catch (SQLException e) {
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
            }
        }
    }

    // initialize database structure
    private void initDBStructure() {
        if (mConnection == null)
            return;

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
                + "MEMO varchar (" + TRANSACTIONMEMOLEN + "), "
                + "AMOUNT decimal(20,4), "
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
                + "CLEARED integer, "
                + "CATEGORYID integer, "   // positive for category ID, negative for transfer account id
                + "TAGID integer, "
                + "MEMO varchar(" + TRANSACTIONMEMOLEN + "), "
                + "REFERENCE varchar (" + TRANSACTIONREFLEN + "), "  // reference or check number as string
                + "PAYEE varchar (" + TRANSACTIONPAYEELEN + "), "
                + "SPLITFLAG boolean, "
                + "ADDRESSID integer, "
                + "AMORTIZATIONID integer, "
                + "TRADEACTION varchar(" + TRANSACTIONTRACEACTIONLEN + "), "
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
                + "MatchQuantity decimal(20,6), "
                + "Constraint UniquePair unique (TransID, MatchID));";
        sqlCreateTable(sqlCmd);

        // SavedReports table
        sqlCmd = "create table SAVEDREPORTS ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), "  // make sure to start with 1
                + "NAME varchar (32) UNIQUE NOT NULL, "       // name of the report
                + "TYPE varchar (16) NOT NULL, "              // type of the report
                + "DATEPERIOD varchar (16) NOT NULL, "        // enum for dateperiod
                + "SDATE date NOT NULL, "                              // customized start date
                + "EDATE date NOT NULL, "                              // customized start date
                + "FREQUENCY varchar (16) NOT NULL);";                 // frequency enum
        sqlCreateTable(sqlCmd);

        // SavedReportDetails table
        sqlCmd = "create table SAVEDREPORTDETAILS ("
                + "REPORTID integer NOT NULL, "
                + "ITEMNAME varchar(16) NOT NULL, "
                + "ITEMVALUE varchar(16) NOT NULL, "
                + "SELECTEDORDER integer NOT NULL);";
        sqlCreateTable(sqlCmd);

        // Tag table
        sqlCmd = "create table TAGS ("
                + "ID integer NOT NULL AUTO_INCREMENT (1), " // starting 1
                + "NAME varchar(20), "
                + "DESCRIPTION varchar(80), "
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

    private static String SQLExceptionToString(SQLException e) {
        String s = "";
        while (e != null) {
            s += ("--- SQLException ---" +
                    "  SQL State: " + e.getSQLState() +
                    "  Message:   " + e.getMessage()) + "\n";
            e = e.getNextException();
        }
        return s;
    }

    // init the main layout
    private void initMainLayout() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("MainLayout.fxml"));
            mPrimaryStage.setScene(new Scene(loader.load()));
            mPrimaryStage.show();
            ((MainController) loader.getController()).setMainApp(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        closeConnection();
    }

    @Override
    public void init() { mPrefs = Preferences.userNodeForPackage(MainApp.class); }

    @Override
    public void start(final Stage stage) throws Exception {
        mPrimaryStage = stage;
        mPrimaryStage.setTitle("FaCai168");
        initMainLayout();
    }

    public static void main(String[] args) {
        // set error stream to a file in the current directory
        try {
            File file = File.createTempFile("FC168-", ".err", new File(System.getProperty("user.dir")));
            System.err.println("Redirect System.err to " + file.getCanonicalPath());
            System.setErr(new PrintStream(file));
            System.err.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        launch(args);
    }
}
