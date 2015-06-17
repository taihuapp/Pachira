package net.taihuapp.facai168;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    public void updateTransactionListBalance() {
        BigDecimal b = new BigDecimal(0);
        for (Transaction t : getTransactionList()) {
            BigDecimal amount = t.getCashAmountProperty().get();
            b = b.add(amount);
            t.setBalance(b);
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
                printSQLException(e);
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
    public boolean insertUpdatePriceToDB(Integer securityID, Date date, BigDecimal p, int mode) {
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
            preparedStatement.setDate(3, date);
            preparedStatement.executeUpdate();
            status = true;
        } catch (SQLException e) {
            printSQLException(e);
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return status;
    }

    // the name should be a category name or account name surrounded by []
    // return categoryID or negative accountID
    private int mapCategoryOrAccountNameToID(String name) {
        if (name.startsWith("[") && name.endsWith("]")) {
            int len = name.length();
            Account a = getAccountByName(name.substring(1, len-1));
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
            Account a = getAccountByID(-id);
            if (a != null)
                return "[" + a.getName() + "]";
            return "";
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
            printSQLException(e);
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
            printSQLException(e);
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
            printSQLException(e);
            e.printStackTrace();
        }
        return rowID;
    }

    // insert transaction to database and returns rowID
    // return -1 if failed
    public int insertTransactionToDB(QIFParser.BankTransaction bt) throws SQLException {
        int rowID = -1;
        System.out.println("Inserting " + bt.toString());
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
                printSQLException(e);
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
        System.out.println("Inserting " + tt.toString());
        Account account = getAccountByName(tt.getAccountName());
        if (account == null) {
            System.err.println("Account [" + tt.getAccountName() + "] not found, nothing inserted");
            return -1;
        }

        // temporarily unset autocommit
        mConnection.setAutoCommit(false);

        String sqlCmd = "insert into TRANSACTIONS " +
                "(ACCOUNTID, DATE, AMOUNT, TRADEACTION, SECURITYID) values (?, ?,?,?, ?)";
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

            preparedStatement.executeUpdate();
            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                if (resultSet.next()) {
                    rowID = resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            printSQLException(e);
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

            printSQLException(e);
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
            if (account.getID() >= 0) {
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
                printSQLException(e);
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
                printSQLException(e);
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
            printSQLException(e);
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
                printSQLException(e);
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
            printSQLException(e);
            e.printStackTrace();
        } finally {
            try {
                if (statement != null) statement.close();
                if (resultSet != null) resultSet.close();
            } catch (SQLException e) {
                printSQLException(e);
                e.printStackTrace();
            }
        }
    }

    public void initAccountList() {
        mAccountList.clear();
        if (mConnection == null) return;

        try (Statement statement = mConnection.createStatement()){
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
            printSQLException(e);
            e.printStackTrace();
        }
    }

    public void initSecurityList() {
        mSecurityList.clear();
        if (mConnection == null) return;

        try (Statement statement = mConnection.createStatement()){
            String sqlCmd = "select ID, TICKER, NAME, TYPE from SECURITIES order by ID";
            ResultSet rs = statement.executeQuery(sqlCmd);
            while (rs.next()) {
                int id = rs.getInt("ID");
                int typeOrdinal = rs.getInt("TYPE");
                String ticker = rs.getString("TICKER");
                String name = rs.getString("NAME");
                Security.Type type = Security.Type.values()[typeOrdinal];
                mSecurityList.add(new Security(id, ticker, name, type));
            }
        } catch (SQLException e) {
            printSQLException(e);
            e.printStackTrace();
        }
        System.out.println("Security List: " + mSecurityList.size());
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
                Date date = resultSet.getDate("DATE");
                String reference = resultSet.getString("REFERENCE");
                String payee = resultSet.getString("PAYEE");
                String memo = resultSet.getString("MEMO");
                BigDecimal amount = resultSet.getBigDecimal("AMOUNT");
                String categoryStr = mapCategoryOrAccountIDToName(resultSet.getInt("CATEGORYID"));
                Transaction.TradeAction tradeAction = null;
                String taStr = resultSet.getString("TRADEACTION");

                if (taStr != null) tradeAction = Transaction.TradeAction.valueOf(taStr);

                int securityID = resultSet.getInt("SECURITYID");
                BigDecimal quantity = resultSet.getBigDecimal("QUANTITY");
                BigDecimal commission = resultSet.getBigDecimal("COMMISSION");

                if (account.getType() == Account.Type.INVESTING) {
                    String name = "";
                    if (securityID > 0)
                        name = getSecurityByID(securityID).getName();
                    mTransactionList.add(new Transaction(id, accountID, date, tradeAction, name,
                            quantity, memo, commission, amount));
                } else {
                    mTransactionList.add(new Transaction(id, accountID, date, reference, payee,
                            memo, categoryStr, amount));
                }
            }
        } catch (SQLException e) {
            printSQLException(e);
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
            } else {
                controller.setDialogStage(dialogStage);
                controller.setAccount(account);
            }
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

    private void closeConnection() {
        if (mConnection != null) {
            try {
                mConnection.close();
            } catch (SQLException e) {
                printSQLException(e);
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
            System.err.println(e);
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
            if (!insertUpdatePriceToDB(id, Date.valueOf(p.getDate()), p.getPrice(), 3)) {
                System.err.println("Insert to PRICE failed with "
                        + security + "(" + id + ")," + p.getDate() + "," + p.getPrice());
            }
        }

        for (QIFParser.Category c : qifParser.getCategoryList()) insertCategoryToDB(c);
        initCategoryList();

        int cnt = 0;
        for (QIFParser.BankTransaction bt : qifParser.getBankTransactionList()) {
            try {
                int rowID = insertTransactionToDB(bt);
                if (rowID < 0) {
                    System.err.println("Failed to insert transaction: " + bt.toString());
                } else {
                    cnt++;
                }
            } catch (SQLException e) {
                printSQLException(e);
                e.printStackTrace();
            }
        }

        for (QIFParser.TradeTransaction tt : qifParser.getTradeTransactionList()) {
            try {
                int rowID = insertTransactionToDB(tt);
                if (rowID < 0) {
                    System.err.println("Failed to insert transaction: " + tt.toString());
                } else {
                    cnt++;
                }
            } catch (SQLException e) {
                printSQLException(e);
                e.printStackTrace();
            }
        }

        System.out.println("Inserted " + cnt + " transactions");
        System.out.println("Parse " + file.getAbsolutePath());
        System.out.println("CategoryList length = " + qifParser.getCategoryList().size());
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
                    printSQLException(e);
                    e.printStackTrace();
                    break;
            }
            alert.showAndWait();
        }

        // todo
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
            printSQLException(e);
            e.printStackTrace();
        } finally {
            try {
                if (preparedStatement != null)
                    preparedStatement.close();
            } catch (SQLException e) {
                printSQLException(e);
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
                + "primary key (ID));";
        sqlCreateTable(sqlCmd);
    }

    public static void printSQLException(SQLException e) {
        // Unwraps the entire exception chain to unveil the real cause of the
        // Exception.
        while (e != null)
        {
            System.err.println("\n----- SQLException -----");
            System.err.println("  SQL State:  " + e.getSQLState());
            System.err.println("  Error Code: " + e.getErrorCode());
            System.err.println("  Message:    " + e.getMessage());
            // for stack traces, refer to derby.log or uncomment this:
            //e.printStackTrace(System.err);
            e = e.getNextException();
        }
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
