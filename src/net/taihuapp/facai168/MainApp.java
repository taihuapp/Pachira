package net.taihuapp.facai168;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import sun.plugin.javascript.navig.Anchor;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class MainApp extends Application {

    static int MAXOPENEDFILEHIST = 5; // keep max 5 opened files
    static String KEY_OPENEDFILESPREFIX = "OPENEDFILES#";
    static String DBOWNER = "FC168ADM";

    private Preferences mPrefs;
    private BorderPane mMainLayout;
    private Stage mPrimaryStage;
    private Connection mConnection = null;

    private void initPrefs() {
        mPrefs = Preferences.userNodeForPackage(MainApp.class);
    }

    List<String> getOpenedFileNames(Preferences prefs) {
        List<String> fileNameList = new ArrayList<String>();

        for (int i = 0; i < MAXOPENEDFILEHIST; i++) {
            String fileName = prefs.get(KEY_OPENEDFILESPREFIX + i, "");
            if (!fileName.isEmpty()) {
                fileNameList.add(fileName);
            }
        }
        return fileNameList;
    }

    void putOpenedFileNames(List<String> openedFileNameList, Preferences prefs) {
        for (int i = 0; i < openedFileNameList.size(); i++) {
            prefs.put(KEY_OPENEDFILESPREFIX + i, openedFileNameList.get(i));
        }
    }

    List<String> updateOpenedFileNames(List<String> openedFileNameList, String fileName) {
        int idx = openedFileNameList.indexOf(fileName);
        if (idx > -1) {
            openedFileNameList.remove(idx);
        }
        openedFileNameList.add(0, fileName);  // always add on the top

        // keep only MAXOPENEDFILEHIST
        while (openedFileNameList.size() > MAXOPENEDFILEHIST) {
            openedFileNameList.remove(MAXOPENEDFILEHIST);
        }

        return openedFileNameList;
    }

    void openFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open FaCai file");
        File file;
        file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            List<String> openedFileNames = getOpenedFileNames(mPrefs);
            openedFileNames = updateOpenedFileNames(openedFileNames, file.getAbsolutePath());
            for (String s : openedFileNames) {
                System.out.println(s);
            }
            putOpenedFileNames(openedFileNames, mPrefs);
        }
    }

    // returns a new password or null
    private String showNewPasswordDialog() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("NewPasswordDialog.fxml"));
            AnchorPane pane = (AnchorPane) loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Set New Password");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mPrimaryStage);
            dialogStage.setScene(new Scene(pane));

            NewPasswordDialogController controller = loader.getController();
            controller.setmDialogStage(dialogStage);
            dialogStage.showAndWait();

            return controller.getPassword();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getConnectionURL(String dbName, boolean create, String bootPassword, String user, String password) {
        String url = "jdbc:derby:";
        if (dbName != null) {
            url += dbName + ";";
        }
        if (create) {
            url += "create=true;";
        }
        if (bootPassword != null) {
            url += "dataEncryption=true;encryptionAlgorithm=Blowfish/CBC/NoPadding;bootPassword="
                    + bootPassword + ";";
        }
        if (user != null) {
            url += "user=" + user + ";";
        }
        if (password != null) {
            url += "password=" + password + ";";
        }
        return url;
    }

    // create a new database
    public void newDB() {

        String password = showNewPasswordDialog();
        if (password == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(mPrimaryStage);
            alert.setTitle("Password not set");
            alert.setHeaderText("Need a password to continue...");
            alert.showAndWait();
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Create a new FaiCai168 database...");
        File file = fileChooser.showSaveDialog(mPrimaryStage);
        if (file == null) {
            return;
        }

        String dbName = file.getAbsolutePath();
        //System.setProperty("derby.authentication.provider", "Native:"  + dbName + ":LOCAL");

        try {
            // create a db without authentication first
            String url = getConnectionURL(dbName, true, password, DBOWNER, password);
            System.out.println(url);
            mConnection = DriverManager.getConnection(url);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (mConnection == null) {
            System.err.println("Failed to create database " + dbName);
            return;
        }

        System.out.println("Need to setup database structure here");
    }

    // init the main layout
    private void initMainLayout() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("MainLayout.fxml"));
            mMainLayout = (BorderPane) loader.load();
            mPrimaryStage.setScene(new Scene(mMainLayout));
            mPrimaryStage.show();

            ((MainController) loader.getController()).setMainApp(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        System.out.println("Stopping...");
        if (mConnection != null) {
            // close connection
            try {
                mConnection.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mConnection = null;
        }

        // Shutdown database
        try {
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
        } catch (SQLException e) {
            if ((e.getErrorCode() == 50000) && ("XJ015".equals(e.getSQLState()))) {
                System.out.println("Derby shutdown normally");
            } else {
                System.err.println("Derby didn't shut down normally.");
                e.printStackTrace();
            }
        }
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
