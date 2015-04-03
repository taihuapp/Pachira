package net.taihuapp.facai168;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
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
    static int SECURITYTYPELEN = 16;

    private Preferences mPrefs;
    private Stage mPrimaryStage;
    private Connection mConnection = null;

    private ObservableList<Account> mAccountList = FXCollections.observableArrayList();

    // get opened named from pref
    List<String> getOpenedDBNames() {
        List<String> fileNameList = new ArrayList<String>();

        for (int i = 0; i < MAXOPENEDDBHIST; i++) {
            String fileName = mPrefs.get(KEY_OPENEDDBPREFIX + i, "");
            if (!fileName.isEmpty()) {
                fileNameList.add(fileName);
            }
        }
        return fileNameList;
    }

    public ObservableList<Account> getAccountList() {
        return mAccountList;
    }

    public Account getAccountByName(String name) {
        for (Account a : getAccountList()) {
            if (a.getName().equals(name)) {
                return a;
            }
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
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            preparedStatement = mConnection.prepareStatement(sqlCmd);
            preparedStatement.setString(1, security.getTicker());
            preparedStatement.setString(2, security.getName());
            preparedStatement.setInt(3, security.getType().ordinal());
            if (security.getID() >= 0) {
                preparedStatement.setInt(4, security.getID());
            }
            if (preparedStatement.executeUpdate() == 0) {
                throw new SQLException("Insert Security failed, no rows affected");
            }
            resultSet = preparedStatement.getGeneratedKeys();
            if (resultSet.next()) {
                security.setID(resultSet.getInt(1));
            } else {
                throw new SQLException("Insert Security failed, no ID obtained");
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

    public void initAccountList() {
        if (mConnection != null) {
            mAccountList.clear();
            Statement stmt = null;
            try {
                stmt = mConnection.createStatement();
                String sqlCmd = "select ID, TYPE, NAME, DESCRIPTION from ACCOUNTS order by TYPE, ID";
                ResultSet rs = stmt.executeQuery(sqlCmd);
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
            dialogStage.setScene(new Scene((AnchorPane) loader.load()));
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
            dialogStage.setScene(new Scene((AnchorPane) loader.load()));
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
            System.err.println("Inserting... " + qa.getName());
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

        List<QIFParser.Security> sList = qifParser.getSecurityList();
        for (QIFParser.Security s : sList) {
            insertUpdateSecurityToDB(new Security(-1, s.getSymbol(), s.getName(), Security.Type.fromString(s.getType())));
        }

        List<QIFParser.Category> cList = qifParser.getCategoryList();
        for (QIFParser.Category c : cList) {
            System.out.println(c);
        }
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
        closeConnection();

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

        mPrimaryStage.setTitle("FaCai168 " + dbName);
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
                + "PRIMARY KEY (ID));";
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = mConnection.prepareStatement(sqlCmd);
            preparedStatement.executeUpdate();
            preparedStatement.close();
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

        // Security Table
        sqlCmd = "create table SECURITIES ("
                + "ID integer NOT NULL AUTO_INCREMENT, "
                + "TICKER varchar(" + SECURITYTICKERLEN + ") UNIQUE NOT NULL, "
                + "NAME varchar(" + SECURITYNAMELEN + ") UNIQUE NOT NULL, "
                + "TYPE integer NOT NULL);";
        try {
            preparedStatement = mConnection.prepareStatement(sqlCmd);
            preparedStatement.executeUpdate();
            preparedStatement.close();
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

    public static void printSQLException(SQLException e)
    {
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
            mPrimaryStage.setScene(new Scene((BorderPane) loader.load()));
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
