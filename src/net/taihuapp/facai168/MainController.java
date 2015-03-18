package net.taihuapp.facai168;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import java.util.ArrayList;
import java.util.List;

public class MainController {

    private MainApp mMainApp;

    @FXML
    private Menu mRecentDBMenu;

    public void setMainApp(MainApp mainApp) {
        mMainApp = mainApp;
        updateRecentMenu();
    }

    @FXML
    private void handleClose() {
        Platform.exit();
    }

    @FXML
    private void handleOpen() {
        mMainApp.openDatabase(false, null);
        updateRecentMenu();
    }

    @FXML
    private void handleNew() {
        mMainApp.openDatabase(true, null);
        updateRecentMenu();
    }

    @FXML
    private void handleBackup() {
        System.out.println("Backup");
    }

    @FXML
    private void handleClearList() {
        mMainApp.putOpenedDBNames(new ArrayList<String>());
        updateRecentMenu();
        System.out.println("Clear List");
    }

    public void updateRecentMenu() {
        EventHandler<ActionEvent> menuAction = new EventHandler<ActionEvent> () {
            public void handle(ActionEvent t) {
                MenuItem mi = (MenuItem) t.getTarget();
                mMainApp.openDatabase(false, mi.getText());
            }
        };
        ObservableList<MenuItem> recentList = mRecentDBMenu.getItems();
        recentList.remove(0, recentList.size()-2);
        List<String> newList = mMainApp.getOpenedDBNames();
        int n = newList.size();
        for (int i = 0; i < n; i++) {
            MenuItem mi = new MenuItem(newList.get(n-i-1));
            mi.setOnAction(menuAction);
            recentList.add(0, mi);
        }
    }

    @FXML
    private void initialize() {
    }
}
