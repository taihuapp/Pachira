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
import sun.awt.datatransfer.DataTransferer;

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

    static int ACCUONTTYPELEN = 16;
    static int ACCOUNTNAMELEN = 16;
    static int ACCOUNTDESCLEN = 256;

    private Preferences mPrefs;
    private Stage mPrimaryStage;
    private Connection mConnection = null;

    private ObservableList<Account> mAccountData = FXCollections.observableArrayList();

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

    List<AccountType> getAccountTypeList() {
        List<AccountType> aList = new ArrayList<AccountType>();

        if (mConnection != null) {
            Statement stmt = null;
            try {
                stmt = mConnection.createStatement();
                String sqlCmd = "select ID, TYPE from ACCOUNTTYPES order by ID;";
                ResultSet rs = stmt.executeQuery(sqlCmd);
                while (rs.next()) {
                    int id = rs.getInt("ID");
                    String type = rs.getString("TYPE");
                    aList.add(new AccountType(id, type));
                }
                rs.close();
            } catch (SQLException e) {
                printSQLException(e);
                e.printStackTrace();
            }
        }

        return aList;
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

    public void showEditAccountDialog(Account account) {
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
            EditAccountDialogController controller = (EditAccountDialogController) loader.getController();
            if (controller == null) {
                System.out.println("Null controller?");
            } else {
                controller.setMainApp(this);
            }
            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
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
            if (errorCode == 90049 || errorCode == 28000) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.initOwner(mPrimaryStage);
                alert.setTitle("Bad password");
                alert.setHeaderText("Wrong password for " + dbName);
                alert.showAndWait();
            } else {
                printSQLException(e);
                e.printStackTrace();
            }
        }
        if (mConnection == null) {
            return;
        }

        // save opened DB hist
        putOpenedDBNames(updateOpenedDBNames(getOpenedDBNames(), dbName));

        if (isNew) {
            initDBStructure();
        }
    }

    // initialize database structure
    private void initDBStructure() {
        if (mConnection == null)
            return;

        // Create and populate AccountType table
        String createCmd0 = "create table ACCOUNTTYPES ("
                + "ID integer NOT NULL AUTO_INCREMENT, "
                + "TYPE varchar(" + ACCUONTTYPELEN + ") NOT NULL, "
                + "PRIMARY KEY (ID));";

        String insertCmd0 = "insert into ACCOUNTTYPES (TYPE) VALUES ('Spending');";
        String insertCmd1 = "insert into ACCOUNTTYPES (TYPE) VALUES ('Investing');";
        String insertCmd2 = "insert into ACCOUNTTYPES (TYPE) VALUES ('Property');";
        String insertCmd3 = "insert into ACCOUNTTYPES (TYPE) VALUES ('Debt');";
        String createCmd1 = "create table ACCOUNTS ("
                + "ID integer NOT NULL AUTO_INCREMENT, "
                + "TYPE_ID integer NOT NULL, "
                + "NAME varchar(" + ACCOUNTNAMELEN + ") NOT NULL, "
                + "DESCRIPTION varchar(" + ACCOUNTDESCLEN + ") NOT NULL, "
                + "PRIMARY KEY (ID));";
        Statement stmt = null;
        try {
            stmt = mConnection.createStatement();
            stmt.executeUpdate(createCmd0);
            stmt.executeUpdate(insertCmd0);
            stmt.executeUpdate(insertCmd1);
            stmt.executeUpdate(insertCmd2);
            stmt.executeUpdate(insertCmd3);
            stmt.executeUpdate(createCmd1);
            stmt.close();
        } catch (SQLException e) {
            printSQLException(e);
            e.printStackTrace();
        } finally {
            try {
                if (stmt != null)
                    stmt.close();
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
            System.out.println("before loader.getController");
            ((MainController) loader.getController()).setMainApp(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        System.out.println("Stopping...");
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
        //initPrefs();

/*
        //Parent root = FXMLLoader.load(getClass().getResource("MainLayout.fxml"));
        //StackPane root = new StackPane();
        primaryStage.setTitle("FaCai168");
        final VBox vbox = new VBox();
        vbox.setAlignment(Pos.CENTER);


        Scene scene = new Scene(vbox, 300, 275);
        primaryStage.setScene(scene);

        // menuBar and fileMenu
        MenuBar menuBar = new MenuBar();

        Menu menuFile = new Menu("File");

        MenuItem miNew = new MenuItem("New...");
        miNew.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                System.out.println("New..." + event.toString());
            }
        });

        menuFile.getItems().addAll(miNew);
        menuBar.getMenus().addAll(menuFile);
        ((VBox) scene.getRoot()).getChildren().addAll(menuBar, vbox);

        primaryStage.show();

        Button button =  new Button();
        button.setText("Say Hi");
        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openFile(primaryStage);
            }
        });
        vbox.getChildren().add(button);
*/

    }

    public static void main(String[] args) {

        launch(args);
    }
}
