package net.taihuapp.facai168;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class MainApp extends Application {

    static int MAXOPENEDFILEHIST = 5; // keep max 5 opened files
    static String KEY_OPENEDFILESPREFIX = "OPENEDFILES#";

    Preferences mPrefs;
    private BorderPane mMainLayout;

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

    // init the main layout
    private void initMainLayout(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("MainLayout.fxml"));
            mMainLayout = (BorderPane) loader.load();
            stage.setScene(new Scene(mMainLayout));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        System.out.println("Stopping...");
    }

    @Override
    public void start(final Stage primaryStage) throws Exception {

        primaryStage.setTitle("FaCai168");
        initMainLayout(primaryStage);
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
