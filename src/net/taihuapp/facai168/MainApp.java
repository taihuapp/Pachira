package net.taihuapp.facai168;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.prefs.Preferences;

public class MainApp extends Application {

    static int MAXOPENEDDBHIST = 5; // keep max 5 opened files
    static String KEY_OPENEDDBPREFIX = "OPENEDDB#";
    static String DBOWNER = "FC168ADM";
    static String DBPOSTFIX = ".h2.db"; // it is changes to mv.db in H2 1.4beta when MVStore enabled

    static int ACCOUNTNAMELEN = 40;
    static int ACCOUNTDESCLEN = 256;
    static int SECURITYTICKERLEN = 16;
    static int SECURITYNAMELEN = 32;

    static int CATEGORYNAMELEN = 40;
    static int CATEGORYDESCLEN = 256;

    static int TRANSACTIONMEMOLEN = 64;
    static int TRANSACTIONREFLEN = 8;
    static int TRANSACTIONPAYEELEN = 32;
    static int TRANSACTIONTRACEACTIONLEN = 16;
    static int TRANSACTIONTRANSFERREMINDERLEN = 40;
    static int ADDRESSLINELEN = 32;

    static int AMORTLINELEN = 32;


    private Preferences mPrefs;
    private Stage mPrimaryStage;
    private Connection mConnection = null;  // todo replace Connection with a custom db class object

    private ObservableList<Account> mAccountList = FXCollections.observableArrayList();
    private ObservableList<Transaction> mTransactionList = FXCollections.observableArrayList();
    private ObservableList<QIFParser.Category> mCategoryList = FXCollections.observableArrayList();
    private ObservableList<Security> mSecurityList = FXCollections.observableArrayList();
    private ObservableList<SecurityHolding> mSecurityHoldingList = FXCollections.observableArrayList();
    private SecurityHolding mRootSecurityHolding = new SecurityHolding("Root");

    private Account mCurrentAccount = null;

    public void setCurrentAccount(Account a) { mCurrentAccount = a; }
    public Account getCurrentAccount() { return mCurrentAccount; }

    public void updateTransactionListBalance() {
        BigDecimal b = new BigDecimal(0);
        for (Transaction t : getTransactionList()) {
            BigDecimal amount = t.getCashAmountProperty().get();
            if (amount == null) {
                System.err.println(t.toString());
            } else {
                b = b.add(amount);
                t.setBalance(b);
            }
        }
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

    public ObservableList<Account> getAccountList() { return mAccountList; }
    public ObservableList<Transaction> getTransactionList() { return mTransactionList; }
    public ObservableList<QIFParser.Category> getCategoryList() { return mCategoryList; }
    public ObservableList<Security> getSecurityList() { return mSecurityList; }
    public ObservableList<SecurityHolding> getSecurityHoldingList() { return mSecurityHoldingList; }
    public SecurityHolding getRootSecurityHolding() { return mRootSecurityHolding; }

    public Account getAccountByName(String name) {
        for (Account a : getAccountList()) {
            if (a.getName().equals(name)) {
                return a;
            }
        }
        return null;
    }

    public Account getAccountByID(int id) {
        for (Account a : getAccountList()) {
            if (a.getID() == id)
                return a;
        }
        return null;
    }

    public Account getAccountByWrapedName(String wrapedName) {
        // mapCategoryOrAccountNameToID unwraps a wraped account name and return a valid account
        int id = -mapCategoryOrAccountNameToID(wrapedName);
        if (id <= 0)
            return null;
        return getAccountByID(id);
    }

    public Security getSecurityByID(int id) {
        for (Security s : getSecurityList()) {
            if (s.getID() == id)
                return s;
        }
        return null;
    }

    public Security getSecurityByName(String name) {
        for (Security s : getSecurityList()) {
            if (s.getName().equals(name))
                return s;
        }
        return null;
    }

    public QIFParser.Category getCategoryByID(int id) {
        for (QIFParser.Category c : getCategoryList()) {
            if (c.getID() == id)
                return c;
        }
        return null;
    }

    public QIFParser.Category getCategoryByName(String name) {
        for (QIFParser.Category c : getCategoryList()) {
            if (c.getName().equals(name)) return c;
        }
        return null;
    }

    // return affected transaction id if success, 0 for failure.
    public int insertUpDateTransactionToDB(Transaction t) {
        String sqlCmd;
        // be extra careful about the order of the columns
        if (t.getID() <= 0) {
            sqlCmd = "insert into TRANSACTIONS " +
                    "(ACCOUNTID, DATE, AMOUNT, TRADEACTION, SECURITYID, " +
                    "CLEARED, CATEGORYID, MEMO, PRICE, QUANTITY, COMMISSION, " +
                    "MATCHTRANSACTIONID, MATCHSPLITTRANSACTIONID, PAYEE, ADATE) " +
                    "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            sqlCmd = "update TRANSACTIONS set " +
                    "ACCOUNTID = ?, DATE = ?, AMOUNT = ?, TRADEACTION = ?, " +
                    "SECURITYID = ?, CLEARED = ?, CATEGORYID = ?, MEMO = ?, " +
                    "PRICE = ?, QUANTITY = ?, COMMISSION = ?, " +
                    "MATCHTRANSACTIONID = ?, MATCHSPLITTRANSACTIONID = ?, " +
                    "PAYEE = ?, ADATE = ? " +
                    "where ID = ?";
        }
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            preparedStatement.setInt(1, t.getAccountID());
            preparedStatement.setDate(2, Date.valueOf(t.getTDate()));
            preparedStatement.setBigDecimal(3, t.getAmount());
            preparedStatement.setString(4, t.getTradeActionProperty().get());
            Security security = getSecurityByName(t.getSecurityName());
            int securityID = 0;
            if (security != null)
                securityID = security.getID();
            preparedStatement.setInt(5, securityID);
            preparedStatement.setInt(6, 0); // cleared
            preparedStatement.setInt(7, mapCategoryOrAccountNameToID(t.getCategoryProperty().get()));
            preparedStatement.setString(8, t.getMemoProperty().get());
            preparedStatement.setBigDecimal(9, t.getPrice());
            preparedStatement.setBigDecimal(10, t.getQuantity());
            preparedStatement.setBigDecimal(11, t.getCommission());
            preparedStatement.setInt(12, t.getMatchID()); // matchTransactionID, ignore for now
            preparedStatement.setInt(13, t.getMatchSplitID()); // matchSplitTransactionID, ignore for now
            preparedStatement.setString(14, t.getPayeeProperty().get());
            if (t.getADate() == null) {
                preparedStatement.setNull(15, java.sql.Types.DATE);
            } else {
                preparedStatement.setDate(15, Date.valueOf(t.getADate()));
            }
            if (t.getID() > 0)  {
                preparedStatement.setInt(16, t.getID());
            }
            if (preparedStatement.executeUpdate() == 0) {
                throw new SQLException("Insert/Update Transaction failed, no rows changed");
            }

            if (t.getID() > 0) {
                return t.getID();
            } else {
                try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                    if (resultSet.next()) {
                        t.setID(resultSet.getInt(1));
                        return t.getID();
                    }
                } catch (SQLException e) {
                    throw e;
                }
            }
        } catch (SQLException e) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle("Database Error");
            alert.setHeaderText("Unable to insert/update Transaction");
            alert.setContentText(SQLExceptionToString(e));
            alert.showAndWait();
        }
        return 0;
    }

    public void insertUpdateSecurityToDB(Security security) {
        String sqlCmd;
        if (security.getID() < 0) {
            sqlCmd = "insert into SECURITIES (TICKER, NAME, TYPE) values (?,?,?)";
        } else {
            sqlCmd = "update SECURITIES set TICKER = ?, NAME = ?, TYPE = ? where ID = ?";
        }

        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {
            preparedStatement.setString(1, security.getTicker());
            preparedStatement.setString(2, security.getName());
            preparedStatement.setInt(3, security.getType().ordinal());
            if (security.getID() >= 0) {
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
        } catch (NullPointerException e) {
            System.err.println("mConnection is null");
            e.printStackTrace();
        }
    }

    // mode = 1, insert
    //        2  update
    //        3 insert and update
    // return true of operation successful
    //        false otherwise
    public boolean insertUpdatePriceToDB(Integer securityID, LocalDate date, BigDecimal p, int mode) {
        boolean status = false;
        String sqlCmd;
        switch (mode) {
            case 1:
                sqlCmd = "insert into PRICES (PRICE, SECURITYID, DATE) values (?, ?, ?)";
                break;
            case 2:
                sqlCmd = "update PRICES set PRICE = ? where SECURITYID = ? and DATE = ?";
                break;
            case 3:
                return insertUpdatePriceToDB(securityID, date, p, 1) || insertUpdatePriceToDB(securityID, date, p, 2);
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
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return status;

    }

    // construct a wraped account name
    public String getWrappedAccountName(Account a) {
        if (a == null)
            return "";
        return "[" + a.getName() + "]";
    }

    // the name should be a category name or account name surrounded by []
    // return categoryID or negative accountID
    private int mapCategoryOrAccountNameToID(String name) {
        if (name == null)
            return 0;
        if (name.startsWith("[") && name.endsWith("]")) {
            int len = name.length();
            Account a = getAccountByName(name.substring(1, len - 1));
            if (a != null)
                return -a.getID();
            return 0;
        } else {
            QIFParser.Category c = getCategoryByName(name);
            if (c != null)
                return c.getID();
            return 0;
        }
    }

    private String mapCategoryOrAccountIDToName(int id) {
        if (id > 0) {
            QIFParser.Category c = getCategoryByID(id);
            if (c != null)
                return c.getName();
            return "";
        } else if (id < 0) {
            return getWrappedAccountName(getAccountByID(-id));
        } else {
            return "";
        }
    }

    // take a transaction id, and a list of split BT, insert the list of bt into database
    // return the number of splitBT inserted, which should be same as the length of
    // the input list
    public int insertSplitBTToDB(int btID, List<QIFParser.BankTransaction.SplitBT> splitBTList) {
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
    public int insertAddressToDB(List<String> address) {
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
    public int insertAmortizationToDB(String[] amortLines) {
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
    public int insertTransactionToDB(QIFParser.BankTransaction bt) throws SQLException {
        int rowID = -1;
        String accountName = bt.getAccountName();
        Account account = getAccountByName(accountName);
        if (account == null) {
            System.err.println("Account [" + accountName + "] not found, nothing inserted");
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
                    "PAYEE, SPLITFLAG, ADDRESSID, AMORTIZATIONID" +
                    ") values (?,?,?,?,?,?,?,?,?,?,?)";

            try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)) {

                preparedStatement.setInt(1, account.getID());
                preparedStatement.setDate(2, Date.valueOf(bt.getDate()));
                preparedStatement.setBigDecimal(3, bt.getTAmount());
                preparedStatement.setInt(4, bt.getCleared());
                String categoryName = bt.getCategory();
                String transferName = bt.getTransfer();
                int categoryID = 0;
                int transferID = 0;

                if (categoryName != null) {
                    categoryID = getCategoryByName(categoryName).getID();
                } else if (transferName != null) {
                    transferID = getAccountByName(bt.getTransfer()).getID();
                }
                preparedStatement.setInt(5, categoryID > 0 ? categoryID : -transferID);
                preparedStatement.setString(6, bt.getMemo());
                preparedStatement.setString(7, bt.getReference());
                preparedStatement.setString(8, bt.getPayee());
                preparedStatement.setBoolean(9, !splitList.isEmpty());
                preparedStatement.setInt(10, addressID);
                preparedStatement.setInt(11, amortID);

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
    public int insertTransactionToDB(QIFParser.TradeTransaction tt) throws SQLException {
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
                "CLEARED, CATEGORYID, MEMO, PRICE, QUANTITY, COMMISSION) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement preparedStatement = mConnection.prepareStatement(sqlCmd)){
            preparedStatement.setInt(1, account.getID());
            preparedStatement.setDate(2, Date.valueOf(tt.getDate()));
            preparedStatement.setBigDecimal(3, tt.getTAmount());
            preparedStatement.setString(4, tt.getAction().name());
            String name = tt.getSecurityName();
            int securityID = -1;
            if (name != null && name.length() > 0) {
                System.err.println("name = " + name);
                securityID = getSecurityByName(name).getID();
            }
            preparedStatement.setInt(5, securityID);
            preparedStatement.setInt(6, tt.getCleared());
            preparedStatement.setInt(7, mapCategoryOrAccountNameToID(tt.getCategoryOrTransfer()));
            preparedStatement.setString(8, tt.getMemo());
            preparedStatement.setBigDecimal(9, tt.getPrice());
            preparedStatement.setBigDecimal(10, tt.getQuantity());
            preparedStatement.setBigDecimal(11, tt.getCommission());

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

    public void insertCategoryToDB(QIFParser.Category category) {
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

    public void insertUpdateAccountToDB(Account account) {
        String sqlCmd;
        if (account.getID() < 0) {
            sqlCmd = "insert into ACCOUNTS (TYPE, NAME, DESCRIPTION) values (?,?,?)";
        } else {
            sqlCmd = "update ACCOUNTS set TYPE = ?, NAME = ?, DESCRIPTION = ? where ID = ?";
        }
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            preparedStatement = mConnection.prepareStatement(sqlCmd);
            preparedStatement.setInt(1, account.getType().ordinal());
            preparedStatement.setString(2, account.getName());
            preparedStatement.setString(3, account.getDescription());
            if (account.getID() > 0) {
                preparedStatement.setInt(4, account.getID());
            }
            if (preparedStatement.executeUpdate() == 0) {
                throw new SQLException("Insert Account failed, no rows affected");
            }
            resultSet = preparedStatement.getGeneratedKeys();
            if (resultSet.next()) {
                account.setID(resultSet.getInt(1));
            } else {
                throw new SQLException("Insert Account failed, no ID obtained");
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
        } finally {
            try {
                if (preparedStatement != null)
                    preparedStatement.close();
                if (resultSet != null)
                    resultSet.close();
            } catch (SQLException e) {
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
            }
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

    public void initCategoryList() {
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
                QIFParser.Category category = new QIFParser.Category();
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

    public void initAccountList() {
        mAccountList.clear();
        if (mConnection == null) return;

        try (Statement statement = mConnection.createStatement()) {
            String sqlCmd = "select ID, TYPE, NAME, DESCRIPTION from ACCOUNTS order by TYPE, ID";
            ResultSet rs = statement.executeQuery(sqlCmd);
            while (rs.next()) {
                int id = rs.getInt("ID");
                int typeOrdinal = rs.getInt("TYPE");
                Account.Type type = Account.Type.values()[typeOrdinal];
                String name = rs.getString("NAME");
                String description = rs.getString("DESCRIPTION");
                mAccountList.add(new Account(id, type, name, description));
            }
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
    }

    public void initSecurityList() {
        mSecurityList.clear();
        if (mConnection == null) return;

        try (Statement statement = mConnection.createStatement()) {
            String sqlCmd = "select ID, TICKER, NAME, TYPE from SECURITIES order by ID";
            ResultSet rs = statement.executeQuery(sqlCmd);
            while (rs.next()) {
                int id = rs.getInt("ID");
                int typeOrdinal = rs.getInt("TYPE");
                String ticker = rs.getString("TICKER");
                String name = rs.getString("NAME");
                Security.Type type = Security.Type.values()[typeOrdinal];
                if (type == Security.Type.INDEX)
                    continue; // skip index
                mSecurityList.add(new Security(id, ticker, name, type));
            }
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
    }

    private List<Transaction> loadSplitTransactions(int tID) {
        List<Transaction> stList = new ArrayList<>();

        String sqlCmd = "select *"
                + " from SPLITTRANSACTIONS where TRANSACTIONID = " + tID
                + " order by ID";
        try (Statement statement = mConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                String categoryStr = mapCategoryOrAccountIDToName(resultSet.getInt("CATEGORYID"));
                String memo = resultSet.getString("MEMO");
                BigDecimal amount = resultSet.getBigDecimal("AMOUNT");
                if (amount == null) {
                    amount = BigDecimal.ZERO;
                }
                // todo
                // do we need percentage?
                // ignore it for now
                int matchID = resultSet.getInt("MATCHTRANSACTIONID");
                int matchSplitID = resultSet.getInt("MATCHSPLITTRANSACTIONID");
                // we store split transaction id in the ID field
                // ignore accountID, date, reference, payee,
                int accountID = -1; // ignore accountID
                LocalDate date = null; // ignore date
                String reference = null; // ignore reference
                String payee = null; // ignore payee

                stList.add(new Transaction(id, accountID, date, reference, payee,
                        memo, categoryStr, amount, matchID, matchSplitID));
            }
        }  catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
        return stList;
    }

    public void initTransactionList(Account account) {
        mTransactionList.clear();
        if (mConnection == null)
            return;
        int accountID = account.getID();

        String sqlCmd = "select * "
                + " from TRANSACTIONS where ACCOUNTID = " + accountID
                + " order by DATE, ID";
        try (Statement statement = mConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
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
                String categoryStr = mapCategoryOrAccountIDToName(resultSet.getInt("CATEGORYID"));
                Transaction.TradeAction tradeAction = null;
                String taStr = resultSet.getString("TRADEACTION");

                if (taStr != null && taStr.length() > 0) tradeAction = Transaction.TradeAction.valueOf(taStr);

                int securityID = resultSet.getInt("SECURITYID");
                BigDecimal quantity = resultSet.getBigDecimal("QUANTITY");
                BigDecimal commission = resultSet.getBigDecimal("COMMISSION");
                BigDecimal price = resultSet.getBigDecimal("PRICE");
                int matchID = resultSet.getInt("MATCHTRANSACTIONID");
                int matchSplitID = resultSet.getInt("MATCHSPLITTRANSACTIONID");

                if (account.getType() == Account.Type.INVESTING) {
                    String name = "";
                    if (securityID > 0)
                        name = getSecurityByID(securityID).getName();
                    if (quantity == null) quantity = BigDecimal.ZERO;
                    if (commission == null) commission = BigDecimal.ZERO;
                    if (price == null) price = BigDecimal.ZERO;
                    mTransactionList.add(new Transaction(id, accountID, tDate, aDate, tradeAction, name,
                            price, quantity, memo, commission, amount, categoryStr, matchID, matchSplitID));
                } else {
                    Transaction bt = new Transaction(id, accountID, tDate, reference, payee,
                            memo, categoryStr, amount, matchID, matchSplitID);
                    if (resultSet.getBoolean("SPLITFLAG")) {
                        bt.setSplitTransactionList(loadSplitTransactions(id));
                    }
                    mTransactionList.add(bt);
                }
            }
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
    }

    void putOpenedDBNames(List<String> openedDBNames) {
        for (int i = 0; i < openedDBNames.size(); i++) {
            mPrefs.put(KEY_OPENEDDBPREFIX + i, openedDBNames.get(i));
        }
        for (int i = openedDBNames.size(); i < MAXOPENEDDBHIST; i++) {
            mPrefs.remove(KEY_OPENEDDBPREFIX+i);
        }
    }

    List<String> updateOpenedDBNames(List<String> openedDBNames, String fileName) {
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

    public boolean showEditAccountDialog(Account account) {
        boolean isNew = account.getID() < 0;
        String title;
        if (isNew) {
            title = "New Account";
        } else {
            title = "Edit Account";
        }

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
            controller.setAccount(account);
            dialogStage.showAndWait();
            return controller.isOK();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // returns a password or null
    private String showPasswordDialog(PasswordDialogController.MODE mode) {
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

            return controller.getPassword();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // update HoldingsList to date but exclude transaction exTid
    public void updateHoldingsList(LocalDate date, int exTid) {
        // empty the list first
        mSecurityHoldingList.clear();

        CashHolding cashHolding = new CashHolding();
        cashHolding.setPrice(BigDecimal.ONE);
        Map<String, Integer> indexMap = new HashMap<>();  // security name and location index

        // sort the transaction list first
        SortedList<Transaction> sortedTransactionList = new SortedList<>(mTransactionList,
                Comparator.comparing(Transaction::getTDate).thenComparing(Transaction::getID));
        for (Transaction t : sortedTransactionList) {
            if (t.getTDate().isAfter(date))
                break; // we are done

            int tid = t.getID();
            if (tid == exTid)
                continue;  // exclude exTid from the holdings list

            LocalDate tDate = t.getTDate();
            BigDecimal tCashAmt = t.getCashAmount();
            String name = t.getSecurityName();
            cashHolding.addLot(new SecurityHolding.LotInfo(tid, name, t.getTradeAction(), tDate,
                    BigDecimal.ONE, tCashAmt, tCashAmt));

            if (!name.isEmpty()) {
                // it's not cash transaction, add security lot
                Integer index = indexMap.get(name);
                if (index == null) {
                    // first time seeing this security, add to the end
                    index = mSecurityHoldingList.size();
                    indexMap.put(name, index);
                    mSecurityHoldingList.add(new SecurityHolding(name));
                }
                BigDecimal q = t.getQuantity();
                mSecurityHoldingList.get(index).addLot(new SecurityHolding.LotInfo(t.getID(), name,
                        t.getTradeAction(), t.getTDate(), t.getPrice(), t.getSignedQuantity(), t.getCostBasis()),
                        getMatchInfoList(tid));
            }
        }

        for (Iterator<SecurityHolding> securityHoldingIterator = mSecurityHoldingList.iterator();
             securityHoldingIterator.hasNext(); ) {
            SecurityHolding securityHolding = securityHoldingIterator.next();

            if (securityHolding.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                // remove security with zero quantity
                securityHoldingIterator.remove();
            }
            //securityHolding.setPrice(getLatestSecurityPrice(securityHolding.getSecurityName(), date));
            securityHolding.updateMarketValue(getLatestSecurityPrice(securityHolding.getSecurityName(), date));
            securityHolding.updatePctRet();
        }

/*
        for (SecurityHolding securityHolding : mSecurityHoldingList) {
            String name = securityHolding.getSecurityNameProperty().get();
            securityHolding.setPrice(getLatestSecurityPrice(name, date));
        }
*/
        // put cash holding at the bottom
        mSecurityHoldingList.add(cashHolding);
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

    // retrieve the latest price no later than date
    public BigDecimal getLatestSecurityPrice(String securityName, LocalDate date) {
        BigDecimal price = null;
        String sqlCmd = "select top 1 p.price from PRICES p inner join SECURITIES s " +
                "where s.NAME = '" + securityName + "' and s.ID = p.SECURITYID " +
                " and p.DATE <= '" + date.toString() + "' order by DATE desc";
        try (Statement statement = mConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlCmd)) {
                if (resultSet.next())
                    price = resultSet.getBigDecimal(1);
        } catch (SQLException e) {
            System.err.print(SQLExceptionToString(e));
            e.printStackTrace();
        }
        return price;
    }

    private void showWarningDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
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

    public void showSpecifyLotsDialog(Transaction t, List<SecurityHolding.MatchInfo> matchInfoList) {
        System.out.println("Show Specify Lot window...");

/*
        Security security = mSecurityChoiceBox.getValue();
        if (security == null) {
            showWarningDialog("No Security Chosen", "No Security Chosen",
                    "Please select a valid security first");
            return;
        }
*/
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

    public void showEditTransactionDialog(Transaction transaction) {
        if (mCurrentAccount == null) {
            System.err.println("Can't show holdings for null account.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation((MainApp.class.getResource("EditTransactionDialog.fxml")));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Enter Transaction:");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(loader.load()));

            EditTransactionDialogController controller = loader.getController();
            controller.setMainApp(this, transaction, dialogStage);
            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showAccountHoldings() {
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

    public boolean isConnected() { return mConnection != null; }

    // import data from QIF file
    public void importQIF() {
        File file;
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("QIF", "*.QIF"));
        fileChooser.setTitle("Import QIF file...");
        file = fileChooser.showOpenDialog(mPrimaryStage);
        if (file == null) {
            return;
        }

        QIFParser qifParser = new QIFParser();
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
                insertUpdateAccountToDB(new Account(-1, at, qa.getName(), qa.getDescription()));
            } else {
                System.err.println("Unknow account type: " + qa.getType()
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

        for (QIFParser.Category c : qifParser.getCategoryList()) insertCategoryToDB(c);
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
            }
        }

        for (QIFParser.TradeTransaction tt : qifParser.getTradeTransactionList()) {
            try {
                int rowID = insertTransactionToDB(tt);
                if (rowID < 0) {
                    System.err.println("Failed to insert transaction: " + tt.toString());
                }
            } catch (SQLException e) {
                System.err.print(SQLExceptionToString(e));
                e.printStackTrace();
            }
        }
    }

    // create a new database
    public void openDatabase(boolean isNew, String dbName) {
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
        initAccountList();  // empty it
        initTransactionList(null); // empty it
        initSecurityList(); // empty it


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

        String password = showPasswordDialog(
                isNew ? PasswordDialogController.MODE.NEW : PasswordDialogController.MODE.ENTER);
        if (password == null) {
            if (isNew) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.initOwner(mPrimaryStage);
                alert.setTitle("Password not set");
                alert.setHeaderText("Need a password to continue...");
                alert.showAndWait();
            }
            return;
        }

        try {
            String url = "jdbc:h2:"+dbName+";CIPHER=AES;";
            if (!isNew) {
                // open existing
                url += "IFEXISTS=TRUE;";
            }
            mConnection = DriverManager.getConnection(url, DBOWNER, password + " " + password);
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

        // todo ???
        if (mConnection == null) {
            return;
        }

        // save opened DB hist
        putOpenedDBNames(updateOpenedDBNames(getOpenedDBNames(), dbName));

        if (isNew) {
            initDBStructure();
        }
        // initialize
        initAccountList();
        initCategoryList();
        initSecurityList();

        mPrimaryStage.setTitle("FaCai168 " + dbName);
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
        String sqlCmd = "create table ACCOUNTS ("
                + "ID integer NOT NULL AUTO_INCREMENT, "
                + "TYPE integer NOT NULL, "
                + "NAME varchar(" + ACCOUNTNAMELEN + ") UNIQUE NOT NULL, "
                + "DESCRIPTION varchar(" + ACCOUNTDESCLEN + ") NOT NULL, "
                + "primary key (ID));";
        sqlCreateTable(sqlCmd);

        // Security Table
        sqlCmd = "create table SECURITIES ("
                + "ID integer NOT NULL AUTO_INCREMENT, "
                + "TICKER varchar(" + SECURITYTICKERLEN + ") UNIQUE NOT NULL, "
                + "NAME varchar(" + SECURITYNAMELEN + ") UNIQUE NOT NULL, "
                + "TYPE integer NOT NULL, "
                + "primary key (ID));";
        sqlCreateTable(sqlCmd);

        // Price Table
        sqlCmd = "create table PRICES ("
                + "SECURITYID integer NOT NULL, "
                + "DATE date NOT NULL, "
                + "PRICE decimal(20,8),"
                + "PRIMARY KEY (SECURITYID, DATE));";
        sqlCreateTable(sqlCmd);

        // Category Table
        sqlCmd = "create table CATEGORIES ("
                + "ID integer NOT NULL AUTO_INCREMENT, "
                + "NAME varchar(" + CATEGORYNAMELEN + ") UNIQUE NOT NULL, "
                + "DESCRIPTION varchar(" + CATEGORYDESCLEN + ") NOT NULL, "
                + "INCOMEFLAG boolean, "
                + "TAXREFNUM integer, "
                + "BUDGETAMOUNT decimal(20,4), "
                + "primary key (ID))";
        sqlCreateTable(sqlCmd);

        // SplitTransaction
        sqlCmd = "create table SPLITTRANSACTIONS ("
                + "ID integer NOT NULL AUTO_INCREMENT, "
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
        sqlCmd = "create table ADDRESSES ("
                + "ID integer not null auto_increment, "
                + "LINE0 varchar(" + ADDRESSLINELEN + "), "
                + "LINE1 varchar(" + ADDRESSLINELEN + "), "
                + "LINE2 varchar(" + ADDRESSLINELEN + "), "
                + "LINE3 varchar(" + ADDRESSLINELEN + "), "
                + "LINE4 varchar(" + ADDRESSLINELEN + "), "
                + "LINE5 varchar(" + ADDRESSLINELEN + "), "
                + "primary key (ID));";
        sqlCreateTable(sqlCmd);

        // amortlines table
        sqlCmd = "create table AMORTIZATIONLINES ("
                + "ID integer not null auto_increment, "
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
        sqlCmd = "create table TRANSACTIONS ("
                + "ID integer NOT NULL AUTO_INCREMENT, "
                + "ACCOUNTID integer NOT NULL, "
                + "DATE date NOT NULL, "
                + "ADATE date, "
                + "AMOUNT decimal(20,4), "
                + "CLEARED integer, "
                + "CATEGORYID integer, "   // positive for category ID, negative for transfer account id
                + "MEMO varchar(" + TRANSACTIONMEMOLEN + "), "
                + "REFERENCE varchar (" + TRANSACTIONREFLEN + "), "  // reference or check number as string
                + "PAYEE varchar (" + TRANSACTIONPAYEELEN + "), "
                + "SPLITFLAG boolean, "
                + "ADDRESSID integer, "
                + "AMORTIZATIONID integer, "
                + "TRADEACTION varchar(" + TRANSACTIONTRACEACTIONLEN + "), "
                + "SECURITYID integer, "
                + "PRICE decimal(20,6), "
                + "QUANTITY decimal(20,6), "
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

    }

    public static String SQLExceptionToString(SQLException e) {
        String s = "";
        while (e != null) {
            s += ("--- SQLException ---" +
                    "  SQL State: " + e.getSQLState() +
                    "  Message:   " + e.getMessage()) + "\n";
            e = e.getNextException();
        }
        return s;
    }

//    public static void printSQLException(SQLException e) {
//        System.err.print(SQLExceptionToString(e));
//    }

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
    public void init() {
        mPrefs = Preferences.userNodeForPackage(MainApp.class);

        // add a change listener to update Balance
        mTransactionList.addListener((ListChangeListener<Transaction>) c -> updateTransactionListBalance());
    }

    @Override
    public void start(final Stage stage) throws Exception {

        mPrimaryStage = stage;
        mPrimaryStage.setTitle("FaCai168");
        initMainLayout();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
